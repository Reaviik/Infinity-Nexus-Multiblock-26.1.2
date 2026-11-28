package com.Infinity.Nexus.Multiblock.network;

import java.util.ArrayList;
import java.util.List;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.client.MultiblockBlockedHighlights;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MultiblockBlockedHighlightPayload(
        Identifier dimension,
        BlockPos controller,
        List<BlockPos> positions,
        long durationMillis) implements CustomPacketPayload {
    public static final int MAX_POSITIONS = 512;
    public static final Type<MultiblockBlockedHighlightPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    InfinityNexusMultiblock.MOD_ID, "multiblock_blocked_highlight"));

    public MultiblockBlockedHighlightPayload {
        positions = List.copyOf(positions.size() > MAX_POSITIONS
                ? positions.subList(0, MAX_POSITIONS)
                : positions);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, MultiblockBlockedHighlightPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public MultiblockBlockedHighlightPayload decode(RegistryFriendlyByteBuf buffer) {
                    Identifier dimension = buffer.readIdentifier();
                    BlockPos controller = buffer.readBlockPos();
                    int size = buffer.readVarInt();
                    List<BlockPos> positions = new ArrayList<>(Math.min(size, MAX_POSITIONS));
                    for (int index = 0; index < size; index++) {
                        BlockPos pos = buffer.readBlockPos();
                        if (index < MAX_POSITIONS) {
                            positions.add(pos);
                        }
                    }
                    long durationMillis = buffer.readLong();
                    return new MultiblockBlockedHighlightPayload(dimension, controller, positions, durationMillis);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MultiblockBlockedHighlightPayload payload) {
                    buffer.writeIdentifier(payload.dimension());
                    buffer.writeBlockPos(payload.controller());
                    buffer.writeVarInt(payload.positions().size());
                    for (BlockPos pos : payload.positions()) {
                        buffer.writeBlockPos(pos);
                    }
                    buffer.writeLong(payload.durationMillis());
                }
            };

    @Override
    public Type<MultiblockBlockedHighlightPayload> type() {
        return TYPE;
    }

    public static void handle(MultiblockBlockedHighlightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MultiblockBlockedHighlights.add(
                payload.dimension(),
                payload.controller(),
                payload.positions(),
                payload.durationMillis()));
    }
}
