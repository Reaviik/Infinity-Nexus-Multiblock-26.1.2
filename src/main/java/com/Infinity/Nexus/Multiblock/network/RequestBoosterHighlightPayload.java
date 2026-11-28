package com.Infinity.Nexus.Multiblock.network;

import java.util.List;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlockEntity;
import com.Infinity.Nexus.Multiblock.menu.MultiblockMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

public record RequestBoosterHighlightPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<RequestBoosterHighlightPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    InfinityNexusMultiblock.MOD_ID, "request_booster_highlight"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestBoosterHighlightPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public RequestBoosterHighlightPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new RequestBoosterHighlightPayload(buffer.readBlockPos());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, RequestBoosterHighlightPayload payload) {
                    buffer.writeBlockPos(payload.pos());
                }
            };

    @Override
    public Type<RequestBoosterHighlightPayload> type() {
        return TYPE;
    }

    public static void handle(RequestBoosterHighlightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof MultiblockMenu menu)) {
                return;
            }
            MultiblockControllerBlockEntity controller = menu.blockEntity();
            if (!controller.getBlockPos().equals(payload.pos()) || !menu.stillValid(player)) {
                return;
            }
            @Nullable MultiblockDefinition definition = controller.definition();
            if (definition == null || definition.upgrades().isEmpty()) {
                return;
            }
            List<BlockPos> positions = definition.upgradeMembers(controller.getBlockPos(), controller.rotation());
            PacketDistributor.sendToPlayer(player, new BoosterHighlightPayload(
                    player.level().dimension().identifier(),
                    controller.getBlockPos(),
                    positions,
                    BoosterHighlightPayload.DURATION_MILLIS));
        });
    }
}
