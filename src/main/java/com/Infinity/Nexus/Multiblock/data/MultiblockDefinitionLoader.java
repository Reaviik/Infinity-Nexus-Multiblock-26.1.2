package com.Infinity.Nexus.Multiblock.data;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.BlockMatcher;
import com.Infinity.Nexus.Multiblock.api.CapabilityPorts;
import com.Infinity.Nexus.Multiblock.api.EnergyCapability;
import com.Infinity.Nexus.Multiblock.api.FluidFilter;
import com.Infinity.Nexus.Multiblock.api.FluidTank;
import com.Infinity.Nexus.Multiblock.api.ItemFilter;
import com.Infinity.Nexus.Multiblock.api.ItemSlot;
import com.Infinity.Nexus.Multiblock.api.ItemSlotRole;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockGui;
import com.Infinity.Nexus.Multiblock.api.MultiblockMenuLayout;
import com.Infinity.Nexus.Multiblock.api.MultiblockPattern;
import com.Infinity.Nexus.Multiblock.api.MultiblockType;
import com.Infinity.Nexus.Multiblock.api.SlotPosition;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class MultiblockDefinitionLoader extends SimpleJsonResourceReloadListener<JsonObject> {
    private static volatile Map<Identifier, JsonObject> resolvedFiles = Map.of();

    private static final Codec<JsonObject> JSON_CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(JsonOps.INSTANCE).getValue().getAsJsonObject(),
            json -> new Dynamic<>(JsonOps.INSTANCE, json));

    private static final Codec<Character> SYMBOL_CODEC = Codec.STRING.flatXmap(
            value -> value.length() == 1
                    ? DataResult.success(value.charAt(0))
                    : DataResult.error(() -> "Expected a single character, got '" + value + "'"),
            value -> DataResult.success(String.valueOf(value)));

    private static final Codec<BlockMatcher> MATCHER_CODEC = Codec.either(Codec.STRING, MatcherFile.CODEC).flatXmap(
            either -> either.map(MultiblockDefinitionLoader::parseMatcher, MatcherFile::toMatcher),
            matcher -> DataResult.error(() -> "Multiblock matchers are load-only"));

    private static final Codec<DefinitionFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(DefinitionFile::schemaVersion),
            Identifier.CODEC.fieldOf("id").forGetter(DefinitionFile::id),
            MultiblockType.CODEC.fieldOf("type").forGetter(DefinitionFile::type),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(DefinitionFile::priority),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("tier", 1).forGetter(DefinitionFile::tier),
            Codec.intRange(1, 6).optionalFieldOf("inventory_rows", 3).forGetter(DefinitionFile::inventoryRows),
            ItemSlotFile.CODEC.listOf().optionalFieldOf("item_slots", List.of()).forGetter(DefinitionFile::itemSlots),
            CapabilitiesFile.CODEC.optionalFieldOf("capabilities", CapabilitiesFile.EMPTY).forGetter(DefinitionFile::capabilities),
            MultiblockGui.CODEC.optionalFieldOf("gui").forGetter(DefinitionFile::gui),
            PortsFile.CODEC.optionalFieldOf("capability_ports").forGetter(DefinitionFile::capabilityPorts),
            PatternFile.CODEC.fieldOf("pattern").forGetter(DefinitionFile::pattern))
            .apply(instance, DefinitionFile::new));

    public MultiblockDefinitionLoader() {
        super(JSON_CODEC, FileToIdConverter.json("multiblock"));
    }

    private static DataResult<BlockMatcher> parseMatcher(String value) {
        if (value.startsWith("#")) {
            return Identifier.read(value.substring(1))
                    .map(id -> BlockMatcher.tag(TagKey.create(Registries.BLOCK, id)));
        }

        return Identifier.read(value).flatMap(id -> BuiltInRegistries.BLOCK.getOptional(id)
                .<DataResult<BlockMatcher>>map(block -> DataResult.success(BlockMatcher.of(block)))
                .orElseGet(() -> DataResult.error(() -> "Unknown block: " + id)));
    }

    private static MultiblockDefinition createDefinition(Identifier id, DefinitionFile file) {
        if (!id.equals(file.id())) {
            throw new IllegalArgumentException("Definition id " + file.id() + " does not match file id " + id);
        }
        if (file.schemaVersion() != MultiblockDefinition.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema_version: " + file.schemaVersion());
        }

        MultiblockPattern.Builder pattern = MultiblockPattern.builder().anchor(file.pattern().anchor());
        file.pattern().matchers().forEach((symbol, matcher) -> pattern.matcher(symbol, matcher));
        for (List<String> layer : file.pattern().layers()) {
            pattern.layer(layer.toArray(String[]::new));
        }

        List<ItemSlot> itemSlots = file.itemSlots().stream().map(ItemSlotFile::toSlot).toList();
        List<FluidTank> fluidTanks = file.capabilities().fluids().stream().map(FluidFile::toTank).toList();
        MultiblockDefinition.Builder definition = MultiblockDefinition.builder(id)
                .schemaVersion(file.schemaVersion())
                .type(file.type())
                .priority(file.priority())
                .tier(file.tier())
                .inventoryRows(file.inventoryRows())
                .itemSlots(itemSlots)
                .fluidTanks(fluidTanks)
                .capabilityPorts(file.capabilityPorts().map(PortsFile::toPorts).orElse(CapabilityPorts.ALL))
                .pattern(pattern.build());
        file.capabilities().energy().ifPresent(energy -> definition.energy(energy.toCapability()));
        file.gui().ifPresent(definition::gui);
        MultiblockDefinition built = definition.build();
        MultiblockMenuLayout.create(built);
        return built;
    }

    @Override
    protected void apply(Map<Identifier, JsonObject> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        Set<Identifier> referencedTemplates = new HashSet<>();
        for (JsonObject file : prepared.values()) {
            try {
                MultiblockDefinitionFiles.parentId(file).ifPresent(referencedTemplates::add);
            } catch (RuntimeException ignored) {
            }
        }

        Map<Identifier, JsonObject> resolved = new LinkedHashMap<>();
        Map<Identifier, JsonObject> synced = new LinkedHashMap<>();
        Map<Identifier, MultiblockDefinition> definitions = new LinkedHashMap<>();
        prepared.forEach((id, file) -> {
            try {
                if (MultiblockDefinitionFiles.isTemplate(file)) {
                    InfinityNexusMultiblock.LOGGER.debug("Skipped multiblock template {}", id);
                    return;
                }
                JsonObject resolvedFile = MultiblockDefinitionFiles.resolve(id, prepared, resolved, new HashSet<>());
                if (!resolvedFile.has("pattern") && referencedTemplates.contains(id)) {
                    InfinityNexusMultiblock.LOGGER.debug("Skipped multiblock template {}", id);
                    return;
                }
                DefinitionFile definitionFile = decode(id, resolvedFile);
                definitions.put(id, createDefinition(id, definitionFile));
                synced.put(id, resolvedFile.deepCopy());
            } catch (RuntimeException exception) {
                InfinityNexusMultiblock.LOGGER.error("Failed to load multiblock definition {}: {}", id, exception.getMessage());
            }
        });

        MultiblockDefinitions.replaceAll(definitions);
        resolvedFiles = Map.copyOf(synced);
        InfinityNexusMultiblock.LOGGER.info("Loaded {} multiblock definitions", definitions.size());
    }

    public static Map<Identifier, JsonObject> resolvedFiles() {
        return resolvedFiles;
    }

    public static void applyResolved(Map<Identifier, JsonObject> files) {
        Map<Identifier, MultiblockDefinition> definitions = new LinkedHashMap<>();
        files.forEach((id, json) -> {
            try {
                if (!json.has("pattern")) {
                    return;
                }
                definitions.put(id, createDefinition(id, decode(id, json)));
            } catch (RuntimeException exception) {
                InfinityNexusMultiblock.LOGGER.error("Failed to load synced multiblock definition {}: {}", id, exception.getMessage());
            }
        });
        MultiblockDefinitions.replaceAll(definitions);
        resolvedFiles = Map.copyOf(files);
        InfinityNexusMultiblock.LOGGER.info("Loaded {} multiblock definitions from server", definitions.size());
    }

    private static DefinitionFile decode(Identifier id, JsonObject json) {
        return CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(message -> new IllegalArgumentException("Invalid multiblock definition " + id + ": " + message));
    }

    public record DefinitionFile(
            int schemaVersion,
            Identifier id,
            MultiblockType type,
            int priority,
            int tier,
            int inventoryRows,
            List<ItemSlotFile> itemSlots,
            CapabilitiesFile capabilities,
            Optional<MultiblockGui> gui,
            Optional<PortsFile> capabilityPorts,
            PatternFile pattern) {
    }

    public record PortsFile(
            Optional<List<String>> item,
            Optional<List<String>> energy,
            Optional<List<String>> fluid) {
        private static final Codec<PortsFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().optionalFieldOf("item").forGetter(PortsFile::item),
                Codec.STRING.listOf().optionalFieldOf("energy").forGetter(PortsFile::energy),
                Codec.STRING.listOf().optionalFieldOf("fluid").forGetter(PortsFile::fluid))
                .apply(instance, PortsFile::new));

        public CapabilityPorts toPorts() {
            return new CapabilityPorts(
                    toSelectors(this.item),
                    toSelectors(this.energy),
                    toSelectors(this.fluid));
        }

        private static Optional<List<CapabilityPorts.PortSelector>> toSelectors(Optional<List<String>> values) {
            return values.map(list -> list.stream().map(PortsFile::toSelector).toList());
        }

        private static CapabilityPorts.PortSelector toSelector(String value) {
            if (value.length() == 1) {
                return CapabilityPorts.PortSelector.symbol(value.charAt(0));
            }
            BlockMatcher matcher = parseMatcher(value).getOrThrow(message ->
                    new IllegalArgumentException("Invalid capability port '" + value + "': " + message));
            return CapabilityPorts.PortSelector.matcher(matcher);
        }
    }

    public record CapabilitiesFile(Optional<EnergyFile> energy, List<FluidFile> fluids) {
        private static final CapabilitiesFile EMPTY = new CapabilitiesFile(Optional.empty(), List.of());

        private static final Codec<CapabilitiesFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EnergyFile.CODEC.optionalFieldOf("energy").forGetter(CapabilitiesFile::energy),
                FluidFile.CODEC.listOf().optionalFieldOf("fluids", List.of()).forGetter(CapabilitiesFile::fluids))
                .apply(instance, CapabilitiesFile::new));
    }

    public record EnergyFile(String id, int capacity, int maxReceive, int maxExtract) {
        private static final Codec<EnergyFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(EnergyFile::id),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("capacity").forGetter(EnergyFile::capacity),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("max_receive", 0).forGetter(EnergyFile::maxReceive),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("max_extract", 0).forGetter(EnergyFile::maxExtract))
                .apply(instance, EnergyFile::new));

        public EnergyCapability toCapability() {
            return new EnergyCapability(this.id, this.capacity, this.maxReceive, this.maxExtract);
        }
    }

    public record FluidFile(
            String id,
            int capacity,
            int maxReceive,
            int maxExtract,
            Optional<FluidFilter> filter) {
        private static final Codec<FluidFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(FluidFile::id),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("capacity").forGetter(FluidFile::capacity),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("max_receive", 0).forGetter(FluidFile::maxReceive),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("max_extract", 0).forGetter(FluidFile::maxExtract),
                FluidFilter.CODEC.optionalFieldOf("filter").forGetter(FluidFile::filter))
                .apply(instance, FluidFile::new));

        public FluidTank toTank() {
            return new FluidTank(
                    this.id,
                    this.capacity,
                    this.maxReceive,
                    this.maxExtract,
                    this.filter.orElse(null));
        }
    }

    public record ItemSlotFile(
            String id,
            ItemSlotRole role,
            Optional<ItemFilter> filter,
            int limit,
            Optional<FacesFile> faces,
            Optional<Integer> x,
            Optional<Integer> y) {
        private static final Codec<ItemSlotFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(ItemSlotFile::id),
                ItemSlotRole.CODEC.fieldOf("role").forGetter(ItemSlotFile::role),
                ItemFilter.CODEC.optionalFieldOf("filter").forGetter(ItemSlotFile::filter),
                Codec.intRange(1, 64).optionalFieldOf("limit", 64).forGetter(ItemSlotFile::limit),
                FacesFile.CODEC.optionalFieldOf("faces").forGetter(ItemSlotFile::faces),
                Codec.INT.optionalFieldOf("x").forGetter(ItemSlotFile::x),
                Codec.INT.optionalFieldOf("y").forGetter(ItemSlotFile::y))
                .apply(instance, ItemSlotFile::new));

        public ItemSlot toSlot() {
            Set<Direction> insertFaces = this.faces.flatMap(FacesFile::insert)
                    .map(ItemSlotFile::toFaceSet)
                    .orElseGet(this.role::defaultInsertFaces);
            Set<Direction> extractFaces = this.faces.flatMap(FacesFile::extract)
                    .map(ItemSlotFile::toFaceSet)
                    .orElseGet(this.role::defaultExtractFaces);
            return new ItemSlot(this.id, this.role, this.filter.orElse(null), this.limit,
                    insertFaces, extractFaces, this.position());
        }

        private SlotPosition position() {
            if (this.x.isEmpty() && this.y.isEmpty()) {
                return null;
            }
            if (this.x.isEmpty() || this.y.isEmpty()) {
                throw new IllegalArgumentException("Item slot " + this.id + " must define both x and y");
            }
            return new SlotPosition(this.x.get(), this.y.get());
        }

        private static Set<Direction> toFaceSet(List<Direction> directions) {
            return directions.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(directions));
        }
    }

    public record FacesFile(Optional<List<Direction>> insert, Optional<List<Direction>> extract) {
        private static final Codec<FacesFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Direction.CODEC.listOf().optionalFieldOf("insert").forGetter(FacesFile::insert),
                Direction.CODEC.listOf().optionalFieldOf("extract").forGetter(FacesFile::extract))
                .apply(instance, FacesFile::new));
    }

    public record MatcherFile(Optional<String> block, Optional<String> tag, Map<String, String> state) {
        private static final Codec<MatcherFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("block").forGetter(MatcherFile::block),
                Codec.STRING.optionalFieldOf("tag").forGetter(MatcherFile::tag),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("state", Map.of()).forGetter(MatcherFile::state))
                .apply(instance, MatcherFile::new));

        private DataResult<BlockMatcher> toMatcher() {
            if (this.block.isPresent() == this.tag.isPresent()) {
                return DataResult.error(() -> "Matcher objects need exactly one of 'block' or 'tag'");
            }
            if (this.tag.isPresent()) {
                if (!this.state.isEmpty()) {
                    return DataResult.error(() -> "Block state properties are only supported for block matchers");
                }
                String rawTag = this.tag.get();
                String tagId = rawTag.startsWith("#") ? rawTag.substring(1) : rawTag;
                return Identifier.read(tagId)
                        .map(id -> BlockMatcher.tag(TagKey.create(Registries.BLOCK, id)));
            }

            return Identifier.read(this.block.get()).flatMap(id -> BuiltInRegistries.BLOCK.getOptional(id)
                    .<DataResult<BlockMatcher>>map(block -> this.state.isEmpty()
                            ? DataResult.success(BlockMatcher.of(block))
                            : stateMatcher(block, this.state))
                    .orElseGet(() -> DataResult.error(() -> "Unknown block: " + id)));
        }

        @SuppressWarnings("deprecation")
        private static DataResult<BlockMatcher> stateMatcher(Block block, Map<String, String> values) {
            Map<Property<?>, Comparable<?>> properties = new LinkedHashMap<>();
            BlockState expected = block.defaultBlockState();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
                if (property == null) {
                    return DataResult.error(() -> "Unknown block state property '" + entry.getKey() + "' for " + BuiltInRegistries.BLOCK.getKey(block));
                }
                Optional<? extends Comparable<?>> parsed = property.getValue(entry.getValue());
                if (parsed.isEmpty()) {
                    return DataResult.error(() -> "Invalid value '" + entry.getValue() + "' for block state property '" + entry.getKey() + "'");
                }
                Comparable<?> value = parsed.get();
                properties.put(property, value);
                expected = setProperty(expected, property, value);
            }
            BlockState base = expected;
            BlockState[] rotatedStates = new BlockState[Rotation.values().length];
            for (Rotation rotation : Rotation.values()) {
                rotatedStates[rotation.ordinal()] = base.rotate(rotation);
            }
            return DataResult.success(new BlockMatcher() {
                @Override
                public boolean matches(BlockState state) {
                    return matches(state, Rotation.NONE);
                }

                @Override
                public boolean matches(BlockState state, Rotation rotation) {
                    BlockState rotated = rotatedStates[rotation.ordinal()];
                    if (!state.is(rotated.getBlock())) {
                        return false;
                    }
                    for (Property<?> property : properties.keySet()) {
                        if (!state.getValue(property).equals(rotated.getValue(property))) {
                            return false;
                        }
                    }
                    return true;
                }

                @Override
                public Optional<BlockMatcher.Display> display() {
                    return Optional.of(new BlockMatcher.Display(
                            List.of(base),
                            base.getBlock().asItem() == Items.AIR
                                    ? List.of()
                                    : List.of(new ItemStack(base.getBlock())),
                            null));
                }
            });
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private static BlockState setProperty(BlockState state, Property property, Comparable value) {
            return state.setValue(property, value);
        }
    }

    public record PatternFile(List<List<String>> layers, char anchor, Map<Character, BlockMatcher> matchers) {
        private static final Codec<PatternFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().listOf().fieldOf("layers").forGetter(PatternFile::layers),
                SYMBOL_CODEC.optionalFieldOf("anchor", 'C').forGetter(PatternFile::anchor),
                Codec.unboundedMap(SYMBOL_CODEC, MATCHER_CODEC).fieldOf("matchers").forGetter(PatternFile::matchers))
                .apply(instance, PatternFile::new));
    }
}
