package io.github.dillanprince.minecraftdnd.network;

import io.github.dillanprince.minecraftdnd.minecraftdnd;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Clientbound: tells the DM's client to open the approval popup for a pending action.
 */
public record OpenApprovalPayload(int id, String description) implements CustomPacketPayload {

    public static final Type<OpenApprovalPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(minecraftdnd.MODID, "open_approval"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenApprovalPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, OpenApprovalPayload::id,
                    ByteBufCodecs.STRING_UTF8, OpenApprovalPayload::description,
                    OpenApprovalPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
