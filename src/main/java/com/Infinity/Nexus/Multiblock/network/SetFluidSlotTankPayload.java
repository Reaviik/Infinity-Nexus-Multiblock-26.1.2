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

public record SetFluidSlotTankPayload(BlockPos pos, int slotIndex, int tankIndex) implements CustomPacketPayload {
    public static final Type<SetFluidSlotTankPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "set_fluid_slot_tank"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFluidSlotTankPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public SetFluidSlotTankPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new SetFluidSlotTankPayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, SetFluidSlotTankPayload payload) {
                    buffer.writeBlockPos(payload.pos());
                    buffer.writeVarInt(payload.slotIndex());
                    buffer.writeVarInt(payload.tankIndex());
                }
            };

    @Override
    public Type<SetFluidSlotTankPayload> type() {
        return TYPE;
    }

    public static void handle(SetFluidSlotTankPayload payload, IPayloadContext context) {
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
            menu.blockEntity().setFluidSlotTank(payload.slotIndex(), payload.tankIndex());
        });
    }
}
