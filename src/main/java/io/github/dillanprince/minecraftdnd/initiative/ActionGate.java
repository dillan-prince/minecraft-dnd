package io.github.dillanprince.minecraftdnd.initiative;

import java.util.UUID;

import io.github.dillanprince.minecraftdnd.minecraftdnd;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Full-freeze off-turn action gating (MVP rule from CLAUDE.md): while initiative is active,
 * any participant who is not the current-turn player is fully restricted.
 *
 * <p>Two mechanisms:
 * <ul>
 *   <li>Cancelable interactions (attack / block break / item &amp; block &amp; entity use) are
 *       canceled outright via {@code setCanceled(true)}.</li>
 *   <li>Movement has no cancelable event, so a per-server-tick snap-back teleports off-turn
 *       participants to a stored anchor. The current player's anchor follows them, pinning
 *       them where they stop when their turn ends.</li>
 * </ul>
 *
 * <p>Only <em>participants</em> are gated — non-participant players (e.g. a spectating DM)
 * are untouched. Non-participants who are also not the current player are simply ignored.
 */
@EventBusSubscriber(modid = minecraftdnd.MODID)
public final class ActionGate {

    private ActionGate() {}

    /** Squared distance (blocks^2) an off-turn player may drift before being snapped back. */
    private static final double TOLERANCE_SQ = 0.1 * 0.1;

    /** True if this player is a frozen (off-turn) participant whose actions should be blocked. */
    private static boolean isFrozen(Player player) {
        InitiativeManager manager = InitiativeManager.get();
        if (!manager.isActive()) {
            return false;
        }
        UUID id = player.getUUID();
        return manager.isParticipant(id) && !manager.isCurrent(id);
    }

    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        if (isFrozen(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelIfFrozen(event);
    }

    @SubscribeEvent
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelIfFrozen(event);
    }

    @SubscribeEvent
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelIfFrozen(event);
    }

    @SubscribeEvent
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelIfFrozen(event);
    }

    @SubscribeEvent
    static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        cancelIfFrozen(event);
    }

    private static <E extends PlayerInteractEvent & ICancellableEvent> void cancelIfFrozen(E event) {
        if (isFrozen(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * Per-tick movement enforcement. For each participant: the current player's anchor is
     * refreshed to their live position (free roam); every other participant is teleported
     * back to their anchor if they've drifted past the tolerance.
     */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        InitiativeManager manager = InitiativeManager.get();
        if (!manager.isActive()) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (!manager.isParticipant(id)) {
                continue;
            }
            if (manager.isCurrent(id)) {
                // Current player roams freely; anchor trails them so they're pinned on turn end.
                manager.setAnchor(id, player.position());
                continue;
            }
            Vec3 anchor = manager.getAnchor(id);
            if (anchor == null) {
                manager.setAnchor(id, player.position());
                continue;
            }
            if (player.position().distanceToSqr(anchor) > TOLERANCE_SQ) {
                // teleportTo(x,y,z) routes through the connection, syncing to the client and
                // zeroing velocity while preserving look direction.
                player.teleportTo(anchor.x, anchor.y, anchor.z);
            }
        }
    }
}
