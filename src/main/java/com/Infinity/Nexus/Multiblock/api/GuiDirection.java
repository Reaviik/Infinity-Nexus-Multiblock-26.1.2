package com.Infinity.Nexus.Multiblock.api;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum GuiDirection implements StringRepresentable {
    UP("up"),
    DOWN("down"),
    LEFT("left"),
    RIGHT("right");

    public static final Codec<GuiDirection> CODEC = StringRepresentable.fromEnum(GuiDirection::values);

    private final String name;

    GuiDirection(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
