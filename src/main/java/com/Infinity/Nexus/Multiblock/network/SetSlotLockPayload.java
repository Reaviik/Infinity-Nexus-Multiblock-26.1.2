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

public record SetSlotLockPayload(BlockPos pos, int slotIndex, boolean locked) implements CustomPacketPayload {
    public static final Type<SetSlotLockPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "set_slot_lock"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetSlotLockPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public SetSlotLockPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new SetSlotLockPayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readBoolean());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, SetSlotLockPayload payload) {
                    buffer.writeBlockPos(payload.pos());
                    buffer.writeVarInt(payload.slotIndex());
                    buffer.writeBoolean(payload.locked());
                }
            };

    @Override
    public Type<SetSlotLockPayload> type() {
        return TYPE;
    }

    public static void handle(SetSlotLockPayload payload, IPayloadContext context) {
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
            menu.blockEntity().setSlotLocked(payload.slotIndex(), payload.locked());
        });
    }
}
