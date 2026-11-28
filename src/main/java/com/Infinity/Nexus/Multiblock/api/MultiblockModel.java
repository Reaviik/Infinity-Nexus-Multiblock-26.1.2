package com.Infinity.Nexus.Multiblock.api;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

public record MultiblockModel(
        Optional<Identifier> idle,
        Optional<Identifier> active,
        Optional<Identifier> building) {
    public static final MultiblockModel EMPTY =
            new MultiblockModel(Optional.empty(), Optional.empty(), Optional.empty());

    public static final MapCodec<MultiblockModel> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("idle").forGetter(MultiblockModel::idle),
            Identifier.CODEC.optionalFieldOf("active").forGetter(MultiblockModel::active),
            Identifier.CODEC.optionalFieldOf("building").forGetter(MultiblockModel::building))
            .apply(instance, MultiblockModel::new));

    public boolean isEmpty() {
        return this.idle.isEmpty() && this.active.isEmpty() && this.building.isEmpty();
    }

    public Optional<Identifier> resolve(MultiblockTextures.State state) {
        return switch (state) {
            case IDLE -> this.idle;
            case ACTIVE -> this.active.or(() -> this.idle);
            case BUILDING -> this.building.or(() -> this.active).or(() -> this.idle);
        };
    }
}
