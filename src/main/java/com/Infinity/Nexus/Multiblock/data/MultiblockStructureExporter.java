package com.Infinity.Nexus.Multiblock.data;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockType;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlock;
import com.Infinity.Nexus.Multiblock.config.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class MultiblockStructureExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private MultiblockStructureExporter() {
    }

    public static Result export(ServerLevel level, BlockPos controllerPos) {
        int limit = Config.PATTERN_SCAN_LIMIT.get();
        Scan scan = scan(level, controllerPos, limit);
        Rotation rotation = MultiblockControllerBlock.inverseRotation(
                MultiblockControllerBlock.rotationFromState(level.getBlockState(controllerPos)));
        Scan normalized = normalize(scan, controllerPos, rotation);

        JsonObject json = new JsonObject();
        json.addProperty("schema_version", MultiblockDefinition.CURRENT_SCHEMA_VERSION);
        json.addProperty("id", exportId(controllerPos));
        json.addProperty("type", MultiblockType.STORAGE.serializedName());
        json.add("pattern", pattern(BlockPos.ZERO, normalized, limit));

        return new Result(GSON.toJson(json), scan.blocks().size(), scan.truncated(), limit);
    }

    @SuppressWarnings("deprecation")
    private static Scan normalize(Scan scan, BlockPos controllerPos, Rotation rotation) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : scan.blocks().entrySet()) {
            BlockPos offset = entry.getKey().subtract(controllerPos).rotate(rotation);
            blocks.put(offset, entry.getValue().rotate(rotation));
        }
        return new Scan(blocks, scan.truncated());
    }

    private static Scan scan(ServerLevel level, BlockPos controllerPos, int limit) {
        Deque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        open.addLast(controllerPos);
        seen.add(controllerPos);

        boolean truncated = false;
        while (!open.isEmpty()) {
            if (blocks.size() >= limit) {
                truncated = true;
                break;
            }

            BlockPos pos = open.removeFirst();
            blocks.put(pos, level.getBlockState(pos));
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (!seen.add(neighbor) || !level.isLoaded(neighbor)) {
                    continue;
                }
                if (level.getBlockState(neighbor).isAir()) {
                    continue;
                }
                if (blocks.size() + open.size() >= limit) {
                    truncated = true;
                    continue;
                }
                open.addLast(neighbor);
            }
        }
        return new Scan(blocks, truncated);
    }

    private static JsonObject pattern(BlockPos anchorPos, Scan scan, int limit) {
        char[] availableSymbols = createSymbols(limit);
        Map<BlockState, Character> symbols = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : scan.blocks().entrySet()) {
            if (entry.getKey().equals(anchorPos) || symbols.containsKey(entry.getValue())) {
                continue;
            }
            if (symbols.size() >= availableSymbols.length) {
                throw new IllegalStateException("Too many unique block states to export");
            }
            symbols.put(entry.getValue(), availableSymbols[symbols.size()]);
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : scan.blocks().keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        JsonArray layers = new JsonArray();
        for (int y = minY; y <= maxY; y++) {
            JsonArray rows = new JsonArray();
            for (int z = minZ; z <= maxZ; z++) {
                StringBuilder row = new StringBuilder(maxX - minX + 1);
                for (int x = minX; x <= maxX; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (pos.equals(anchorPos)) {
                        row.append('C');
                        continue;
                    }
                    BlockState state = scan.blocks().get(pos);
                    Character symbol = state == null ? null : symbols.get(state);
                    row.append(symbol == null ? ' ' : symbol);
                }
                rows.add(row.toString());
            }
            layers.add(rows);
        }

        JsonObject matchers = new JsonObject();
        for (Map.Entry<BlockState, Character> entry : symbols.entrySet()) {
            matchers.add(String.valueOf(entry.getValue()), matcherJson(entry.getKey()));
        }

        JsonObject pattern = new JsonObject();
        pattern.add("layers", layers);
        pattern.addProperty("anchor", "C");
        pattern.add("matchers", matchers);
        return pattern;
    }

    public static JsonObject matcherJson(BlockState state) {
        JsonObject matcher = new JsonObject();
        matcher.addProperty("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        JsonObject stateProperties = stateJson(state);
        if (!stateProperties.isEmpty()) {
            matcher.add("state", stateProperties);
        }
        return matcher;
    }

    public static JsonObject stateJson(BlockState state) {
        JsonObject properties = new JsonObject();
        for (Property<?> property : state.getProperties()) {
            properties.addProperty(property.getName(), propertyValue(state, property));
        }
        return properties;
    }

    private static String exportId(BlockPos pos) {
        return Identifier.fromNamespaceAndPath(
                InfinityNexusMultiblock.MOD_ID,
                "exported_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ()).toString();
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static char[] createSymbols(int limit) {
        StringBuilder symbols = new StringBuilder();
        for (char symbol = 'A'; symbol <= 'Z'; symbol++) {
            if (symbol != 'C') {
                symbols.append(symbol);
            }
        }
        for (char symbol = 'a'; symbol <= 'z'; symbol++) {
            symbols.append(symbol);
        }
        for (char symbol = '0'; symbol <= '9'; symbol++) {
            symbols.append(symbol);
        }
        for (int codePoint = 0x00A1; codePoint <= 0x2FFF && symbols.length() < limit; codePoint++) {
            char symbol = (char) codePoint;
            if (symbol == 'C' || Character.isWhitespace(symbol) || Character.isISOControl(symbol) || Character.isSurrogate(symbol)) {
                continue;
            }
            symbols.append(symbol);
        }
        return symbols.toString().toCharArray();
    }

    private record Scan(Map<BlockPos, BlockState> blocks, boolean truncated) {
    }

    public record Result(String json, int scanned, boolean truncated, int limit) {
    }
}
