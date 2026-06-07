package io.github.dillanprince.minecraftdnd.spell;

/**
 * Action-economy classification for a spell. Drives the off-turn budget gate later:
 * ACTION/BONUS_ACTION are only castable on your turn; REACTION may be cast off-turn while
 * a reaction is available. For now this is just display metadata in the spellbook.
 */
public enum SpellType {
    ACTION("Action"),
    BONUS_ACTION("Bonus Action"),
    REACTION("Reaction");

    private final String label;

    SpellType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
