package com.Infinity.Nexus.Multiblock.data;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
import com.Infinity.Nexus.Multiblock.api.MultiblockModel;
import com.Infinity.Nexus.Multiblock.api.MultiblockPattern;
import com.Infinity.Nexus.Multiblock.api.MultiblockShape;
import com.Infinity.Nexus.Multiblock.api.MultiblockSounds;
import com.Infinity.Nexus.Multiblock.api.MultiblockTextures;
import com.Infinity.Nexus.Multiblock.api.MultiblockType;
import com.Infinity.Nexus.Multiblock.api.SlotPosition;
import com.Infinity.Nexus.Multiblock.api.addon.MultiblockAddons;
import com.Infinity.Nexus.Multiblock.api.addon.MultiblockResourceType;
import com.Infinity.Nexus.Multiblock.api.addon.ResourceTankInfo;
import com.Infinity.Nexus.Multiblock.config.Config;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public final class MultiblockDefinitionLoader extends SimpleJsonResourceReloadListener<JsonObject> {
    private static volatile Map<Identifier, JsonObject> resolvedFiles = Map.of();
    private static volatile @Nullable MultiblockDefinitionLoader instance;

    private static final Codec<JsonObject> JSON_CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(JsonOps.INSTANCE).getValue().getAsJsonObject(),
            json -> new Dynamic<>(JsonOps.INSTANCE, json));

    private static final Codec<Character> SYMBOL_CODEC = Codec.STRING.flatXmap(
            value -> value.length() == 1
                    ? DataResult.success(value.charAt(0))
                    : DataResult.error(() -> "Expected a single character, got '" + value + "'"),
            value -> DataResult.success(String.valueOf(value)));

    private static final Codec<CapabilityPorts.PortSelector> PORT_SELECTOR_CODEC = Codec.STRING.flatXmap(
            value -> {
                Optional<CapabilityPorts.PortSelector> parsed = CapabilityPorts.PortSelector.parse(value);
                return parsed.<DataResult<CapabilityPorts.PortSelector>>map(DataResult::success)
                        .orElseGet(() -> DataResult.error(
                                () -> "Invalid port selector '" + value
                                        + "': expected a pattern key, a block id or a block tag"));
            },
            selector -> DataResult.error(() -> "Fluid tank ports are load-only"));

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
            SoundsFile.CODEC.optionalFieldOf("sounds").forGetter(DefinitionFile::sounds),
            MultiblockTextures.CODEC.codec().optionalFieldOf("textures").forGetter(DefinitionFile::textures),
            MultiblockModel.CODEC.codec().optionalFieldOf("model").forGetter(DefinitionFile::model),
            MultiblockShape.CODEC.optionalFieldOf("shape").forGetter(DefinitionFile::shape),
            PortsFile.CODEC.optionalFieldOf("capability_ports").forGetter(DefinitionFile::capabilityPorts),
            PatternFile.CODEC.fieldOf("pattern").forGetter(DefinitionFile::pattern))
            .apply(instance, DefinitionFile::new));

    public MultiblockDefinitionLoader() {
        super(JSON_CODEC, FileToIdConverter.json("multiblock"));
        instance = this;
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

    private static MultiblockDefinition createDefinition(Identifier id, DefinitionFile file, JsonObject raw) {
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
        Map<String, JsonElement> resourceConfigs = resourceConfigs(raw);
        Map<String, List<ResourceTankInfo>> resourceTanks = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : resourceConfigs.entrySet()) {
            String kind = entry.getKey();
            MultiblockResourceType type = MultiblockAddons.get(kind);
            if (type == null) {
                throw new IllegalArgumentException("Unknown multiblock resource kind '" + kind
                        + "': is the addon installed?");
            }
            resourceTanks.put(kind, type.describeTanks(entry.getValue()));
        }
        Map<String, List<CapabilityPorts.PortSelector>> resourcePorts = resourcePorts(raw);
        CapabilityPorts basePorts = file.capabilityPorts().map(PortsFile::toPorts).orElse(CapabilityPorts.ALL);
        CapabilityPorts capabilityPorts = new CapabilityPorts(
                basePorts.item(),
                basePorts.energy(),
                basePorts.fluid(),
                resourcePorts);
        MultiblockDefinition.Builder definition = MultiblockDefinition.builder(id)
                .schemaVersion(file.schemaVersion())
                .type(file.type())
                .priority(file.priority())
                .tier(file.tier())
                .inventoryRows(file.inventoryRows())
                .itemSlots(itemSlots)
                .fluidTanks(fluidTanks)
                .resourceConfigs(resourceConfigs)
                .resourceTanks(resourceTanks)
                .sounds(file.sounds().map(SoundsFile::toSounds).orElse(MultiblockSounds.EMPTY))
                .textures(file.textures().orElse(MultiblockTextures.EMPTY))
                .model(file.model().orElse(MultiblockModel.EMPTY))
                .shape(file.shape().orElse(MultiblockShape.EMPTY))
                .capabilityPorts(capabilityPorts)
                .pattern(pattern.build());
        file.capabilities().energy().ifPresent(energy -> definition.energy(energy.toCapability()));
        file.gui().ifPresent(definition::gui);
        MultiblockDefinition built = definition.build();
        validateFluidPorts(built);
        MultiblockMenuLayout.create(built);
        return built;
    }

    private static Map<String, JsonElement> resourceConfigs(JsonObject raw) {
        JsonObject capabilities = raw.getAsJsonObject("capabilities");
        if (capabilities == null) {
            return Map.of();
        }
        Map<String, JsonElement> resources = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : capabilities.entrySet()) {
            if (entry.getKey().equals("energy") || entry.getKey().equals("fluids")) {
                continue;
            }
            resources.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(resources);
    }

    private static Map<String, List<CapabilityPorts.PortSelector>> resourcePorts(JsonObject raw) {
        JsonObject ports = raw.getAsJsonObject("capability_ports");
        if (ports == null) {
            return Map.of();
        }
        Map<String, List<CapabilityPorts.PortSelector>> resources = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : ports.entrySet()) {
            if (entry.getKey().equals("item") || entry.getKey().equals("energy")
                    || entry.getKey().equals("fluid")) {
                continue;
            }
            JsonElement value = entry.getValue();
            if (!value.isJsonArray()) {
                throw new IllegalArgumentException("capability_ports." + entry.getKey()
                        + " must be an array of selectors");
            }
            List<CapabilityPorts.PortSelector> selectors = new ArrayList<>();
            for (JsonElement element : value.getAsJsonArray()) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("capability_ports." + entry.getKey()
                            + " selectors must be strings");
                }
                selectors.add(PortsFile.toSelector(element.getAsString()));
            }
            resources.put(entry.getKey(), List.copyOf(selectors));
        }
        return Map.copyOf(resources);
    }

    private static void validateFluidPorts(MultiblockDefinition definition) {
        Set<Character> symbols = new HashSet<>();
        for (MultiblockPattern.PatternCell cell : definition.pattern().cells()) {
            symbols.add(cell.symbol());
        }
        Optional<List<CapabilityPorts.PortSelector>> fluidPorts =
                definition.capabilityPorts().selectors(CapabilityPorts.CapabilityType.FLUID);
        for (FluidTank tank : definition.fluidTanks()) {
            CapabilityPorts.PortSelector selector = tank.port();
            if (selector == null || selector.symbol().isEmpty()) {
                continue;
            }
            char symbol = selector.symbol().get();
            if (!symbols.contains(symbol)) {
                throw new IllegalArgumentException("Fluid tank '" + tank.id() + "' port key '" + symbol
                        + "' is not used by the pattern");
            }
            if (fluidPorts.isPresent()) {
                boolean possible = fluidPorts.get().stream()
                        .anyMatch(port -> port.symbol().map(value -> value == symbol).orElse(true));
                if (!possible) {
                    throw new IllegalArgumentException("Fluid tank '" + tank.id() + "' port key '" + symbol
                            + "' is not selected by capability_ports.fluid");
                }
            }
        }
    }

    @Override
    protected void apply(Map<Identifier, JsonObject> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        applyDefinitions(prepared);
    }

    public static int reloadFrom(ResourceManager resourceManager) {
        Map<Identifier, JsonObject> prepared = new LinkedHashMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(
                resourceManager, FileToIdConverter.json("multiblock"), conditionalOps(), JSON_CODEC, prepared);
        return applyDefinitions(prepared);
    }

    public static DynamicOps<JsonElement> conditionalOps() {
        MultiblockDefinitionLoader loader = instance;
        return loader == null ? JsonOps.INSTANCE : loader.makeConditionalOps();
    }

    private static int applyDefinitions(Map<Identifier, JsonObject> prepared) {
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
                if (isExampleDisabled(file)) {
                    InfinityNexusMultiblock.LOGGER.debug("Skipped example multiblock definition {}", id);
                    return;
                }
                JsonObject resolvedFile = MultiblockDefinitionFiles.resolve(id, prepared, resolved, new HashSet<>());
                if (!resolvedFile.has("pattern") && referencedTemplates.contains(id)) {
                    InfinityNexusMultiblock.LOGGER.debug("Skipped multiblock template {}", id);
                    return;
                }
                if (isExampleDisabled(resolvedFile)) {
                    InfinityNexusMultiblock.LOGGER.debug("Skipped example multiblock definition {}", id);
                    return;
                }
                DefinitionFile definitionFile = decode(id, resolvedFile);
                definitions.put(id, createDefinition(id, definitionFile, resolvedFile));
                synced.put(id, resolvedFile.deepCopy());
            } catch (RuntimeException exception) {
                InfinityNexusMultiblock.LOGGER.error("Failed to load multiblock definition {}: {}", id, exception.getMessage());
            }
        });

        MultiblockDefinitions.replaceAll(definitions);
        resolvedFiles = Map.copyOf(synced);
        InfinityNexusMultiblock.LOGGER.info("Loaded {} multiblock definitions", definitions.size());
        return definitions.size();
    }

    public static Map<Identifier, JsonObject> resolvedFiles() {
        return resolvedFiles;
    }

    public static void applyResolved(Map<Identifier, JsonObject> files) {
        Map<Identifier, MultiblockDefinition> definitions = new LinkedHashMap<>();
        Map<Identifier, JsonObject> enabled = new LinkedHashMap<>();
        files.forEach((id, json) -> {
            try {
                if (!json.has("pattern")) {
                    return;
                }
                if (isExampleDisabled(json)) {
                    InfinityNexusMultiblock.LOGGER.debug("Skipped example multiblock definition {} from server", id);
                    return;
                }
                definitions.put(id, createDefinition(id, decode(id, json), json));
                enabled.put(id, json);
            } catch (RuntimeException exception) {
                InfinityNexusMultiblock.LOGGER.error("Failed to load synced multiblock definition {}: {}", id, exception.getMessage());
            }
        });
        MultiblockDefinitions.replaceAll(definitions);
        resolvedFiles = Map.copyOf(enabled);
        InfinityNexusMultiblock.LOGGER.info("Loaded {} multiblock definitions from server", definitions.size());
    }

    private static boolean isExampleDisabled(JsonObject json) {
        JsonElement type = json.get("type");
        if (type == null || !type.isJsonPrimitive() || !type.getAsJsonPrimitive().isString()) {
            return false;
        }
        return MultiblockType.isExampleSerializedName(type.getAsString()) && !Config.examplesEnabled();
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
            Optional<SoundsFile> sounds,
            Optional<MultiblockTextures> textures,
            Optional<MultiblockModel> model,
            Optional<MultiblockShape> shape,
            Optional<PortsFile> capabilityPorts,
            PatternFile pattern) {
    }

    public record SoundsFile(Optional<Identifier> processing, Optional<Identifier> finish) {
        private static final Codec<SoundsFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.optionalFieldOf("processing").forGetter(SoundsFile::processing),
                Identifier.CODEC.optionalFieldOf("finish").forGetter(SoundsFile::finish))
                .apply(instance, SoundsFile::new));

        public MultiblockSounds toSounds() {
            return new MultiblockSounds(this.processing, this.finish);
        }
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
            Optional<FluidFilter> filter,
            Optional<CapabilityPorts.PortSelector> port) {
        private static final Codec<FluidFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(FluidFile::id),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("capacity").forGetter(FluidFile::capacity),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("max_receive", 0).forGetter(FluidFile::maxReceive),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("max_extract", 0).forGetter(FluidFile::maxExtract),
                FluidFilter.CODEC.optionalFieldOf("filter").forGetter(FluidFile::filter),
                PORT_SELECTOR_CODEC.optionalFieldOf("port").forGetter(FluidFile::port))
                .apply(instance, FluidFile::new));

        public FluidTank toTank() {
            return new FluidTank(
                    this.id,
                    this.capacity,
                    this.maxReceive,
                    this.maxExtract,
                    this.filter.orElse(null),
                    this.port.orElse(null));
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
                String rawTag = this.tag.get();
                String tagId = rawTag.startsWith("#") ? rawTag.substring(1) : rawTag;
                return Identifier.read(tagId)
                        .map(id -> {
                            TagKey<Block> tag = TagKey.create(Registries.BLOCK, id);
                            return this.state.isEmpty()
                                    ? BlockMatcher.tag(tag)
                                    : tagStateMatcher(tag, this.state);
                        });
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

        private static BlockMatcher tagStateMatcher(TagKey<Block> tag, Map<String, String> values) {
            return new TagStateMatcher(tag, Map.copyOf(values));
        }

        private static final class TagStateMatcher implements BlockMatcher {
            private final TagKey<Block> tag;
            private final Map<String, String> values;
            private final Map<Block, BlockState[]> rotated = new ConcurrentHashMap<>();
            private volatile @Nullable Display cached;

            private TagStateMatcher(TagKey<Block> tag, Map<String, String> values) {
                this.tag = tag;
                this.values = values;
            }

            @Override
            public boolean matches(BlockState state) {
                return matches(state, Rotation.NONE);
            }

            @Override
            public boolean matches(BlockState state, Rotation rotation) {
                if (!state.is(this.tag)) {
                    return false;
                }
                BlockState expected = this.expected(state.getBlock(), rotation);
                if (expected == null) {
                    return false;
                }
                for (String name : this.values.keySet()) {
                    Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
                    if (property == null || !state.getValue(property).equals(expected.getValue(property))) {
                        return false;
                    }
                }
                return true;
            }

            private @Nullable BlockState expected(Block block, Rotation rotation) {
                BlockState[] states = this.rotated.computeIfAbsent(block, this::rotateStates);
                return states.length == 0 ? null : states[rotation.ordinal()];
            }

            private BlockState[] rotateStates(Block block) {
                BlockState expected = block.defaultBlockState();
                for (Map.Entry<String, String> entry : this.values.entrySet()) {
                    Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
                    if (property == null) {
                        return new BlockState[0];
                    }
                    Optional<? extends Comparable<?>> parsed = property.getValue(entry.getValue());
                    if (parsed.isEmpty()) {
                        return new BlockState[0];
                    }
                    expected = setProperty(expected, property, parsed.get());
                }
                BlockState[] states = new BlockState[Rotation.values().length];
                for (Rotation rotation : Rotation.values()) {
                    states[rotation.ordinal()] = expected.rotate(rotation);
                }
                return states;
            }

            @Override
            public Optional<Display> display() {
                Display current = this.cached;
                if (current == null) {
                    List<BlockState> states = new ArrayList<>();
                    List<ItemStack> stacks = new ArrayList<>();
                    for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(this.tag)) {
                        Block block = holder.value();
                        BlockState[] candidates = this.rotated.computeIfAbsent(block, this::rotateStates);
                        if (candidates.length == 0) {
                            continue;
                        }
                        states.add(candidates[Rotation.NONE.ordinal()]);
                        Item item = block.asItem();
                        if (item != Items.AIR && stacks.stream().noneMatch(stack -> stack.is(item))) {
                            stacks.add(new ItemStack(item));
                        }
                    }
                    current = new Display(
                            List.copyOf(states),
                            List.copyOf(stacks),
                            Component.literal("#" + this.tag.location()));
                    if (!states.isEmpty() || !stacks.isEmpty()) {
                        this.cached = current;
                    }
                }
                return Optional.of(current);
            }
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
