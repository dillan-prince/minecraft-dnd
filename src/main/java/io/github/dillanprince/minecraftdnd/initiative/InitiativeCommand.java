package io.github.dillanprince.minecraftdnd.initiative;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.github.dillanprince.minecraftdnd.initiative.InitiativeManager.Participant;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * {@code /initiative} (alias {@code /init}) — the DM-facing command set for the initiative
 * spine. Op-gated (LEVEL_GAMEMASTERS == legacy permission level 2).
 *
 * <pre>
 *   /initiative start   roll d20 per non-spectator player, sort desc, broadcast order
 *   /initiative next    advance the turn (commits prior turn once rewind lands)
 *   /initiative end     leave initiative, release everyone
 *   /initiative status  show order and whose turn it is
 * </pre>
 */
public final class InitiativeCommand {

    private InitiativeCommand() {}

    /**
     * DM gate (per chosen design): only the world host may run DM commands. On an
     * integrated/LAN server that's the singleplayer owner; the server console is also
     * allowed. NOTE: on a true dedicated server there is no host player, so only the
     * console passes — this will need an explicit DM designation if we deploy that way.
     */
    private static boolean isDm(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return true; // server console / command block on the host
        }
        return source.getServer().isSingleplayerOwner(player.nameAndId());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("initiative")
                .requires(InitiativeCommand::isDm)
                .then(Commands.literal("start").executes(ctx -> start(ctx.getSource())))
                .then(Commands.literal("next").executes(ctx -> next(ctx.getSource())))
                .then(Commands.literal("end").executes(ctx -> end(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())));

        com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> node = dispatcher.register(command);
        // Alias: /init -> /initiative
        dispatcher.register(Commands.literal("init")
                .requires(InitiativeCommand::isDm)
                .redirect(node));
    }

    private static int start(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        InitiativeManager manager = InitiativeManager.get();

        // Participants = all non-spectator players currently online.
        List<ServerPlayer> players = server.getPlayerList().getPlayers().stream()
                .filter(p -> !p.isSpectator())
                .toList();

        if (players.isEmpty()) {
            source.sendFailure(Component.literal("No non-spectator players to roll initiative for."));
            return 0;
        }

        RandomSource random = RandomSource.create();
        List<Participant> order = manager.start(players, random);

        Component header = Component.literal("Initiative order (round 1):").withStyle(ChatFormatting.GOLD);
        server.getPlayerList().broadcastSystemMessage(header, false);
        int position = 1;
        for (Participant p : order) {
            Component line = Component.literal("  " + position + ". " + p.name() + "  (" + p.roll() + ")")
                    .withStyle(ChatFormatting.YELLOW);
            server.getPlayerList().broadcastSystemMessage(line, false);
            position++;
        }
        announceTurn(server, manager);
        return order.size();
    }

    private static int next(CommandSourceStack source) {
        InitiativeManager manager = InitiativeManager.get();
        if (!manager.isActive()) {
            source.sendFailure(Component.literal("Initiative is not active. Use /initiative start."));
            return 0;
        }
        manager.next();
        announceTurn(source.getServer(), manager);
        return 1;
    }

    private static int end(CommandSourceStack source) {
        InitiativeManager manager = InitiativeManager.get();
        if (!manager.isActive()) {
            source.sendFailure(Component.literal("Initiative is not active."));
            return 0;
        }
        manager.end();
        source.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("Initiative ended.").withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        InitiativeManager manager = InitiativeManager.get();
        if (!manager.isActive()) {
            source.sendSuccess(() -> Component.literal("Initiative is not active."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Initiative order (round " + manager.round() + "):")
                .withStyle(ChatFormatting.GOLD), false);
        int position = 1;
        for (Participant p : manager.order()) {
            boolean isCurrent = manager.isCurrent(p.id());
            String marker = isCurrent ? " <- current turn" : "";
            ChatFormatting color = isCurrent ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
            final int pos = position;
            source.sendSuccess(() -> Component.literal("  " + pos + ". " + p.name() + "  (" + p.roll() + ")" + marker)
                    .withStyle(color), false);
            position++;
        }
        return 1;
    }

    private static void announceTurn(MinecraftServer server, InitiativeManager manager) {
        manager.current().ifPresent(p -> {
            Component msg = Component.literal("It's " + p.name() + "'s turn (round " + manager.round() + ").")
                    .withStyle(ChatFormatting.GREEN);
            server.getPlayerList().broadcastSystemMessage(msg, false);
        });
    }
}
