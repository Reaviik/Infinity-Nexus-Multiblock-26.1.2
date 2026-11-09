package com.Infinity.Nexus.Multiblock.block;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.Infinity.Nexus.Core.itemStackHandler.RestrictedItemStackHandler;
import com.mojang.serialization.Codec;
import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.BlockMatcher;
import com.Infinity.Nexus.Multiblock.api.CapabilityPorts;
import com.Infinity.Nexus.Multiblock.api.FluidTank;
import com.Infinity.Nexus.Multiblock.api.ItemSlot;
import com.Infinity.Nexus.Multiblock.api.ItemSlotRole;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockMenuLayout;
import com.Infinity.Nexus.Multiblock.data.MultiblockDefinitions;
import com.Infinity.Nexus.Multiblock.registry.ModRecipes;
import com.Infinity.Nexus.Multiblock.api.MultiblockPattern;
import com.Infinity.Nexus.Multiblock.api.MultiblockType;
import com.Infinity.Nexus.Multiblock.api.SlotLock;
import com.Infinity.Nexus.Multiblock.client.MultiblockClientCache;
import com.Infinity.Nexus.Multiblock.data.MultiblockSavedData;
import com.Infinity.Nexus.Multiblock.data.MultiblockStructureExporter;
import com.Infinity.Nexus.Multiblock.network.MultiblockExportPayload;
import com.Infinity.Nexus.Multiblock.menu.MultiblockMenu;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipe;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeIngredient;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeInput;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

