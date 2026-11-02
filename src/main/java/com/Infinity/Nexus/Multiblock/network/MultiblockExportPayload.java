package com.Infinity.Nexus.Multiblock.network;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MultiblockExportPayload(String json) implements CustomPacketPayload {
    public static final Type<MultiblockExportPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "multiblock_export"));
    private static final int MAX_JSON_LENGTH = 1_048_576;

    public static final StreamCodec<RegistryFriendlyByteBuf, MultiblockExportPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public MultiblockExportPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new MultiblockExportPayload(buffer.readUtf(MAX_JSON_LENGTH));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MultiblockExportPayload payload) {
                    buffer.writeUtf(payload.json(), MAX_JSON_LENGTH);
                }
            };

    @Override
    public Type<MultiblockExportPayload> type() {
        return TYPE;
    }

    public static void handle(MultiblockExportPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().keyboardHandler.setClipboard(payload.json()));
    }
}
