package com.Infinity.Nexus.Multiblock.api;

/**
 * Resultado agregado dos boosters de um modifier.
 *
 * <p>Para modifiers de aumento ({@code output}, {@code thread}, {@code damage}) o multiplicador e' >= 1 e
 * representa o ganho. Para modifiers de reducao ({@code speed}, {@code energy},
 * {@code efficiency}) o multiplicador esta' entre 0 e 1 e representa a fracao restante.</p>
 */
public record UpgradeBonus(double multiplier, double flat) {
    public static final UpgradeBonus IDENTITY = new UpgradeBonus(1.0, 0.0);

    public UpgradeBonus {
        if (!Double.isFinite(multiplier)) {
            multiplier = 1.0;
        }
        if (!Double.isFinite(flat)) {
            flat = 0.0;
        }
        multiplier = Math.max(0.0, multiplier);
    }

    public boolean isIdentity() {
        return Math.abs(this.multiplier - 1.0) < 1.0E-9 && Math.abs(this.flat) < 1.0E-9;
    }
}
