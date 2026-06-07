package io.github.dillanprince.minecraftdnd.initiative;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.dillanprince.minecraftdnd.minecraftdnd;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * "Downed, not dead." In this DM-run mode nothing actually dies: the killing blow is
 * cancelled and the entity drops into a frozen downed state (AI off, no further damage,
 * lying in the dying pose) that preserves its UUID. The DM then removes downed enemies or
 * revives anyone with {@code /dm remove} / {@code /dm revive}.
 *
 * <p>All item and XP drops are suppressed globally as well — partly belt-and-suspenders
 * (death never completes) and partly to cover any non-lethal drop paths.
 */
@EventBusSubscriber(modid = minecraftdnd.MODID)
public final class DownedManager {

    private static final DownedManager INSTANCE = new DownedManager();

    public static DownedManager get() {
        return INSTANCE;
    }

    private DownedManager() {}

    /** HP a downed entity is held at — positive so it doesn't re-trigger death each tick. */
    private static final float DOWNED_HEALTH = 1.0F;

    private final Map<UUID, DownedInfo> downed = new HashMap<>();

    /** Saved name-display state so an entity can be restored exactly on revive. */
    private record DownedInfo(Component originalName, boolean originalNameVisible) {}

    public boolean isDowned(UUID id) {
        return downed.containsKey(id);
    }

    public int downedCount() {
        return downed.size();
    }

    public void clearAll() {
        downed.clear();
    }

    // --- events ---

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        event.setCanceled(true);
        INSTANCE.markDowned(entity);
    }

    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        event.setCanceled(true); // no item drops, ever
    }

    @SubscribeEvent
    static void onExperience(LivingExperienceDropEvent event) {
        event.setCanceled(true); // no XP, ever
    }

    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        // A downed entity is out of the fight — ignore further damage so it stays put.
        if (INSTANCE.downed.containsKey(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    /** Keep downed entities lying down and motionless (poses/positions get recomputed). */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (INSTANCE.downed.isEmpty()) {
            return;
        }
        for (UUID id : INSTANCE.downed.keySet()) {
            LivingEntity entity = INSTANCE.find(event.getServer(), id);
            if (entity != null) {
                if (entity.getPose() != Pose.DYING) {
                    entity.setPose(Pose.DYING);
                }
                entity.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    // --- state ---

    private void markDowned(LivingEntity entity) {
        if (downed.containsKey(entity.getUUID())) {
            return;
        }
        downed.put(entity.getUUID(), new DownedInfo(entity.getCustomName(), entity.isCustomNameVisible()));

        String baseName = entity.getName().getString();
        entity.setHealth(DOWNED_HEALTH);
        entity.setDeltaMovement(Vec3.ZERO);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        entity.setPose(Pose.DYING); // renders the collapse for players; mobs use the marker below
        // Visual marker: glowing outline + a red "(downed)" name tag, since most mobs won't
        // visually lie down without actually dying.
        entity.setGlowingTag(true);
        entity.setCustomName(Component.literal(baseName + " (downed)").withStyle(ChatFormatting.DARK_RED));
        entity.setCustomNameVisible(true);
        announce(entity, baseName + " has fallen!");
    }

    public void revive(LivingEntity entity) {
        DownedInfo info = downed.remove(entity.getUUID());
        if (info == null) {
            return;
        }
        if (entity instanceof Mob mob) {
            mob.setNoAi(false);
        }
        entity.setPose(Pose.STANDING);
        entity.setGlowingTag(false);
        entity.setCustomName(info.originalName());          // null clears it back to the default name
        entity.setCustomNameVisible(info.originalNameVisible());
        entity.setHealth(entity.getMaxHealth());
    }

    /** Revive every downed entity (mobs regain AI; all restored to full health). */
    public int reviveAll(MinecraftServer server) {
        int count = 0;
        for (UUID id : Set.copyOf(downed.keySet())) {
            LivingEntity entity = find(server, id);
            if (entity != null) {
                revive(entity);
                count++;
            } else {
                downed.remove(id);
            }
        }
        return count;
    }

    /** Remove (discard) every downed non-player entity. Players are left for revive. */
    public int removeDowned(MinecraftServer server) {
        int count = 0;
        for (UUID id : Set.copyOf(downed.keySet())) {
            LivingEntity entity = find(server, id);
            if (entity == null) {
                downed.remove(id);
                continue;
            }
            if (entity instanceof Player) {
                continue; // never discard a player
            }
            entity.remove(Entity.RemovalReason.DISCARDED);
            downed.remove(id);
            count++;
        }
        return count;
    }

    private LivingEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private void announce(LivingEntity entity, String message) {
        MinecraftServer server = entity.level().getServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal(message).withStyle(ChatFormatting.DARK_RED), false);
        }
    }
}
