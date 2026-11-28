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
import java.util.UUID;

import com.Infinity.Nexus.Core.itemStackHandler.RestrictedItemStackHandler;
import com.mojang.serialization.Codec;
import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.CapabilityPorts;
import com.Infinity.Nexus.Multiblock.api.FluidTank;
import com.Infinity.Nexus.Multiblock.api.ItemSlot;
import com.Infinity.Nexus.Multiblock.api.ItemSlotRole;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.addon.MultiblockAddons;
import com.Infinity.Nexus.Multiblock.api.addon.MultiblockResourceStorage;
import com.Infinity.Nexus.Multiblock.api.addon.MultiblockResourceType;
import com.Infinity.Nexus.Multiblock.api.addon.ResourceTankInfo;
import com.Infinity.Nexus.Multiblock.api.addon.StoredResource;
import com.Infinity.Nexus.Multiblock.api.MultiblockMenuLayout;
import com.Infinity.Nexus.Multiblock.api.MultiblockMotion;
import com.Infinity.Nexus.Multiblock.api.MultiblockUpgrade;
import com.Infinity.Nexus.Multiblock.api.MultiblockUpgradeMath;
import com.Infinity.Nexus.Multiblock.api.MultiblockUpgradeMath.ScaledAmount;
import com.Infinity.Nexus.Multiblock.api.UpgradeBonus;
import com.Infinity.Nexus.Multiblock.data.MultiblockDefinitions;
import com.Infinity.Nexus.Multiblock.registry.ModRecipes;
import com.Infinity.Nexus.Multiblock.api.MultiblockPattern;
import com.Infinity.Nexus.Multiblock.api.SlotLock;
import com.Infinity.Nexus.Multiblock.client.ClientMultiblockModelData;
import com.Infinity.Nexus.Multiblock.client.MultiblockClientCache;
import com.Infinity.Nexus.Multiblock.config.Config;
import com.Infinity.Nexus.Multiblock.data.MultiblockSavedData;
import com.Infinity.Nexus.Multiblock.data.MultiblockStructureExporter;
import com.Infinity.Nexus.Multiblock.entity.ModEntities;
import com.Infinity.Nexus.Multiblock.entity.MotionStructure.PartEntry;
import com.Infinity.Nexus.Multiblock.entity.MotionStructure.StructureSnapshot;
import com.Infinity.Nexus.Multiblock.entity.MultiblockMotionEntity;
import com.Infinity.Nexus.Multiblock.network.MultiblockBlockedHighlightPayload;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
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
import net.minecraft.world.level.block.Blocks;
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
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

final class MultiblockRecipeController {
    private final MultiblockControllerBlockEntity owner;
    private int progress;
    private int maxProgress;
    private @Nullable Identifier activeRecipeId;
    private @Nullable MultiblockRecipe activeRecipe;
    private int activeThreads = 1;
    private long activeEnergyTotal;
    private List<EntityType<?>> activeEntityTypes = List.of();
    private List<MultiblockControllerBlockEntity.AreaEntityCount> areaEntityCounts = List.of();
    private long lastAreaEntityScan = -20L;

    MultiblockRecipeController(MultiblockControllerBlockEntity owner) {
        this.owner = owner;
    }

    boolean isProcessing() {
        return this.owner.formed && this.activeRecipe != null && this.progress > 0;
    }

    void save(ValueOutput output) {
        output.putInt("Progress", this.progress);
        output.putInt("MaxProgress", this.maxProgress);
        if (this.activeRecipeId != null) {
            output.store("Recipe", Identifier.CODEC, this.activeRecipeId);
        }
        output.store("ActiveEntities", Identifier.CODEC.listOf(), this.activeEntityTypeIds());
        ValueOutput upgrades = output.child("Upgrades");
        upgrades.putInt("Threads", this.activeThreads);
        upgrades.putLong("EnergyTotal", this.activeEnergyTotal);
    }

