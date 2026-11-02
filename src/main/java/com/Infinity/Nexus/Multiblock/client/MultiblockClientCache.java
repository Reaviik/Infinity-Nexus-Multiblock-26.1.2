package com.Infinity.Nexus.Multiblock.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class MultiblockClientCache {
    private record DimensionIndex(Map<BlockPos, BlockPos> controllerByMember, Map<BlockPos, List<BlockPos>> membersByController) {
        private DimensionIndex() {
            this(new HashMap<>(), new HashMap<>());
        }
    }

    private static final Map<ResourceKey<Level>, DimensionIndex> DIMENSIONS = new HashMap<>();

    private MultiblockClientCache() {}

    private static DimensionIndex index(Level level) {
        return DIMENSIONS.computeIfAbsent(level.dimension(), ignored -> new DimensionIndex());
    }

    public static void track(Level level, BlockPos controller, List<BlockPos> members) {
        DimensionIndex index = index(level);
        removeController(level, controller);
        List<BlockPos> copy = List.copyOf(members);
        index.membersByController().put(controller, copy);
        for (BlockPos member : copy) {
            if (!member.equals(controller)) {
                index.controllerByMember().put(member, controller);
            }
        }
    }

    public static @Nullable BlockPos getController(Level level, BlockPos member) {
        DimensionIndex index = DIMENSIONS.get(level.dimension());
        return index == null ? null : index.controllerByMember().get(member);
    }

    public static void removeController(Level level, BlockPos controller) {
        DimensionIndex index = DIMENSIONS.get(level.dimension());
        if (index == null) {
            return;
        }
        List<BlockPos> members = index.membersByController().remove(controller);
        if (members == null) {
            return;
        }
        for (BlockPos member : members) {
            index.controllerByMember().remove(member, controller);
        }
    }

    public static void clear() {
        DIMENSIONS.clear();
    }
}
