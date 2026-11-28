package com.Infinity.Nexus.Multiblock.block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.Infinity.Nexus.Core.itemStackHandler.RestrictedItemStackHandler;
import com.Infinity.Nexus.Multiblock.api.ItemSlot;
import com.Infinity.Nexus.Multiblock.api.ItemSlotRole;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockMenuLayout;
import com.Infinity.Nexus.Multiblock.api.SlotLock;
import com.Infinity.Nexus.Multiblock.api.addon.MultiblockResourceStorage;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import com.mojang.serialization.Codec;

final class MultiblockSlotController {
    private final MultiblockControllerBlockEntity owner;

    MultiblockSlotController(MultiblockControllerBlockEntity owner) {
        this.owner = owner;
    }

    void configure(MultiblockDefinition definition, boolean changed) {
        this.owner.menuLayout = MultiblockMenuLayout.create(definition);
        this.owner.inputSlots = slotIndices(definition, ItemSlotRole.INPUT);
        this.owner.outputSlots = slotIndices(definition, ItemSlotRole.OUTPUT);

        boolean needsItemHandler = !(this.owner.inventory instanceof MultiblockItemHandler)
                || this.owner.inventory.getContainerSize() != definition.inventorySize()
                || changed;
        if (needsItemHandler) {
            this.replaceInventory(definition);
        }
        this.sanitizeSlotLocks();
    }

    private void replaceInventory(MultiblockDefinition definition) {
        List<ItemSlot> slots = definition.itemSlots().isEmpty()
                ? defaultInventorySlots(definition.inventorySize())
                : definition.itemSlots();
        RestrictedItemStackHandler replacement = new MultiblockItemHandler(slots, this::slotLockAt);
        ItemStack[] previous = this.owner.inventory.getStacks();
        ItemStack[] stacks = replacement.getStacks();
        for (int index = 0; index < previous.length && index < stacks.length; index++) {
            stacks[index] = previous[index];
        }
        replacement.setOnChangeCallback(() -> this.owner.setChanged());
        this.owner.inventory = replacement;
    }