    void load(ValueInput input) {
        this.progress = input.getIntOr("Progress", 0);
        this.maxProgress = input.getIntOr("MaxProgress", 0);
        this.activeRecipeId = input.read("Recipe", Identifier.CODEC).orElse(null);
        this.activeRecipe = null;
        this.activeThreads = 1;
        this.activeEnergyTotal = 0L;
        input.child("Upgrades").ifPresent(upgrades -> {
            this.activeThreads = Math.max(1, Math.min(
                    MultiblockUpgradeMath.MAX_THREADS,
                    upgrades.getIntOr("Threads", 1)));
            this.activeEnergyTotal = Math.max(0L, upgrades.getLongOr("EnergyTotal", 0L));
        });
        this.activeEntityTypes = input.read("ActiveEntities", Identifier.CODEC.listOf())
                .orElse(List.of())
                .stream()
                .flatMap(id -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).stream())
                .toList();
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

    public List<MultiblockControllerBlockEntity.AreaEntityCount> areaEntityCounts() {
        long gameTime = this.owner.getLevel() == null ? 0L : this.owner.getLevel().getGameTime();
        if (this.lastAreaEntityScan < 0L
                || gameTime - this.lastAreaEntityScan >= MultiblockControllerBase.AREA_ENTITY_SCAN_INTERVAL) {
            this.lastAreaEntityScan = gameTime;
            this.areaEntityCounts = this.scanAreaEntities();
        }
        return this.areaEntityCounts;
    }

    private List<MultiblockControllerBlockEntity.AreaEntityCount> scanAreaEntities() {
        if (this.owner.getLevel() == null) {
            return List.of();
        }
        AABB area = this.multiblockBounds();
        if (area == null) {
            return List.of();
        }
        Map<EntityType<?>, Integer> counts = new LinkedHashMap<>();
        for (Entity entity : this.owner.getLevel().getEntitiesOfClass(Entity.class, area,
                entity -> entity.isAlive()
                        && entity instanceof LivingEntity
                        && !(entity instanceof Player))) {
            counts.merge(entity.getType(), 1, Integer::sum);
        }
        List<MultiblockControllerBlockEntity.AreaEntityCount> result = new ArrayList<>(counts.size());
        for (Map.Entry<EntityType<?>, Integer> entry : counts.entrySet()) {
            result.add(new MultiblockControllerBlockEntity.AreaEntityCount(entry.getKey(), entry.getValue()));
        }
        result.sort((left, right) -> entityTypeId(left.type()).compareTo(entityTypeId(right.type())));
        return List.copyOf(result);
    }

