package com.Infinity.Nexus.Multiblock.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public final class MultiblockPattern {
    public record PatternCell(int x, int y, int z, char symbol, BlockMatcher matcher, boolean anchor) {}
    public record UpgradeCell(int x, int y, int z, char symbol) {}

    private final List<PatternCell> cells;
    private final List<UpgradeCell> upgradeCells;
    private final BlockPos anchorOffset;
    private final char anchor;
    private final char upgradeKey;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    private MultiblockPattern(
            List<PatternCell> cells,
            List<UpgradeCell> upgradeCells,
            BlockPos anchorOffset,
            char anchor,
            char upgradeKey,
            int sizeX,
            int sizeY,
            int sizeZ) {
        this.cells = cells;
        this.upgradeCells = upgradeCells;
        this.anchorOffset = anchorOffset;
        this.anchor = anchor;
        this.upgradeKey = upgradeKey;
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

    public boolean hasUpgradeKey() {
        return this.upgradeKey != '\0';
    }

    public char upgradeKey() {
        return this.upgradeKey;
    }

    public List<UpgradeCell> upgradeCells() {
        return this.upgradeCells;
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

    public List<BlockPos> upgradeMembers(BlockPos controllerPos, Rotation rotation) {
        if (this.upgradeCells.isEmpty()) {
            return List.of();
        }
        BlockPos origin = this.origin(controllerPos, rotation);
        List<BlockPos> positions = new ArrayList<>(this.upgradeCells.size());
        for (UpgradeCell cell : this.upgradeCells) {
            BlockPos offset = new BlockPos(cell.x(), cell.y(), cell.z()).rotate(rotation);
            positions.add(origin.offset(offset.getX(), offset.getY(), offset.getZ()));
        }
        return List.copyOf(positions);
    }

    public boolean hasStructuralSymbol(char symbol) {
        for (PatternCell cell : this.cells) {
            if (!cell.anchor() && cell.symbol() == symbol) {
                return true;
            }
        }
        return false;
    }

    public boolean matches(LevelReader level, BlockPos origin, Rotation rotation) {
        return matches(level, origin, rotation, Set.of());
    }

    /**
     * Igual a {@link #matches(LevelReader, BlockPos, Rotation)}, mas pula as
     * celulas cujo simbolo esta em {@code ignoredSymbols}. Usado enquanto as
     * partes moveis estao fora do lugar (blocos viraram ar).
     */
    public boolean matches(LevelReader level, BlockPos origin, Rotation rotation, Set<Character> ignoredSymbols) {
        for (PatternCell cell : this.cells) {
            if (cell.anchor() || ignoredSymbols.contains(cell.symbol())) {
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
        private char upgradeKey = '\0';

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

        public Builder upgradeKey(char upgradeKey) {
            this.upgradeKey = upgradeKey;
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
            List<UpgradeCell> upgradeCells = new ArrayList<>();

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
                        if (this.upgradeKey != '\0' && symbol == this.upgradeKey) {
                            if (symbol == this.anchor) {
                                throw new IllegalStateException("Upgrade key cannot be the anchor");
                            }
                            if (this.matchers.containsKey(symbol)) {
                                throw new IllegalStateException("Upgrade key cannot have a matcher");
                            }
                            upgradeCells.add(new UpgradeCell(x, y, z, symbol));
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
            if (this.upgradeKey != '\0' && upgradeCells.isEmpty()) {
                throw new IllegalStateException("Upgrade key '" + this.upgradeKey + "' does not appear in the pattern");
            }

            return new MultiblockPattern(
                    Collections.unmodifiableList(cells),
                    Collections.unmodifiableList(upgradeCells),
                    anchorOffset,
                    this.anchor,
                    this.upgradeKey,
                    width,
                    this.layers.size(),
                    height);
        }
    }
}