    private static List<ItemSlot> defaultInventorySlots(int size) {
        List<ItemSlot> slots = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            slots.add(new ItemSlot(
                    "slot_" + index,
                    ItemSlotRole.STORAGE,
                    null,
                    64,
                    ItemSlotRole.STORAGE.defaultInsertFaces(),
                    ItemSlotRole.STORAGE.defaultExtractFaces()));
        }
        return slots;
    }

    private static int[] slotIndices(@Nullable MultiblockDefinition definition, ItemSlotRole role) {
        if (definition == null) {
            return new int[0];
        }
        List<ItemSlot> slots = definition.itemSlots();
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < slots.size(); index++) {
            if (slots.get(index).role() == role) {
                indices.add(index);
            }
        }
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    @Nullable SlotLock slotLockAt(int slotIndex) {
        MultiblockMenuLayout.SlotPlacement placement = this.placementAt(slotIndex);
        return placement == null ? null : this.owner.slotLocks.get(lockKey(placement));
    }

    boolean slotLockAllows(int slotIndex, ItemStack stack) {
        SlotLock lock = this.slotLockAt(slotIndex);
        return lock == null || lock.matches(stack);
    }

    boolean setSlotLocked(int slotIndex, boolean locked) {
        MultiblockMenuLayout.SlotPlacement placement = this.placementAt(slotIndex);
        if (placement == null
                || placement.role() != ItemSlotRole.INPUT
                || slotIndex >= this.owner.inventory.getContainerSize()) {
            return false;
        }

        String key = lockKey(placement);
        if (!locked) {
            if (this.owner.slotLocks.remove(key) != null) {
                this.syncSlotState();
            }
            return true;
        }

        ItemStack current = this.owner.inventory.getItem(slotIndex);
        if (!current.isEmpty() && placement.filter() != null && !placement.filter().matches(current)) {
            return false;
        }

        SlotLock lock = new SlotLock(placement.id(), slotIndex, current);
        if (lock.equals(this.owner.slotLocks.get(key))) {
            return true;
        }
        this.owner.slotLocks.put(key, lock);
        this.syncSlotState();
        return true;
    }

    private MultiblockMenuLayout.@Nullable SlotPlacement placementAt(int slotIndex) {
        if (slotIndex < 0) {
            return null;
        }
        for (MultiblockMenuLayout.SlotPlacement placement : this.owner.menuLayout.slots()) {
            if (placement.index() == slotIndex) {
                return placement;
            }
        }
        return null;
    }

    private static String lockKey(MultiblockMenuLayout.SlotPlacement placement) {
        return placement.id().isBlank() ? "#" + placement.index() : placement.id();
    }

    private void syncSlotState() {
        this.owner.setChanged();
        if (this.owner.getLevel() != null && !this.owner.getLevel().isClientSide()) {
            BlockState state = this.owner.getBlockState();
            this.owner.getLevel().sendBlockUpdated(this.owner.getBlockPos(), state, state, Block.UPDATE_CLIENTS);
        }
    }

    void sanitizeSlotLocks() {
        if (this.owner.slotLocks.isEmpty()) {
            return;
        }

        Map<String, SlotLock> sanitized = new LinkedHashMap<>();
        for (MultiblockMenuLayout.SlotPlacement placement : this.owner.menuLayout.slots()) {
            if (placement.role() != ItemSlotRole.INPUT) {
                continue;
            }

            String key = lockKey(placement);
            SlotLock lock = this.owner.slotLocks.get(key);
            if (lock == null) {
                continue;
            }

            ItemStack current = placement.index() < this.owner.inventory.getContainerSize()
                    ? this.owner.inventory.getItem(placement.index())
                    : ItemStack.EMPTY;
            if (lock.stack().isEmpty()) {
                if (!current.isEmpty()) {
                    continue;
                }
            } else {
                if (placement.filter() != null && !placement.filter().matches(lock.stack())) {
                    continue;
                }
                if (!current.isEmpty() && !lock.matches(current)) {
                    continue;
                }
            }

            sanitized.put(key, new SlotLock(placement.id(), placement.index(), lock.stack()));
        }

        if (!sanitized.equals(this.owner.slotLocks)) {
            this.owner.slotLocks.clear();
            this.owner.slotLocks.putAll(sanitized);
        }
    }

    int fluidSlotTankIndex(int slotIndex) {
        MultiblockMenuLayout.SlotPlacement placement = this.placementAt(slotIndex);
        if (placement == null
                || placement.role() != ItemSlotRole.FLUID
                || this.owner.fluidHandler == null
                || this.owner.fluidHandler.size() == 0) {
            return -1;
        }
        String tankId = this.owner.fluidSlotTanks.get(lockKey(placement));
        if (tankId != null) {
            int index = this.owner.fluidHandler.indexOf(tankId);
            if (index >= 0) {
                return index;
            }
        }
        return 0;
    }

    boolean setFluidSlotTank(int slotIndex, int tankIndex) {
        MultiblockMenuLayout.SlotPlacement placement = this.placementAt(slotIndex);
        if (placement == null
                || placement.role() != ItemSlotRole.FLUID
                || this.owner.fluidHandler == null
                || tankIndex < 0
                || tankIndex >= this.owner.fluidHandler.size()) {
            return false;
        }
        String key = lockKey(placement);
        String tankId = this.owner.fluidHandler.tank(tankIndex).id();
        if (tankId.equals(this.owner.fluidSlotTanks.get(key))) {
            return true;
        }
        this.owner.fluidSlotTanks.put(key, tankId);
        this.syncSlotState();
        return true;
    }

    boolean cycleFluidSlotTank(int slotIndex) {
        int tankIndex = this.fluidSlotTankIndex(slotIndex);
        if (tankIndex < 0 || this.owner.fluidHandler == null) {
            return false;
        }
        return this.setFluidSlotTank(slotIndex, (tankIndex + 1) % this.owner.fluidHandler.size());
    }

    void onFluidSlotPlaced(int slotIndex) {
        if (this.owner.getLevel() == null || this.owner.getLevel().isClientSide()) {
            return;
        }
        MultiblockMenuLayout.SlotPlacement placement = this.placementAt(slotIndex);
        if (placement == null || placement.role() != ItemSlotRole.FLUID) {
            return;
        }
        this.owner.drainedFluidSlots.remove(lockKey(placement));
        this.drainFluidSlot(slotIndex, placement);
    }

    ItemStack onFluidSlotTaken(int slotIndex, ItemStack removed) {
        if (this.owner.getLevel() == null || this.owner.getLevel().isClientSide() || removed.isEmpty()) {
            return removed;
        }
        MultiblockMenuLayout.SlotPlacement placement = this.placementAt(slotIndex);
        if (placement == null || placement.role() != ItemSlotRole.FLUID) {
            return removed;
        }
        String key = lockKey(placement);
        if (this.owner.drainedFluidSlots.remove(key)) {
            if (slotIndex < this.owner.inventory.getContainerSize()
                    && !this.owner.inventory.getItem(slotIndex).isEmpty()) {
                this.owner.drainedFluidSlots.add(key);
            }
            this.owner.setChanged();
            return removed;
        }
        int tankIndex = this.fluidSlotTankIndex(slotIndex);
        if (tankIndex < 0) {
            return removed;
        }
        ItemStack filled = this.fillFluidContainer(tankIndex, removed);
        if (filled == null) {
            return removed;
        }
        int remaining = removed.getCount() - 1;
        if (remaining > 0) {
            ItemStack remainder = removed.copyWithCount(remaining);
            this.owner.inventory.setItem(slotIndex, remainder);
            if (!ItemStack.matches(this.owner.inventory.getItem(slotIndex), remainder)) {
                Block.popResource(this.owner.getLevel(), this.owner.getBlockPos(), remainder);
            }
        }
        this.owner.setChanged();
        return filled;
    }

    private void drainFluidSlot(int slotIndex, MultiblockMenuLayout.SlotPlacement placement) {
        if (this.owner.fluidHandler == null) {
            return;
        }
        int tankIndex = this.fluidSlotTankIndex(slotIndex);
        if (tankIndex < 0) {
            return;
        }
        ItemStack stack = this.owner.inventory.getItem(slotIndex);
        if (stack.isEmpty()) {
            return;
        }

        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, stack.copy());
        ItemAccess access = ItemAccess.forHandlerIndexStrict(
                VanillaContainerWrapper.ofUncached(container), 0);
        ResourceHandler<FluidResource> itemFluids = access.getCapability(Capabilities.Fluid.ITEM);
        if (itemFluids == null) {
            return;
        }

        ItemStack before = container.getItem(0).copy();
        int moved;
        try (Transaction transaction = Transaction.open(null)) {
            var result = ResourceHandlerUtil.moveFirst(
                    itemFluids,
                    this.owner.fluidHandler.manualAccess(tankIndex),
                    resource -> true,
                    Integer.MAX_VALUE,
                    transaction);
            moved = result == null ? 0 : result.amount();
            ItemStack after = container.getItem(0);
            if (moved > 0
                    && !ItemStack.matches(before, after)
                    && this.fluidSlotAccepts(slotIndex, after)) {
                transaction.commit();
                this.owner.inventory.setItem(slotIndex, after);
                this.owner.drainedFluidSlots.add(lockKey(placement));
                this.owner.setChanged();
            }
        }
    }

    private @Nullable ItemStack fillFluidContainer(int tankIndex, ItemStack stack) {
        if (this.owner.fluidHandler == null || stack.isEmpty()) {
            return null;
        }
        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, stack.copyWithCount(1));
        ItemAccess access = ItemAccess.forHandlerIndexStrict(
                VanillaContainerWrapper.ofUncached(container), 0);
        ResourceHandler<FluidResource> itemFluids = access.getCapability(Capabilities.Fluid.ITEM);
        if (itemFluids == null) {
            return null;
        }

        ItemStack before = container.getItem(0).copy();
        int moved;
        try (Transaction transaction = Transaction.open(null)) {
            var result = ResourceHandlerUtil.moveFirst(
                    this.owner.fluidHandler.manualAccess(tankIndex),
                    itemFluids,
                    resource -> true,
                    Integer.MAX_VALUE,
                    transaction);
            moved = result == null ? 0 : result.amount();
            if (moved > 0) {
                transaction.commit();
            }
        }
        ItemStack after = container.getItem(0);
        return moved > 0 && !ItemStack.matches(before, after) ? after : null;
    }

    boolean interactWithTank(Player player, AbstractContainerMenu menu, String tankId) {
        if (this.owner.fluidHandler == null || tankId == null || tankId.isBlank()) {
            return false;
        }
        int tankIndex = this.owner.fluidHandler.indexOf(tankId);
        if (tankIndex < 0) {
            return false;
        }
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return false;
        }
        ItemAccess cursor = ItemAccess.forPlayerCursor(player, menu).oneByOne();
        if (carried.is(Items.SPONGE)) {
            FluidResource resource = this.owner.fluidHandler.getResource(tankIndex);
            int amount = this.owner.fluidHandler.getAmountAsInt(tankIndex);
            if (amount <= 0 || resource.isEmpty() || resource.getFluid() != Fluids.WATER) {
                return false;
            }
            try (Transaction transaction = Transaction.open(null)) {
                int extracted = this.owner.fluidHandler
                        .manualAccess(tankIndex)
                        .extract(0, resource, amount, transaction);
                if (extracted <= 0) {
                    return false;
                }
                if (cursor.extract(ItemResource.of(carried), 1, transaction) != 1) {
                    return false;
                }
                if (cursor.insert(ItemResource.of(Items.WET_SPONGE), 1, transaction) != 1) {
                    return false;
                }
                transaction.commit();
            }
            this.owner.setChanged();
            return true;
        }
        ResourceHandler<FluidResource> itemFluids = cursor.getCapability(Capabilities.Fluid.ITEM);
        if (itemFluids == null) {
            return false;
        }
        ResourceHandler<FluidResource> tank = this.owner.fluidHandler.manualAccess(tankIndex);
        try (Transaction transaction = Transaction.open(null)) {
            var moved = ResourceHandlerUtil.moveFirst(tank, itemFluids, resource -> true, Integer.MAX_VALUE, transaction);
            boolean pickup = moved != null;
            if (moved == null) {
                moved = ResourceHandlerUtil.moveFirst(itemFluids, tank, resource -> true, Integer.MAX_VALUE, transaction);
            }
            if (moved == null) {
                return false;
            }
            transaction.commit();
            FluidUtil.triggerSoundAndGameEvent(
                    moved.resource(),
                    player.level(),
                    Vec3.atCenterOf(this.owner.getBlockPos()),
                    player,
                    pickup);
        }
        this.owner.setChanged();
        return true;
    }

    private boolean fluidSlotAccepts(int slotIndex, ItemStack stack) {
        MultiblockDefinition definition = this.owner.definition();
        if (definition == null || slotIndex < 0 || slotIndex >= definition.itemSlots().size()) {
            return false;
        }
        ItemSlot itemSlot = definition.itemSlots().get(slotIndex);
        return itemSlot.accepts(stack)
                && this.slotLockAllows(slotIndex, stack)
                && stack.getCount() <= Math.min(itemSlot.limit(), stack.getMaxStackSize());
    }

    void sanitizeFluidSlots() {
        if (this.owner.menuLayout.slots().isEmpty()) {
            this.owner.fluidSlotTanks.clear();
            this.owner.drainedFluidSlots.clear();
            return;
        }

        Map<String, String> tanks = new LinkedHashMap<>();
        Set<String> drained = new HashSet<>();
        for (MultiblockMenuLayout.SlotPlacement placement : this.owner.menuLayout.slots()) {
            if (placement.role() != ItemSlotRole.FLUID && placement.role() != ItemSlotRole.RESOURCE) {
                continue;
            }
            String key = lockKey(placement);
            if (placement.role() == ItemSlotRole.FLUID) {
                String tankId = this.owner.fluidSlotTanks.get(key);
                if (tankId != null && this.owner.fluidHandler != null
                        && this.owner.fluidHandler.indexOf(tankId) >= 0) {
                    tanks.put(key, tankId);
                }
            }
            if (this.owner.drainedFluidSlots.contains(key)) {
                ItemStack current = placement.index() < this.owner.inventory.getContainerSize()
                        ? this.owner.inventory.getItem(placement.index())
                        : ItemStack.EMPTY;
                if (!current.isEmpty()) {
                    drained.add(key);
                }
            }
        }
        if (!tanks.equals(this.owner.fluidSlotTanks)) {
            this.owner.fluidSlotTanks.clear();
            this.owner.fluidSlotTanks.putAll(tanks);
        }
        if (!drained.equals(this.owner.drainedFluidSlots)) {
            this.owner.drainedFluidSlots.clear();
            this.owner.drainedFluidSlots.addAll(drained);
        }
    }

    int resourceSlotIndex(int slotIndex) {
        MultiblockMenuLayout.SlotPlacement placement = this.placementAt(slotIndex);
        ResourceSlotTarget target = placement == null ? null : this.resourceSlotTarget(placement);
        return target == null ? -1 : target.index();
    }

    void onResourceSlotPlaced(int slotIndex) {
        if (this.owner.getLevel() == null || this.owner.getLevel().isClientSide()) {
            return;
        }
        MultiblockMenuLayout.SlotPlacement placement = this.placementAt(slotIndex);
        ResourceSlotTarget target = placement == null ? null : this.resourceSlotTarget(placement);
        if (target == null) {
            return;
        }
        this.owner.drainedFluidSlots.remove(lockKey(placement));
        this.drainResourceSlot(slotIndex, target);
    }

    ItemStack onResourceSlotTaken(int slotIndex, ItemStack removed) {
        if (this.owner.getLevel() == null || this.owner.getLevel().isClientSide() || removed.isEmpty()) {
            return removed;
        }
        MultiblockMenuLayout.SlotPlacement placement = this.placementAt(slotIndex);
        if (placement == null) {
            return removed;
        }
        String key = lockKey(placement);
        if (this.owner.drainedFluidSlots.remove(key)) {
            if (slotIndex < this.owner.inventory.getContainerSize()
                    && !this.owner.inventory.getItem(slotIndex).isEmpty()) {
                this.owner.drainedFluidSlots.add(key);
            }
            this.owner.setChanged();
            return removed;
        }
        ResourceSlotTarget target = this.resourceSlotTarget(placement);
        if (target == null) {
            return removed;
        }
        ItemStack single = removed.copyWithCount(1);
        ItemStack filled = target.storage().fillContainer(target.index(), single);
        if (filled == null || ItemStack.matches(filled, single)) {
            return removed;
        }
        int remaining = removed.getCount() - 1;
        if (remaining > 0) {
            ItemStack remainder = removed.copyWithCount(remaining);
            this.owner.inventory.setItem(slotIndex, remainder);
            if (!ItemStack.matches(this.owner.inventory.getItem(slotIndex), remainder)) {
                Block.popResource(this.owner.getLevel(), this.owner.getBlockPos(), remainder);
            }
        }
        this.owner.setChanged();
        return filled;
    }

    private void drainResourceSlot(int slotIndex, ResourceSlotTarget target) {
        ItemStack stack = this.owner.inventory.getItem(slotIndex);
        if (stack.isEmpty()) {
            return;
        }
        ItemStack drained = target.storage().drainContainer(target.index(), stack.copy());
        if (drained != null && !ItemStack.matches(stack, drained)) {
            this.owner.inventory.setItem(slotIndex, drained);
            this.owner.drainedFluidSlots.add(lockKey(this.placementAt(slotIndex)));
            this.owner.setChanged();
        }
    }

    private @Nullable ResourceSlotTarget resourceSlotTarget(MultiblockMenuLayout.SlotPlacement placement) {
        if (placement.role() != ItemSlotRole.RESOURCE) {
            return null;
        }
        String id = placement.id();
        int separator = id.indexOf(':');
        if (separator <= 0 || separator == id.length() - 1) {
            return null;
        }
        String kind = id.substring(0, separator);
        String tank = id.substring(separator + 1);
        MultiblockResourceStorage storage = this.owner.resourceStorages.get(kind);
        if (storage == null) {
            return null;
        }
        int index = storage.indexOf(tank);
        return index < 0 ? null : new ResourceSlotTarget(kind, storage, index);
    }

    void save(ValueOutput output) {
        output.store("MenuLayout", MultiblockMenuLayout.CODEC, this.owner.menuLayout);
        output.store("SlotLocks", SlotLock.CODEC.listOf(), List.copyOf(this.owner.slotLocks.values()));
        output.store(
                "FluidSlotTanks",
                Codec.unboundedMap(Codec.STRING, Codec.STRING),
                Map.copyOf(this.owner.fluidSlotTanks));
        output.store("DrainedFluidSlots", Codec.STRING.listOf(), List.copyOf(this.owner.drainedFluidSlots));
        NonNullList<ItemStack> items =
                NonNullList.withSize(this.owner.inventory.getContainerSize(), ItemStack.EMPTY);
        ItemStack[] stacks = this.owner.inventory.getStacks();
        for (int i = 0; i < stacks.length; i++) {
            items.set(i, stacks[i]);
        }
        ContainerHelper.saveAllItems(output, items);
    }

    void load(ValueInput input) {
        this.owner.menuLayout = input.read("MenuLayout", MultiblockMenuLayout.CODEC).orElse(this.owner.menuLayout);
        this.owner.slotLocks.clear();
        for (SlotLock lock : input.read("SlotLocks", SlotLock.CODEC.listOf()).orElse(List.of())) {
            this.owner.slotLocks.put(lock.key(), lock);
        }
        this.owner.fluidSlotTanks.clear();
        this.owner.fluidSlotTanks.putAll(input.read(
                "FluidSlotTanks", Codec.unboundedMap(Codec.STRING, Codec.STRING)).orElse(Map.of()));
        this.owner.drainedFluidSlots.clear();
        this.owner.drainedFluidSlots.addAll(input.read(
                "DrainedFluidSlots", Codec.STRING.listOf()).orElse(List.of()));
        NonNullList<ItemStack> items =
                NonNullList.withSize(this.owner.inventory.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        ItemStack[] stacks = this.owner.inventory.getStacks();
        for (int i = 0; i < items.size() && i < stacks.length; i++) {
            stacks[i] = items.get(i);
        }
    }

    void sanitize() {
        this.sanitizeSlotLocks();
        this.sanitizeFluidSlots();
    }

    private record ResourceSlotTarget(String kind, MultiblockResourceStorage storage, int index) {
    }
}