    private static String entityTypeId(EntityType<?> type) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id == null ? "" : id.toString();
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
        return this.owner.getLevel() == null ? null : this.owner.getLevel().registryAccess().lookupOrThrow(Registries.ENTITY_TYPE);
    }

    private void syncClientState() {
        if (this.owner.getLevel() != null && !this.owner.getLevel().isClientSide()) {
            BlockState state = this.owner.getBlockState();
            this.owner.getLevel().sendBlockUpdated(this.owner.getBlockPos(), state, state, Block.UPDATE_CLIENTS);
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


    void tick() {
        ServerLevel serverLevel = this.owner.serverLevel();
        if (serverLevel == null) {
            return;
        }
        MultiblockDefinition definition = this.owner.definition();
        if (definition == null
                || !definition.type().isRecipe()
                || !this.owner.formed) {
            this.reset();
            return;
        }

        RecipeType<?> recipeType = this.recipeType();
        if (recipeType == null) {
            this.owner.stopMotions();
            this.reset();
            return;
        }

        if (this.activeRecipe == null && this.activeRecipeId != null) {
            this.resolveActiveRecipe(serverLevel);
        }

        MultiblockRecipeInput input = this.createRecipeInput();
        if (this.activeRecipe == null) {
            if (this.progress != 0 || this.maxProgress != 0 || !this.activeEntityTypes.isEmpty()) {
                this.reset();
            }
            Optional<RecipeHolder<MultiblockRecipe>> found = findRecipe(serverLevel, recipeType, input);
            if (found.isEmpty()) {
                this.owner.stopMotions();
                return;
            }
            MultiblockRecipe recipe = found.get().value();
            this.activeRecipe = recipe;
            this.activeRecipeId = found.get().id().identifier();
            this.owner.refreshUpgrades(definition);
            this.maxProgress = MultiblockUpgradeMath.effectiveTicks(
                    recipe.time(), this.owner.bonus(definition, MultiblockUpgrade.Modifier.SPEED));
            this.progress = 0;
            this.activeThreads = Math.max(1, Math.min(
                    MultiblockUpgradeMath.MAX_THREADS,
                    MultiblockUpgradeMath.threads(this.owner.bonus(definition, MultiblockUpgrade.Modifier.THREAD))));
            long perCraft = MultiblockUpgradeMath.effectiveEnergy(
                    recipe.energy(), this.owner.bonus(definition, MultiblockUpgrade.Modifier.ENERGY));
            this.activeEnergyTotal = perCraft * this.activeThreads;
            this.refreshActiveEntityTypes();
            this.owner.ensureMotionsStarted(serverLevel, definition);
            this.owner.setChanged();
            return;
        }

        this.owner.ensureMotionsStarted(serverLevel, definition);
        MultiblockRecipe recipe = this.activeRecipe;
        UpgradeBonus efficiency = this.owner.bonus(definition, MultiblockUpgrade.Modifier.EFFICIENCY);
        UpgradeBonus output = this.owner.bonus(definition, MultiblockUpgrade.Modifier.OUTPUT);
        if (this.maxProgress <= 0
                || this.progress < 0
                || this.progress >= this.maxProgress
                || !this.hasStorageFor(recipe)
                || !recipe.matches(input, serverLevel, efficiency)
                || !this.matchesEntityInputs(recipe)) {
            this.reset();
            return;
        }
        if (!this.canCompleteRecipe(recipe, input, output, efficiency)) {
            return;
        }

        long energyCost = energyCost(recipe, this.progress);
        if (energyCost > 0L) {
            if (this.owner.energyStorage == null || this.owner.energyStorage.getAmountAsLong() < energyCost) {
                return;
            }
            this.owner.energyStorage.set((int) Math.min(
                    Integer.MAX_VALUE,
                    this.owner.energyStorage.getAmountAsLong() - energyCost));
        }

        this.progress++;
        if (this.progress >= this.maxProgress) {
            int threads = Math.max(1, Math.min(this.activeThreads,
                    MultiblockUpgradeMath.threads(this.owner.bonus(definition, MultiblockUpgrade.Modifier.THREAD))));
            boolean completed = false;
            for (int craft = 0; craft < threads; craft++) {
                if (!this.completeRecipe(recipe, output, efficiency)) {
                    break;
                }
                completed = true;
            }
            this.progress = 0;
            this.maxProgress = 0;
            this.activeRecipe = null;
            this.activeRecipeId = null;
            this.activeThreads = 1;
            this.activeEnergyTotal = 0L;
            this.refreshActiveEntityTypes();
            if (completed) {
                definition.sounds().finish().ifPresent(this::playMachineSound);
            }
        } else if ((this.progress - 1) % MultiblockControllerBase.PROCESSING_SOUND_INTERVAL == 0) {
            definition.sounds().processing().ifPresent(this::playMachineSound);
        }
        this.owner.setChanged();
    }

    private void playMachineSound(Identifier soundId) {
        if (this.owner.getLevel() == null) {
            return;
        }
        this.owner.getLevel().playSound(
                null,
                this.owner.getBlockPos(),
                SoundEvent.createVariableRangeEvent(soundId),
                SoundSource.BLOCKS,
                1.0F,
                1.0F);
    }

    private void resolveActiveRecipe(ServerLevel serverLevel) {
        Identifier recipeId = this.activeRecipeId;
        RecipeType<?> recipeType = this.recipeType();
        if (recipeId == null || recipeType == null) {
            this.reset();
            return;
        }
        Optional<RecipeHolder<?>> holder = serverLevel.recipeAccess()
                .byKey(ResourceKey.create(Registries.RECIPE, recipeId));
        if (holder.isPresent()
                && holder.get().value() instanceof MultiblockRecipe recipe
                && recipe.getType() == recipeType
                && java.util.Objects.equals(recipe.definition(), this.owner.definitionId)) {
            this.activeRecipe = recipe;
            if (this.maxProgress <= 0) {
                MultiblockDefinition definition = this.owner.definition();
                this.owner.refreshUpgrades(definition);
                this.maxProgress = MultiblockUpgradeMath.effectiveTicks(
                        recipe.time(), this.owner.bonus(definition, MultiblockUpgrade.Modifier.SPEED));
            }
            this.refreshActiveEntityTypes();
        } else {
            this.reset();
        }
    }

    private @Nullable RecipeType<?> recipeType() {
        MultiblockDefinition definition = this.owner.definition();
        return definition != null && definition.type().isRecipe()
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
        MultiblockDefinition definition = this.owner.definition();
        UpgradeBonus output = this.owner.bonus(definition, MultiblockUpgrade.Modifier.OUTPUT);
        UpgradeBonus efficiency = this.owner.bonus(definition, MultiblockUpgrade.Modifier.EFFICIENCY);
        for (RecipeHolder<?> holder : holders) {
            if (holder.value() instanceof MultiblockRecipe recipe
                    && java.util.Objects.equals(recipe.definition(), this.owner.definitionId)
                    && this.hasStorageFor(recipe)
                    && recipe.matches(input, serverLevel, efficiency)
                    && this.matchesEntityInputs(recipe)) {
                RecipeHolder<MultiblockRecipe> matched = (RecipeHolder<MultiblockRecipe>) (RecipeHolder) holder;
                if (this.canCompleteRecipe(recipe, input, output, efficiency)) {
                    return Optional.of(matched);
                }
                if (this.requiresSpaceToStart(recipe)) {
                    continue;
                }
                if (fallback == null) {
                    fallback = matched;
                }
            }
        }
        return Optional.ofNullable(fallback);
    }

    private MultiblockRecipeInput createRecipeInput() {
        List<ItemStack> items = new ArrayList<>(this.owner.inputSlots.length);
        for (int slot : this.owner.inputSlots) {
            items.add(this.owner.inventory.getItem(slot));
        }
        List<FluidStack> fluids = new ArrayList<>();
        if (this.owner.fluidHandler != null) {
            for (int index = 0; index < this.owner.fluidHandler.size(); index++) {
                FluidResource resource = this.owner.fluidHandler.getResource(index);
                int amount = this.owner.fluidHandler.getAmountAsInt(index);
                fluids.add(resource.isEmpty() || amount <= 0 ? FluidStack.EMPTY : resource.toStack(amount));
            }
        }
        List<StoredResource> resources = new ArrayList<>();
        for (MultiblockResourceStorage storage : this.owner.resourceStorages.values()) {
            resources.addAll(storage.snapshot());
        }
        return new MultiblockRecipeInput(items, fluids, resources);
    }

    private boolean hasStorageFor(MultiblockRecipe recipe) {
        if (!this.hasEnergyStorageFor(recipe)) {
            return false;
        }
        for (MultiblockRecipeIngredient.Resource input : recipe.resourceInputs()) {
            if (this.owner.resourceStorages.get(input.typeId()) == null) {
                return false;
            }
        }
        for (MultiblockRecipeOutput.Resource output : recipe.resourceOutputs()) {
            if (this.owner.resourceStorages.get(output.typeId()) == null) {
                return false;
            }
        }
        return true;
    }

    private boolean hasEnergyStorageFor(MultiblockRecipe recipe) {
        return recipe.energy() <= 0 || this.owner.energyStorage != null;
    }

    private boolean requiresSpaceToStart(MultiblockRecipe recipe) {
        for (MultiblockRecipeOutput.Resource output : recipe.resourceOutputs()) {
            MultiblockResourceStorage storage = this.owner.resourceStorages.get(output.typeId());
            if (storage != null && storage.requiresSpaceToStart()) {
                return true;
            }
        }
        return false;
    }

    private boolean canCompleteRecipe(
            MultiblockRecipe recipe,
            MultiblockRecipeInput input,
            UpgradeBonus outputBonus,
            UpgradeBonus efficiency) {
        return this.planOutputs(recipe, false, input, outputBonus, efficiency) != null;
    }

    private @Nullable OutputPlan planOutputs(
            MultiblockRecipe recipe,
            boolean rollChance,
            MultiblockRecipeInput input,
            UpgradeBonus outputBonus,
            UpgradeBonus efficiency) {
        ServerLevel serverLevel = this.owner.serverLevel();
        MultiblockDefinition definition = this.owner.definition();
        if (serverLevel == null || definition == null) {
            return null;
        }
        ItemStack[] items = new ItemStack[this.owner.outputSlots.length];
        for (int index = 0; index < this.owner.outputSlots.length; index++) {
            items[index] = this.owner.inventory.getItem(this.owner.outputSlots[index]).copy();
        }
        int tankCount = this.owner.fluidHandler == null ? 0 : this.owner.fluidHandler.size();
        FluidResource[] resources = new FluidResource[tankCount];
        int[] amounts = new int[tankCount];
        for (int index = 0; index < tankCount; index++) {
            resources[index] = this.owner.fluidHandler.getResource(index);
            amounts[index] = this.owner.fluidHandler.getAmountAsInt(index);
        }
        long energy = this.owner.energyStorage == null ? 0L : this.owner.energyStorage.getAmountAsLong();
        long energyCapacity = this.owner.energyStorage == null ? 0L : this.owner.energyStorage.getCapacityAsLong();
        if (this.owner.energyStorage != null) {
            energy = Math.max(0L, energy - remainingEnergyCost(recipe, this.progress));
        }
        int[] matchedTanks = recipe.matchFluidTanks(input.fluids(), efficiency);
        List<MultiblockRecipeIngredient.Fluid> fluidInputs = recipe.fluidInputs();
        for (int index = 0; index < matchedTanks.length; index++) {
            int tank = matchedTanks[index];
            if (tank < 0) {
                return null;
            }
            amounts[tank] -= MultiblockUpgradeMath.scaledInput(
                    fluidInputs.get(index).ingredient().amount(), efficiency);
            if (amounts[tank] <= 0) {
                amounts[tank] = 0;
                resources[tank] = FluidResource.EMPTY;
            }
        }
        Map<String, List<ResourceTankState>> resourceStates = new LinkedHashMap<>();
        for (Map.Entry<String, MultiblockResourceStorage> entry : this.owner.resourceStorages.entrySet()) {
            MultiblockResourceStorage storage = entry.getValue();
            List<ResourceTankState> states = new ArrayList<>(storage.size());
            for (int index = 0; index < storage.size(); index++) {
                states.add(new ResourceTankState(
                        storage.tankId(index),
                        storage.capacity(index),
                        storage.resourceId(index),
                        storage.amount(index),
                        storage,
                        index));
            }
            resourceStates.put(entry.getKey(), states);
        }
        List<MultiblockRecipe.ResourceAllocation> resourceInputs =
                recipe.findResourceInputs(input.resources(), efficiency);
        if (resourceInputs == null) {
            return null;
        }
        for (MultiblockRecipe.ResourceAllocation allocation : resourceInputs) {
            List<ResourceTankState> states = resourceStates.get(allocation.kind());
            if (states == null) {
                return null;
            }
            ResourceTankState state = findResourceTank(states, allocation.tank());
            if (state == null || state.amount < allocation.amount()) {
                return null;
            }
            state.amount -= allocation.amount();
            if (state.amount <= 0L) {
                state.amount = 0L;
                state.id = "";
            }
        }
        List<PlannedResource> plannedResources = new ArrayList<>();
        List<PlannedEntity> entities = new ArrayList<>();
        for (MultiblockRecipeOutput recipeOutput : recipe.outputs()) {
            if (rollChance && recipeOutput.chance() < 1.0F
                    && serverLevel.getRandom().nextFloat() >= recipeOutput.chance()) {
                continue;
            }
            switch (recipeOutput) {
                case MultiblockRecipeOutput.Item item -> {
                    ItemStack stack = item.stack().create();
                    ScaledAmount scaled = MultiblockUpgradeMath.scaleOutput(
                            stack.getCount(), outputBonus, rollChance, serverLevel.getRandom());
                    stack.setCount(scaled.whole());
                    if (!this.insertItemPlan(definition, items, stack)) {
                        return null;
                    }
                    if (scaled.extra()) {
                        ItemStack extra = item.stack().create();
                        extra.setCount(1);
                        ItemStack[] trial = items.clone();
                        if (this.insertItemPlan(definition, trial, extra)) {
                            System.arraycopy(trial, 0, items, 0, items.length);
                        }
                    }
                }
                case MultiblockRecipeOutput.Fluid fluid -> {
                    FluidStack fluidStack = fluid.stack();
                    ScaledAmount scaled = MultiblockUpgradeMath.scaleOutput(
                            fluidStack.getAmount(), outputBonus, rollChance, serverLevel.getRandom());
                    FluidStack scaledStack = fluidStack.copy();
                    scaledStack.setAmount(scaled.whole());
                    if (!this.insertFluidPlan(definition, resources, amounts, scaledStack)) {
                        return null;
                    }
                    if (scaled.extra()) {
                        FluidResource[] trialResources = resources.clone();
                        int[] trialAmounts = amounts.clone();
                        FluidStack extra = fluidStack.copy();
                        extra.setAmount(1);
                        if (this.insertFluidPlan(definition, trialResources, trialAmounts, extra)) {
                            System.arraycopy(trialResources, 0, resources, 0, resources.length);
                            System.arraycopy(trialAmounts, 0, amounts, 0, amounts.length);
                        }
                    }
                }
                case MultiblockRecipeOutput.Energy energyOutput -> {
                    ScaledAmount scaled = MultiblockUpgradeMath.scaleOutput(
                            energyOutput.amount(), outputBonus, rollChance, serverLevel.getRandom());
                    if (this.owner.energyStorage == null || energy + scaled.whole() > energyCapacity) {
                        return null;
                    }
                    energy += scaled.whole();
                    if (scaled.extra() && energy + 1L <= energyCapacity) {
                        energy++;
                    }
                }
                case MultiblockRecipeOutput.Resource resourceOutput -> {
                    List<ResourceTankState> states = resourceStates.get(resourceOutput.typeId());
                    if (states == null) {
                        return null;
                    }
                    ScaledAmount scaled = MultiblockUpgradeMath.scaleOutput(
                            resourceOutput.amount(), outputBonus, rollChance, serverLevel.getRandom());
                    int amount = scaled.whole();
                    ResourceTankState target = null;
                    if (!resourceOutput.tank().isEmpty()) {
                        target = findResourceTank(states, resourceOutput.tank());
                    } else {
                        for (ResourceTankState state : states) {
                            if (state.accepts(resourceOutput.id())
                                    && state.space(resourceOutput.id()) >= amount) {
                                target = state;
                                break;
                            }
                        }
                    }
                    if (target == null
                            || !target.accepts(resourceOutput.id())
                            || target.space(resourceOutput.id()) < amount) {
                        return null;
                    }
                    target.reserve(resourceOutput.id(), amount);
                    int produced = amount;
                    if (scaled.extra() && target.space(resourceOutput.id()) >= 1) {
                        target.reserve(resourceOutput.id(), 1);
                        produced++;
                    }
                    plannedResources.add(new PlannedResource(
                            resourceOutput.typeId(),
                            target.tank,
                            resourceOutput.id(),
                            produced));
                }
                case MultiblockRecipeOutput.Entity entityOutput -> {
                    Optional<BlockPos> spawnPos = this.resolveSpawnPos(entityOutput);
                    HolderGetter<EntityType<?>> registry = this.entityTypeRegistry();
                    if (spawnPos.isEmpty() || registry == null) {
                        return null;
                    }
                    ScaledAmount scaled = MultiblockUpgradeMath.scaleOutput(
                            entityOutput.count(), outputBonus, rollChance, serverLevel.getRandom());
                    for (int index = 0; index < scaled.whole(); index++) {
                        Optional<EntityType<?>> entityType = entityOutput.type()
                                .pick(registry, serverLevel.getRandom());
                        if (entityType.isEmpty()) {
                            return null;
                        }
                        entities.add(new PlannedEntity(entityType.get(), spawnPos.get().above()));
                    }
                    if (scaled.extra()) {
                        entityOutput.type().pick(registry, serverLevel.getRandom())
                                .ifPresent(type -> entities.add(
                                        new PlannedEntity(type, spawnPos.get().above())));
                    }
                }
            }
        }
        return new OutputPlan(
                items,
                resources,
                amounts,
                (int) Math.min(Integer.MAX_VALUE, energy),
                entities,
                List.copyOf(plannedResources));
    }

    private static @Nullable ResourceTankState findResourceTank(
            List<ResourceTankState> states,
            String tankId) {
        for (ResourceTankState state : states) {
            if (state.tank.equals(tankId)) {
                return state;
            }
        }
        return null;
    }

    private boolean insertItemPlan(
            MultiblockDefinition definition,
            ItemStack[] simulated,
            ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        ItemStack remaining = result.copy();
        for (int index = 0; index < this.owner.outputSlots.length; index++) {
            ItemSlot itemSlot = definition.itemSlots().get(this.owner.outputSlots[index]);
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
        for (int index = 0; index < this.owner.outputSlots.length; index++) {
            this.owner.inventory.setItem(this.owner.outputSlots[index], plan.items()[index]);
        }
        if (this.owner.fluidHandler != null) {
            for (int index = 0; index < plan.amounts().length; index++) {
                this.owner.fluidHandler.set(index, plan.resources()[index], plan.amounts()[index]);
            }
        }
        if (this.owner.energyStorage != null) {
            this.owner.energyStorage.set(plan.energy());
        }
        for (PlannedResource resource : plan.plannedResources()) {
            MultiblockResourceStorage storage = this.owner.resourceStorages.get(resource.kind());
            if (storage == null) {
                continue;
            }
            int index = storage.indexOf(resource.tank());
            if (index < 0) {
                continue;
            }
            storage.insert(index, resource.id(), resource.amount(), false);
        }
        for (PlannedEntity entity : plan.entities()) {
            entity.entityType().spawn(
                    serverLevel,
                    entity.spawnPos(),
                    EntitySpawnReason.SPAWNER);
        }
    }

    private boolean completeRecipe(
            MultiblockRecipe recipe,
            UpgradeBonus outputBonus,
            UpgradeBonus efficiency) {
        ServerLevel serverLevel = this.owner.serverLevel();
        if (serverLevel == null) {
            return false;
        }
        MultiblockRecipeInput input = this.createRecipeInput();
        List<MultiblockRecipe.ItemAllocation> itemAllocations = recipe.findItemInputs(input.items(), efficiency);
        if (itemAllocations == null) {
            return false;
        }
        int[] matchedTanks = recipe.matchFluidTanks(input.fluids(), efficiency);
        for (int tank : matchedTanks) {
            if (tank < 0) {
                return false;
            }
        }
        List<Entity> entityInputs = this.findEntityInputs(recipe);
        if (entityInputs == null) {
            return false;
        }
        List<MultiblockRecipe.ResourceAllocation> resourceAllocations =
                recipe.findResourceInputs(input.resources(), efficiency);
        if (resourceAllocations == null) {
            return false;
        }
        OutputPlan plan = this.planOutputs(recipe, true, input, outputBonus, efficiency);
        if (plan == null) {
            return false;
        }
        for (MultiblockRecipe.ItemAllocation allocation : itemAllocations) {
            this.owner.inventory.removeItem(this.owner.inputSlots[allocation.slot()], allocation.count());
        }
        for (MultiblockRecipe.ResourceAllocation allocation : resourceAllocations) {
            MultiblockResourceStorage storage = this.owner.resourceStorages.get(allocation.kind());
            if (storage == null) {
                return false;
            }
            int index = storage.indexOf(allocation.tank());
            if (index < 0) {
                return false;
            }
            if (storage.extract(index, allocation.id(), allocation.amount(), false) < allocation.amount()) {
                return false;
            }
        }
        this.commitOutputPlan(plan, serverLevel);
        for (Entity entity : entityInputs) {
            entity.discard();
        }
        return true;
    }

    private boolean matchesEntityInputs(MultiblockRecipe recipe) {
        return this.findEntityInputs(recipe) != null;
    }

    private @Nullable List<Entity> findEntityInputs(MultiblockRecipe recipe) {
        List<MultiblockRecipeIngredient.Entity> required = recipe.entityInputs();
        if (required.isEmpty()) {
            return List.of();
        }
        ServerLevel serverLevel = this.owner.serverLevel();
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
        if (!this.owner.formed) {
            return null;
        }
        int minX = this.owner.getBlockPos().getX();
        int minY = this.owner.getBlockPos().getY();
        int minZ = this.owner.getBlockPos().getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;
        for (BlockPos member : this.owner.members) {
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
        ServerLevel serverLevel = this.owner.serverLevel();
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
        MultiblockDefinition definition = this.owner.definition();
        if (definition == null || !this.owner.formed || this.owner.getLevel() == null) {
            return Optional.empty();
        }
        CapabilityPorts.PortSelector selector = CapabilityPorts.PortSelector.parse(spawn).orElse(null);
        if (selector == null) {
            return Optional.empty();
        }
        for (MultiblockPattern.PatternCell cell : definition.pattern().cells()) {
            BlockPos offset = new BlockPos(cell.x(), cell.y(), cell.z()).rotate(this.owner.rotation);
            BlockPos pos = this.owner.origin.offset(offset.getX(), offset.getY(), offset.getZ());
            if (selector.matches(cell.symbol(), this.owner.getLevel().getBlockState(pos), this.owner.rotation)) {
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
            List<PlannedEntity> entities,
            List<PlannedResource> plannedResources) {
    }

    private record PlannedEntity(EntityType<?> entityType, BlockPos spawnPos) {
    }

    private record PlannedResource(String kind, String tank, String id, int amount) {
    }


    private static final class ResourceTankState {
        private final String tank;
        private final long capacity;
        private final MultiblockResourceStorage storage;
        private final int index;
        private final Map<String, Long> planned = new LinkedHashMap<>();
        private String id;
        private long amount;

        private ResourceTankState(String tank, long capacity, String id, long amount,
                MultiblockResourceStorage storage, int index) {
            this.tank = tank;
            this.capacity = capacity;
            this.id = id;
            this.amount = amount;
            this.storage = storage;
            this.index = index;
        }

        private boolean accepts(String resourceId) {
            return this.storage.accepts(this.index, resourceId);
        }

        private long space(String resourceId) {
            if (!this.storage.perResourceSpace()) {
                return Math.max(0L, this.capacity - this.amount);
            }
            long available = this.storage.space(this.index, resourceId);
            return Math.max(0L, available - this.planned.getOrDefault(resourceId, 0L));
        }

        private void reserve(String resourceId, long outputAmount) {
            this.planned.merge(resourceId, outputAmount, Long::sum);
            this.id = resourceId;
            this.amount += outputAmount;
        }
    }


    void reset() {
        if (this.progress != 0 || this.maxProgress != 0 || this.activeRecipe != null
                || this.activeRecipeId != null || !this.activeEntityTypes.isEmpty()) {
            this.progress = 0;
            this.maxProgress = 0;
            this.activeRecipe = null;
            this.activeRecipeId = null;
            this.activeThreads = 1;
            this.activeEnergyTotal = 0L;
            this.refreshActiveEntityTypes();
            this.owner.setChanged();
        }
    }

    private long energyCost(MultiblockRecipe recipe, int progress) {
        long total = this.activeEnergyTotal > 0L ? this.activeEnergyTotal : recipe.energy();
        int duration = this.maxProgress > 0 ? this.maxProgress : Math.max(1, recipe.time());
        long consumedBefore = total * progress / duration;
        long consumedAfter = total * (progress + 1L) / duration;
        return consumedAfter - consumedBefore;
    }

    private long remainingEnergyCost(MultiblockRecipe recipe, int progress) {
        long total = this.activeEnergyTotal > 0L ? this.activeEnergyTotal : recipe.energy();
        int duration = this.maxProgress > 0 ? this.maxProgress : Math.max(1, recipe.time());
        long consumed = total * Math.min(progress, duration) / duration;
        return Math.max(0L, total - consumed);
    }
}