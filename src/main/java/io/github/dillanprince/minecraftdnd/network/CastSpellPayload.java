package io.github.dillanprince.minecraftdnd.network;

import io.github.dillanprince.minecraftdnd.minecraftdnd;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Serverbound request to cast a spell by id. The server is authoritative: it validates
 * turn/action-budget and resolves the effect — the client only declares intent.
 */
public record CastSpellPayload(String spellId) implements CustomPacketPayload {

    public static final Type<CastSpellPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(minecraftdnd.MODID, "cast_spell"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastSpellPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, CastSpellPayload::spellId,
                    CastSpellPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
