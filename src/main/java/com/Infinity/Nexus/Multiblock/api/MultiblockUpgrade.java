package com.Infinity.Nexus.Multiblock.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Um booster de multibloco: um bloco (ou tag/estado) dentro de uma celula de upgrade que
 * altera uma das caracteristicas da receita.
 */
public record MultiblockUpgrade(
        BlockMatcher matcher,
        Modifier modifier,
        Mode mode,
        double factor,
        int maxBlocks) {

    public MultiblockUpgrade {
        Objects.requireNonNull(matcher, "matcher");
        Objects.requireNonNull(modifier, "modifier");
        Objects.requireNonNull(mode, "mode");
        if (!Double.isFinite(factor) || factor <= 0.0) {
            throw new IllegalArgumentException("factor must be finite and greater than 0");
        }
        if (maxBlocks < 1) {
            throw new IllegalArgumentException("max_blocks must be at least 1");
        }
    }

    public int counted(int blocks) {
        return Math.min(Math.max(0, blocks), this.maxBlocks);
    }

    public enum Modifier {
        OUTPUT("output", true),
        SPEED("speed", false),
        ENERGY("energy", false),
        EFFICIENCY("efficiency", false),
        THREAD("thread", true),
        DAMAGE("damage", true);

        private final String id;
        private final boolean increases;

        Modifier(String id, boolean increases) {
            this.id = id;
            this.increases = increases;
        }

        public String id() {
            return this.id;
        }

        public boolean increases() {
            return this.increases;
        }

        public static Optional<Modifier> byId(String id) {
            for (Modifier modifier : values()) {
                if (modifier.id.equals(id)) {
                    return Optional.of(modifier);
                }
            }
            return Optional.empty();
        }
    }

    public enum Mode {
        ADDITIVE("additive"),
        MULTIPLICATIVE("multiplicative"),
        FLAT("flat");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        public static Optional<Mode> byId(String id) {
            for (Mode mode : values()) {
                if (mode.id.equals(id)) {
                    return Optional.of(mode);
                }
            }
            return Optional.empty();
        }
    }
}
