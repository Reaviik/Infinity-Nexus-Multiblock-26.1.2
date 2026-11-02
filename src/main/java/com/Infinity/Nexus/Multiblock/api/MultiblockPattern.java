package com.Infinity.Nexus.Multiblock.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public final class MultiblockPattern {
    public record PatternCell(int x, int y, int z, char symbol, BlockMatcher matcher, boolean anchor) {}

    private final List<PatternCell> cells;
    private final BlockPos anchorOffset;
    private final char anchor;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    private MultiblockPattern(List<PatternCell> cells, BlockPos anchorOffset, char anchor, int sizeX, int sizeY, int sizeZ) {
        this.cells = cells;
        this.anchorOffset = anchorOffset;
        this.anchor = anchor;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<PatternCell> cells() {
        return this.cells;
    }

    public BlockPos anchorOffset() {
        return this.anchorOffset;
    }

    public char anchor() {
        return this.anchor;
    }

    public int sizeX() {
        return this.sizeX;
    }

    public int sizeY() {
        return this.sizeY;
    }

    public int sizeZ() {
        return this.sizeZ;
    }

    public BlockPos origin(BlockPos controllerPos, Rotation rotation) {
        return controllerPos.subtract(this.anchorOffset.rotate(rotation));
    }

    public BlockPos anchorPos(BlockPos controllerPos, Rotation rotation) {
        BlockPos rotatedAnchor = this.anchorOffset.rotate(rotation);
        return this.origin(controllerPos, rotation).offset(rotatedAnchor.getX(), rotatedAnchor.getY(), rotatedAnchor.getZ());
    }

    public List<BlockPos> members(BlockPos controllerPos, Rotation rotation) {
        BlockPos origin = this.origin(controllerPos, rotation);
        List<BlockPos> members = new ArrayList<>(this.cells.size() - 1);
        for (PatternCell cell : this.cells) {
            if (!cell.anchor()) {
                BlockPos offset = new BlockPos(cell.x(), cell.y(), cell.z()).rotate(rotation);
                members.add(origin.offset(offset.getX(), offset.getY(), offset.getZ()));
            }
        }
        return List.copyOf(members);
    }

    public boolean matches(LevelReader level, BlockPos origin, Rotation rotation) {
        for (PatternCell cell : this.cells) {
            if (cell.anchor()) {
                continue;
            }
            BlockPos offset = new BlockPos(cell.x(), cell.y(), cell.z()).rotate(rotation);
            BlockPos pos = origin.offset(offset.getX(), offset.getY(), offset.getZ());
            BlockState state = level.getBlockState(pos);
            if (!cell.matcher().matches(state, rotation)) {
                return false;
            }
        }
        return true;
    }

    public static final class Builder {
        private final List<List<String>> layers = new ArrayList<>();
        private final Map<Character, BlockMatcher> matchers = new LinkedHashMap<>();
        private char anchor = 'C';

        public Builder layer(String... rows) {
            this.layers.add(List.of(rows));
            return this;
        }

        public Builder matcher(char symbol, BlockMatcher matcher) {
            this.matchers.put(symbol, matcher);
            return this;
        }

        public Builder matcher(char symbol, net.minecraft.world.level.block.Block block) {
            return this.matcher(symbol, BlockMatcher.of(block));
        }

        public Builder anchor(char anchor) {
            this.anchor = anchor;
            return this;
        }

        public MultiblockPattern build() {
            if (this.layers.isEmpty()) {
                throw new IllegalStateException("A multiblock pattern needs at least one layer");
            }
            if (this.anchor == ' ') {
                throw new IllegalStateException("The anchor symbol cannot be a space");
            }

            int height = this.layers.get(0).size();
            if (height == 0) {
                throw new IllegalStateException("A multiblock pattern layer needs at least one row");
            }
            int width = this.layers.get(0).get(0).length();
            if (width == 0) {
                throw new IllegalStateException("A multiblock pattern row needs at least one column");
            }

            int anchorCount = 0;
            BlockPos anchorOffset = null;
            List<PatternCell> cells = new ArrayList<>();

            for (int y = 0; y < this.layers.size(); y++) {
                List<String> layer = this.layers.get(y);
                if (layer.size() != height) {
                    throw new IllegalStateException("All pattern layers must have the same number of rows");
                }
                for (int z = 0; z < height; z++) {
                    String row = layer.get(z);
                    if (row.length() != width) {
                        throw new IllegalStateException("All pattern rows must have the same width");
                    }
                    for (int x = 0; x < width; x++) {
                        char symbol = row.charAt(x);
                        if (symbol == ' ') {
                            continue;
                        }
                        boolean isAnchor = symbol == this.anchor;
                        BlockMatcher matcher = this.matchers.get(symbol);
                        if (isAnchor) {
                            anchorCount++;
                            anchorOffset = new BlockPos(x, y, z);
                        } else if (matcher == null) {
                            throw new IllegalStateException("No matcher registered for pattern symbol '" + symbol + "'");
                        }
                        cells.add(new PatternCell(x, y, z, symbol, matcher, isAnchor));
                    }
                }
            }

            if (anchorCount != 1) {
                throw new IllegalStateException("A multiblock pattern needs exactly one anchor, found " + anchorCount);
            }

            return new MultiblockPattern(
                    Collections.unmodifiableList(cells),
                    anchorOffset,
                    this.anchor,
                    width,
                    this.layers.size(),
                    height);
        }
    }
}
