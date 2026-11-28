package com.Infinity.Nexus.Multiblock.network;

import java.util.ArrayList;
import java.util.List;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.client.MultiblockBoosterHighlights;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BoosterHighlightPayload(
        Identifier dimension,
        BlockPos controller,
        List<BlockPos> positions,
        long durationMillis) implements CustomPacketPayload {
    public static final long DURATION_MILLIS = 10_000L;
    public static final int MAX_POSITIONS = 512;
    public static final Type<BoosterHighlightPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    InfinityNexusMultiblock.MOD_ID, "booster_highlight"));

    public BoosterHighlightPayload {
        positions = List.copyOf(positions.size() > MAX_POSITIONS
                ? positions.subList(0, MAX_POSITIONS)
                : positions);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, BoosterHighlightPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public BoosterHighlightPayload decode(RegistryFriendlyByteBuf buffer) {
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
                    return new BoosterHighlightPayload(dimension, controller, positions, durationMillis);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, BoosterHighlightPayload payload) {
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
    public Type<BoosterHighlightPayload> type() {
        return TYPE;
    }

    public static void handle(BoosterHighlightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MultiblockBoosterHighlights.add(
                payload.dimension(),
                payload.controller(),
                payload.positions(),
                payload.durationMillis()));
    }
}
