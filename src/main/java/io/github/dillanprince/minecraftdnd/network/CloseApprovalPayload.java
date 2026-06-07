package io.github.dillanprince.minecraftdnd.network;

import io.github.dillanprince.minecraftdnd.minecraftdnd;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Clientbound: tells the DM's client to dismiss the approval popup for a given action id, if
 * it's currently showing it (because the action was resolved elsewhere or timed out).
 */
public record CloseApprovalPayload(int id) implements CustomPacketPayload {

    public static final Type<CloseApprovalPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(minecraftdnd.MODID, "close_approval"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CloseApprovalPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CloseApprovalPayload::id, CloseApprovalPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
