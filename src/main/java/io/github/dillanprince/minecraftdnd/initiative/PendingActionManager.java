package io.github.dillanprince.minecraftdnd.initiative;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.dillanprince.minecraftdnd.minecraftdnd;
import io.github.dillanprince.minecraftdnd.network.CloseApprovalPayload;
import io.github.dillanprince.minecraftdnd.network.OpenApprovalPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side registry of {@link PendingAction}s awaiting DM resolution. Actions are held
 * as a stack so nested reactions (e.g. a counterspell cast in response to a cast) unwind in
 * reverse. A per-tick countdown enforces the approval window without ever blocking the tick:
 * the handler returns immediately, and the next server tick advances the timers.
 */
@EventBusSubscriber(modid = minecraftdnd.MODID)
public final class PendingActionManager {

    private static final PendingActionManager INSTANCE = new PendingActionManager();

    public static PendingActionManager get() {
        return INSTANCE;
    }

    private PendingActionManager() {}

    /** Newest action is the tail (top of stack). */
    private final Deque<PendingAction> stack = new ArrayDeque<>();
    private int nextId = 1;
    /** Last server seen (set on submit/tick); used to push popup-close packets to DMs. */
    private MinecraftServer server;

    public boolean hasPendingFor(UUID casterId) {
        return stack.stream().anyMatch(p -> p.casterId().equals(casterId));
    }

    /**
     * Suspend an action and open its DM-approval window. Returns the assigned id. Does NOT
     * run the continuation — that happens only on {@link #approve}.
     */
    public int submit(String description, UUID casterId, Runnable onApprove, Runnable onDeny,
                      int timeoutTicks, MinecraftServer server) {
        this.server = server;
        int id = nextId++;
        PendingAction action = new PendingAction(id, description, casterId, onApprove, onDeny, timeoutTicks);
        stack.addLast(action);
        promptDm(server, action, timeoutTicks);
        return id;
    }

    public boolean approve(int id) {
        PendingAction action = remove(id);
        if (action == null) {
            return false;
        }
        action.runApprove();
        sendClose(id);
        return true;
    }

    public boolean deny(int id) {
        PendingAction action = remove(id);
        if (action == null) {
            return false;
        }
        action.runDeny();
        sendClose(id);
        return true;
    }

    /** The most recently submitted pending action (top of stack), if any. */
    public Optional<PendingAction> top() {
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.peekLast());
    }

    public List<PendingAction> list() {
        return List.copyOf(stack);
    }

    /** Drop all pending actions without running their continuations (e.g. on encounter end). */
    public void clearAll() {
        stack.clear();
    }

    private PendingAction remove(int id) {
        Iterator<PendingAction> it = stack.iterator();
        while (it.hasNext()) {
            PendingAction a = it.next();
            if (a.id() == id) {
                it.remove();
                return a;
            }
        }
        return null;
    }

    /** Advance approval-window timers; expired actions are dropped (treated as denied). */
    void tick(MinecraftServer server) {
        this.server = server;
        if (stack.isEmpty()) {
            return;
        }
        List<PendingAction> expired = new ArrayList<>();
        for (PendingAction a : stack) {
            a.tickDown();
            if (a.expired()) {
                expired.add(a);
            }
        }
        for (PendingAction a : expired) {
            stack.remove(a);
            a.runDeny();
            sendClose(a.id());
            if (server != null) {
                for (ServerPlayer dm : DmAccess.dmPlayers(server)) {
                    dm.sendSystemMessage(Component.literal("#" + a.id() + " " + a.description() + " timed out (denied).")
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }

    /** Tell DM clients to dismiss the popup for this action id, if they're showing it. */
    private void sendClose(int id) {
        if (server == null) {
            return;
        }
        for (ServerPlayer dm : DmAccess.dmPlayers(server)) {
            PacketDistributor.sendToPlayer(dm, new CloseApprovalPayload(id));
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        INSTANCE.tick(event.getServer());
    }

    private void promptDm(MinecraftServer server, PendingAction action, int timeoutTicks) {
        int seconds = Math.max(1, timeoutTicks / 20);
        Component message = Component.literal("[DM] #" + action.id() + " " + action.description()
                + " (" + seconds + "s) — resolve in the popup, or /dm approve " + action.id() + " / /dm deny " + action.id())
                .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer dm : DmAccess.dmPlayers(server)) {
            dm.sendSystemMessage(message);
            PacketDistributor.sendToPlayer(dm, new OpenApprovalPayload(action.id(), action.description()));
        }
    }

    /** Re-open the popup for the current top pending action on the DM's client(s), if any. */
    public void promptNext(MinecraftServer server) {
        top().ifPresent(action -> {
            for (ServerPlayer dm : DmAccess.dmPlayers(server)) {
                PacketDistributor.sendToPlayer(dm, new OpenApprovalPayload(action.id(), action.description()));
            }
        });
    }
}
