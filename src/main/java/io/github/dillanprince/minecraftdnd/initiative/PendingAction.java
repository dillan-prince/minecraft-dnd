package io.github.dillanprince.minecraftdnd.initiative;

import java.util.UUID;

/**
 * A suspended action awaiting DM approval. The continuation ({@code onApprove}) is the work
 * that would finish the action — it is stored, NOT run, until the DM approves. On deny or
 * timeout, {@code onDeny} runs instead and the continuation is dropped ("withhold, don't
 * undo": nothing happened, so there is nothing to reverse).
 */
public final class PendingAction {

    private final int id;
    private final String description;
    private final UUID casterId;
    private final Runnable onApprove;
    private final Runnable onDeny;
    private int ticksRemaining;

    public PendingAction(int id, String description, UUID casterId,
                         Runnable onApprove, Runnable onDeny, int timeoutTicks) {
        this.id = id;
        this.description = description;
        this.casterId = casterId;
        this.onApprove = onApprove;
        this.onDeny = onDeny;
        this.ticksRemaining = timeoutTicks;
    }

    public int id() {
        return id;
    }

    public String description() {
        return description;
    }

    public UUID casterId() {
        return casterId;
    }

    public int ticksRemaining() {
        return ticksRemaining;
    }

    void tickDown() {
        ticksRemaining--;
    }

    boolean expired() {
        return ticksRemaining <= 0;
    }

    void runApprove() {
        onApprove.run();
    }

    void runDeny() {
        onDeny.run();
    }
}
