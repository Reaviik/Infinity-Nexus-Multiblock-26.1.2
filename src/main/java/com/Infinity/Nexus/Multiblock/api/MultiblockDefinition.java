package com.Infinity.Nexus.Multiblock.api;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;

public final class MultiblockDefinition {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private final Identifier id;
    private final int schemaVersion;
    private final MultiblockType type;
    private final int priority;
    private final int tier;
    private final MultiblockPattern pattern;
    private final int inventoryRows;
    private final List<ItemSlot> itemSlots;
    private final Optional<EnergyCapability> energy;
    private final List<FluidTank> fluidTanks;
    private final Optional<MultiblockGui> gui;
    private final CapabilityPorts capabilityPorts;

    private MultiblockDefinition(
            Identifier id,
            int schemaVersion,
            MultiblockType type,
            int priority,
            int tier,
            MultiblockPattern pattern,
            int inventoryRows,
            List<ItemSlot> itemSlots,
            Optional<EnergyCapability> energy,
            List<FluidTank> fluidTanks,
            Optional<MultiblockGui> gui,
            CapabilityPorts capabilityPorts) {
        this.id = id;
        this.schemaVersion = schemaVersion;
        this.type = type;
        this.priority = priority;
        this.tier = tier;
        this.pattern = pattern;
        this.inventoryRows = inventoryRows;
        this.itemSlots = itemSlots;
        this.energy = energy;
        this.fluidTanks = fluidTanks;
        this.gui = gui;
        this.capabilityPorts = capabilityPorts;
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public Identifier id() {
        return this.id;
    }

    public Component displayName() {
        String key = "multiblock." + this.id.getNamespace() + "." + this.id.getPath();
        return Component.translatableWithFallback(key, fallbackName(this.id.getPath()));
    }

    private static String fallbackName(String path) {
        String[] words = path.split("_");
        StringBuilder name = new StringBuilder(path.length());
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            name.append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return name.isEmpty() ? path : name.toString();
    }

    public int schemaVersion() {
        return this.schemaVersion;
    }

    public MultiblockType type() {
        return this.type;
    }

    public int priority() {
        return this.priority;
    }

    public int tier() {
        return this.tier;
    }

    public MultiblockPattern pattern() {
        return this.pattern;
    }

    public int inventoryRows() {
        return this.inventoryRows;
    }

    public int inventorySize() {
        return this.itemSlots.isEmpty() ? this.inventoryRows * 9 : this.itemSlots.size();
    }

    public List<ItemSlot> itemSlots() {
        return this.itemSlots;
    }

    public Optional<EnergyCapability> energy() {
        return this.energy;
    }

    public List<FluidTank> fluidTanks() {
        return this.fluidTanks;
    }

    public Optional<MultiblockGui> gui() {
        return this.gui;
    }

    public CapabilityPorts capabilityPorts() {
        return this.capabilityPorts;
    }

    public BlockPos origin(BlockPos controllerPos, Rotation rotation) {
        return this.pattern.origin(controllerPos, rotation);
    }

    public List<BlockPos> members(BlockPos controllerPos, Rotation rotation) {
        return this.pattern.members(controllerPos, rotation);
    }

    public boolean matches(LevelReader level, BlockPos controllerPos, Rotation rotation, Block anchorBlock) {
        BlockPos origin = this.origin(controllerPos, rotation);
        BlockPos anchorPos = this.pattern.anchorPos(controllerPos, rotation);
        if (!level.getBlockState(anchorPos).is(anchorBlock)) {
            return false;
        }
        return this.pattern.matches(level, origin, rotation);
    }

    public static final class Builder {
        private final Identifier id;
        private int schemaVersion = CURRENT_SCHEMA_VERSION;
        private MultiblockType type = MultiblockType.RECIPE;
        private int priority;
        private int tier = 1;
        private MultiblockPattern pattern;
        private int inventoryRows = 3;
        private List<ItemSlot> itemSlots = List.of();
        private EnergyCapability energy;
        private List<FluidTank> fluidTanks = List.of();
        private MultiblockGui gui;
        private CapabilityPorts capabilityPorts = CapabilityPorts.ALL;

        private Builder(Identifier id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder schemaVersion(int schemaVersion) {
            if (schemaVersion != CURRENT_SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported schema version: " + schemaVersion);
            }
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder type(MultiblockType type) {
            this.type = Objects.requireNonNull(type, "type");
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder tier(int tier) {
            if (tier < 1) {
                throw new IllegalArgumentException("tier must be at least 1");
            }
            this.tier = tier;
            return this;
        }

        public Builder pattern(MultiblockPattern pattern) {
            this.pattern = Objects.requireNonNull(pattern, "pattern");
            return this;
        }

        public Builder inventoryRows(int inventoryRows) {
            if (inventoryRows < 1 || inventoryRows > 6) {
                throw new IllegalArgumentException("inventoryRows must be between 1 and 6");
            }
            this.inventoryRows = inventoryRows;
            return this;
        }

        public Builder itemSlots(List<ItemSlot> itemSlots) {
            this.itemSlots = List.copyOf(Objects.requireNonNull(itemSlots, "itemSlots"));
            return this;
        }

        public Builder energy(EnergyCapability energy) {
            this.energy = Objects.requireNonNull(energy, "energy");
            return this;
        }

        public Builder fluidTanks(List<FluidTank> fluidTanks) {
            this.fluidTanks = List.copyOf(Objects.requireNonNull(fluidTanks, "fluidTanks"));
            return this;
        }

        public Builder gui(MultiblockGui gui) {
            this.gui = Objects.requireNonNull(gui, "gui");
            return this;
        }

        public Builder capabilityPorts(CapabilityPorts capabilityPorts) {
            this.capabilityPorts = Objects.requireNonNull(capabilityPorts, "capabilityPorts");
            return this;
        }

        public MultiblockDefinition build() {
            if (this.pattern == null) {
                throw new IllegalStateException("A multiblock definition needs a pattern");
            }
            Set<String> slotIds = new HashSet<>();
            for (ItemSlot slot : this.itemSlots) {
                if (!slotIds.add(slot.id())) {
                    throw new IllegalStateException("Duplicate item slot id: " + slot.id());
                }
            }
            Set<String> tankIds = new HashSet<>();
            for (FluidTank tank : this.fluidTanks) {
                if (!tankIds.add(tank.id())) {
                    throw new IllegalStateException("Duplicate fluid tank id: " + tank.id());
                }
            }
            if (this.energy != null && tankIds.contains(this.energy.id())) {
                throw new IllegalStateException("Energy capability id conflicts with a fluid tank id: " + this.energy.id());
            }
            this.capabilityPorts.validate(this.pattern);
            return new MultiblockDefinition(
                    this.id,
                    this.schemaVersion,
                    this.type,
                    this.priority,
                    this.tier,
                    this.pattern,
                    this.inventoryRows,
                    this.itemSlots,
                    Optional.ofNullable(this.energy),
                    this.fluidTanks,
                    Optional.ofNullable(this.gui),
                    this.capabilityPorts);
        }
    }
}
