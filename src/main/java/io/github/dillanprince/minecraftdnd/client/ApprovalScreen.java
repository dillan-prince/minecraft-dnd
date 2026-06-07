package io.github.dillanprince.minecraftdnd.client;

import io.github.dillanprince.minecraftdnd.network.ResolvePendingPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * DM-facing popup to approve or deny a pending action. The decision is sent to the server
 * (authoritative); closing without choosing leaves the action pending until the DM resolves
 * it (via popup/command) or it times out. Not a pause screen, so the approval timer keeps
 * running while it's open.
 */
public class ApprovalScreen extends Screen {

    private final int actionId;
    private final String description;
    /** Screen that was showing when this popup opened, restored when it closes (may be null). */
    private final @Nullable Screen previousScreen;

    public ApprovalScreen(int actionId, String description, @Nullable Screen previousScreen) {
        super(Component.literal("Pending Action"));
        this.actionId = actionId;
        this.description = description;
        this.previousScreen = previousScreen;
    }

    public int actionId() {
        return actionId;
    }

    public @Nullable Screen previousScreen() {
        return previousScreen;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(previousScreen);
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int buttonY = this.height / 2 + 20;
        addRenderableWidget(Button.builder(Component.literal("Approve").withStyle(ChatFormatting.GREEN), b -> resolve(true))
                .bounds(centerX - 105, buttonY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Deny").withStyle(ChatFormatting.RED), b -> resolve(false))
                .bounds(centerX + 5, buttonY, 100, 20).build());
    }

    private void resolve(boolean approve) {
        ClientPacketDistributor.sendToServer(new ResolvePendingPayload(actionId, approve));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 30, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.literal(description), this.width / 2, this.height / 2 - 10, 0xFFFFD700);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
