package com.Infinity.Nexus.Multiblock.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public enum MultiblockType {
    RECIPE("recipe"),
    STORAGE("storage");

    public static final Codec<MultiblockType> CODEC = Codec.STRING.flatXmap(
            MultiblockType::fromSerializedName,
            type -> DataResult.success(type.serializedName));

    private final String serializedName;

    MultiblockType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return this.serializedName;
    }

    private static DataResult<MultiblockType> fromSerializedName(String serializedName) {
        for (MultiblockType type : values()) {
            if (type.serializedName.equals(serializedName)) {
                return DataResult.success(type);
            }
        }
        return DataResult.error(() -> "Unknown multiblock type: " + serializedName);
    }
}
