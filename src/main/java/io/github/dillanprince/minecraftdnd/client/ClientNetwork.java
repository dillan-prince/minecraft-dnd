package io.github.dillanprince.minecraftdnd.client;

import io.github.dillanprince.minecraftdnd.network.CloseApprovalPayload;
import io.github.dillanprince.minecraftdnd.network.OpenApprovalPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Client-only handlers for clientbound payloads. Referenced lazily from the network
 * registration so the (common) registration code never hard-loads client classes.
 */
public final class ClientNetwork {

    private ClientNetwork() {}

    public static void openApproval(OpenApprovalPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        // Always surface the popup — even over the pause menu (which Minecraft auto-opens when
        // the DM alt-tabs away). Capture whatever was showing so it can be restored on close.
        Screen current = minecraft.screen;
        if (current instanceof ApprovalScreen existing) {
            if (existing.actionId() == payload.id()) {
                return; // already showing this exact action
            }
            // Replacing one popup with another: keep the original underlying screen, don't nest.
            minecraft.setScreen(new ApprovalScreen(payload.id(), payload.description(), existing.previousScreen()));
        } else {
            minecraft.setScreen(new ApprovalScreen(payload.id(), payload.description(), current));
        }
    }

    public static void closeApproval(CloseApprovalPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        // Only dismiss if we're actually showing this action's popup; restore what it covered.
        if (minecraft.screen instanceof ApprovalScreen screen && screen.actionId() == payload.id()) {
            minecraft.setScreen(screen.previousScreen());
        }
    }
}
