package com.Infinity.Nexus.Multiblock.api;

import java.util.EnumSet;
import java.util.Set;

import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

public enum ItemSlotRole implements StringRepresentable {
    INPUT("input"),
    OUTPUT("output"),
    FUEL("fuel"),
    UPGRADE("upgrade"),
    COMPONENT("component"),
    STORAGE("storage"),
    FLUID("fluid");

    public static final Codec<ItemSlotRole> CODEC = StringRepresentable.fromEnum(ItemSlotRole::values);

    private final String name;

    ItemSlotRole(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public Set<Direction> defaultInsertFaces() {
        return switch (this) {
            case OUTPUT, FLUID -> Set.of();
            default -> Set.copyOf(EnumSet.allOf(Direction.class));
        };
    }

    public Set<Direction> defaultExtractFaces() {
        return switch (this) {
            case OUTPUT, STORAGE -> Set.copyOf(EnumSet.allOf(Direction.class));
            default -> Set.of();
        };
    }
}
