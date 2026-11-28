package com.Infinity.Nexus.Multiblock.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.addon.MultiblockAddons;
import com.Infinity.Nexus.Multiblock.api.addon.MultiblockResourceStorage;
import com.Infinity.Nexus.Multiblock.api.addon.MultiblockResourceType;
import com.Infinity.Nexus.Multiblock.api.addon.ResourceTankInfo;
import com.Infinity.Nexus.Multiblock.api.addon.StoredResource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

final class MultiblockStorageController {
    private final MultiblockControllerBlockEntity owner;

    MultiblockStorageController(MultiblockControllerBlockEntity owner) {
        this.owner = owner;
    }

    void configure(MultiblockDefinition definition, boolean changed) {
        if (definition.energy().isEmpty()) {
            this.owner.energyStorage = null;
            this.owner.pendingEnergy = null;
        } else if (changed || this.owner.energyStorage == null) {
            var energy = definition.energy().orElseThrow();
            long stored = this.owner.energyStorage == null ? 0L : this.owner.energyStorage.getAmountAsLong();
            MultiblockEnergyStorage replacement = new MultiblockEnergyStorage(
                    energy.id(),
                    energy.capacity(),
                    energy.maxReceive(),
                    energy.maxExtract(),
                    this.owner::onCapabilityChanged);
            replacement.set((int) Math.min(stored, energy.capacity()));
            this.owner.energyStorage = replacement;
        }

        if (definition.fluidTanks().isEmpty()) {
            this.owner.fluidHandler = null;
            this.owner.pendingFluid = null;
        } else if (changed || this.owner.fluidHandler == null) {
            MultiblockFluidHandler replacement =
                    new MultiblockFluidHandler(definition.fluidTanks(), this.owner::onCapabilityChanged);
            if (this.owner.fluidHandler != null) {
                copyFluids(this.owner.fluidHandler, replacement);
            }
            this.owner.fluidHandler = replacement;
        }
        this.configureResourceStorages(definition);
    }

    private static void copyFluids(MultiblockFluidHandler source, MultiblockFluidHandler target) {
        int count = Math.min(source.size(), target.size());
        for (int index = 0; index < count; index++) {
            FluidResource resource = source.getResource(index);
            long amount = source.getAmountAsLong(index);
            if (resource.isEmpty() || amount <= 0L || !target.isValid(index, resource)) {
                continue;
            }
            target.set(index, resource, (int) Math.min(amount, target.getCapacityAsLong(index, resource)));
        }
    }

    private void configureResourceStorages(MultiblockDefinition definition) {
        Set<String> kinds = definition.resourceKinds();
        this.owner.resourceStorages.keySet().removeIf(kind -> !kinds.contains(kind));
        this.owner.pendingResources.keySet().removeIf(kind -> !kinds.contains(kind));
        for (String kind : kinds) {
            MultiblockResourceType type = MultiblockAddons.get(kind);
            if (type == null) {
                continue;
            }
            List<ResourceTankInfo> tanks = definition.resourceTanks(kind);
            MultiblockResourceStorage existing = this.owner.resourceStorages.get(kind);
            if (existing != null && sameTanks(existing, tanks)) {
                continue;
            }
            MultiblockResourceStorage storage = type.createStorage(
                    definition,
                    definition.resourceConfigs().get(kind),
                    this.owner::onCapabilityChanged);
            if (existing != null) {
                storage.restore(existing.snapshot());
            }
            this.owner.resourceStorages.put(kind, storage);
        }
    }

    private static boolean sameTanks(MultiblockResourceStorage storage, List<ResourceTankInfo> tanks) {
        if (storage.size() != tanks.size()) {
            return false;
        }
        for (int index = 0; index < tanks.size(); index++) {
            ResourceTankInfo tank = tanks.get(index);
            if (!storage.tankId(index).equals(tank.id()) || storage.capacity(index) != tank.capacity()) {
                return false;
            }
        }
        return true;
    }

