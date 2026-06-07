package io.github.dillanprince.minecraftdnd.spell;

import java.util.List;

/**
 * Static catalog of available spells. This is a placeholder for the MVP spellbook GUI so
 * there is something to browse; later, spells known per-player will live in a data
 * attachment and spells bound to a specific book in a data component (per CLAUDE.md).
 */
public final class Spells {

    private Spells() {}

    public static final Spell FIRE_BOLT = new Spell(
            "fire_bolt", "Fire Bolt", SpellType.ACTION, 0,
            "Hurl a mote of fire at a creature or object within range. On a hit it takes 1d10 fire damage.");

    public static final Spell MAGIC_MISSILE = new Spell(
            "magic_missile", "Magic Missile", SpellType.ACTION, 1,
            "Create three glowing darts of magical force, each dealing 1d4+1 force damage to a target you can see.");

    public static final Spell HEALING_WORD = new Spell(
            "healing_word", "Healing Word", SpellType.BONUS_ACTION, 1,
            "A creature of your choice within range regains hit points equal to 2d4 + your spellcasting modifier.");

    public static final Spell SHIELD = new Spell(
            "shield", "Shield", SpellType.REACTION, 1,
            "An invisible barrier of force protects you. Until your next turn you have +5 AC, including against the triggering attack.");

    public static final Spell FIREBALL = new Spell(
            "fireball", "Fireball", SpellType.ACTION, 3,
            "A bright streak flashes to a point you choose and blossoms into flame. Each creature in a 20-foot radius takes 8d6 fire damage.");

    /** All spells, in browse order. */
    public static final List<Spell> ALL = List.of(FIRE_BOLT, MAGIC_MISSILE, HEALING_WORD, SHIELD, FIREBALL);

    /** Look up a spell by its id, or null if unknown. Used to validate incoming cast packets. */
    public static Spell byId(String id) {
        for (Spell spell : ALL) {
            if (spell.id().equals(id)) {
                return spell;
            }
        }
        return null;
    }
}
