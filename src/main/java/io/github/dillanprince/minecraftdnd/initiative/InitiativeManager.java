package io.github.dillanprince.minecraftdnd.initiative;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * Server-authoritative state for a D&D initiative encounter. This is the spine the rest of
 * the mod hangs off (action gating, snapshots, pending actions all key on the participant
 * list and current-turn pointer held here).
 *
 * <p>Single private session server, so this is a process-wide singleton reset on server
 * stop. State is intentionally simple — correctness and clarity over performance at our
 * ~20-entity scale.
 */
public final class InitiativeManager {
    private static final InitiativeManager INSTANCE = new InitiativeManager();

    public static InitiativeManager get() {
        return INSTANCE;
    }

    private InitiativeManager() {}

    /** One combatant in the turn order. For now participants are players; entity enemies come later. */
    public record Participant(UUID id, String name, int roll) {}

    private boolean active = false;
    private final List<Participant> order = new ArrayList<>();
    private int turnIndex = 0;
    private int round = 0;

    public boolean isActive() {
        return active;
    }

    public List<Participant> order() {
        return List.copyOf(order);
    }

    public int round() {
        return round;
    }

    /** The participant whose turn it currently is, or empty if not active / order empty. */
    public Optional<Participant> current() {
        if (!active || order.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(order.get(turnIndex));
    }

    /** True if the given player is the current-turn participant. Drives off-turn action gating. */
    public boolean isCurrent(UUID id) {
        return current().map(p -> p.id().equals(id)).orElse(false);
    }

    /**
     * Roll d20 for each participant, sort descending, and begin. Replaces any existing
     * encounter. Returns the established order (also useful for broadcasting).
     */
    public List<Participant> start(List<ServerPlayer> players, RandomSource random) {
        order.clear();
        for (ServerPlayer player : players) {
            int roll = random.nextInt(20) + 1; // d20
            order.add(new Participant(player.getUUID(), player.getName().getString(), roll));
        }
        // Sort by roll descending. Ties keep insertion order (stable sort) — DM can re-roll if it matters.
        order.sort((a, b) -> Integer.compare(b.roll(), a.roll()));
        turnIndex = 0;
        round = 1;
        active = true;
        return order();
    }

    /**
     * Advance to the next participant's turn (committing the prior turn — commit semantics
     * are layered on later by the rewind system). Wraps to a new round at the end of the
     * order. No-op if not active.
     *
     * @return the participant whose turn it now is, or empty if not active
     */
    public Optional<Participant> next() {
        if (!active || order.isEmpty()) {
            return Optional.empty();
        }
        turnIndex++;
        if (turnIndex >= order.size()) {
            turnIndex = 0;
            round++;
        }
        return current();
    }

    /** End the encounter and release everyone. */
    public void end() {
        active = false;
        order.clear();
        turnIndex = 0;
        round = 0;
    }
}
