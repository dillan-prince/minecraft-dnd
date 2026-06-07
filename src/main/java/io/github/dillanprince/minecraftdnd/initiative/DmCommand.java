package io.github.dillanprince.minecraftdnd.initiative;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /dm} — DM resolution of pending actions (approve/deny/list). DM-gated via
 * {@link DmAccess}. The Approve/Deny chat buttons in the prompt run these.
 *
 * <pre>
 *   /dm approve [id]   approve a pending action (default: the most recent)
 *   /dm deny [id]      deny/drop a pending action (default: the most recent)
 *   /dm pending        list outstanding pending actions
 * </pre>
 */
public final class DmCommand {

    private DmCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dm")
                .requires(DmAccess::isDm)
                .then(Commands.literal("approve")
                        .executes(ctx -> approveTop(ctx.getSource()))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> approve(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id")))))
                .then(Commands.literal("deny")
                        .executes(ctx -> denyTop(ctx.getSource()))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> deny(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id")))))
                .then(Commands.literal("pending")
                        .executes(ctx -> listPending(ctx.getSource())))
                .then(Commands.literal("remove")
                        .executes(ctx -> removeDowned(ctx.getSource())))
                .then(Commands.literal("revive")
                        .executes(ctx -> revive(ctx.getSource())))
                .then(Commands.literal("downed")
                        .executes(ctx -> listDowned(ctx.getSource()))));
    }

    private static int removeDowned(CommandSourceStack source) {
        int count = DownedManager.get().removeDowned(source.getServer());
        source.sendSuccess(() -> Component.literal("Removed " + plural(count, "downed enemy", "downed enemies") + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return count;
    }

    private static int revive(CommandSourceStack source) {
        int count = DownedManager.get().reviveAll(source.getServer());
        source.sendSuccess(() -> Component.literal("Revived " + plural(count, "entity", "entities") + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return count;
    }

    private static int listDowned(CommandSourceStack source) {
        int count = DownedManager.get().downedCount();
        source.sendSuccess(() -> Component.literal(plural(count, "downed entity", "downed entities") + ".")
                .withStyle(ChatFormatting.GRAY), false);
        return count;
    }

    /** Formats a count with the matching singular/plural noun, e.g. {@code "1 entity"} / {@code "3 entities"}. */
    private static String plural(int n, String singular, String pluralForm) {
        return n + " " + (n == 1 ? singular : pluralForm);
    }

    private static int approveTop(CommandSourceStack source) {
        return PendingActionManager.get().top()
                .map(a -> approve(source, a.id()))
                .orElseGet(() -> noPending(source));
    }

    private static int denyTop(CommandSourceStack source) {
        return PendingActionManager.get().top()
                .map(a -> deny(source, a.id()))
                .orElseGet(() -> noPending(source));
    }

    private static int approve(CommandSourceStack source, int id) {
        if (PendingActionManager.get().approve(id)) {
            source.sendSuccess(() -> Component.literal("Approved #" + id + ".").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        source.sendFailure(Component.literal("No pending action #" + id + "."));
        return 0;
    }

    private static int deny(CommandSourceStack source, int id) {
        if (PendingActionManager.get().deny(id)) {
            source.sendSuccess(() -> Component.literal("Denied #" + id + ".").withStyle(ChatFormatting.RED), false);
            return 1;
        }
        source.sendFailure(Component.literal("No pending action #" + id + "."));
        return 0;
    }

    private static int listPending(CommandSourceStack source) {
        var pending = PendingActionManager.get().list();
        if (pending.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No pending actions.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Pending actions:").withStyle(ChatFormatting.GOLD), false);
        for (PendingAction a : pending) {
            source.sendSuccess(() -> Component.literal("  #" + a.id() + " " + a.description()
                    + " (" + Math.max(0, a.ticksRemaining() / 20) + "s)").withStyle(ChatFormatting.YELLOW), false);
        }
        return pending.size();
    }

    private static int noPending(CommandSourceStack source) {
        source.sendFailure(Component.literal("No pending actions."));
        return 0;
    }
}