public class MultiblockControllerBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    private static final long AREA_ENTITY_SCAN_INTERVAL = 10L;

    private RestrictedItemStackHandler inventory;
    private MultiblockMenuLayout menuLayout;
    private @Nullable MultiblockEnergyStorage energyStorage;
    private @Nullable MultiblockFluidHandler fluidHandler;
    private boolean formed;
    private boolean structureValidated;
    private Rotation rotation = Rotation.NONE;
    private BlockPos origin = BlockPos.ZERO;
    private List<BlockPos> members = List.of();
    private Set<BlockPos> itemCapabilityPorts = Set.of();
    private Set<BlockPos> energyCapabilityPorts = Set.of();
    private Set<BlockPos> fluidCapabilityPorts = Set.of();
    private Map<BlockPos, List<Integer>> fluidPortTanks = Map.of();
    private long lastValidationTick = -1L;
    private boolean capabilitySyncPending;
    private int[] inputSlots;
    private int[] outputSlots;
    private @Nullable Identifier definitionId;
    private @Nullable MultiblockDefinition configuredDefinition;
    private @Nullable CompoundTag pendingEnergy;
    private @Nullable CompoundTag pendingFluid;
    private int progress;
    private int maxProgress;
    private @Nullable Identifier activeRecipeId;
    private @Nullable MultiblockRecipe activeRecipe;
    private List<EntityType<?>> activeEntityTypes = List.of();
    private List<AreaEntityCount> areaEntityCounts = List.of();
    private long lastAreaEntityScan = -20L;
    private final Map<String, SlotLock> slotLocks = new LinkedHashMap<>();
    private final Map<String, String> fluidSlotTanks = new LinkedHashMap<>();
    private final Set<String> drainedFluidSlots = new HashSet<>();

    public MultiblockControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.inventory = new RestrictedItemStackHandler(54);
        this.inventory.setOnChangeCallback(this::setChanged);
        this.inputSlots = new int[0];
        this.outputSlots = new int[0];
        this.menuLayout = MultiblockMenuLayout.create(null);
        this.configure(null);
    }

    private void configure(@Nullable MultiblockDefinition definition) {
        if (definition == null) {
            this.invalidateCapabilityPorts();
            this.clearCapabilityPorts();
            this.configuredDefinition = null;
            this.structureValidated = false;
            return;
        }

        boolean changed = this.configuredDefinition != definition;
        if (changed) {
            this.invalidateCapabilityPorts();
            this.clearCapabilityPorts();
            this.structureValidated = false;
        }
        this.configuredDefinition = definition;
        this.menuLayout = MultiblockMenuLayout.create(definition);
        this.inputSlots = slotIndices(definition, ItemSlotRole.INPUT);
        this.outputSlots = slotIndices(definition, ItemSlotRole.OUTPUT);

        boolean needsItemHandler = !(this.inventory instanceof MultiblockItemHandler)
                || this.inventory.getContainerSize() != definition.inventorySize()
                || changed;
        if (needsItemHandler) {
            this.replaceInventory(definition);
        }
        this.sanitizeSlotLocks();

        if (definition.energy().isEmpty()) {
            this.energyStorage = null;
            this.pendingEnergy = null;
        } else if (this.energyStorage == null) {
            var energy = definition.energy().orElseThrow();
            this.energyStorage = new MultiblockEnergyStorage(
                    energy.id(),
                    energy.capacity(),
                    energy.maxReceive(),
                    energy.maxExtract(),
                    this::onCapabilityChanged);
        }

        if (definition.fluidTanks().isEmpty()) {
            this.fluidHandler = null;
            this.pendingFluid = null;
        } else if (this.fluidHandler == null) {
            this.fluidHandler = new MultiblockFluidHandler(definition.fluidTanks(), this::onCapabilityChanged);
        }
        this.sanitizeFluidSlotState();

        this.applyPendingCapabilities();
    }

    private void replaceInventory(MultiblockDefinition definition) {
        List<ItemSlot> slots = definition.itemSlots().isEmpty()
                ? defaultInventorySlots(definition.inventorySize())
                : definition.itemSlots();
        RestrictedItemStackHandler replacement = new MultiblockItemHandler(slots, this::slotLockAt);
        ItemStack[] previous = this.inventory.getStacks();
        ItemStack[] stacks = replacement.getStacks();
        for (int index = 0; index < previous.length && index < stacks.length; index++) {
            stacks[index] = previous[index];
        }
        replacement.setOnChangeCallback(this::setChanged);
        this.inventory = replacement;
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

    private void applyPendingCapabilities() {
        if (this.level == null) {
            return;
        }
        var registries = this.level.registryAccess();
        if (this.pendingEnergy != null && this.energyStorage != null) {
            this.energyStorage.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, registries, this.pendingEnergy));
            this.pendingEnergy = null;
        }
        if (this.pendingFluid != null && this.fluidHandler != null) {
            this.fluidHandler.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, registries, this.pendingFluid));
            this.pendingFluid = null;
        }
    }

    private void ensureConfigured() {
        MultiblockDefinition definition = this.definition();
        if (definition != null && definition != this.configuredDefinition) {
            this.configure(definition);
        }
        this.applyPendingCapabilities();
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

    public @Nullable MultiblockDefinition definition() {
        return this.definitionId == null ? null : MultiblockDefinitions.get(this.definitionId);
    }

    public @Nullable Identifier definitionId() {
        return this.definitionId;
    }

    public void setDefinitionId(Identifier definitionId) {
        if (java.util.Objects.equals(this.definitionId, definitionId)) {
            return;
        }
        this.definitionId = definitionId;
        MultiblockDefinition definition = this.definition();
        if (definition != null) {
            this.configure(definition);
        }
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public boolean isFormed() {
        return this.formed;
    }

    public boolean allowsCapability(BlockPos pos, CapabilityPorts.CapabilityType type) {
        MultiblockDefinition definition = this.definition();
        if (definition == null) {
            return false;
        }
        if (!this.formed) {
            return this.worldPosition.equals(pos) && !definition.capabilityPorts().isDefined(type);
        }
        return switch (type) {
            case ITEM -> this.itemCapabilityPorts.contains(pos);
            case ENERGY -> this.energyCapabilityPorts.contains(pos);
            case FLUID -> this.fluidCapabilityPorts.contains(pos);
        };
    }

    public Rotation rotation() {
        return this.rotation;
    }

    public BlockPos origin() {
        return this.origin;
    }

    public List<BlockPos> members() {
        return this.members;
    }

    public RestrictedItemStackHandler inventory() {
        return this.inventory;
    }

    public RestrictedItemStackHandler getInternalItemHandler() {
        return this.inventory;
    }

    public MultiblockMenuLayout menuLayout() {
        return this.menuLayout;
    }

    public @Nullable SlotLock slotLockAt(int slotIndex) {
        MultiblockMenuLayout.SlotPlacement placement = this.placementAt(slotIndex);
        return placement == null ? null : this.slotLocks.get(lockKey(placement));
    }

    public boolean slotLockAllows(int slotIndex, ItemStack stack) {
        SlotLock lock = this.slotLockAt(slotIndex);
        return lock == null || lock.matches(stack);
    }

    public boolean setSlotLocked(int slotIndex, boolean locked) {
        MultiblockMenuLayout.SlotPlacement placement = this.placementAt(slotIndex);
        if (placement == null
                || placement.role() != ItemSlotRole.INPUT
                || slotIndex >= this.inventory.getContainerSize()) {
            return false;
        }

        String key = lockKey(placement);
        if (!locked) {
            if (this.slotLocks.remove(key) != null) {
                this.syncSlotState();
            }
            return true;
        }

        ItemStack current = this.inventory.getItem(slotIndex);
        if (!current.isEmpty() && placement.filter() != null && !placement.filter().matches(current)) {
            return false;
        }

        SlotLock lock = new SlotLock(placement.id(), slotIndex, current);
        if (lock.equals(this.slotLocks.get(key))) {
            return true;
        }
        this.slotLocks.put(key, lock);
        this.syncSlotState();
        return true;
    }

    private MultiblockMenuLayout.@Nullable SlotPlacement placementAt(int slotIndex) {
        if (slotIndex < 0) {
            return null;
        }
        for (MultiblockMenuLayout.SlotPlacement placement : this.menuLayout.slots()) {
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
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void sanitizeSlotLocks() {
        if (this.slotLocks.isEmpty()) {
            return;
        }

        Map<String, SlotLock> sanitized = new LinkedHashMap<>();
        for (MultiblockMenuLayout.SlotPlacement placement : this.menuLayout.slots()) {
            if (placement.role() != ItemSlotRole.INPUT) {
                continue;
            }

            String key = lockKey(placement);
            SlotLock lock = this.slotLocks.get(key);
            if (lock == null) {
                continue;
            }

            ItemStack current = placement.index() < this.inventory.getContainerSize()
                    ? this.inventory.getItem(placement.index())
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

        if (!sanitized.equals(this.slotLocks)) {
            this.slotLocks.clear();
            this.slotLocks.putAll(sanitized);
        }
    }

    public int fluidSlotTankIndex(int slotIndex) {
        MultiblockMenuLayout.SlotPlacement placement = this.placementAt(slotIndex);
        if (placement == null
                || placement.role() != ItemSlotRole.FLUID
                || this.fluidHandler == null
                || this.fluidHandler.size() == 0) {
            return -1;
        }
        String tankId = this.fluidSlotTanks.get(lockKey(placement));
        if (tankId != null) {
            int index = this.fluidHandler.indexOf(tankId);
            if (index >= 0) {
                return index;
            }
        }
        return 0;
    }

    public boolean setFluidSlotTank(int slotIndex, int tankIndex) {
        MultiblockMenuLayout.SlotPlacement placement = this.placementAt(slotIndex);
        if (placement == null
                || placement.role() != ItemSlotRole.FLUID
                || this.fluidHandler == null
                || tankIndex < 0
                || tankIndex >= this.fluidHandler.size()) {
            return false;
        }
        String key = lockKey(placement);
        String tankId = this.fluidHandler.tank(tankIndex).id();
        if (tankId.equals(this.fluidSlotTanks.get(key))) {
            return true;
        }
        this.fluidSlotTanks.put(key, tankId);
        this.syncSlotState();
        return true;
    }

    public boolean cycleFluidSlotTank(int slotIndex) {
        int tankIndex = this.fluidSlotTankIndex(slotIndex);
        if (tankIndex < 0 || this.fluidHandler == null) {
            return false;
        }
        return this.setFluidSlotTank(slotIndex, (tankIndex + 1) % this.fluidHandler.size());
    }

    public void onFluidSlotPlaced(int slotIndex) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        MultiblockMenuLayout.SlotPlacement placement = this.placementAt(slotIndex);
        if (placement == null || placement.role() != ItemSlotRole.FLUID) {
            return;
        }
        this.drainedFluidSlots.remove(lockKey(placement));
        this.drainFluidSlot(slotIndex, placement);
    }

    public ItemStack onFluidSlotTaken(int slotIndex, ItemStack removed) {
        if (this.level == null || this.level.isClientSide() || removed.isEmpty()) {
            return removed;
        }
        MultiblockMenuLayout.SlotPlacement placement = this.placementAt(slotIndex);
        if (placement == null || placement.role() != ItemSlotRole.FLUID) {
            return removed;
        }
        String key = lockKey(placement);
        if (this.drainedFluidSlots.remove(key)) {
            if (slotIndex < this.inventory.getContainerSize() && !this.inventory.getItem(slotIndex).isEmpty()) {
                this.drainedFluidSlots.add(key);
            }
            this.setChanged();
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
            this.inventory.setItem(slotIndex, remainder);
            if (!ItemStack.matches(this.inventory.getItem(slotIndex), remainder)) {
                Block.popResource(this.level, this.worldPosition, remainder);
            }
        }
        this.setChanged();
        return filled;
    }

    private void drainFluidSlot(int slotIndex, MultiblockMenuLayout.SlotPlacement placement) {
        if (this.fluidHandler == null) {
            return;
        }
        int tankIndex = this.fluidSlotTankIndex(slotIndex);
        if (tankIndex < 0) {
            return;
        }
        ItemStack stack = this.inventory.getItem(slotIndex);
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
                    this.fluidHandler.manualAccess(tankIndex),
                    resource -> true,
                    Integer.MAX_VALUE,
                    transaction);
            moved = result == null ? 0 : result.amount();
            ItemStack after = container.getItem(0);
            if (moved > 0
                    && !ItemStack.matches(before, after)
                    && this.fluidSlotAccepts(slotIndex, after)) {
                transaction.commit();
                this.inventory.setItem(slotIndex, after);
                this.drainedFluidSlots.add(lockKey(placement));
                this.setChanged();
            }
        }
    }

    private @Nullable ItemStack fillFluidContainer(int tankIndex, ItemStack stack) {
        if (this.fluidHandler == null || stack.isEmpty()) {
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
                    this.fluidHandler.manualAccess(tankIndex),
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

    private boolean fluidSlotAccepts(int slotIndex, ItemStack stack) {
        MultiblockDefinition definition = this.definition();
        if (definition == null || slotIndex < 0 || slotIndex >= definition.itemSlots().size()) {
            return false;
        }
        ItemSlot itemSlot = definition.itemSlots().get(slotIndex);
        return itemSlot.accepts(stack)
                && this.slotLockAllows(slotIndex, stack)
                && stack.getCount() <= Math.min(itemSlot.limit(), stack.getMaxStackSize());
    }

    private void sanitizeFluidSlotState() {
        if (this.menuLayout.slots().isEmpty()) {
            this.fluidSlotTanks.clear();
            this.drainedFluidSlots.clear();
            return;
        }

        Map<String, String> tanks = new LinkedHashMap<>();
        Set<String> drained = new HashSet<>();
        for (MultiblockMenuLayout.SlotPlacement placement : this.menuLayout.slots()) {
            if (placement.role() != ItemSlotRole.FLUID) {
                continue;
            }
            String key = lockKey(placement);
            String tankId = this.fluidSlotTanks.get(key);
            if (tankId != null && this.fluidHandler != null && this.fluidHandler.indexOf(tankId) >= 0) {
                tanks.put(key, tankId);
            }
            if (this.drainedFluidSlots.contains(key)) {
                ItemStack current = placement.index() < this.inventory.getContainerSize()
                        ? this.inventory.getItem(placement.index())
                        : ItemStack.EMPTY;
                if (!current.isEmpty()) {
                    drained.add(key);
                }
            }
        }
        if (!tanks.equals(this.fluidSlotTanks)) {
            this.fluidSlotTanks.clear();
            this.fluidSlotTanks.putAll(tanks);
        }
        if (!drained.equals(this.drainedFluidSlots)) {
            this.drainedFluidSlots.clear();
            this.drainedFluidSlots.addAll(drained);
        }
    }

    public @Nullable MultiblockEnergyStorage energyStorage() {
        return this.energyStorage;
    }

    public @Nullable MultiblockFluidHandler fluidHandler() {
        return this.fluidHandler;
    }

    public int progress() {
        return this.progress;
    }

    public int maxProgress() {
        return this.maxProgress;
    }

    public List<EntityType<?>> activeEntityTypes() {
        return this.activeEntityTypes;
    }

    public List<AreaEntityCount> areaEntityCounts() {
        long gameTime = this.level == null ? 0L : this.level.getGameTime();
        if (this.lastAreaEntityScan < 0L
                || gameTime - this.lastAreaEntityScan >= AREA_ENTITY_SCAN_INTERVAL) {
            this.lastAreaEntityScan = gameTime;
            this.areaEntityCounts = this.scanAreaEntities();
        }
        return this.areaEntityCounts;
    }

    private List<AreaEntityCount> scanAreaEntities() {
        if (this.level == null) {
            return List.of();
        }
        AABB area = this.multiblockBounds();
        if (area == null) {
            return List.of();
        }
        Map<EntityType<?>, Integer> counts = new LinkedHashMap<>();
        for (Entity entity : this.level.getEntitiesOfClass(Entity.class, area,
                entity -> entity.isAlive()
                        && entity instanceof LivingEntity
                        && !(entity instanceof Player))) {
            counts.merge(entity.getType(), 1, Integer::sum);
        }
        List<AreaEntityCount> result = new ArrayList<>(counts.size());
        for (Map.Entry<EntityType<?>, Integer> entry : counts.entrySet()) {
            result.add(new AreaEntityCount(entry.getKey(), entry.getValue()));
        }
        result.sort((left, right) -> entityTypeId(left.type()).compareTo(entityTypeId(right.type())));
        return List.copyOf(result);
    }

    private static String entityTypeId(EntityType<?> type) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id == null ? "" : id.toString();
    }

    public record AreaEntityCount(EntityType<?> type, int count) {
    }

    private void refreshActiveEntityTypes() {
        Set<EntityType<?>> types = new LinkedHashSet<>();
        if (this.activeRecipe != null) {
            HolderGetter<EntityType<?>> registry = this.entityTypeRegistry();
            if (registry != null) {
                for (MultiblockRecipeIngredient.Entity input : this.activeRecipe.entityInputs()) {
                    types.addAll(input.type().resolve(registry));
                }
                for (MultiblockRecipeOutput output : this.activeRecipe.outputs()) {
                    if (output instanceof MultiblockRecipeOutput.Entity entity) {
                        types.addAll(entity.type().resolve(registry));
                    }
                }
            }
        }
        List<EntityType<?>> resolved = List.copyOf(types);
        if (!resolved.equals(this.activeEntityTypes)) {
            this.activeEntityTypes = resolved;
            this.syncClientState();
        }
    }

    private @Nullable HolderGetter<EntityType<?>> entityTypeRegistry() {
        return this.level == null ? null : this.level.registryAccess().lookupOrThrow(Registries.ENTITY_TYPE);
    }

    private void syncClientState() {
        if (this.level != null && !this.level.isClientSide()) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private List<Identifier> activeEntityTypeIds() {
        List<Identifier> ids = new ArrayList<>(this.activeEntityTypes.size());
        for (EntityType<?> type : this.activeEntityTypes) {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction direction) {
        return this.energyStorage;
    }

    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction direction) {
        return this.fluidHandler;
    }

    public @Nullable ResourceHandler<FluidResource> getFluidHandler(BlockPos pos, @Nullable Direction direction) {
        if (this.fluidHandler == null) {
            return null;
        }
        List<Integer> tanks = this.fluidPortTanks.get(pos);
        if (tanks == null || tanks.size() == this.fluidHandler.size()) {
            return this.fluidHandler;
        }
        return this.fluidHandler.indexedAccess(tanks);
    }

    private void onCapabilityChanged() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.capabilitySyncPending = true;
        }
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.ensureConfigured();
        if (this.capabilitySyncPending && this.level.getGameTime() % 5L == 0L) {
            this.capabilitySyncPending = false;
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
        if (!this.structureValidated || this.level.getGameTime() % 20L == 0L) {
            this.validateStructure();
        }
        this.tickRecipe();
        this.updateProcessingState();
    }

    private void updateProcessingState() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        boolean processing = this.formed && this.activeRecipe != null && this.progress > 0;
        BlockState state = this.getBlockState();
        if (state.getValue(MultiblockControllerBlock.PROCESSING) == processing) {
            return;
        }
        BlockState updated = state.setValue(MultiblockControllerBlock.PROCESSING, processing);
        this.level.setBlock(this.worldPosition, updated, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
        this.level.sendBlockUpdated(this.worldPosition, state, updated, Block.UPDATE_CLIENTS);
    }

    public void clientTick() {
        if (this.level == null || !this.level.isClientSide()) {
            return;
        }
        this.ensureConfigured();
    }

    private void tickRecipe() {
        ServerLevel serverLevel = this.serverLevel();
        if (serverLevel == null) {
            return;
        }
        MultiblockDefinition definition = this.definition();
        if (definition == null
                || definition.type() != MultiblockType.RECIPE
                || !this.formed) {
            this.resetRecipe();
            return;
        }

        RecipeType<?> recipeType = this.recipeType();
        if (recipeType == null) {
            this.resetRecipe();
            return;
        }

        if (this.activeRecipe == null && this.activeRecipeId != null) {
            this.resolveActiveRecipe(serverLevel);
        }

        MultiblockRecipeInput input = this.createRecipeInput();
        if (this.activeRecipe == null) {
            if (this.progress != 0 || this.maxProgress != 0 || !this.activeEntityTypes.isEmpty()) {
                this.resetRecipe();
            }
            Optional<RecipeHolder<MultiblockRecipe>> found = findRecipe(serverLevel, recipeType, input);
            if (found.isEmpty()) {
                return;
            }
            MultiblockRecipe recipe = found.get().value();
            this.activeRecipe = recipe;
            this.activeRecipeId = found.get().id().identifier();
            this.maxProgress = recipe.time();
            this.progress = 0;
            this.refreshActiveEntityTypes();
            this.setChanged();
            return;
        }

        MultiblockRecipe recipe = this.activeRecipe;
        if (this.maxProgress <= 0
                || this.progress < 0
                || this.progress >= this.maxProgress
                || !recipe.matches(input, serverLevel)
                || !this.matchesEntityInputs(recipe)) {
            this.resetRecipe();
            return;
        }
        if (!this.canCompleteRecipe(recipe, input)) {
            return;
        }

        int energyCost = energyCost(recipe, this.progress);
        if (energyCost > 0) {
            if (this.energyStorage == null || this.energyStorage.getAmountAsInt() < energyCost) {
                return;
            }
            this.energyStorage.set(this.energyStorage.getAmountAsInt() - energyCost);
        }

        this.progress++;
        if (this.progress >= this.maxProgress) {
            this.completeRecipe(recipe);
            this.progress = 0;
            this.maxProgress = 0;
            this.activeRecipe = null;
            this.activeRecipeId = null;
            this.refreshActiveEntityTypes();
        }
        this.setChanged();
    }

    private void resolveActiveRecipe(ServerLevel serverLevel) {
        Identifier recipeId = this.activeRecipeId;
        RecipeType<?> recipeType = this.recipeType();
        if (recipeId == null || recipeType == null) {
            this.resetRecipe();
            return;
        }
        Optional<RecipeHolder<?>> holder = serverLevel.recipeAccess()
                .byKey(ResourceKey.create(Registries.RECIPE, recipeId));
        if (holder.isPresent()
                && holder.get().value() instanceof MultiblockRecipe recipe
                && recipe.getType() == recipeType
                && java.util.Objects.equals(recipe.definition(), this.definitionId)) {
            this.activeRecipe = recipe;
            if (this.maxProgress <= 0) {
                this.maxProgress = recipe.time();
            }
            this.refreshActiveEntityTypes();
        } else {
            this.resetRecipe();
        }
    }

    private @Nullable RecipeType<?> recipeType() {
        MultiblockDefinition definition = this.definition();
        return definition != null && definition.type() == MultiblockType.RECIPE
                ? ModRecipes.MULTIBLOCK_TYPE
                : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<RecipeHolder<MultiblockRecipe>> findRecipe(
            ServerLevel serverLevel, RecipeType<?> recipeType, MultiblockRecipeInput input) {
        Collection<RecipeHolder<?>> holders = (Collection) serverLevel.recipeAccess()
                .recipeMap()
                .byType((RecipeType) recipeType);
        RecipeHolder<MultiblockRecipe> fallback = null;
        for (RecipeHolder<?> holder : holders) {
            if (holder.value() instanceof MultiblockRecipe recipe
                    && java.util.Objects.equals(recipe.definition(), this.definitionId)
                    && this.hasEnergyStorageFor(recipe)
                    && recipe.matches(input, serverLevel)
                    && this.matchesEntityInputs(recipe)) {
                RecipeHolder<MultiblockRecipe> matched = (RecipeHolder<MultiblockRecipe>) (RecipeHolder) holder;
                if (this.canCompleteRecipe(recipe, input)) {
                    return Optional.of(matched);
                }
                if (fallback == null) {
                    fallback = matched;
                }
            }
        }
        return Optional.ofNullable(fallback);
    }

    private MultiblockRecipeInput createRecipeInput() {
        List<ItemStack> items = new ArrayList<>(this.inputSlots.length);
        for (int slot : this.inputSlots) {
            items.add(this.inventory.getItem(slot));
        }
        List<FluidStack> fluids = new ArrayList<>();
        if (this.fluidHandler != null) {
            for (int index = 0; index < this.fluidHandler.size(); index++) {
                FluidResource resource = this.fluidHandler.getResource(index);
                int amount = this.fluidHandler.getAmountAsInt(index);
                fluids.add(resource.isEmpty() || amount <= 0 ? FluidStack.EMPTY : resource.toStack(amount));
            }
        }
        return new MultiblockRecipeInput(items, fluids);
    }

    private boolean hasEnergyStorageFor(MultiblockRecipe recipe) {
        return recipe.energy() <= 0 || this.energyStorage != null;
    }

    private boolean canCompleteRecipe(MultiblockRecipe recipe, MultiblockRecipeInput input) {
        return this.planOutputs(recipe, false, input) != null;
    }

    private @Nullable OutputPlan planOutputs(
            MultiblockRecipe recipe,
            boolean rollChance,
            MultiblockRecipeInput input) {
        ServerLevel serverLevel = this.serverLevel();
        MultiblockDefinition definition = this.definition();
        if (serverLevel == null || definition == null) {
            return null;
        }
        ItemStack[] items = new ItemStack[this.outputSlots.length];
        for (int index = 0; index < this.outputSlots.length; index++) {
            items[index] = this.inventory.getItem(this.outputSlots[index]).copy();
        }
        int tankCount = this.fluidHandler == null ? 0 : this.fluidHandler.size();
        FluidResource[] resources = new FluidResource[tankCount];
        int[] amounts = new int[tankCount];
        for (int index = 0; index < tankCount; index++) {
            resources[index] = this.fluidHandler.getResource(index);
            amounts[index] = this.fluidHandler.getAmountAsInt(index);
        }
        long energy = this.energyStorage == null ? 0L : this.energyStorage.getAmountAsLong();
        long energyCapacity = this.energyStorage == null ? 0L : this.energyStorage.getCapacityAsLong();
        if (this.energyStorage != null) {
            energy = Math.max(0L, energy - remainingEnergyCost(recipe, this.progress));
        }
        int[] matchedTanks = recipe.matchFluidTanks(input.fluids());
        List<MultiblockRecipeIngredient.Fluid> fluidInputs = recipe.fluidInputs();
        for (int index = 0; index < matchedTanks.length; index++) {
            int tank = matchedTanks[index];
            if (tank < 0) {
                return null;
            }
            amounts[tank] -= fluidInputs.get(index).ingredient().amount();
            if (amounts[tank] <= 0) {
                amounts[tank] = 0;
                resources[tank] = FluidResource.EMPTY;
            }
        }
        List<PlannedEntity> entities = new ArrayList<>();
        for (MultiblockRecipeOutput output : recipe.outputs()) {
            if (rollChance && output.chance() < 1.0F
                    && serverLevel.getRandom().nextFloat() >= output.chance()) {
                continue;
            }
            switch (output) {
                case MultiblockRecipeOutput.Item item -> {
                    if (!this.insertItemPlan(definition, items, item.stack().create())) {
                        return null;
                    }
                }
                case MultiblockRecipeOutput.Fluid fluid -> {
                    if (!this.insertFluidPlan(definition, resources, amounts, fluid.stack())) {
                        return null;
                    }
                }
                case MultiblockRecipeOutput.Energy energyOutput -> {
                    if (this.energyStorage == null
                            || energy + energyOutput.amount() > energyCapacity) {
                        return null;
                    }
                    energy += energyOutput.amount();
                }
                case MultiblockRecipeOutput.Entity entityOutput -> {
                    Optional<BlockPos> spawnPos = this.resolveSpawnPos(entityOutput);
                    HolderGetter<EntityType<?>> registry = this.entityTypeRegistry();
                    if (spawnPos.isEmpty() || registry == null) {
                        return null;
                    }
                    for (int index = 0; index < entityOutput.count(); index++) {
                        Optional<EntityType<?>> entityType = entityOutput.type()
                                .pick(registry, serverLevel.getRandom());
                        if (entityType.isEmpty()) {
                            return null;
                        }
                        entities.add(new PlannedEntity(entityType.get(), spawnPos.get().above()));
                    }
                }
            }
        }
        return new OutputPlan(items, resources, amounts, (int) Math.min(Integer.MAX_VALUE, energy), entities);
    }

    private boolean insertItemPlan(
            MultiblockDefinition definition,
            ItemStack[] simulated,
            ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        ItemStack remaining = result.copy();
        for (int index = 0; index < this.outputSlots.length; index++) {
            ItemSlot itemSlot = definition.itemSlots().get(this.outputSlots[index]);
            if (!itemSlot.accepts(result)) {
                continue;
            }
            ItemStack current = simulated[index];
            int limit = Math.min(itemSlot.limit(), result.getMaxStackSize());
            if (current.isEmpty()) {
                int moved = Math.min(remaining.getCount(), limit);
                if (moved <= 0) {
                    continue;
                }
                ItemStack placed = remaining.copy();
                placed.setCount(moved);
                simulated[index] = placed;
                remaining.shrink(moved);
            } else if (ItemStack.isSameItemSameComponents(current, result)) {
                int moved = Math.min(remaining.getCount(), limit - current.getCount());
                if (moved <= 0) {
                    continue;
                }
                ItemStack merged = current.copy();
                merged.grow(moved);
                simulated[index] = merged;
                remaining.shrink(moved);
            }
            if (remaining.isEmpty()) {
                return true;
            }
        }
        return remaining.isEmpty();
    }

    private boolean insertFluidPlan(
            MultiblockDefinition definition,
            FluidResource[] resources,
            int[] amounts,
            FluidStack result) {
        if (result.isEmpty() || resources.length != definition.fluidTanks().size()) {
            return false;
        }
        FluidResource resource = FluidResource.of(result);
        int remaining = result.getAmount();
        for (int index = 0; index < resources.length; index++) {
            FluidTank tank = definition.fluidTanks().get(index);
            if (!tank.accepts(resource)) {
                continue;
            }
            if (!resources[index].isEmpty() && !resources[index].matches(result)) {
                continue;
            }
            int moved = Math.min(Math.max(0, tank.capacity() - amounts[index]), remaining);
            if (moved <= 0) {
                continue;
            }
            resources[index] = resource;
            amounts[index] += moved;
            remaining -= moved;
            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
    }

    private void commitOutputPlan(OutputPlan plan, ServerLevel serverLevel) {
        for (int index = 0; index < this.outputSlots.length; index++) {
            this.inventory.setItem(this.outputSlots[index], plan.items()[index]);
        }
        if (this.fluidHandler != null) {
            for (int index = 0; index < plan.amounts().length; index++) {
                this.fluidHandler.set(index, plan.resources()[index], plan.amounts()[index]);
            }
        }
        if (this.energyStorage != null) {
            this.energyStorage.set(plan.energy());
        }
        for (PlannedEntity entity : plan.entities()) {
            entity.entityType().spawn(
                    serverLevel,
                    entity.spawnPos(),
                    EntitySpawnReason.SPAWNER);
        }
    }

    private void completeRecipe(MultiblockRecipe recipe) {
        ServerLevel serverLevel = this.serverLevel();
        if (serverLevel == null) {
            return;
        }
        MultiblockRecipeInput input = this.createRecipeInput();
        List<MultiblockRecipe.ItemAllocation> itemAllocations = recipe.findItemInputs(input.items());
        if (itemAllocations == null) {
            return;
        }
        int[] matchedTanks = recipe.matchFluidTanks(input.fluids());
        for (int tank : matchedTanks) {
            if (tank < 0) {
                return;
            }
        }
        List<Entity> entityInputs = this.findEntityInputs(recipe);
        if (entityInputs == null) {
            return;
        }
        OutputPlan plan = this.planOutputs(recipe, true, input);
        if (plan == null) {
            return;
        }
        for (MultiblockRecipe.ItemAllocation allocation : itemAllocations) {
            this.inventory.removeItem(this.inputSlots[allocation.slot()], allocation.count());
        }
        this.commitOutputPlan(plan, serverLevel);
        for (Entity entity : entityInputs) {
            entity.discard();
        }
    }

    private boolean matchesEntityInputs(MultiblockRecipe recipe) {
        return this.findEntityInputs(recipe) != null;
    }

    private @Nullable List<Entity> findEntityInputs(MultiblockRecipe recipe) {
        List<MultiblockRecipeIngredient.Entity> required = recipe.entityInputs();
        if (required.isEmpty()) {
            return List.of();
        }
        ServerLevel serverLevel = this.serverLevel();
        if (serverLevel == null) {
            return null;
        }
        Set<Entity> used = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Entity> matched = new ArrayList<>();
        for (MultiblockRecipeIngredient.Entity input : required) {
            AABB area = this.entityInputArea(input);
            if (area == null) {
                return null;
            }
            for (int index = 0; index < input.count(); index++) {
                Entity found = null;
                for (Entity candidate : serverLevel.getEntitiesOfClass(
                        Entity.class,
                        area,
                        entity -> entity.isAlive()
                                && !used.contains(entity)
                                && input.type().matches(entity.getType()))) {
                    found = candidate;
                    break;
                }
                if (found == null) {
                    return null;
                }
                used.add(found);
                matched.add(found);
            }
        }
        return matched;
    }

    private @Nullable AABB entityInputArea(MultiblockRecipeIngredient.Entity input) {
        if (!input.spawn().isEmpty()) {
            Optional<BlockPos> selected = this.resolveSelectorPos(input.spawn());
            if (selected.isEmpty()) {
                return null;
            }
            BlockPos pos = selected.get();
            double x = pos.getX() + 0.5D;
            double y = pos.getY();
            double z = pos.getZ() + 0.5D;
            return new AABB(x - 2.0D, y - 2.0D, z - 2.0D, x + 2.0D, y + 2.0D, z + 2.0D);
        }
        return this.multiblockBounds();
    }

    private @Nullable AABB multiblockBounds() {
        AABB volume = this.multiblockVolume();
        return volume == null ? null : volume.inflate(1.0D);
    }

    private @Nullable AABB multiblockVolume() {
        if (!this.formed) {
            return null;
        }
        int minX = this.worldPosition.getX();
        int minY = this.worldPosition.getY();
        int minZ = this.worldPosition.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;
        for (BlockPos member : this.members) {
            minX = Math.min(minX, member.getX());
            minY = Math.min(minY, member.getY());
            minZ = Math.min(minZ, member.getZ());
            maxX = Math.max(maxX, member.getX());
            maxY = Math.max(maxY, member.getY());
            maxZ = Math.max(maxZ, member.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    private Optional<BlockPos> resolveSpawnPos(MultiblockRecipeOutput.Entity output) {
        Optional<BlockPos> selected = this.resolveSelectorPos(output.spawn());
        if (selected.isPresent() || !output.spawn().isEmpty()) {
            return selected;
        }
        return this.defaultEntitySpawnPos();
    }

    private Optional<BlockPos> defaultEntitySpawnPos() {
        ServerLevel serverLevel = this.serverLevel();
        AABB volume = this.multiblockVolume();
        if (serverLevel == null || volume == null) {
            return Optional.empty();
        }
        BlockPos min = BlockPos.containing(volume.minX, volume.minY, volume.minZ);
        BlockPos max = BlockPos.containing(volume.maxX - 1.0E-4D, volume.maxY - 1.0E-4D, volume.maxZ - 1.0E-4D);
        Optional<BlockPos> interior = findEntitySpawnFloor(serverLevel, min, max, max.getY() - 2);
        return interior.isPresent() ? interior : findEntitySpawnFloor(serverLevel, min, max, max.getY());
    }

    private static Optional<BlockPos> findEntitySpawnFloor(
            ServerLevel serverLevel, BlockPos min, BlockPos max, int startY) {
        for (int floorY = startY; floorY >= min.getY(); floorY--) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos floorPos = new BlockPos(x, floorY, z);
                    if (canSpawnEntityAt(serverLevel, floorPos)) {
                        return Optional.of(floorPos);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean canSpawnEntityAt(ServerLevel level, BlockPos floorPos) {
        if (level.getBlockState(floorPos).isAir()) {
            return false;
        }
        BlockPos spawnPos = floorPos.above();
        return level.getBlockState(spawnPos).canBeReplaced()
                && level.getBlockState(spawnPos.above()).canBeReplaced();
    }

    private Optional<BlockPos> resolveSelectorPos(String spawn) {
        MultiblockDefinition definition = this.definition();
        if (definition == null || !this.formed || this.level == null) {
            return Optional.empty();
        }
        CapabilityPorts.PortSelector selector = CapabilityPorts.PortSelector.parse(spawn).orElse(null);
        if (selector == null) {
            return Optional.empty();
        }
        for (MultiblockPattern.PatternCell cell : definition.pattern().cells()) {
            BlockPos offset = new BlockPos(cell.x(), cell.y(), cell.z()).rotate(this.rotation);
            BlockPos pos = this.origin.offset(offset.getX(), offset.getY(), offset.getZ());
            if (selector.matches(cell.symbol(), this.level.getBlockState(pos), this.rotation)) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    private record OutputPlan(
            ItemStack[] items,
            FluidResource[] resources,
            int[] amounts,
            int energy,
            List<PlannedEntity> entities) {
    }

    private record PlannedEntity(EntityType<?> entityType, BlockPos spawnPos) {
    }

    private void resetRecipe() {
        if (this.progress != 0 || this.maxProgress != 0 || this.activeRecipe != null
                || this.activeRecipeId != null || !this.activeEntityTypes.isEmpty()) {
            this.progress = 0;
            this.maxProgress = 0;
            this.activeRecipe = null;
            this.activeRecipeId = null;
            this.refreshActiveEntityTypes();
            this.setChanged();
        }
    }

    private static int energyCost(MultiblockRecipe recipe, int progress) {
        long total = recipe.energy();
        long consumedBefore = total * progress / recipe.time();
        long consumedAfter = total * (progress + 1L) / recipe.time();
        return (int) (consumedAfter - consumedBefore);
    }

    private static long remainingEnergyCost(MultiblockRecipe recipe, int progress) {
        long total = recipe.energy();
        long consumed = total * Math.min(progress, recipe.time()) / recipe.time();
        return Math.max(0L, total - consumed);
    }

    public void onNeighborChanged() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        long gameTime = this.level.getGameTime();
        if (gameTime == this.lastValidationTick) {
            return;
        }
        this.lastValidationTick = gameTime;
        this.validateStructure();
    }

    private @Nullable ServerLevel serverLevel() {
        return this.level instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    public void validateStructure() {
        ServerLevel serverLevel = this.serverLevel();
        if (serverLevel == null) {
            return;
        }
        this.ensureConfigured();
        MultiblockDefinition definition = this.definition();
        if (definition == null) {
            this.invalidateStructure();
            this.structureValidated = true;
            return;
        }

        Rotation newRotation = this.rotationFromState();
        boolean matched = definition.matches(serverLevel, this.worldPosition, newRotation, this.getBlockState().getBlock());
        List<BlockPos> newMembers = matched ? definition.members(this.worldPosition, newRotation) : List.of();
        List<BlockPos> previousMembers = this.members;
        boolean membersChanged = !previousMembers.equals(newMembers);

        if (matched) {
            MultiblockSavedData.Registration registration = MultiblockSavedData.get(serverLevel)
                    .register(this.worldPosition, newMembers, definition.priority());
            if (!registration.accepted()) {
                matched = false;
                newMembers = List.of();
            } else {
                this.invalidateEvictedControllers(serverLevel, registration.evicted());
            }
        }

        if (!matched) {
            MultiblockSavedData data = MultiblockSavedData.getIfPresent(serverLevel);
            if (data != null) {
                data.unregisterController(this.worldPosition);
            }
        }

        BlockPos newOrigin = definition.origin(this.worldPosition, newRotation);
        boolean changed = this.formed != matched;
        boolean dataChanged = changed
                || this.rotation != newRotation
                || !this.origin.equals(newOrigin)
                || !this.members.equals(newMembers);
        this.formed = matched;
        this.rotation = newRotation;
        this.origin = newOrigin;
        this.members = newMembers;

        Set<BlockPos> previousItemPorts = this.itemCapabilityPorts;
        Set<BlockPos> previousEnergyPorts = this.energyCapabilityPorts;
        Set<BlockPos> previousFluidPorts = this.fluidCapabilityPorts;
        Map<BlockPos, List<Integer>> previousFluidPortTanks = this.fluidPortTanks;
        this.updateCapabilityPorts(serverLevel, definition, newOrigin, newRotation, matched);
        if (!previousItemPorts.equals(this.itemCapabilityPorts)
                || !previousEnergyPorts.equals(this.energyCapabilityPorts)
                || !previousFluidPorts.equals(this.fluidCapabilityPorts)
                || !previousFluidPortTanks.equals(this.fluidPortTanks)) {
            Set<BlockPos> invalidated = new HashSet<>();
            invalidated.addAll(previousItemPorts);
            invalidated.addAll(previousEnergyPorts);
            invalidated.addAll(previousFluidPorts);
            invalidated.addAll(this.itemCapabilityPorts);
            invalidated.addAll(this.energyCapabilityPorts);
            invalidated.addAll(this.fluidCapabilityPorts);
            invalidated.addAll(previousFluidPortTanks.keySet());
            invalidated.addAll(this.fluidPortTanks.keySet());
            this.invalidateCapabilities(List.copyOf(invalidated));
        }

        BlockState current = this.getBlockState();
        boolean stateChanged = current.getValue(MultiblockControllerBlock.FORMED) != matched;
        if (stateChanged) {
            BlockState updated = current.setValue(MultiblockControllerBlock.FORMED, matched);
            serverLevel.setBlock(this.worldPosition, updated, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            serverLevel.sendBlockUpdated(this.worldPosition, current, updated, Block.UPDATE_CLIENTS);
        } else if (dataChanged) {
            serverLevel.sendBlockUpdated(this.worldPosition, current, current, Block.UPDATE_CLIENTS);
        }

        if (dataChanged) {
            this.setChanged();
        }
        if (membersChanged) {
            this.invalidateCapabilities(previousMembers);
            this.invalidateCapabilities(newMembers);
        }
        this.structureValidated = true;
    }

    private void invalidateEvictedControllers(ServerLevel level, List<BlockPos> controllers) {
        for (BlockPos controllerPos : controllers) {
            if (this.worldPosition.equals(controllerPos)) {
                continue;
            }
            if (level.getBlockEntity(controllerPos) instanceof MultiblockControllerBlockEntity controller) {
                controller.invalidateStructure();
            }
        }
    }

    public boolean tryAssemble(Player player) {
        ServerLevel serverLevel = this.serverLevel();
        if (serverLevel == null || this.formed || player.isSpectator()) {
            return false;
        }
        this.ensureConfigured();
        MultiblockDefinition definition = this.definition();
        if (definition == null) {
            return false;
        }

        Rotation rotation = MultiblockControllerBlock.rotationFromState(this.getBlockState());
        BlockPos origin = definition.origin(this.worldPosition, rotation);
        List<AssemblyRequirement> requirements = new ArrayList<>();
        int blocked = 0;
        for (MultiblockPattern.PatternCell cell : definition.pattern().cells()) {
            if (cell.anchor()) {
                continue;
            }
            BlockPos offset = new BlockPos(cell.x(), cell.y(), cell.z()).rotate(rotation);
            BlockPos pos = origin.offset(offset.getX(), offset.getY(), offset.getZ());
            BlockState current = serverLevel.getBlockState(pos);
            if (cell.matcher().matches(current, rotation)) {
                continue;
            }
            if (!current.canBeReplaced()) {
                blocked++;
                continue;
            }
            requirements.add(new AssemblyRequirement(pos, cell.matcher()));
        }

        boolean creative = player.getAbilities().instabuild;
        List<ItemStack> available = new ArrayList<>();
        if (!creative) {
            available.addAll(player.getInventory().getNonEquipmentItems());
            available.add(player.getOffhandItem());
        }
        int[] remaining = new int[available.size()];
        for (int i = 0; i < available.size(); i++) {
            remaining[i] = available.get(i).getCount();
        }

        int placedCount = 0;
        int missing = 0;
        int failed = 0;
        boolean inventoryChanged = false;
        for (AssemblyRequirement requirement : requirements) {
            BlockState placement = null;
            int stackIndex = -1;
            if (creative) {
                placement = MultiblockAssemblyHelper.representativeState(requirement.matcher(), rotation);
            } else {
                for (int i = 0; i < available.size(); i++) {
                    if (remaining[i] <= 0) {
                        continue;
                    }
                    BlockState candidate = MultiblockAssemblyHelper.placementState(available.get(i), requirement.matcher(), rotation);
                    if (candidate == null) {
                        continue;
                    }
                    placement = candidate;
                    stackIndex = i;
                    break;
                }
            }
            if (placement == null) {
                missing++;
                continue;
            }
            if (!serverLevel.setBlock(requirement.pos(), placement, Block.UPDATE_ALL)) {
                failed++;
                continue;
            }

            placedCount++;
            if (!creative && stackIndex >= 0) {
                remaining[stackIndex]--;
                available.get(stackIndex).shrink(1);
                inventoryChanged = true;
            }
        }
        if (inventoryChanged) {
            player.getInventory().setChanged();
        }

        this.validateStructure();
        if (this.formed) {
            player.sendOverlayMessage(Component.translatable("message.infinity_nexus_multiblock.assembled"));
            return true;
        }

        if (placedCount > 0) {
            int remainingBlocks = missing + blocked + failed;
            if (remainingBlocks > 0) {
                player.sendOverlayMessage(Component.translatable(
                        "message.infinity_nexus_multiblock.partially_assembled", placedCount, remainingBlocks));
            } else {
                player.sendOverlayMessage(Component.translatable("message.infinity_nexus_multiblock.assembly_failed"));
            }
            return false;
        }
        if (missing > 0) {
            player.sendOverlayMessage(Component.translatable("message.infinity_nexus_multiblock.missing_blocks", missing));
            return false;
        }
        if (blocked > 0 || failed > 0) {
            player.sendOverlayMessage(Component.translatable(
                    "message.infinity_nexus_multiblock.blocked_cells", blocked + failed));
            return false;
        }
        player.sendOverlayMessage(Component.translatable("message.infinity_nexus_multiblock.assembly_failed"));
        return false;
    }

    public void exportStructure(Player player) {
        ServerLevel serverLevel = this.serverLevel();
        if (serverLevel == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        this.ensureConfigured();
        MultiblockDefinition definition = this.definition();
        if (definition == null) {
            player.sendOverlayMessage(Component.translatable("message.infinity_nexus_multiblock.export_failed"));
            return;
        }

        try {
            MultiblockStructureExporter.Result result =
                    MultiblockStructureExporter.export(serverLevel, this.worldPosition);
            PacketDistributor.sendToPlayer(serverPlayer, new MultiblockExportPayload(result.json()));
            Component message = result.truncated()
                    ? Component.translatable(
                            "message.infinity_nexus_multiblock.exported_truncated",
                            result.scanned(),
                            result.limit())
                    : Component.translatable(
                            "message.infinity_nexus_multiblock.exported",
                            result.scanned());
            player.sendOverlayMessage(message);
            serverLevel.playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.NOTE_BLOCK_PLING.value(),
                    SoundSource.PLAYERS,
                    0.8F,
                    1.2F);
        } catch (RuntimeException exception) {
            InfinityNexusMultiblock.LOGGER.error(
                    "Failed to export multiblock structure at {}", this.worldPosition, exception);
            player.sendOverlayMessage(Component.translatable("message.infinity_nexus_multiblock.export_failed"));
        }
    }

    public void invalidateStructure() {
        ServerLevel serverLevel = this.serverLevel();
        if (serverLevel == null) {
            return;
        }

        List<BlockPos> previousMembers = new ArrayList<>(this.members);
        MultiblockSavedData data = MultiblockSavedData.getIfPresent(serverLevel);
        if (data != null) {
            for (BlockPos member : data.getMembers(this.worldPosition)) {
                if (!previousMembers.contains(member)) {
                    previousMembers.add(member);
                }
            }
            data.unregisterController(this.worldPosition);
        }
        this.members = List.of();
        this.clearCapabilityPorts();
        this.resetRecipe();

        if (this.formed) {
            this.formed = false;
            BlockState current = this.getBlockState();
            BlockState updated = current.setValue(MultiblockControllerBlock.FORMED, false);
            serverLevel.setBlock(this.worldPosition, updated, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            serverLevel.sendBlockUpdated(this.worldPosition, current, updated, Block.UPDATE_CLIENTS);
            this.setChanged();
        }
        this.updateProcessingState();
        if (!previousMembers.contains(this.worldPosition)) {
            previousMembers.add(this.worldPosition);
        }
        this.invalidateCapabilities(previousMembers);
    }

    private void invalidateCapabilities(List<BlockPos> positions) {
        if (this.level == null) {
            return;
        }
        for (BlockPos pos : positions) {
            this.level.invalidateCapabilities(pos);
        }
    }

    private void updateCapabilityPorts(
            ServerLevel level,
            MultiblockDefinition definition,
            BlockPos newOrigin,
            Rotation newRotation,
            boolean matched) {
        Set<BlockPos> items = new HashSet<>();
        Set<BlockPos> energy = new HashSet<>();
        Set<BlockPos> fluids = new HashSet<>();
        Map<BlockPos, List<Integer>> fluidTanksByPort = new LinkedHashMap<>();
        if (matched) {
            CapabilityPorts ports = definition.capabilityPorts();
            List<FluidTank> tanks = definition.fluidTanks();
            for (MultiblockPattern.PatternCell cell : definition.pattern().cells()) {
                BlockPos offset = new BlockPos(cell.x(), cell.y(), cell.z()).rotate(newRotation);
                BlockPos pos = newOrigin.offset(offset.getX(), offset.getY(), offset.getZ());
                BlockState state = level.getBlockState(pos);
                if (ports.allows(CapabilityPorts.CapabilityType.ITEM, cell.symbol(), state, newRotation)) {
                    items.add(pos);
                }
                if (ports.allows(CapabilityPorts.CapabilityType.ENERGY, cell.symbol(), state, newRotation)) {
                    energy.add(pos);
                }
                if (ports.allows(CapabilityPorts.CapabilityType.FLUID, cell.symbol(), state, newRotation)) {
                    fluids.add(pos);
                    List<Integer> allowed = new ArrayList<>(tanks.size());
                    for (int index = 0; index < tanks.size(); index++) {
                        if (tanks.get(index).allowsPort(cell.symbol(), state, newRotation)) {
                            allowed.add(index);
                        }
                    }
                    fluidTanksByPort.put(pos, List.copyOf(allowed));
                }
            }
        }
        this.itemCapabilityPorts = Set.copyOf(items);
        this.energyCapabilityPorts = Set.copyOf(energy);
        this.fluidCapabilityPorts = Set.copyOf(fluids);
        this.fluidPortTanks = Map.copyOf(fluidTanksByPort);
    }

    private void clearCapabilityPorts() {
        this.itemCapabilityPorts = Set.of();
        this.energyCapabilityPorts = Set.of();
        this.fluidCapabilityPorts = Set.of();
        this.fluidPortTanks = Map.of();
    }

    private void invalidateCapabilityPorts() {
        if (this.level == null) {
            return;
        }
        Set<BlockPos> positions = new HashSet<>();
        positions.add(this.worldPosition);
        positions.addAll(this.itemCapabilityPorts);
        positions.addAll(this.energyCapabilityPorts);
        positions.addAll(this.fluidCapabilityPorts);
        this.invalidateCapabilities(List.copyOf(positions));
    }

    private Rotation rotationFromState() {
        return MultiblockControllerBlock.rotationFromState(this.getBlockState());
    }

    private void updateClientCache() {
        if (this.level == null || !this.level.isClientSide()) {
            return;
        }
        if (this.formed && !this.members.isEmpty()) {
            MultiblockClientCache.track(this.level, this.worldPosition, this.members);
        } else {
            MultiblockClientCache.removeController(this.level, this.worldPosition);
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        this.ensureConfigured();
        if (level.isClientSide()) {
            this.updateClientCache();
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null && this.level.isClientSide()) {
            MultiblockClientCache.removeController(this.level, this.worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        ServerLevel serverLevel = this.serverLevel();
        if (serverLevel != null) {
            List<BlockPos> previousMembers = new ArrayList<>(this.members);
            MultiblockSavedData data = MultiblockSavedData.getIfPresent(serverLevel);
            if (data != null) {
                for (BlockPos member : data.getMembers(this.worldPosition)) {
                    if (!previousMembers.contains(member)) {
                        previousMembers.add(member);
                    }
                }
                data.unregisterController(this.worldPosition);
            }
            this.invalidateCapabilities(previousMembers);
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        if (this.definitionId != null) {
            output.store("Definition", Identifier.CODEC, this.definitionId);
        }
        output.putBoolean("Formed", this.formed);
        output.store("Rotation", Rotation.CODEC, this.rotation);
        output.store("Origin", BlockPos.CODEC, this.origin);
        output.store("Members", BlockPos.CODEC.listOf(), this.members);
        output.putInt("Progress", this.progress);
        output.putInt("MaxProgress", this.maxProgress);
        if (this.activeRecipeId != null) {
            output.store("Recipe", Identifier.CODEC, this.activeRecipeId);
        }
        output.store("ActiveEntities", Identifier.CODEC.listOf(), this.activeEntityTypeIds());
        output.store("MenuLayout", MultiblockMenuLayout.CODEC, this.menuLayout);
        output.store("SlotLocks", SlotLock.CODEC.listOf(), List.copyOf(this.slotLocks.values()));
        output.store("FluidSlotTanks", Codec.unboundedMap(Codec.STRING, Codec.STRING), Map.copyOf(this.fluidSlotTanks));
        output.store("DrainedFluidSlots", Codec.STRING.listOf(), List.copyOf(this.drainedFluidSlots));
        NonNullList<ItemStack> items = NonNullList.withSize(this.inventory.getContainerSize(), ItemStack.EMPTY);
        ItemStack[] stacks = this.inventory.getStacks();
        for (int i = 0; i < stacks.length; i++) {
            items.set(i, stacks[i]);
        }
        ContainerHelper.saveAllItems(output, items);
        if (this.energyStorage != null) {
            this.energyStorage.serialize(output.child("energy"));
        } else if (this.pendingEnergy != null) {
            output.store("energy", CompoundTag.CODEC, this.pendingEnergy);
        }
        if (this.fluidHandler != null) {
            this.fluidHandler.serialize(output.child("fluid"));
        } else if (this.pendingFluid != null) {
            output.store("fluid", CompoundTag.CODEC, this.pendingFluid);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        this.definitionId = input.read("Definition", Identifier.CODEC).orElse(null);
        this.formed = input.getBooleanOr("Formed", false);
        this.structureValidated = false;
        this.rotation = input.read("Rotation", Rotation.CODEC).orElse(Rotation.NONE);
        this.origin = input.read("Origin", BlockPos.CODEC).orElse(BlockPos.ZERO);
        this.members = input.read("Members", BlockPos.CODEC.listOf()).orElse(List.of());
        this.progress = input.getIntOr("Progress", 0);
        this.maxProgress = input.getIntOr("MaxProgress", 0);
        this.activeRecipeId = input.read("Recipe", Identifier.CODEC).orElse(null);
        this.activeRecipe = null;
        this.activeEntityTypes = input.read("ActiveEntities", Identifier.CODEC.listOf())
                .orElse(List.of())
                .stream()
                .flatMap(id -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).stream())
                .toList();
        this.menuLayout = input.read("MenuLayout", MultiblockMenuLayout.CODEC).orElse(this.menuLayout);
        this.slotLocks.clear();
        for (SlotLock lock : input.read("SlotLocks", SlotLock.CODEC.listOf()).orElse(List.of())) {
            this.slotLocks.put(lock.key(), lock);
        }
        this.fluidSlotTanks.clear();
        this.fluidSlotTanks.putAll(input.read(
                "FluidSlotTanks", Codec.unboundedMap(Codec.STRING, Codec.STRING)).orElse(Map.of()));
        this.drainedFluidSlots.clear();
        this.drainedFluidSlots.addAll(input.read(
                "DrainedFluidSlots", Codec.STRING.listOf()).orElse(List.of()));
        MultiblockDefinition definition = this.definition();
        if (definition != null) {
            this.menuLayout = MultiblockMenuLayout.create(definition);
        }
        NonNullList<ItemStack> items = NonNullList.withSize(this.inventory.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        ItemStack[] stacks = this.inventory.getStacks();
        for (int i = 0; i < items.size() && i < stacks.length; i++) {
            stacks[i] = items.get(i);
        }
        this.ensureConfigured();
        this.sanitizeSlotLocks();
        this.sanitizeFluidSlotState();
        if (this.energyStorage != null) {
            input.child("energy").ifPresent(this.energyStorage::deserialize);
        } else {
            this.pendingEnergy = input.read("energy", CompoundTag.CODEC).orElse(null);
        }
        if (this.fluidHandler != null) {
            input.child("fluid").ifPresent(this.fluidHandler::deserialize);
        } else {
            this.pendingFluid = input.read("fluid", CompoundTag.CODEC).orElse(null);
        }

        if (this.level != null && this.level.isClientSide()) {
            this.updateClientCache();
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public int getContainerSize() {
        return this.inventory.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return this.inventory.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.inventory.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = this.inventory.removeItem(slot, amount);
        if (!stack.isEmpty()) {
            this.setChanged();
        }
        return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return this.inventory.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.inventory.setItem(slot, stack);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return this.inventory.canPlaceItem(slot, stack);
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        if (!this.allowsCapability(this.worldPosition, CapabilityPorts.CapabilityType.ITEM)) {
            return new int[0];
        }
        return this.inventory.getSlotsForFace(direction);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return this.allowsCapability(this.worldPosition, CapabilityPorts.CapabilityType.ITEM)
                && this.inventory.canPlaceItemThroughFace(slot, stack, direction);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return this.allowsCapability(this.worldPosition, CapabilityPorts.CapabilityType.ITEM)
                && this.inventory.canTakeItemThroughFace(slot, stack, direction);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.inventory.clearContent();
    }

    @Override
    public Component getDisplayName() {
        MultiblockDefinition definition = this.definition();
        return definition == null ? this.getBlockState().getBlock().getName() : definition.displayName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        this.ensureConfigured();
        return new MultiblockMenu(containerId, inventory, this, this.menuLayout);
    }

    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        this.ensureConfigured();
        buffer.writeBlockPos(this.worldPosition);
        MultiblockMenuLayout layout = menu instanceof MultiblockMenu multiblockMenu
                ? multiblockMenu.layout()
                : this.menuLayout;
        Tag tag = MultiblockMenuLayout.CODEC.encodeStart(NbtOps.INSTANCE, layout)
                .getOrThrow(message -> new IllegalStateException("Failed to encode multiblock gui layout: " + message));
        buffer.writeNbt(tag);
    }

    private record AssemblyRequirement(BlockPos pos, BlockMatcher matcher) {}
}
