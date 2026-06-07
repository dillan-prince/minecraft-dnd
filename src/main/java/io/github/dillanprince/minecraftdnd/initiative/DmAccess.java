package io.github.dillanprince.minecraftdnd.initiative;

import java.util.List;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared DM-identity logic. Per the chosen design, the DM is the world host (integrated /
 * LAN singleplayer owner); the server console also counts. On a true dedicated server there
 * is no host player, so this would need an explicit DM designation.
 */
public final class DmAccess {

    private DmAccess() {}

    /** Command predicate: may this source run DM commands? */
    public static boolean isDm(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return true; // server console / command block on the host
        }
        return isDm(source.getServer(), player);
    }

    /** True if this player is the DM (world host). */
    public static boolean isDm(MinecraftServer server, ServerPlayer player) {
        return server.isSingleplayerOwner(player.nameAndId());
    }

    /** Online players who are the DM, for routing prompts. */
    public static List<ServerPlayer> dmPlayers(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream()
                .filter(p -> isDm(server, p))
                .toList();
    }
}
