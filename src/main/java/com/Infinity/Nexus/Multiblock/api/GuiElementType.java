package com.Infinity.Nexus.Multiblock.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.StringRepresentable;

public enum GuiElementType implements StringRepresentable {
    TITLE("title"),
    TEXT("text"),
    PLAYER_INVENTORY("player_inventory"),
    SLOT("slot"),
    ENERGY_BAR("energy_bar"),
    FLUID_BAR("fluid_bar"),
    RESOURCE_BAR("resource_bar"),
    RESOURCE_LIST("resource_list"),
    PROGRESS_BAR("progress_bar"),
    ARROW("arrow"),
    ENTITY("entity"),
    BOOSTER_BUTTON("booster_button");

    public static final Codec<GuiElementType> CODEC = Codec.STRING.comapFlatMap(
            GuiElementType::fromSerializedName,
            GuiElementType::getSerializedName);

    private final String name;

    GuiElementType(String name) {
        this.name = name;
    }

    private static DataResult<GuiElementType> fromSerializedName(String name) {
        for (GuiElementType type : values()) {
            if (type.name.equals(name)) {
                return DataResult.success(type);
            }
        }
        if ("chemical_bar".equals(name)) {
            return DataResult.success(RESOURCE_BAR);
        }
        return DataResult.error(() -> "Unknown GUI element type: " + name);
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
