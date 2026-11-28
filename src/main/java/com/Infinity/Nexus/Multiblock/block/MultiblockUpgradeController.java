package com.Infinity.Nexus.Multiblock.block;

import java.util.List;

import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockUpgrade;
import com.Infinity.Nexus.Multiblock.api.MultiblockUpgradeMath;
import com.Infinity.Nexus.Multiblock.api.UpgradeBonus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class MultiblockUpgradeController {
    private final MultiblockControllerBlockEntity owner;
    private List<BlockPos> members = List.of();
    private int[] counts = new int[0];

    MultiblockUpgradeController(MultiblockControllerBlockEntity owner) {
        this.owner = owner;
    }

    List<BlockPos> members() {
        return this.members;
    }

    int[] counts() {
        return this.counts.clone();
    }

    void reset() {
        this.members = List.of();
        this.counts = new int[0];
    }

    void refresh(@Nullable MultiblockDefinition definition) {
        if (definition == null || definition.upgrades().isEmpty() || this.owner.getLevel() == null) {
            this.reset();
            return;
        }
        Rotation rotation = MultiblockControllerBlock.rotationFromState(this.owner.getBlockState());
        List<BlockPos> positions = definition.upgradeMembers(this.owner.getBlockPos(), rotation);
        List<MultiblockUpgrade> upgrades = definition.upgrades();
        int[] nextCounts = new int[upgrades.size()];
        for (BlockPos pos : positions) {
            BlockState state = this.owner.getLevel().getBlockState(pos);
            for (int index = 0; index < upgrades.size(); index++) {
                if (upgrades.get(index).matcher().matches(state, rotation)) {
                    nextCounts[index]++;
                }
            }
        }
        this.members = positions;
        this.counts = nextCounts;
    }

    UpgradeBonus bonus(@Nullable MultiblockDefinition definition, MultiblockUpgrade.Modifier modifier) {
        if (definition == null || definition.upgrades().isEmpty() || this.counts.length == 0) {
            return UpgradeBonus.IDENTITY;
        }
        return MultiblockUpgradeMath.combine(definition.upgrades(), modifier, this.counts);
    }

    int[] boosterDisplayData(@Nullable MultiblockDefinition definition) {
        UpgradeBonus output = this.bonus(definition, MultiblockUpgrade.Modifier.OUTPUT);
        UpgradeBonus speed = this.bonus(definition, MultiblockUpgrade.Modifier.SPEED);
        UpgradeBonus energy = this.bonus(definition, MultiblockUpgrade.Modifier.ENERGY);
        UpgradeBonus efficiency = this.bonus(definition, MultiblockUpgrade.Modifier.EFFICIENCY);
        UpgradeBonus damage = this.bonus(definition, MultiblockUpgrade.Modifier.DAMAGE);
        int threads = Math.max(1, MultiblockUpgradeMath.threads(
                this.bonus(definition, MultiblockUpgrade.Modifier.THREAD)));
        return new int[] {
                permille(output.multiplier()), permille(output.flat()),
                permille(speed.multiplier()), permille(speed.flat()),
                permille(energy.multiplier()), permille(energy.flat()),
                permille(efficiency.multiplier()), permille(efficiency.flat()),
                threads,
                permille(damage.multiplier()), permille(damage.flat())
        };
    }

    private static int permille(double value) {
        if (!Double.isFinite(value)) {
            return 0;
        }
        long scaled = Math.round(value * 1000.0D);
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, scaled));
    }
}
