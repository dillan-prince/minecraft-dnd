package io.github.dillanprince.minecraftdnd.client;

import io.github.dillanprince.minecraftdnd.minecraftdnd;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Replaces the vanilla heart bar with a numeric HP readout. D&D hit points blow past the
 * 10-heart display, so we drive HP off the max-health attribute and show "HP cur / max"
 * where the hearts used to be. Colored by remaining fraction.
 */
@EventBusSubscriber(modid = minecraftdnd.MODID, value = Dist.CLIENT)
public final class HealthHud {

    private HealthHud() {}

    private static final int GREEN = 0xFF55FF55;
    private static final int ORANGE = 0xFFFFAA00;
    private static final int RED = 0xFFFF5555;

    @SubscribeEvent
    static void onRegisterLayers(RegisterGuiLayersEvent event) {
        // Swap the heart bar for our readout, keeping its place in the HUD layer order.
        event.replaceLayer(VanillaGuiLayers.PLAYER_HEALTH, HealthHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        // Vanilla only shows the health bar in survival/adventure.
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        int current = (int) Math.ceil(player.getHealth());
        int max = (int) Math.round(player.getMaxHealth());
        String text = "HP " + current + " / " + max;
        int color = healthColor(player.getHealth(), player.getMaxHealth());

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int x = screenWidth / 2 - 91;   // left edge of the hotbar (where hearts start)
        int y = screenHeight - 39;      // the hearts row, just above the hotbar
        graphics.text(minecraft.font, text, x, y, color, true);
    }

    private static int healthColor(float current, float max) {
        float fraction = max <= 0 ? 0 : current / max;
        if (fraction > 0.5F) {
            return GREEN;
        }
        if (fraction > 0.25F) {
            return ORANGE;
        }
        return RED;
    }
}
