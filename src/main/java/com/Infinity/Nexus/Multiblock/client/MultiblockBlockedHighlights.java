package com.Infinity.Nexus.Multiblock.client;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public final class MultiblockBlockedHighlights {
    private static final List<Highlight> HIGHLIGHTS = new ArrayList<>();

    private MultiblockBlockedHighlights() {}

    public static void add(Identifier dimension, BlockPos controller, List<BlockPos> positions, long durationMillis) {
        HIGHLIGHTS.removeIf(highlight -> highlight.dimension().equals(dimension)
                && highlight.controller().equals(controller));
        if (positions.isEmpty()) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + Math.max(0L, durationMillis);
        HIGHLIGHTS.add(new Highlight(dimension, controller.immutable(), List.copyOf(positions), expiresAt));
    }

    public static List<BlockPos> active(ClientLevel level) {
        Identifier dimension = level.dimension().identifier();
        long now = System.currentTimeMillis();
        List<BlockPos> result = new ArrayList<>();
        ListIterator<Highlight> iterator = HIGHLIGHTS.listIterator();
        while (iterator.hasNext()) {
            Highlight highlight = iterator.next();
            if (now >= highlight.expiresAt()) {
                iterator.remove();
                continue;
            }
            if (!highlight.dimension().equals(dimension)) {
                continue;
            }
            List<BlockPos> remaining = new ArrayList<>(highlight.positions().size());
            for (BlockPos pos : highlight.positions()) {
                if (!level.hasChunkAt(pos) || !level.getBlockState(pos).canBeReplaced()) {
                    remaining.add(pos);
                }
            }
            if (remaining.isEmpty()) {
                iterator.remove();
                continue;
            }
            if (remaining.size() != highlight.positions().size()) {
                iterator.set(new Highlight(
                        highlight.dimension(), highlight.controller(), List.copyOf(remaining), highlight.expiresAt()));
            }
            result.addAll(remaining);
        }
        return result;
    }

    public static void clear() {
        HIGHLIGHTS.clear();
    }

    private record Highlight(Identifier dimension, BlockPos controller, List<BlockPos> positions, long expiresAt) {}
}
