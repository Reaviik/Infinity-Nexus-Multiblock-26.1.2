package com.Infinity.Nexus.Multiblock.api;

import com.Infinity.Nexus.Multiblock.api.addon.ResourceTankInfo;
import com.google.gson.JsonElement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;

import java.util.*;

public final class MultiblockDefinition {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private final Identifier id;
    private final int schemaVersion;
    private final MultiblockType type;
    private final int priority;
    private final int tier;
    private final MultiblockPattern pattern;
    private final List<MultiblockUpgrade> upgrades;
    private final List<MultiblockMotion> motions;
    private final int inventoryRows;
    private final List<ItemSlot> itemSlots;
    private final Optional<EnergyCapability> energy;
    private final List<FluidTank> fluidTanks;
    private final Optional<MultiblockGui> gui;
    private final MultiblockSounds sounds;
    private final MultiblockTextures textures;
    private final MultiblockModel model;
    private final MultiblockShape shape;
    private final CapabilityPorts capabilityPorts;
    private final Map<String, JsonElement> resourceConfigs;
    private final Map<String, List<ResourceTankInfo>> resourceTanks;

    private MultiblockDefinition(
            Identifier id,
            int schemaVersion,
            MultiblockType type,
            int priority,
            int tier,
            MultiblockPattern pattern,
            List<MultiblockUpgrade> upgrades,
            List<MultiblockMotion> motions,
            int inventoryRows,
            List<ItemSlot> itemSlots,
            Optional<EnergyCapability> energy,
            List<FluidTank> fluidTanks,
            Optional<MultiblockGui> gui,
            MultiblockSounds sounds,
            MultiblockTextures textures,
            MultiblockModel model,
            MultiblockShape shape,
            CapabilityPorts capabilityPorts,
            Map<String, JsonElement> resourceConfigs,
            Map<String, List<ResourceTankInfo>> resourceTanks) {
        this.id = id;
        this.schemaVersion = schemaVersion;
        this.type = type;
        this.priority = priority;
        this.tier = tier;
        this.pattern = pattern;
        this.upgrades = List.copyOf(upgrades);
        this.motions = List.copyOf(motions);
        this.inventoryRows = inventoryRows;
        this.itemSlots = itemSlots;
        this.energy = energy;
        this.fluidTanks = fluidTanks;
        this.gui = gui;
        this.sounds = sounds;
        this.textures = textures;
        this.model = model;
        this.shape = shape;
        this.capabilityPorts = capabilityPorts;
        this.resourceConfigs = Map.copyOf(resourceConfigs);
        this.resourceTanks = Map.copyOf(resourceTanks);
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

    public List<MultiblockUpgrade> upgrades() {
        return this.upgrades;
    }

    public List<MultiblockMotion> motions() {
        return this.motions;
    }

    /**
     * Uniao das keys de todas as partes moveis. Enquanto as partes estao fora
     * do lugar, essas celulas sao ignoradas na validacao da estrutura.
     */
    public Set<Character> motionKeys() {
        if (this.motions.isEmpty()) {
            return Set.of();
        }
        Set<Character> keys = new HashSet<>();
        for (MultiblockMotion motion : this.motions) {
            keys.addAll(motion.keys());
        }
        return Set.copyOf(keys);
    }

    public Optional<Character> upgradeKey() {
        return this.pattern.hasUpgradeKey()
                ? Optional.of(this.pattern.upgradeKey())
                : Optional.empty();
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

    public MultiblockSounds sounds() {
        return this.sounds;
    }

    public MultiblockTextures textures() {
        return this.textures;
    }

    public MultiblockModel model() {
        return this.model;
    }

    public MultiblockShape shape() {
        return this.shape;
    }

    public CapabilityPorts capabilityPorts() {
        return this.capabilityPorts;
    }

    public Map<String, JsonElement> resourceConfigs() {
        return this.resourceConfigs;
    }

    public List<ResourceTankInfo> resourceTanks(String kind) {
        return this.resourceTanks.getOrDefault(kind, List.of());
    }

    public Map<String, List<ResourceTankInfo>> resourceTanks() {
        return this.resourceTanks;
    }

    public Set<String> resourceKinds() {
        return this.resourceTanks.keySet();
    }

    public BlockPos origin(BlockPos controllerPos, Rotation rotation) {
        return this.pattern.origin(controllerPos, rotation);
    }

    public List<BlockPos> members(BlockPos controllerPos, Rotation rotation) {
        return this.pattern.members(controllerPos, rotation);
    }

    public List<BlockPos> upgradeMembers(BlockPos controllerPos, Rotation rotation) {
        return this.pattern.upgradeMembers(controllerPos, rotation);
    }

    public boolean matches(LevelReader level, BlockPos controllerPos, Rotation rotation, Block anchorBlock) {
        return matches(level, controllerPos, rotation, anchorBlock, Set.of());
    }

    public boolean matches(
            LevelReader level,
            BlockPos controllerPos,
            Rotation rotation,
            Block anchorBlock,
            Set<Character> ignoredSymbols) {
        BlockPos origin = this.origin(controllerPos, rotation);
        BlockPos anchorPos = this.pattern.anchorPos(controllerPos, rotation);
        if (!level.getBlockState(anchorPos).is(anchorBlock)) {
            return false;
        }
        return this.pattern.matches(level, origin, rotation, ignoredSymbols);
    }

    public static final class Builder {
        private final Identifier id;
        private int schemaVersion = CURRENT_SCHEMA_VERSION;
        private MultiblockType type = MultiblockType.RECIPE;
        private int priority;
        private int tier = 1;
        private MultiblockPattern pattern;
        private List<MultiblockUpgrade> upgrades = List.of();
        private List<MultiblockMotion> motions = List.of();
        private int inventoryRows = 3;
        private List<ItemSlot> itemSlots = List.of();
        private EnergyCapability energy;
        private List<FluidTank> fluidTanks = List.of();
        private MultiblockGui gui;
        private MultiblockSounds sounds = MultiblockSounds.EMPTY;
        private MultiblockTextures textures = MultiblockTextures.EMPTY;
        private MultiblockModel model = MultiblockModel.EMPTY;
        private MultiblockShape shape = MultiblockShape.EMPTY;
        private CapabilityPorts capabilityPorts = CapabilityPorts.ALL;
        private Map<String, JsonElement> resourceConfigs = Map.of();
        private Map<String, List<ResourceTankInfo>> resourceTanks = Map.of();

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

        public Builder upgrades(List<MultiblockUpgrade> upgrades) {
            this.upgrades = List.copyOf(Objects.requireNonNull(upgrades, "upgrades"));
            return this;
        }

        public Builder motions(List<MultiblockMotion> motions) {
            this.motions = List.copyOf(Objects.requireNonNull(motions, "motions"));
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

        public Builder sounds(MultiblockSounds sounds) {
            this.sounds = Objects.requireNonNull(sounds, "sounds");
            return this;
        }

        public Builder textures(MultiblockTextures textures) {
            this.textures = Objects.requireNonNull(textures, "textures");
            return this;
        }

        public Builder model(MultiblockModel model) {
            this.model = Objects.requireNonNull(model, "model");
            return this;
        }

        public Builder shape(MultiblockShape shape) {
            this.shape = Objects.requireNonNull(shape, "shape");
            return this;
        }

        public Builder capabilityPorts(CapabilityPorts capabilityPorts) {
            this.capabilityPorts = Objects.requireNonNull(capabilityPorts, "capabilityPorts");
            return this;
        }

        public Builder resourceConfigs(Map<String, JsonElement> resourceConfigs) {
            this.resourceConfigs = Map.copyOf(Objects.requireNonNull(resourceConfigs, "resourceConfigs"));
            return this;
        }

        public Builder resourceTanks(Map<String, List<ResourceTankInfo>> resourceTanks) {
            Map<String, List<ResourceTankInfo>> copy = new LinkedHashMap<>();
            Objects.requireNonNull(resourceTanks, "resourceTanks").forEach((kind, tanks) -> {
                if (kind == null || kind.isBlank()) {
                    throw new IllegalArgumentException("Resource kind cannot be blank");
                }
                copy.put(kind, List.copyOf(Objects.requireNonNull(tanks, kind)));
            });
            this.resourceTanks = Map.copyOf(copy);
            return this;
        }

        public MultiblockDefinition build() {
            if (this.pattern == null) {
                throw new IllegalStateException("A multiblock definition needs a pattern");
            }
            if (!this.upgrades.isEmpty() && !this.pattern.hasUpgradeKey()) {
                throw new IllegalStateException("Multiblock upgrades need an upgrade key in the pattern");
            }
            if (this.upgrades.isEmpty() && this.pattern.hasUpgradeKey()) {
                throw new IllegalStateException("Upgrade key '" + this.pattern.upgradeKey()
                        + "' is present in the pattern but no boosters were defined");
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
            for (Map.Entry<String, List<ResourceTankInfo>> entry : this.resourceTanks.entrySet()) {
                String kind = entry.getKey();
                if (!this.resourceConfigs.containsKey(kind)) {
                    throw new IllegalStateException("Missing resource config for kind: " + kind);
                }
                for (ResourceTankInfo tank : entry.getValue()) {
                    if (!tankIds.add(tank.id())) {
                        throw new IllegalStateException("Duplicate resource tank id: " + tank.id());
                    }
                }
            }
            for (String kind : this.resourceConfigs.keySet()) {
                if (!this.resourceTanks.containsKey(kind)) {
                    throw new IllegalStateException("Missing resource tanks for kind: " + kind);
                }
            }
            validateMotions();
            this.capabilityPorts.validate(this.pattern);
            return new MultiblockDefinition(
                    this.id,
                    this.schemaVersion,
                    this.type,
                    this.priority,
                    this.tier,
                    this.pattern,
                    this.upgrades,
                    this.motions,
                    this.inventoryRows,
                    this.itemSlots,
                    Optional.ofNullable(this.energy),
                    this.fluidTanks,
                    Optional.ofNullable(this.gui),
                    this.sounds,
                    this.textures,
                    this.model,
                    this.shape,
                    this.capabilityPorts,
                    this.resourceConfigs,
                    this.resourceTanks);
        }

        private void validateMotions() {
            Set<String> motionIds = new HashSet<>();
            Set<Character> usedKeys = new HashSet<>();
            for (MultiblockMotion motion : this.motions) {
                if (motion.id().isBlank()) {
                    throw new IllegalStateException("A multiblock motion needs a non-blank id");
                }
                if (!motionIds.add(motion.id())) {
                    throw new IllegalStateException("Duplicate multiblock motion id: " + motion.id());
                }
                if (motion.keys().isEmpty()) {
                    throw new IllegalStateException("Multiblock motion '" + motion.id() + "' needs at least one key");
                }
                if (motion.damage() < 0.0F) {
                    throw new IllegalStateException("Multiblock motion '" + motion.id() + "' has a negative damage");
                }
                if (Identifier.tryParse(motion.damageSource()) == null) {
                    throw new IllegalStateException(
                            "Multiblock motion '" + motion.id() + "' has an invalid damage_source");
                }
                for (char key : motion.keys()) {
                    if (key == this.pattern.anchor()) {
                        throw new IllegalStateException(
                                "Multiblock motion '" + motion.id() + "' cannot use the anchor key '" + key + "'");
                    }
                    if (this.pattern.hasUpgradeKey() && key == this.pattern.upgradeKey()) {
                        throw new IllegalStateException(
                                "Multiblock motion '" + motion.id() + "' cannot use the upgrade key '" + key + "'");
                    }
                    if (!this.pattern.hasStructuralSymbol(key)) {
                        throw new IllegalStateException(
                                "Multiblock motion '" + motion.id() + "' uses key '" + key
                                        + "' that is not part of the pattern");
                    }
                    if (!usedKeys.add(key)) {
                        throw new IllegalStateException(
                                "Key '" + key + "' is used by more than one multiblock motion");
                    }
                }
                MultiblockMotion.Animation animation = motion.animation();
                if (animation.duration() < 0) {
                    throw new IllegalStateException(
                            "Multiblock motion '" + motion.id() + "' has a negative animation duration");
                }
                int previousTime = -1;
                for (MultiblockMotion.Keyframe frame : animation.keyframes()) {
                    if (frame.time() < 0 || frame.time() > animation.duration()) {
                        throw new IllegalStateException(
                                "Multiblock motion '" + motion.id() + "' has a keyframe outside 0.."
                                        + animation.duration() + ": " + frame.time());
                    }
                    if (frame.time() < previousTime) {
                        throw new IllegalStateException(
                                "Multiblock motion '" + motion.id() + "' keyframes must be sorted by time");
                    }
                    previousTime = frame.time();
                }
            }
        }
    }
}