    void applyPending() {
        if (this.owner.getLevel() == null) {
            return;
        }
        var registries = this.owner.getLevel().registryAccess();
        if (this.owner.pendingEnergy != null && this.owner.energyStorage != null) {
            this.owner.energyStorage.deserialize(
                    TagValueInput.create(ProblemReporter.DISCARDING, registries, this.owner.pendingEnergy));
            this.owner.pendingEnergy = null;
        }
        if (this.owner.pendingFluid != null && this.owner.fluidHandler != null) {
            this.owner.fluidHandler.deserialize(
                    TagValueInput.create(ProblemReporter.DISCARDING, registries, this.owner.pendingFluid));
            this.owner.pendingFluid = null;
        }
        if (!this.owner.pendingResources.isEmpty()) {
            for (Map.Entry<String, CompoundTag> entry : List.copyOf(this.owner.pendingResources.entrySet())) {
                MultiblockResourceStorage storage = this.owner.resourceStorages.get(entry.getKey());
                if (storage == null) {
                    continue;
                }
                storage.deserialize(TagValueInput.create(
                        ProblemReporter.DISCARDING, registries, entry.getValue()));
                this.owner.pendingResources.remove(entry.getKey());
            }
        }
    }

    /**
     * Preenchimento medio (0 a 1) entre as categorias de armazenamento que
     * existem na definicao: itens, energia, fluidos e recursos de addons.
     */
    double fillRatio() {
        MultiblockDefinition definition = this.owner.definition();
        double total = 0.0D;
        int categories = 0;

        int slots = this.owner.inventory.getContainerSize();
        if (slots > 0) {
            double used = 0.0D;
            double capacity = 0.0D;
            for (int index = 0; index < slots; index++) {
                int limit = 64;
                if (definition != null && index < definition.itemSlots().size()) {
                    limit = definition.itemSlots().get(index).limit();
                }
                capacity += limit;
                used += Math.min(limit, this.owner.inventory.getItem(index).getCount());
            }
            if (capacity > 0.0D) {
                total += used / capacity;
                categories++;
            }
        }

        if (this.owner.energyStorage != null) {
            long capacity = this.owner.energyStorage.getCapacityAsLong();
            if (capacity > 0L) {
                total += (double) this.owner.energyStorage.getAmountAsLong() / capacity;
                categories++;
            }
        }

        if (this.owner.fluidHandler != null) {
            double used = 0.0D;
            double capacity = 0.0D;
            for (int index = 0; index < this.owner.fluidHandler.size(); index++) {
                int tankCapacity = this.owner.fluidHandler.tank(index).capacity();
                if (tankCapacity <= 0) {
                    continue;
                }
                capacity += tankCapacity;
                used += this.owner.fluidHandler.getAmountAsLong(index);
            }
            if (capacity > 0.0D) {
                total += used / capacity;
                categories++;
            }
        }

        if (!this.owner.resourceStorages.isEmpty()) {
            double used = 0.0D;
            double capacity = 0.0D;
            for (MultiblockResourceStorage storage : this.owner.resourceStorages.values()) {
                for (int index = 0; index < storage.size(); index++) {
                    long tankCapacity = storage.capacity(index);
                    if (tankCapacity <= 0L) {
                        continue;
                    }
                    capacity += tankCapacity;
                    used += storage.amount(index);
                }
            }
            if (capacity > 0.0D) {
                total += used / capacity;
                categories++;
            }
        }

        return categories == 0 ? 0.0D : total / categories;
    }

    @Nullable MultiblockEnergyStorage energyStorage() {
        return this.owner.energyStorage;
    }

    @Nullable MultiblockFluidHandler fluidHandler() {
        return this.owner.fluidHandler;
    }

    @Nullable MultiblockResourceStorage resourceStorage(String kind) {
        return kind == null ? null : this.owner.resourceStorages.get(kind);
    }

    Map<String, MultiblockResourceStorage> resourceStorages() {
        return Map.copyOf(this.owner.resourceStorages);
    }

    List<StoredResource> resourceSnapshot() {
        List<StoredResource> stored = new ArrayList<>();
        for (MultiblockResourceStorage storage : this.owner.resourceStorages.values()) {
            stored.addAll(storage.snapshot());
        }
        return List.copyOf(stored);
    }

    void restoreResources(List<StoredResource> stored) {
        if (stored == null || stored.isEmpty()) {
            return;
        }
        Map<String, List<StoredResource>> byKind = new LinkedHashMap<>();
        for (StoredResource resource : stored) {
            byKind.computeIfAbsent(resource.kind(), key -> new ArrayList<>()).add(resource);
        }
        for (Map.Entry<String, List<StoredResource>> entry : byKind.entrySet()) {
            MultiblockResourceStorage storage = this.owner.resourceStorages.get(entry.getKey());
            if (storage != null) {
                storage.restore(entry.getValue());
            }
        }
    }
}
