package io.github.dillanprince.minecraftdnd.initiative;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.dillanprince.minecraftdnd.minecraftdnd;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

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

    public boolean hasPendingFor(UUID casterId) {
        return stack.stream().anyMatch(p -> p.casterId().equals(casterId));
    }

    /**
     * Suspend an action and open its DM-approval window. Returns the assigned id. Does NOT
     * run the continuation — that happens only on {@link #approve}.
     */
    public int submit(String description, UUID casterId, Runnable onApprove, Runnable onDeny,
                      int timeoutTicks, MinecraftServer server) {
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
        return true;
    }

    public boolean deny(int id) {
        PendingAction action = remove(id);
        if (action == null) {
            return false;
        }
        action.runDeny();
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
            if (server != null) {
                for (ServerPlayer dm : DmAccess.dmPlayers(server)) {
                    dm.sendSystemMessage(Component.literal("#" + a.id() + " " + a.description() + " timed out (denied).")
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        INSTANCE.tick(event.getServer());
    }

    private void promptDm(MinecraftServer server, PendingAction action, int timeoutTicks) {
        int seconds = Math.max(1, timeoutTicks / 20);
        MutableComponent approve = Component.literal(" [Approve]")
                .withStyle(s -> s.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.RunCommand("/dm approve " + action.id())));
        MutableComponent denyButton = Component.literal(" [Deny]")
                .withStyle(s -> s.withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent.RunCommand("/dm deny " + action.id())));
        MutableComponent message = Component.literal("[DM] #" + action.id() + " " + action.description() + " (" + seconds + "s)")
                .withStyle(ChatFormatting.GOLD)
                .append(approve)
                .append(denyButton);
        for (ServerPlayer dm : DmAccess.dmPlayers(server)) {
            dm.sendSystemMessage(message);
        }
    }
}
