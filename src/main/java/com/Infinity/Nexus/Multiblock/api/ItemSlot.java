package com.Infinity.Nexus.Multiblock.api;

import java.util.Objects;
import java.util.Set;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public record ItemSlot(
        String id,
        ItemSlotRole role,
        @Nullable ItemFilter filter,
        int limit,
        Set<Direction> insertFaces,
        Set<Direction> extractFaces,
        @Nullable SlotPosition position) {

    public ItemSlot {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Item slot id cannot be blank");
        }
        Objects.requireNonNull(role, "role");
        if (limit < 1 || limit > 64) {
            throw new IllegalArgumentException("Item slot limit must be between 1 and 64");
        }
        insertFaces = Set.copyOf(Objects.requireNonNull(insertFaces, "insertFaces"));
        extractFaces = Set.copyOf(Objects.requireNonNull(extractFaces, "extractFaces"));
    }

    public ItemSlot(
            String id,
            ItemSlotRole role,
            @Nullable ItemFilter filter,
            int limit,
            Set<Direction> insertFaces,
            Set<Direction> extractFaces) {
        this(id, role, filter, limit, insertFaces, extractFaces, null);
    }

    public boolean accepts(ItemStack stack) {
        return this.filter == null || this.filter.matches(stack);
    }

    public boolean canInsertFrom(@Nullable Direction direction) {
        return direction != null && this.insertFaces.contains(direction);
    }

    public boolean canExtractTo(@Nullable Direction direction) {
        return direction != null && this.extractFaces.contains(direction);
    }
}
