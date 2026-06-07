package io.github.dillanprince.minecraftdnd.network;

import io.github.dillanprince.minecraftdnd.minecraftdnd;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Serverbound: the DM's resolution of a pending action (approve or deny) from the popup.
 */
public record ResolvePendingPayload(int id, boolean approve) implements CustomPacketPayload {

    public static final Type<ResolvePendingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(minecraftdnd.MODID, "resolve_pending"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResolvePendingPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ResolvePendingPayload::id,
                    ByteBufCodecs.BOOL, ResolvePendingPayload::approve,
                    ResolvePendingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
