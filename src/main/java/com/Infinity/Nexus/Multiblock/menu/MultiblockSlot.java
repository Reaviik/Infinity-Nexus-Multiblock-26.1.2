package com.Infinity.Nexus.Multiblock.menu;

import com.Infinity.Nexus.Multiblock.api.ItemFilter;
import com.Infinity.Nexus.Multiblock.api.ItemSlotRole;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class MultiblockSlot extends Slot {
    private final int limit;
    private final ItemSlotRole role;
    private final @Nullable ItemFilter filter;

    public MultiblockSlot(Container container, int slot, int x, int y, int limit,
            ItemSlotRole role, @Nullable ItemFilter filter) {
        super(container, slot, x, y);
        this.limit = limit;
        this.role = role;
        this.filter = filter;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (this.role == ItemSlotRole.OUTPUT) {
            return false;
        }
        if (this.filter != null && !this.filter.matches(stack)) {
            return false;
        }
        return !(this.container instanceof MultiblockControllerBlockEntity controller)
                || controller.slotLockAllows(this.getSlotIndex(), stack);
    }

    public boolean isInput() {
        return this.role == ItemSlotRole.INPUT;
    }

    @Override
    public int getMaxStackSize() {
        return Math.min(this.limit, this.container.getMaxStackSize());
    }
}
