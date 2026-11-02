package com.Infinity.Nexus.Multiblock.block;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

import com.Infinity.Nexus.Core.itemStackHandler.RestrictedItemStackHandler;
import com.Infinity.Nexus.Multiblock.api.ItemSlot;
import com.Infinity.Nexus.Multiblock.api.SlotLock;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

public final class MultiblockItemHandler extends RestrictedItemStackHandler {
    private final List<ItemSlot> slots;
    private final int[] limits;
    private final Map<Direction, int[]> faceSlots;
    private final int[] allSlots;
    private final IntFunction<@Nullable SlotLock> lockProvider;

    public MultiblockItemHandler(List<ItemSlot> slots) {
        this(slots, slot -> null);
    }

    public MultiblockItemHandler(List<ItemSlot> slots, IntFunction<@Nullable SlotLock> lockProvider) {
        super(slots.size(), restrictedInsertSlots(slots), restrictedExtractSlots(slots), resource -> true);
        this.slots = List.copyOf(slots);
        this.lockProvider = Objects.requireNonNull(lockProvider, "lockProvider");
        this.limits = slots.stream().mapToInt(ItemSlot::limit).toArray();
        this.allSlots = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            this.allSlots[i] = i;
        }

        EnumMap<Direction, int[]> configuredFaces = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            List<Integer> available = new ArrayList<>();
            for (int slot = 0; slot < slots.size(); slot++) {
                ItemSlot definition = slots.get(slot);
                if (definition.canInsertFrom(direction) || definition.canExtractTo(direction)) {
                    available.add(slot);
                }
            }
            configuredFaces.put(direction, available.stream().mapToInt(Integer::intValue).toArray());
        }
        this.faceSlots = Map.copyOf(configuredFaces);
        this.setSlotValidator((slot, resource) -> {
            if (!this.isSlotIndex(slot)) {
                return false;
            }
            ItemStack stack = resource.toStack(1);
            return this.slots.get(slot).accepts(stack) && this.lockAllows(slot, stack);
        });
    }

    private static Set<Integer> restrictedInsertSlots(List<ItemSlot> slots) {
        Set<Integer> restricted = new HashSet<>();
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).insertFaces().isEmpty()) {
                restricted.add(i);
            }
        }
        return restricted;
    }

    private static Set<Integer> restrictedExtractSlots(List<ItemSlot> slots) {
        Set<Integer> restricted = new HashSet<>();
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).extractFaces().isEmpty()) {
                restricted.add(i);
            }
        }
        return restricted;
    }

    @Override
    public int[] getSlotsForFace(@Nullable Direction direction) {
        if (direction == null) {
            return this.allSlots.clone();
        }
        return this.faceSlots.getOrDefault(direction, new int[0]).clone();
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        if (!this.isSlotIndex(slot) || stack.isEmpty()) {
            return false;
        }
        ItemSlot definition = this.slots.get(slot);
        return definition.canInsertFrom(direction)
                && definition.accepts(stack)
                && this.lockAllows(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return this.isSlotIndex(slot) && this.slots.get(slot).canExtractTo(direction);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (!this.isSlotIndex(slot) || stack.isEmpty()) {
            return false;
        }

        ItemSlot definition = this.slots.get(slot);
        if (!definition.accepts(stack) || !this.lockAllows(slot, stack)) {
            return false;
        }

        int limit = Math.min(definition.limit(), stack.getMaxStackSize());
        if (stack.getCount() > limit) {
            return false;
        }

        ItemStack current = this.getItem(slot);
        return current.isEmpty()
                || !ItemStack.isSameItemSameComponents(current, stack)
                || current.getCount() + stack.getCount() <= limit;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, TransactionContext transaction, boolean simulate, boolean fromAutomation) {
        if (!this.isSlotIndex(slot) || stack.isEmpty()) {
            return stack;
        }

        ItemSlot definition = this.slots.get(slot);
        if (!definition.accepts(stack) || !this.lockAllows(slot, stack)) {
            return stack;
        }

        ItemStack current = this.getItem(slot);
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) {
            return stack;
        }

        int limit = Math.min(definition.limit(), stack.getMaxStackSize());
        int available = limit - current.getCount();
        if (available <= 0) {
            return stack;
        }

        if (stack.getCount() <= available) {
            return super.insertItem(slot, stack, transaction, simulate, fromAutomation);
        }

        ItemStack attempt = stack.copyWithCount(available);
        ItemStack remainder = super.insertItem(slot, attempt, transaction, simulate, fromAutomation);
        int inserted = available - remainder.getCount();
        if (inserted <= 0) {
            return stack;
        }
        return stack.copyWithCount(stack.getCount() - inserted);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!this.isSlotIndex(slot)) {
            return;
        }
        if (stack.isEmpty()) {
            super.setItem(slot, stack);
            return;
        }

        ItemSlot definition = this.slots.get(slot);
        if (!definition.accepts(stack) || !this.lockAllows(slot, stack)) {
            return;
        }

        int limit = Math.min(definition.limit(), stack.getMaxStackSize());
        if (stack.getCount() > limit) {
            return;
        }
        super.setItem(slot, stack);
    }

    private boolean lockAllows(int slot, ItemStack stack) {
        if (!this.isSlotIndex(slot)) {
            return false;
        }
        SlotLock lock = this.lockProvider.apply(slot);
        return lock == null || lock.matches(stack);
    }

    public int getSlotLimit(int slot) {
        return this.isSlotIndex(slot) ? this.limits[slot] : 0;
    }

    private boolean isSlotIndex(int slot) {
        return slot >= 0 && slot < this.slots.size();
    }
}
