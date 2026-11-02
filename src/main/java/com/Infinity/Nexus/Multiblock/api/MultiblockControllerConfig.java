package com.Infinity.Nexus.Multiblock.api;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public record MultiblockControllerConfig(
        Identifier block,
        Textures textures,
        @Nullable FormedTextures formedTextures,
        @Nullable Identifier particle,
        int lightLevel) {

    public static final int MAX_LIGHT_LEVEL = 15;

    public static final Codec<MultiblockControllerConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("block").forGetter(MultiblockControllerConfig::block),
            Textures.CODEC.fieldOf("textures").forGetter(MultiblockControllerConfig::textures),
            FormedTextures.CODEC.optionalFieldOf("formed_textures")
                    .forGetter(config -> Optional.ofNullable(config.formedTextures())),
            Identifier.CODEC.optionalFieldOf("particle").forGetter(config -> Optional.ofNullable(config.particle())),
            Codec.intRange(0, MAX_LIGHT_LEVEL).optionalFieldOf("light_level", 0)
                    .forGetter(MultiblockControllerConfig::lightLevel))
            .apply(instance, (block, textures, formedTextures, particle, lightLevel) ->
                    new MultiblockControllerConfig(
                            block,
                            textures,
                            formedTextures.orElse(null),
                            particle.orElse(null),
                            lightLevel)));

    public record Textures(
            Identifier front,
            Identifier back,
            Identifier left,
            Identifier right,
            Identifier top,
            Identifier bottom) {

        public static final Codec<Textures> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("front").forGetter(Textures::front),
                Identifier.CODEC.fieldOf("back").forGetter(Textures::back),
                Identifier.CODEC.fieldOf("left").forGetter(Textures::left),
                Identifier.CODEC.fieldOf("right").forGetter(Textures::right),
                Identifier.CODEC.fieldOf("top").forGetter(Textures::top),
                Identifier.CODEC.fieldOf("bottom").forGetter(Textures::bottom))
                .apply(instance, Textures::new));
    }

    public record FormedTextures(
            Optional<Identifier> front,
            Optional<Identifier> back,
            Optional<Identifier> left,
            Optional<Identifier> right,
            Optional<Identifier> top,
            Optional<Identifier> bottom) {

        public static final Codec<FormedTextures> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.optionalFieldOf("front").forGetter(FormedTextures::front),
                Identifier.CODEC.optionalFieldOf("back").forGetter(FormedTextures::back),
                Identifier.CODEC.optionalFieldOf("left").forGetter(FormedTextures::left),
                Identifier.CODEC.optionalFieldOf("right").forGetter(FormedTextures::right),
                Identifier.CODEC.optionalFieldOf("top").forGetter(FormedTextures::top),
                Identifier.CODEC.optionalFieldOf("bottom").forGetter(FormedTextures::bottom))
                .apply(instance, FormedTextures::new));

        public boolean isEmpty() {
            return this.front.isEmpty()
                    && this.back.isEmpty()
                    && this.left.isEmpty()
                    && this.right.isEmpty()
                    && this.top.isEmpty()
                    && this.bottom.isEmpty();
        }
    }
}
