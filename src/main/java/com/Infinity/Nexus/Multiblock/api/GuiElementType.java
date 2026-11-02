package com.Infinity.Nexus.Multiblock.api;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum GuiElementType implements StringRepresentable {
    TITLE("title"),
    TEXT("text"),
    PLAYER_INVENTORY("player_inventory"),
    SLOT("slot"),
    ENERGY_BAR("energy_bar"),
    FLUID_BAR("fluid_bar"),
    PROGRESS_BAR("progress_bar"),
    ARROW("arrow"),
    ENTITY("entity");

    public static final Codec<GuiElementType> CODEC = StringRepresentable.fromEnum(GuiElementType::values);

    private final String name;

    GuiElementType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
