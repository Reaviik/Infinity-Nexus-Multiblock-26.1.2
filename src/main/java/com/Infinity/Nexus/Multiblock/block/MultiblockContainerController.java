package com.Infinity.Nexus.Multiblock.block;

import com.Infinity.Nexus.Multiblock.api.CapabilityPorts;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockMenuLayout;
import com.Infinity.Nexus.Multiblock.menu.MultiblockMenu;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

final class MultiblockContainerController {
    private final MultiblockControllerBlockEntity owner;

    MultiblockContainerController(MultiblockControllerBlockEntity owner) {
        this.owner = owner;
    }

    int getContainerSize() {
        return this.owner.inventory.getContainerSize();
    }

    boolean isEmpty() {
        return this.owner.inventory.isEmpty();
    }

    ItemStack getItem(int slot) {
        return this.owner.inventory.getItem(slot);
    }

    ItemStack removeItem(int slot, int amount) {
        ItemStack stack = this.owner.inventory.removeItem(slot, amount);
        if (!stack.isEmpty()) {
            this.owner.setChanged();
        }
        return stack;
    }

    ItemStack removeItemNoUpdate(int slot) {
        return this.owner.inventory.removeItemNoUpdate(slot);
    }

    void setItem(int slot, ItemStack stack) {
        this.owner.inventory.setItem(slot, stack);
    }

    boolean canPlaceItem(int slot, ItemStack stack) {
        return this.owner.inventory.canPlaceItem(slot, stack);
    }

    int[] getSlotsForFace(Direction direction) {
        if (!this.owner.allowsCapability(this.owner.getBlockPos(), CapabilityPorts.CapabilityType.ITEM)) {
            return new int[0];
        }
        return this.owner.inventory.getSlotsForFace(direction);
    }

    boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return this.owner.allowsCapability(this.owner.getBlockPos(), CapabilityPorts.CapabilityType.ITEM)
                && this.owner.inventory.canPlaceItemThroughFace(slot, stack, direction);
    }

    boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return this.owner.allowsCapability(this.owner.getBlockPos(), CapabilityPorts.CapabilityType.ITEM)
                && this.owner.inventory.canTakeItemThroughFace(slot, stack, direction);
    }

    boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this.owner, player);
    }

    void clearContent() {
        this.owner.inventory.clearContent();
    }

    Component getName() {
        return this.getDisplayName();
    }

    Component getDisplayName() {
        MultiblockDefinition definition = this.owner.definition();
        return definition == null
                ? this.owner.getBlockState().getBlock().getName()
                : definition.displayName();
    }

    @Nullable Component getCustomName() {
        MultiblockDefinition definition = this.owner.definition();
        return definition == null ? null : definition.displayName();
    }

    AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        this.owner.ensureConfigured();
        return new MultiblockMenu(containerId, inventory, this.owner, this.owner.menuLayout);
    }

    void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        this.owner.ensureConfigured();
        buffer.writeBlockPos(this.owner.getBlockPos());
        MultiblockMenuLayout layout = menu instanceof MultiblockMenu multiblockMenu
                ? multiblockMenu.layout()
                : this.owner.menuLayout;
        Tag tag = MultiblockMenuLayout.CODEC.encodeStart(NbtOps.INSTANCE, layout)
                .getOrThrow(message -> new IllegalStateException("Failed to encode multiblock gui layout: " + message));
        buffer.writeNbt(tag);
    }
}
