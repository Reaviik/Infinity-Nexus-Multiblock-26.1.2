package com.Infinity.Nexus.Multiblock.block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.Infinity.Nexus.Multiblock.api.CapabilityPorts;
import com.Infinity.Nexus.Multiblock.api.FluidTank;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockPattern;
import com.Infinity.Nexus.Multiblock.api.addon.MultiblockResourceStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class MultiblockCapabilityController {
    private final MultiblockControllerBlockEntity owner;
    private Set<BlockPos> itemPorts = Set.of();
    private Set<BlockPos> energyPorts = Set.of();
    private Set<BlockPos> fluidPorts = Set.of();
    private Map<BlockPos, List<Integer>> fluidPortTanks = Map.of();
    private Map<String, Set<BlockPos>> resourcePorts = Map.of();
    private Map<String, Map<BlockPos, List<Integer>>> resourcePortTanks = Map.of();

    MultiblockCapabilityController(MultiblockControllerBlockEntity owner) {
        this.owner = owner;
    }

    Set<BlockPos> itemPorts() {
        return this.itemPorts;
    }

    Set<BlockPos> energyPorts() {
        return this.energyPorts;
    }

    Set<BlockPos> fluidPorts() {
        return this.fluidPorts;
    }

    Map<BlockPos, List<Integer>> fluidPortTanks() {
        return this.fluidPortTanks;
    }

    Map<String, Set<BlockPos>> resourcePorts() {
        return this.resourcePorts;
    }

    Map<String, Map<BlockPos, List<Integer>>> resourcePortTanks() {
        return this.resourcePortTanks;
    }

    boolean allows(BlockPos pos, CapabilityPorts.CapabilityType type) {
        MultiblockDefinition definition = this.owner.definition();
        if (definition == null) {
            return false;
        }
        if (!this.owner.formed) {
            return this.owner.getBlockPos().equals(pos) && !definition.capabilityPorts().isDefined(type);
        }
        return switch (type) {
            case ITEM -> this.itemPorts.contains(pos);
            case ENERGY -> this.energyPorts.contains(pos);
            case FLUID -> this.fluidPorts.contains(pos);
        };
    }

    boolean allowsResource(BlockPos pos, String kind) {
        MultiblockDefinition definition = this.owner.definition();
        if (definition == null || !definition.resourceKinds().contains(kind)) {
            return false;
        }
        if (!this.owner.formed) {
            return this.owner.getBlockPos().equals(pos) && !definition.capabilityPorts().isDefined(kind);
        }
        return this.resourcePorts.getOrDefault(kind, Set.of()).contains(pos);
    }

    Set<BlockPos> resourcePorts(String kind) {
        return this.resourcePorts.getOrDefault(kind, Set.of());
    }

    @Nullable List<Integer> resourcePortTanks(String kind, BlockPos pos) {
        Map<BlockPos, List<Integer>> ports = this.resourcePortTanks.get(kind);
        return ports == null ? null : ports.get(pos);
    }

    void update(
            Level level,
            MultiblockDefinition definition,
            BlockPos newOrigin,
            Rotation newRotation,
            boolean matched,
            Map<String, MultiblockResourceStorage> resourceStorages) {
        Set<BlockPos> items = new HashSet<>();
        Set<BlockPos> energy = new HashSet<>();
        Set<BlockPos> fluids = new HashSet<>();
        Map<BlockPos, List<Integer>> fluidTanksByPort = new LinkedHashMap<>();
        Map<String, Set<BlockPos>> nextResourcePorts = new LinkedHashMap<>();
        Map<String, Map<BlockPos, List<Integer>>> resourceTanksByPort = new LinkedHashMap<>();
        if (matched) {
            CapabilityPorts ports = definition.capabilityPorts();
            List<FluidTank> tanks = definition.fluidTanks();
            for (String kind : definition.resourceKinds()) {
                nextResourcePorts.put(kind, new HashSet<>());
                resourceTanksByPort.put(kind, new LinkedHashMap<>());
            }
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
                for (String kind : definition.resourceKinds()) {
                    MultiblockResourceStorage storage = resourceStorages.get(kind);
                    if (storage == null || !ports.allows(kind, cell.symbol(), state, newRotation)) {
                        continue;
                    }
                    nextResourcePorts.get(kind).add(pos);
                    List<Integer> allowed = new ArrayList<>(storage.size());
                    for (int index = 0; index < storage.size(); index++) {
                        if (storage.allowsPort(index, cell.symbol(), state, newRotation)) {
                            allowed.add(index);
                        }
                    }
                    resourceTanksByPort.get(kind).put(pos, List.copyOf(allowed));
                }
            }
        }
        this.itemPorts = Set.copyOf(items);
        this.energyPorts = Set.copyOf(energy);
        this.fluidPorts = Set.copyOf(fluids);
        this.fluidPortTanks = Map.copyOf(fluidTanksByPort);
        this.resourcePorts = copyPorts(nextResourcePorts);
        this.resourcePortTanks = copyResourceTanks(resourceTanksByPort);
    }

    void clear() {
        this.itemPorts = Set.of();
        this.energyPorts = Set.of();
        this.fluidPorts = Set.of();
        this.fluidPortTanks = Map.of();
        this.resourcePorts = Map.of();
        this.resourcePortTanks = Map.of();
    }

    void invalidate(Level level, BlockPos controllerPos) {
        Set<BlockPos> positions = new HashSet<>();
        positions.add(controllerPos);
        positions.addAll(this.itemPorts);
        positions.addAll(this.energyPorts);
        positions.addAll(this.fluidPorts);
        this.resourcePorts.values().forEach(positions::addAll);
        this.resourcePortTanks.values().forEach(map -> positions.addAll(map.keySet()));
        positions.forEach(level::invalidateCapabilities);
    }

    Snapshot snapshot() {
        return new Snapshot(
                this.itemPorts,
                this.energyPorts,
                this.fluidPorts,
                this.fluidPortTanks,
                this.resourcePorts,
                this.resourcePortTanks);
    }

    boolean changedFrom(Snapshot previous) {
        return !previous.items().equals(this.itemPorts)
                || !previous.energy().equals(this.energyPorts)
                || !previous.fluids().equals(this.fluidPorts)
                || !previous.fluidTanks().equals(this.fluidPortTanks)
                || !previous.resources().equals(this.resourcePorts)
                || !previous.resourceTanks().equals(this.resourcePortTanks);
    }

    private static Map<String, Set<BlockPos>> copyPorts(Map<String, Set<BlockPos>> ports) {
        if (ports.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<BlockPos>> copy = new LinkedHashMap<>();
        ports.forEach((kind, positions) -> copy.put(kind, Set.copyOf(positions)));
        return Map.copyOf(copy);
    }

    private static Map<String, Map<BlockPos, List<Integer>>> copyResourceTanks(
            Map<String, Map<BlockPos, List<Integer>>> tanks) {
        if (tanks.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<BlockPos, List<Integer>>> copy = new LinkedHashMap<>();
        tanks.forEach((kind, byPort) -> copy.put(kind, Map.copyOf(byPort)));
        return Map.copyOf(copy);
    }

    record Snapshot(
            Set<BlockPos> items,
            Set<BlockPos> energy,
            Set<BlockPos> fluids,
            Map<BlockPos, List<Integer>> fluidTanks,
            Map<String, Set<BlockPos>> resources,
            Map<String, Map<BlockPos, List<Integer>>> resourceTanks) {
    }
}
