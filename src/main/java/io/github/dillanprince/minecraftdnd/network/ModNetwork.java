package io.github.dillanprince.minecraftdnd.network;

import java.util.UUID;

import io.github.dillanprince.minecraftdnd.initiative.InitiativeManager;
import io.github.dillanprince.minecraftdnd.initiative.PendingActionManager;
import io.github.dillanprince.minecraftdnd.minecraftdnd;
import io.github.dillanprince.minecraftdnd.spell.Spell;
import io.github.dillanprince.minecraftdnd.spell.SpellEffects;
import io.github.dillanprince.minecraftdnd.spell.SpellType;
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

    /** DM-approval window length for a suspended cast. */
    private static final int APPROVAL_TIMEOUT_TICKS = 30 * 20; // 30 seconds

    private static void resolveCast(ServerPlayer player, String spellId) {
        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            deny(player, "Unknown spell.");
            return;
        }

        InitiativeManager manager = InitiativeManager.get();
        UUID id = player.getUUID();
        boolean inCombat = manager.isActive() && manager.isParticipant(id);

        // Out of combat (or for a non-participant such as a spectating DM), resolve at once.
        if (!inCombat) {
            resolveEffect(player, spell);
            return;
        }

        // In combat: validate turn + action budget (but don't spend yet), then suspend the
        // cast as a pending action awaiting the DM. The budget is spent and the effect runs
        // only on approval — nothing happens if it's denied or times out.
        String error = validate(manager, id, spell.type());
        if (error != null) {
            deny(player, error);
            return;
        }
        if (PendingActionManager.get().hasPendingFor(id)) {
            deny(player, "You already have an action awaiting the DM.");
            return;
        }

        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        String description = player.getName().getString() + " casts " + spell.name() + " (" + spell.type().label() + ")";
        PendingActionManager.get().submit(
                description,
                id,
                () -> { // onApprove: spend budget and resolve the effect
                    spendBudget(manager, id, spell.type());
                    resolveEffect(player, spell);
                },
                () -> player.sendSystemMessage( // onDeny
                        Component.literal("Your " + spell.name() + " was not allowed.").withStyle(ChatFormatting.RED)),
                APPROVAL_TIMEOUT_TICKS,
                server);
        player.sendSystemMessage(Component.literal("Casting " + spell.name() + " — awaiting the DM's approval...")
                .withStyle(ChatFormatting.GRAY));
    }

    /** Returns an error message if the cast is not permitted by turn/budget rules, else null. */
    private static String validate(InitiativeManager manager, UUID id, SpellType type) {
        return switch (type) {
            case ACTION -> {
                if (!manager.isCurrent(id)) {
                    yield "It's not your turn.";
                }
                yield manager.hasAction(id) ? null : "You've already used your action this turn.";
            }
            case BONUS_ACTION -> {
                if (!manager.isCurrent(id)) {
                    yield "It's not your turn.";
                }
                yield manager.hasBonus(id) ? null : "You've already used your bonus action this turn.";
            }
            // Reactions may be cast off-turn, as long as one is available.
            case REACTION -> manager.hasReaction(id) ? null : "You've already used your reaction.";
        };
    }

    private static void spendBudget(InitiativeManager manager, UUID id, SpellType type) {
        switch (type) {
            case ACTION -> manager.spendAction(id);
            case BONUS_ACTION -> manager.spendBonus(id);
            case REACTION -> manager.spendReaction(id);
        }
    }

    /** Announce the cast to the table, then apply the spell's server-side effect. */
    private static void resolveEffect(ServerPlayer player, Spell spell) {
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal(player.getName().getString() + " casts " + spell.name() + "!")
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    false);
        }
        SpellEffects.resolve(player, spell);
    }

    private static void deny(ServerPlayer player, String reason) {
        player.sendSystemMessage(Component.literal(reason).withStyle(ChatFormatting.RED));
    }
}
