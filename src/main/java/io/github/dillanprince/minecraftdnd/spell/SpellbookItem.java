package io.github.dillanprince.minecraftdnd.spell;

import io.github.dillanprince.minecraftdnd.client.SpellbookClient;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The spellbook item. Right-clicking opens the client-side spellbook screen. Browsing is
 * pure client rendering and always allowed (even off-turn) — only the eventual cast packet
 * will be server-gated.
 */
public class SpellbookItem extends Item {

    public SpellbookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // Open the screen only on the logical client. The reference to the client-only
        // SpellbookClient is never reached on a dedicated server (lazy class loading), so
        // this stays server-safe.
        if (level.isClientSide()) {
            SpellbookClient.openSpellbook();
        }
        return InteractionResult.SUCCESS;
    }
}
