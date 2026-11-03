package com.Infinity.Nexus.Multiblock.block;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.Infinity.Nexus.Core.itemStackHandler.RestrictedItemStackHandler;
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
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeInput;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

public class MultiblockControllerBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
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
    private final Map<String, SlotLock> slotLocks = new LinkedHashMap<>();

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
                this.syncSlotLocks();
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
        this.syncSlotLocks();
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

    private void syncSlotLocks() {
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

    public @Nullable EntityType<?> activeEntityOutputType() {
        if (this.activeRecipe != null
                && this.activeRecipe.output() instanceof MultiblockRecipeOutput.Entity entity) {
            return entity.entityType();
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public @Nullable EntityType<?> previewEntityOutputType() {
        if (this.activeRecipe != null) {
            return this.activeEntityOutputType();
        }
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return null;
        }
        RecipeType<?> recipeType = this.recipeType();
        if (recipeType == null) {
            return null;
        }
        Collection<RecipeHolder<?>> holders = (Collection) serverLevel.recipeAccess()
                .recipeMap()
                .byType((RecipeType) recipeType);
        for (RecipeHolder<?> holder : holders) {
            if (holder.value() instanceof MultiblockRecipe recipe
                    && java.util.Objects.equals(recipe.definition(), this.definitionId)
                    && recipe.output() instanceof MultiblockRecipeOutput.Entity entity) {
                return entity.entityType();
            }
        }
        return null;
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction direction) {
        return this.energyStorage;
    }

    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction direction) {
        return this.fluidHandler;
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
            if (this.progress != 0 || this.maxProgress != 0) {
                this.resetRecipe();
            }
            if (input.isEmpty()) {
                return;
            }
            Optional<RecipeHolder<MultiblockRecipe>> found = findRecipe(serverLevel, recipeType, input);
            if (found.isEmpty()) {
                return;
            }
            MultiblockRecipe recipe = found.get().value();
            if (!this.canCompleteRecipe(recipe)) {
                return;
            }
            this.activeRecipe = recipe;
            this.activeRecipeId = found.get().id().identifier();
            this.maxProgress = recipe.time();
            this.progress = 0;
            this.setChanged();
            return;
        }

        MultiblockRecipe recipe = this.activeRecipe;
        if (this.maxProgress <= 0
                || this.progress < 0
                || this.progress >= this.maxProgress
                || !recipe.matches(input, serverLevel)
                || !this.canCompleteRecipe(recipe)) {
            this.resetRecipe();
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
        for (RecipeHolder<?> holder : holders) {
            if (holder.value() instanceof MultiblockRecipe recipe
                    && java.util.Objects.equals(recipe.definition(), this.definitionId)
                    && recipe.matches(input, serverLevel)) {
                return Optional.of((RecipeHolder<MultiblockRecipe>) (RecipeHolder) holder);
            }
        }
        return Optional.empty();
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

    private boolean canCompleteRecipe(MultiblockRecipe recipe) {
        return switch (recipe.output()) {
            case MultiblockRecipeOutput.Item item -> this.canInsertItemResult(item.stack().create());
            case MultiblockRecipeOutput.Fluid fluid -> this.canInsertFluidResult(fluid.stack());
            case MultiblockRecipeOutput.Entity entity -> this.resolveSpawnPos(entity).isPresent();
        };
    }

    private boolean canInsertItemResult(ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        MultiblockDefinition definition = this.definition();
        if (definition == null) {
            return false;
        }
        List<ItemSlot> slots = definition.itemSlots();
        for (int slot : this.outputSlots) {
            ItemSlot itemSlot = slots.get(slot);
            if (!itemSlot.accepts(result)) {
                continue;
            }
            ItemStack current = this.inventory.getItem(slot);
            int limit = Math.min(itemSlot.limit(), result.getMaxStackSize());
            if (current.isEmpty()) {
                return result.getCount() <= limit;
            }
            if (ItemStack.isSameItemSameComponents(current, result)) {
                if (current.getCount() + result.getCount() <= limit) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canInsertFluidResult(FluidStack result) {
        if (result.isEmpty() || result.getAmount() < 1 || this.fluidHandler == null) {
            return false;
        }
        MultiblockDefinition definition = this.definition();
        if (definition == null) {
            return false;
        }
        FluidResource resource = FluidResource.of(result);
        for (int index = 0; index < this.fluidHandler.size(); index++) {
            FluidTank tank = definition.fluidTanks().get(index);
            if (!tank.accepts(resource)) {
                continue;
            }
            FluidResource current = this.fluidHandler.getResource(index);
            if (!current.isEmpty() && !current.matches(result)) {
                continue;
            }
            int amount = this.fluidHandler.getAmountAsInt(index);
            if ((long) amount + result.getAmount() <= tank.capacity()) {
                return true;
            }
        }
        return false;
    }

    private void completeRecipe(MultiblockRecipe recipe) {
        ServerLevel serverLevel = this.serverLevel();
        if (serverLevel == null) {
            return;
        }
        int[] matchedSlots = recipe.matchItemSlots(this.createRecipeInput().items());
        for (int slot : matchedSlots) {
            if (slot < 0) {
                return;
            }
        }
        boolean outputReady = switch (recipe.output()) {
            case MultiblockRecipeOutput.Item item -> this.insertItemResult(item.stack().create());
            case MultiblockRecipeOutput.Fluid fluid -> this.insertFluidResult(fluid.stack());
            case MultiblockRecipeOutput.Entity entity -> this.spawnEntity(serverLevel, entity);
        };
        if (!outputReady) {
            return;
        }
        for (int slot : matchedSlots) {
            this.inventory.removeItem(this.inputSlots[slot], 1);
        }
        this.consumeFluidIngredients(recipe);
    }

    private void consumeFluidIngredients(MultiblockRecipe recipe) {
        if (this.fluidHandler == null || recipe.fluidIngredients().isEmpty()) {
            return;
        }
        boolean[] used = new boolean[this.fluidHandler.size()];
        for (SizedFluidIngredient ingredient : recipe.fluidIngredients()) {
            for (int index = 0; index < this.fluidHandler.size(); index++) {
                if (used[index]) {
                    continue;
                }
                FluidResource resource = this.fluidHandler.getResource(index);
                int amount = this.fluidHandler.getAmountAsInt(index);
                if (resource.isEmpty() || amount < ingredient.amount()
                        || !ingredient.test(resource.toStack(amount))) {
                    continue;
                }
                used[index] = true;
                this.fluidHandler.set(index, resource, amount - ingredient.amount());
                break;
            }
        }
    }

    private boolean insertItemResult(ItemStack result) {
        MultiblockDefinition definition = this.definition();
        if (definition == null) {
            return false;
        }
        List<ItemSlot> slots = definition.itemSlots();
        for (int slot : this.outputSlots) {
            ItemSlot itemSlot = slots.get(slot);
            if (!itemSlot.accepts(result)) {
                continue;
            }
            ItemStack current = this.inventory.getItem(slot);
            int limit = Math.min(itemSlot.limit(), result.getMaxStackSize());
            if (current.isEmpty()) {
                if (result.getCount() > limit) {
                    continue;
                }
                this.inventory.setItem(slot, result.copy());
                return true;
            }
            if (ItemStack.isSameItemSameComponents(current, result)) {
                if (current.getCount() + result.getCount() <= limit) {
                    ItemStack merged = current.copy();
                    merged.grow(result.getCount());
                    this.inventory.setItem(slot, merged);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean insertFluidResult(FluidStack result) {
        if (!this.canInsertFluidResult(result)) {
            return false;
        }
        MultiblockDefinition definition = this.definition();
        if (definition == null) {
            return false;
        }
        FluidResource resource = FluidResource.of(result);
        for (int index = 0; index < this.fluidHandler.size(); index++) {
            FluidTank tank = definition.fluidTanks().get(index);
            if (!tank.accepts(resource)) {
                continue;
            }
            FluidResource current = this.fluidHandler.getResource(index);
            if (!current.isEmpty() && !current.matches(result)) {
                continue;
            }
            int amount = this.fluidHandler.getAmountAsInt(index);
            if ((long) amount + result.getAmount() <= tank.capacity()) {
                this.fluidHandler.set(index, resource, amount + result.getAmount());
                return true;
            }
        }
        return false;
    }

    private boolean spawnEntity(ServerLevel serverLevel, MultiblockRecipeOutput.Entity output) {
        Optional<BlockPos> spawnPos = this.resolveSpawnPos(output);
        if (spawnPos.isEmpty()) {
            return false;
        }
        return output.entityType().spawn(
                serverLevel,
                spawnPos.get().above(),
                EntitySpawnReason.SPAWNER) != null;
    }

    private Optional<BlockPos> resolveSpawnPos(MultiblockRecipeOutput.Entity output) {
        MultiblockDefinition definition = this.definition();
        if (definition == null || !this.formed || this.level == null) {
            return Optional.empty();
        }
        CapabilityPorts.PortSelector selector = output.selector().orElse(null);
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

    private void resetRecipe() {
        if (this.progress != 0 || this.maxProgress != 0 || this.activeRecipe != null || this.activeRecipeId != null) {
            this.progress = 0;
            this.maxProgress = 0;
            this.activeRecipe = null;
            this.activeRecipeId = null;
            this.setChanged();
        }
    }

    private static int energyCost(MultiblockRecipe recipe, int progress) {
        long total = recipe.energy();
        long consumedBefore = total * progress / recipe.time();
        long consumedAfter = total * (progress + 1L) / recipe.time();
        return (int) (consumedAfter - consumedBefore);
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
        this.updateCapabilityPorts(serverLevel, definition, newOrigin, newRotation, matched);
        if (!previousItemPorts.equals(this.itemCapabilityPorts)
                || !previousEnergyPorts.equals(this.energyCapabilityPorts)
                || !previousFluidPorts.equals(this.fluidCapabilityPorts)) {
            Set<BlockPos> invalidated = new HashSet<>();
            invalidated.addAll(previousItemPorts);
            invalidated.addAll(previousEnergyPorts);
            invalidated.addAll(previousFluidPorts);
            invalidated.addAll(this.itemCapabilityPorts);
            invalidated.addAll(this.energyCapabilityPorts);
            invalidated.addAll(this.fluidCapabilityPorts);
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
        if (matched) {
            CapabilityPorts ports = definition.capabilityPorts();
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
                }
            }
        }
        this.itemCapabilityPorts = Set.copyOf(items);
        this.energyCapabilityPorts = Set.copyOf(energy);
        this.fluidCapabilityPorts = Set.copyOf(fluids);
    }

    private void clearCapabilityPorts() {
        this.itemCapabilityPorts = Set.of();
        this.energyCapabilityPorts = Set.of();
        this.fluidCapabilityPorts = Set.of();
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
        output.store("MenuLayout", MultiblockMenuLayout.CODEC, this.menuLayout);
        output.store("SlotLocks", SlotLock.CODEC.listOf(), List.copyOf(this.slotLocks.values()));
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
        this.menuLayout = input.read("MenuLayout", MultiblockMenuLayout.CODEC).orElse(this.menuLayout);
        this.slotLocks.clear();
        for (SlotLock lock : input.read("SlotLocks", SlotLock.CODEC.listOf()).orElse(List.of())) {
            this.slotLocks.put(lock.key(), lock);
        }
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
        EntityType<?> outputEntityType = menu instanceof MultiblockMenu multiblockMenu
                && multiblockMenu.outputEntityType() != null
                ? multiblockMenu.outputEntityType()
                : this.previewEntityOutputType();
        Identifier outputEntityId = outputEntityType == null
                ? null
                : BuiltInRegistries.ENTITY_TYPE.getKey(outputEntityType);
        buffer.writeUtf(outputEntityId == null ? "" : outputEntityId.toString());
    }

    private record AssemblyRequirement(BlockPos pos, BlockMatcher matcher) {}
}
