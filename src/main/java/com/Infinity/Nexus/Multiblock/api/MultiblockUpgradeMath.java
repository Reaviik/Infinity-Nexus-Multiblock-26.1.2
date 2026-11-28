package com.Infinity.Nexus.Multiblock.api;

import java.util.List;

import net.minecraft.util.RandomSource;

/**
 * Matematica dos boosters de multibloco.
 *
 * <p>Modifiers de aumento ({@code output}, {@code thread}, {@code damage}):
 * {@code mult = (1 + soma_additive) * produto_multiplicative}, {@code flat = soma_flat}.
 * Modifiers de reducao ({@code speed}, {@code energy}, {@code efficiency}):
 * {@code mult = clamp((1 - soma_additive) * produto(1 - factor), 0, 1)}, {@code flat = soma_flat}.</p>
 */
public final class MultiblockUpgradeMath {
    public static final int MAX_THREADS = 64;
    private static final double EPSILON = 1.0E-9;

    private MultiblockUpgradeMath() {
    }

    public static UpgradeBonus combine(
            List<MultiblockUpgrade> upgrades,
            MultiblockUpgrade.Modifier modifier,
            int[] counts) {
        if (upgrades.isEmpty() || counts.length == 0) {
            return UpgradeBonus.IDENTITY;
        }
        double additive = 0.0;
        double multiplicative = 1.0;
        double flat = 0.0;
        int limit = Math.min(upgrades.size(), counts.length);
        for (int index = 0; index < limit; index++) {
            MultiblockUpgrade upgrade = upgrades.get(index);
            if (upgrade.modifier() != modifier) {
                continue;
            }
            int count = upgrade.counted(counts[index]);
            if (count <= 0) {
                continue;
            }
            double factor = upgrade.factor();
            switch (upgrade.mode()) {
                case ADDITIVE -> additive += count * factor;
                case MULTIPLICATIVE -> multiplicative *= Math.pow(
                        modifier.increases() ? 1.0 + factor : Math.max(0.0, 1.0 - factor),
                        count);
                case FLAT -> flat += count * factor;
            }
        }
        double multiplier = modifier.increases()
                ? (1.0 + additive) * multiplicative
                : clamp((1.0 - additive) * multiplicative, 0.0, 1.0);
        return new UpgradeBonus(multiplier, flat);
    }

    public static ScaledAmount scaleOutput(
            int base,
            UpgradeBonus bonus,
            boolean rollExtra,
            RandomSource random) {
        if (base <= 0) {
            return new ScaledAmount(0, false);
        }
        double exact = base * bonus.multiplier() + bonus.flat();
        long capped = Math.min(Integer.MAX_VALUE, (long) Math.floor(exact + EPSILON));
        int whole = (int) Math.max(1L, capped);
        double fraction = Math.max(0.0, exact - whole);
        boolean extra = rollExtra && fraction > EPSILON && random.nextDouble() < fraction;
        return new ScaledAmount(whole, extra);
    }

    public static int effectiveTicks(int time, UpgradeBonus bonus) {
        if (time <= 0) {
            return 0;
        }
        double exact = time * bonus.multiplier() - bonus.flat();
        return Math.max(1, (int) Math.ceil(exact - EPSILON));
    }

    public static long effectiveEnergy(long energy, UpgradeBonus bonus) {
        if (energy <= 0L) {
            return 0L;
        }
        double exact = energy * bonus.multiplier() - bonus.flat();
        long rounded = (long) Math.ceil(exact - EPSILON);
        return Math.max(1L, rounded);
    }

    public static float scaledDamage(float base, UpgradeBonus bonus) {
        if (base <= 0.0F) {
            return 0.0F;
        }
        double exact = base * bonus.multiplier() + bonus.flat();
        if (!Double.isFinite(exact)) {
            return base;
        }
        return (float) Math.max(0.0D, exact);
    }

    public static int threads(UpgradeBonus bonus) {
        double extra = (bonus.multiplier() - 1.0) + bonus.flat();
        int threads = 1 + Math.max(0, (int) Math.floor(extra + EPSILON));
        return Math.min(MAX_THREADS, threads);
    }

    public static int scaledInput(int base, UpgradeBonus efficiency) {
        if (base <= 0) {
            return 0;
        }
        double exact = base * efficiency.multiplier() - efficiency.flat();
        return Math.max(1, (int) Math.ceil(exact - EPSILON));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record ScaledAmount(int whole, boolean extra) {
    }
}
