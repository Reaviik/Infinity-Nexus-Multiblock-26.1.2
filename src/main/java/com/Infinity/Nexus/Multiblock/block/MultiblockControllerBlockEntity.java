package com.Infinity.Nexus.Multiblock.block;

import com.Infinity.Nexus.Core.itemStackHandler.RestrictedItemStackHandler;
import com.Infinity.Nexus.Multiblock.api.*;
import com.Infinity.Nexus.Multiblock.api.addon.MultiblockResourceStorage;
import com.Infinity.Nexus.Multiblock.api.addon.StoredResource;
import com.Infinity.Nexus.Multiblock.entity.MultiblockMotionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultiblockControllerBlockEntity extends MultiblockControllerBase implements WorldlyContainer, MenuProvider, Nameable {
    private final MultiblockMotionController motions;
    private final MultiblockUpgradeController upgrades;
    private final MultiblockAssemblyController assembly;
    private final MultiblockRecipeController recipe;
    private final MultiblockCapabilityController capabilities;
    private final MultiblockSlotController slots;
    private final MultiblockStructureController structure;
    private final MultiblockStorageController storage;
    private final MultiblockPersistenceController persistence;
    private final MultiblockLifecycleController lifecycle;
    private final MultiblockTickController ticker;
    private final MultiblockContainerController container;
    private final MultiblockDefinitionController definitions;

    public MultiblockControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.motions = new MultiblockMotionController(this);
        this.upgrades = new MultiblockUpgradeController(this);
        this.assembly = new MultiblockAssemblyController(this);
        this.recipe = new MultiblockRecipeController(this);
        this.capabilities = new MultiblockCapabilityController(this);
        this.slots = new MultiblockSlotController(this);
        this.structure = new MultiblockStructureController(this);
        this.storage = new MultiblockStorageController(this);
        this.persistence = new MultiblockPersistenceController(this);
        this.lifecycle = new MultiblockLifecycleController(this);
        this.ticker = new MultiblockTickController(this);
        this.container = new MultiblockContainerController(this);
        this.definitions = new MultiblockDefinitionController(this);
        this.inventory = new RestrictedItemStackHandler(54);
        this.inventory.setOnChangeCallback(this::setChanged);
        this.inputSlots = new int[0];
        this.outputSlots = new int[0];
        this.menuLayout = MultiblockMenuLayout.create(null);
        this.definitions.configure(null);
    }

    void ensureConfigured() {
        this.definitions.ensureConfigured();
    }

    public @Nullable MultiblockDefinition definition() {
        return this.definitions.definition();
    }

    public @Nullable Identifier definitionId() {
        return this.definitions.definitionId();
    }

    public void setDefinitionId(Identifier definitionId) {
        this.definitions.setDefinitionId(definitionId);
    }

    public boolean isFormed() {
        return this.formed;
    }

    public List<BlockPos> upgradeMembers() {
        return this.upgrades.members();
    }

    public int[] upgradeCounts() {
        return this.upgrades.counts();
    }

    public boolean allowsCapability(BlockPos pos, CapabilityPorts.CapabilityType type) {
        return this.capabilities.allows(pos, type);
    }

    public boolean allowsResourceCapability(BlockPos pos, String kind) {
        return this.capabilities.allowsResource(pos, kind);
    }

    public Set<BlockPos> resourceCapabilityPorts(String kind) {
        return this.capabilities.resourcePorts(kind);
    }

    public @Nullable List<Integer> resourcePortTanks(String kind, BlockPos pos) {
        return this.capabilities.resourcePortTanks(kind, pos);
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
        return this.slots.slotLockAt(slotIndex);
    }

    public boolean slotLockAllows(int slotIndex, ItemStack stack) {
        return this.slots.slotLockAllows(slotIndex, stack);
    }

    public boolean setSlotLocked(int slotIndex, boolean locked) {
        return this.slots.setSlotLocked(slotIndex, locked);
    }

    public int fluidSlotTankIndex(int slotIndex) {
        return this.slots.fluidSlotTankIndex(slotIndex);
    }

    public boolean setFluidSlotTank(int slotIndex, int tankIndex) {
        return this.slots.setFluidSlotTank(slotIndex, tankIndex);
    }

    public boolean cycleFluidSlotTank(int slotIndex) {
        return this.slots.cycleFluidSlotTank(slotIndex);
    }

    public boolean interactWithTank(Player player, AbstractContainerMenu menu, String tankId) {
        return this.slots.interactWithTank(player, menu, tankId);
    }

    public void onFluidSlotPlaced(int slotIndex) {
        this.slots.onFluidSlotPlaced(slotIndex);
    }

    public ItemStack onFluidSlotTaken(int slotIndex, ItemStack removed) {
        return this.slots.onFluidSlotTaken(slotIndex, removed);
    }

    public int resourceSlotIndex(int slotIndex) {
        return this.slots.resourceSlotIndex(slotIndex);
    }

    public void onResourceSlotPlaced(int slotIndex) {
        this.slots.onResourceSlotPlaced(slotIndex);
    }

    public ItemStack onResourceSlotTaken(int slotIndex, ItemStack removed) {
        return this.slots.onResourceSlotTaken(slotIndex, removed);
    }

    public @Nullable MultiblockEnergyStorage energyStorage() {
        return this.storage.energyStorage();
    }

    public @Nullable MultiblockFluidHandler fluidHandler() {
        return this.storage.fluidHandler();
    }

    public @Nullable MultiblockResourceStorage resourceStorage(String kind) {
        return this.storage.resourceStorage(kind);
    }

    public Map<String, MultiblockResourceStorage> resourceStorages() {
        return this.storage.resourceStorages();
    }

    public List<StoredResource> resourceSnapshot() {
        return this.storage.resourceSnapshot();
    }

    public void restoreResources(List<StoredResource> stored) {
        this.storage.restoreResources(stored);
    }

    public int progress() {
        return this.recipe.progress();
    }

    public int maxProgress() {
        return this.recipe.maxProgress();
    }

    public List<EntityType<?>> activeEntityTypes() {
        return this.recipe.activeEntityTypes();
    }

    public List<AreaEntityCount> areaEntityCounts() {
        return this.recipe.areaEntityCounts();
    }
    public record AreaEntityCount(EntityType<?> type, int count) {
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
        List<Integer> tanks = this.capabilities.fluidPortTanks().get(pos);
        if (tanks == null || tanks.size() == this.fluidHandler.size()) {
            return this.fluidHandler;
        }
        return this.fluidHandler.indexedAccess(tanks);
    }

    public @Nullable ResourceHandler<FluidResource> getManualFluidHandler(BlockPos pos, @Nullable Direction direction) {
        if (this.fluidHandler == null) {
            return null;
        }
        List<Integer> tanks = this.capabilities.fluidPortTanks().get(pos);
        if (tanks == null || tanks.size() == this.fluidHandler.size()) {
            return this.fluidHandler.manualAccessAll();
        }
        return this.fluidHandler.manualAccess(tanks);
    }

    void onCapabilityChanged() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.capabilitySyncPending = true;
        }
    }

    public void serverTick() {
        this.ticker.serverTick();
    }

    void updateBuildingState() {
        this.ticker.updateBuildingState();
    }

    void updateProcessingState() {
        this.ticker.updateProcessingState();
    }

    public void clientTick() {
        this.ticker.clientTick();
    }

    public boolean isAssembling() {
        return this.assembly.isAssembling();
    }

    public float assemblyProgress() {
        return this.assembly.progress();
    }

    void resetRecipe() {
        this.recipe.reset();
    }

    Set<Character> motionKeysFor(MultiblockDefinition definition) {
        return this.motions.keysFor(definition);
    }

    private Set<Character> motionKeys(@Nullable MultiblockDefinition definition) {
        return this.motions.keys(definition);
    }

    void ensureMotionsStarted(ServerLevel serverLevel, MultiblockDefinition definition) {
        this.motions.ensureStarted(serverLevel, definition);
    }

    void stopMotions() {
        this.motions.stop();
    }

    public boolean acceptsMotion(MultiblockMotionEntity entity) {
        return this.motions.accepts(entity);
    }

    public void onMotionEntityRemoved(MultiblockMotionEntity entity) {
        this.motions.onRemoved(entity);
    }

    UpgradeBonus bonus(@Nullable MultiblockDefinition definition, MultiblockUpgrade.Modifier modifier) {
        return this.upgrades.bonus(definition, modifier);
    }

    /**
     * Dano final de uma motion apos os boosters de {@code damage}. Usado pela
     * entidade de motion a cada intervalo de dano.
     */
    public float motionDamage(float base) {
        MultiblockDefinition definition = this.definition();
        if (definition == null || definition.upgrades().isEmpty()) {
            return base;
        }
        return MultiblockUpgradeMath.scaledDamage(
                base, this.bonus(definition, MultiblockUpgrade.Modifier.DAMAGE));
    }

    public int[] boosterDisplayData() {
        return this.upgrades.boosterDisplayData(this.definition());
    }

    void refreshUpgrades(@Nullable MultiblockDefinition definition) {
        this.upgrades.refresh(definition);
    }


    public void onNeighborChanged() {
        this.structure.onNeighborChanged();
    }

    @Nullable ServerLevel serverLevel() {
        return this.level instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    MultiblockCapabilityController capabilities() {
        return this.capabilities;
    }

    MultiblockRecipeController recipe() {
        return this.recipe;
    }

    MultiblockAssemblyController assembly() {
        return this.assembly;
    }

    MultiblockMotionController motions() {
        return this.motions;
    }

    MultiblockSlotController slots() {
        return this.slots;
    }

    MultiblockUpgradeController upgrades() {
        return this.upgrades;
    }

    MultiblockStorageController storage() {
        return this.storage;
    }

    public void validateStructure() {
        this.structure.validate();
    }

    public boolean tryAssemble(Player player) {
        return this.assembly.tryAssemble(player);
    }

    public void exportStructure(Player player) {
        this.structure.export(player);
    }

    public void invalidateStructure() {
        this.structure.invalidate();
    }

    void invalidateCapabilities(List<BlockPos> positions) {
        if (this.level == null) {
            return;
        }
        for (BlockPos pos : positions) {
            this.level.invalidateCapabilities(pos);
        }
    }

    void clearCapabilityPorts() {
        this.capabilities.clear();
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        this.lifecycle.setLevel(level);
    }

    @Override
    public void onLoad() {
        this.lifecycle.onLoad();
    }

    @Override
    public void setRemoved() {
        this.lifecycle.setRemoved();
        super.setRemoved();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        this.lifecycle.preRemoveSideEffects();
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        this.persistence.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        this.persistence.load(input);
    }

    void afterLoad() {
        this.lifecycle.afterLoad();
    }

    @Override
    public ModelData getModelData() {
        Identifier definitionId = this.definitionId;
        return definitionId == null ? ModelData.EMPTY : ModelData.of(DEFINITION_PROPERTY, definitionId);
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
        return this.container.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return this.container.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.container.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return this.container.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return this.container.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.container.setItem(slot, stack);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return this.container.canPlaceItem(slot, stack);
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return this.container.getSlotsForFace(direction);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return this.container.canPlaceItemThroughFace(slot, stack, direction);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return this.container.canTakeItemThroughFace(slot, stack, direction);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public void clearContent() {
        this.container.clearContent();
    }

    @Override
    public Component getName() {
        return this.container.getName();
    }

    @Override
    public Component getDisplayName() {
        return this.container.getDisplayName();
    }

    @Override
    public @Nullable Component getCustomName() {
        return this.container.getCustomName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return this.container.createMenu(containerId, inventory, player);
    }

    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        this.container.writeClientSideData(menu, buffer);
    }
}
