package io.github.dillanprince.minecraftdnd.client;

import io.github.dillanprince.minecraftdnd.network.CastSpellPayload;
import io.github.dillanprince.minecraftdnd.spell.Spell;
import io.github.dillanprince.minecraftdnd.spell.Spells;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Client-side spellbook browser. A list of spell buttons on the left; the selected spell's
 * details render on the right. Pure rendering — no server interaction yet (casting will be
 * a separate, server-gated packet). Not a pause screen, so players can browse off-turn.
 */
public class SpellbookScreen extends Screen {

    private static final int LIST_X = 20;
    private static final int LIST_TOP = 40;
    private static final int BUTTON_W = 140;
    private static final int BUTTON_H = 20;
    private static final int ROW_GAP = 4;

    private static final int DETAIL_X = 180;
    private static final int GOLD = 0xFFFFD700;
    private static final int GREY = 0xFFAAAAAA;
    private static final int WHITE = 0xFFFFFFFF;

    private Spell selected;

    public SpellbookScreen() {
        super(Component.literal("Spellbook"));
    }

    @Override
    protected void init() {
        super.init();
        if (selected == null && !Spells.ALL.isEmpty()) {
            selected = Spells.ALL.getFirst();
        }
        int y = LIST_TOP;
        for (Spell spell : Spells.ALL) {
            final Spell entry = spell;
            addRenderableWidget(Button.builder(Component.literal(spell.name()), b -> this.selected = entry)
                    .bounds(LIST_X, y, BUTTON_W, BUTTON_H)
                    .build());
            y += BUTTON_H + ROW_GAP;
        }

        // Cast the selected spell. The server validates turn/budget and resolves the effect.
        addRenderableWidget(Button.builder(Component.literal("Cast"), b -> castSelected())
                .bounds(DETAIL_X, this.height - 40, 100, BUTTON_H)
                .build());
    }

    private void castSelected() {
        if (selected != null) {
            ClientPacketDistributor.sendToServer(new CastSpellPayload(selected.id()));
            onClose();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 16, WHITE);

        if (selected != null) {
            graphics.text(this.font, selected.name(), DETAIL_X, LIST_TOP, GOLD, true);
            graphics.text(this.font, selected.metaLine(), DETAIL_X, LIST_TOP + 14, GREY, true);
            graphics.textWithWordWrap(this.font, Component.literal(selected.description()),
                    DETAIL_X, LIST_TOP + 34, this.width - DETAIL_X - 20, WHITE);
        }
    }

    @Override
    public boolean isPauseScreen() {
        // Don't pause: browsing is allowed any time, including off-turn planning.
        return false;
    }
}
