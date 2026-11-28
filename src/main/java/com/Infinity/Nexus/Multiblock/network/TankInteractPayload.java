package com.Infinity.Nexus.Multiblock.network;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.menu.MultiblockMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TankInteractPayload(BlockPos pos, String tankId) implements CustomPacketPayload {
    public static final Type<TankInteractPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "tank_interact"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TankInteractPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public TankInteractPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new TankInteractPayload(buffer.readBlockPos(), buffer.readUtf(128));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, TankInteractPayload payload) {
                    buffer.writeBlockPos(payload.pos());
                    buffer.writeUtf(payload.tankId(), 128);
                }
            };

    @Override
    public Type<TankInteractPayload> type() {
        return TYPE;
    }

    public static void handle(TankInteractPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof MultiblockMenu menu)) {
                return;
            }
            if (!menu.blockEntity().getBlockPos().equals(payload.pos()) || !menu.stillValid(player)) {
                return;
            }
            if (menu.blockEntity().interactWithTank(player, menu, payload.tankId())) {
                menu.broadcastChanges();
            }
        });
    }
}
