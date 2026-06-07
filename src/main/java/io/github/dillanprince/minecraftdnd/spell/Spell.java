package io.github.dillanprince.minecraftdnd.spell;

/**
 * A single spell definition. Plain data (no client/server types) so it can be referenced
 * from both the client spellbook screen and the eventual server-side cast pipeline.
 *
 * @param id          stable identifier (lowercase, e.g. "fire_bolt")
 * @param name        display name
 * @param type        action-economy classification
 * @param level       spell level; 0 == cantrip
 * @param description short rules text shown in the spellbook
 */
public record Spell(String id, String name, SpellType type, int level, String description) {

    public boolean isCantrip() {
        return level == 0;
    }

    /** Human-readable level + type line, e.g. "Cantrip • Action" or "Level 3 • Action". */
    public String metaLine() {
        String levelText = isCantrip() ? "Cantrip" : "Level " + level;
        return levelText + "  •  " + type.label();
    }
}
