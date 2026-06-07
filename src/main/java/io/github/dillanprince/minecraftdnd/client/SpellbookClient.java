package io.github.dillanprince.minecraftdnd.client;

import net.minecraft.client.Minecraft;

/**
 * Client-only entry point for opening spellbook UI. Kept separate from the (common)
 * {@code SpellbookItem} so the item class never hard-loads client rendering types — it's
 * only ever reached from {@code SpellbookItem.use} behind a {@code level.isClientSide()}
 * guard, so it is never loaded on a dedicated server.
 */
public final class SpellbookClient {

    private SpellbookClient() {}

    public static void openSpellbook() {
        Minecraft.getInstance().setScreen(new SpellbookScreen());
    }
}
