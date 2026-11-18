package com.Infinity.Nexus.Multiblock.api;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public record CapabilityPorts(
        Optional<List<PortSelector>> item,
        Optional<List<PortSelector>> energy,
        Optional<List<PortSelector>> fluid,
        Map<String, List<PortSelector>> resources) {

    public static final CapabilityPorts ALL = new CapabilityPorts(
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    public CapabilityPorts(
            Optional<List<PortSelector>> item,
            Optional<List<PortSelector>> energy,
            Optional<List<PortSelector>> fluid) {
        this(item, energy, fluid, Map.of());
    }

    public CapabilityPorts {
        item = copy(item, "item");
        energy = copy(energy, "energy");
        fluid = copy(fluid, "fluid");
        resources = copyResources(resources);
    }

    public boolean isDefined(CapabilityType type) {
        return this.selectors(type).isPresent();
    }

    public boolean allows(CapabilityType type, char symbol, BlockState state, Rotation rotation) {
        Optional<List<PortSelector>> selectors = this.selectors(type);
        if (selectors.isEmpty()) {
            return true;
        }
        for (PortSelector selector : selectors.get()) {
            if (selector.matches(symbol, state, rotation)) {
                return true;
            }
        }
        return false;
    }

    public Optional<List<PortSelector>> selectors(CapabilityType type) {
        return switch (Objects.requireNonNull(type, "type")) {
            case ITEM -> this.item;
            case ENERGY -> this.energy;
            case FLUID -> this.fluid;
        };
    }

    public boolean isDefined(String kind) {
        return this.selectors(kind).isPresent();
    }

    public boolean allows(String kind, char symbol, BlockState state, Rotation rotation) {
        Optional<List<PortSelector>> selectors = this.selectors(kind);
        if (selectors.isEmpty()) {
            return true;
        }
        for (PortSelector selector : selectors.get()) {
            if (selector.matches(symbol, state, rotation)) {
                return true;
            }
        }
        return false;
    }

    public Optional<List<PortSelector>> selectors(String kind) {
        return kind == null ? Optional.empty() : Optional.ofNullable(this.resources.get(kind));
    }

    public Set<String> resourceKinds() {
        return this.resources.keySet();
    }

    public void validate(MultiblockPattern pattern) {
        Set<Character> symbols = new HashSet<>();
        for (MultiblockPattern.PatternCell cell : pattern.cells()) {
            symbols.add(cell.symbol());
        }
        for (CapabilityType type : CapabilityType.values()) {
            validateSelectors(this.selectors(type), symbols);
        }
        for (Map.Entry<String, List<PortSelector>> entry : this.resources.entrySet()) {
            validateSelectors(Optional.of(entry.getValue()), symbols);
        }
    }

    private static void validateSelectors(Optional<List<PortSelector>> selectors, Set<Character> symbols) {
        selectors.ifPresent(list -> {
            for (PortSelector selector : list) {
                selector.symbol().ifPresent(symbol -> {
                    if (!symbols.contains(symbol)) {
                        throw new IllegalStateException("Capability port symbol '" + symbol
                                + "' is not used by the pattern");
                    }
                });
            }
        });
    }

    private static Map<String, List<PortSelector>> copyResources(Map<String, List<PortSelector>> resources) {
        Objects.requireNonNull(resources, "resources");
        if (resources.isEmpty()) {
            return Map.of();
        }
        Map<String, List<PortSelector>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<PortSelector>> entry : resources.entrySet()) {
            String kind = Objects.requireNonNull(entry.getKey(), "resource kind");
            if (kind.isBlank()) {
                throw new IllegalArgumentException("Resource port kind cannot be blank");
            }
            copy.put(kind, List.copyOf(Objects.requireNonNull(entry.getValue(), kind)));
        }
        return Map.copyOf(copy);
    }

    private static Optional<List<PortSelector>> copy(Optional<List<PortSelector>> selectors, String name) {
        return Objects.requireNonNull(selectors, name).map(list -> List.copyOf(Objects.requireNonNull(list, name)));
    }

    public enum CapabilityType {
        ITEM,
        ENERGY,
        FLUID
    }

    public record PortSelector(Optional<Character> symbol, Optional<BlockMatcher> matcher) {
        public PortSelector {
            if (symbol.isPresent() == matcher.isPresent()) {
                throw new IllegalArgumentException(
                        "A capability port selector needs exactly one of symbol or block matcher");
            }
            symbol = symbol.map(value -> {
                if (value == ' ') {
                    throw new IllegalArgumentException("A capability port symbol cannot be a space");
                }
                return value;
            });
            matcher = matcher.map(value -> Objects.requireNonNull(value, "matcher"));
        }

        public static PortSelector symbol(char symbol) {
            return new PortSelector(Optional.of(symbol), Optional.empty());
        }

        public static PortSelector matcher(BlockMatcher matcher) {
            return new PortSelector(Optional.empty(), Optional.of(matcher));
        }

        public static Optional<PortSelector> parse(String value) {
            String text = value == null ? "" : value.trim();
            if (text.isEmpty()) {
                return Optional.empty();
            }
            if (text.length() == 1) {
                char symbol = text.charAt(0);
                return symbol == ' ' ? Optional.empty() : Optional.of(symbol(symbol));
            }
            if (text.startsWith("#")) {
                Identifier id = Identifier.tryParse(text.substring(1));
                return id == null
                        ? Optional.empty()
                        : Optional.of(matcher(BlockMatcher.tag(TagKey.create(Registries.BLOCK, id))));
            }
            Identifier id = Identifier.tryParse(text);
            return id == null
                    ? Optional.empty()
                    : BuiltInRegistries.BLOCK.getOptional(id)
                            .map(block -> matcher(BlockMatcher.of(block)));
        }

        public boolean matches(char cellSymbol, BlockState state, Rotation rotation) {
            if (this.symbol.isPresent()) {
                return this.symbol.get() == cellSymbol;
            }
            return this.matcher.map(value -> value.matches(state, rotation)).orElse(false);
        }
    }
}
