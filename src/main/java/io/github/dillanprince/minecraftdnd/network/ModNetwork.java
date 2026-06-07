package io.github.dillanprince.minecraftdnd.network;

import java.util.UUID;

import io.github.dillanprince.minecraftdnd.initiative.InitiativeManager;
import io.github.dillanprince.minecraftdnd.minecraftdnd;
import io.github.dillanprince.minecraftdnd.spell.Spell;
import io.github.dillanprince.minecraftdnd.spell.Spells;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Mod networking: payload registration and server-side handling. Casting is fully
 * server-authoritative — the client only sends intent ({@link CastSpellPayload}); all
 * validation (whose turn, action budget) and effect resolution happen here.
 */
@EventBusSubscriber(modid = minecraftdnd.MODID)
public final class ModNetwork {

    private ModNetwork() {}

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(CastSpellPayload.TYPE, CastSpellPayload.CODEC, ModNetwork::onCast);
    }

    private static void onCast(CastSpellPayload payload, IPayloadContext context) {
        // Handlers run on the network thread; hop to the server thread to touch game state.
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                resolveCast(player, payload.spellId());
            }
        });
    }

    private static void resolveCast(ServerPlayer player, String spellId) {
        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            deny(player, "Unknown spell.");
            return;
        }

        // Action-economy gate, but only while this player is in an active encounter. Out of
        // combat (or for a non-participant such as a spectating DM), casting is unrestricted.
        InitiativeManager manager = InitiativeManager.get();
        UUID id = player.getUUID();
        if (manager.isActive() && manager.isParticipant(id)) {
            switch (spell.type()) {
                case ACTION -> {
                    if (!manager.isCurrent(id)) {
                        deny(player, "It's not your turn.");
                        return;
                    }
                    if (!manager.hasAction(id)) {
                        deny(player, "You've already used your action this turn.");
                        return;
                    }
                    manager.spendAction(id);
                }
                case BONUS_ACTION -> {
                    if (!manager.isCurrent(id)) {
                        deny(player, "It's not your turn.");
                        return;
                    }
                    if (!manager.hasBonus(id)) {
                        deny(player, "You've already used your bonus action this turn.");
                        return;
                    }
                    manager.spendBonus(id);
                }
                case REACTION -> {
                    // Reactions may be cast off-turn, as long as one is available.
                    if (!manager.hasReaction(id)) {
                        deny(player, "You've already used your reaction.");
                        return;
                    }
                    manager.spendReaction(id);
                }
            }
        }

        resolveEffect(player, spell);
    }

    /**
     * Apply the spell's effect. MVP: announce the cast to the table. Real per-spell effects
     * (damage, status, projectiles, particles) will be added here / dispatched per spell.
     */
    private static void resolveEffect(ServerPlayer player, Spell spell) {
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal(player.getName().getString() + " casts " + spell.name() + "!")
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    false);
        }
    }

    private static void deny(ServerPlayer player, String reason) {
        player.sendSystemMessage(Component.literal(reason).withStyle(ChatFormatting.RED));
    }
}
