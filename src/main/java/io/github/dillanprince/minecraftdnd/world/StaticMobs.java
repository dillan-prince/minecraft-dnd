package io.github.dillanprince.minecraftdnd.world;

import io.github.dillanprince.minecraftdnd.minecraftdnd;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Keeps hostile monsters static. They still spawn (so the DM has targets to work with), but
 * their AI is disabled the instant they enter the world, so they never wander or attack on
 * their own. The DM positions and runs them; players engage them through initiative.
 */
@EventBusSubscriber(modid = minecraftdnd.MODID)
public final class StaticMobs {

    private StaticMobs() {}

    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Mob mob && mob instanceof Enemy) {
            mob.setNoAi(true);
        }
    }
}
