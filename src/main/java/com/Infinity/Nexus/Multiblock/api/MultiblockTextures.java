package com.Infinity.Nexus.Multiblock.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

public record MultiblockTextures(TextureSet idle, Optional<TextureSet> active, Optional<TextureSet> building) {
    public enum State {
        IDLE,
        ACTIVE,
        BUILDING
    }

    public static final MultiblockTextures EMPTY =
            new MultiblockTextures(TextureSet.EMPTY, Optional.empty(), Optional.empty());

    public static final MapCodec<MultiblockTextures> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TextureSet.MAP_CODEC.forGetter(MultiblockTextures::idle),
            TextureSet.MAP_CODEC.codec().optionalFieldOf("active").forGetter(MultiblockTextures::active),
            TextureSet.MAP_CODEC.codec().optionalFieldOf("building").forGetter(MultiblockTextures::building))
            .apply(instance, MultiblockTextures::new));

    public boolean isEmpty() {
        return this.idle.isEmpty()
                && this.active.map(TextureSet::isEmpty).orElse(true)
                && this.building.map(TextureSet::isEmpty).orElse(true);
    }

    public Map<String, Identifier> resolve(boolean active) {
        return resolve(active ? State.ACTIVE : State.IDLE);
    }

    public Map<String, Identifier> resolve(State state) {
        if (this.isEmpty()) {
            return Map.of();
        }

        Map<String, Identifier> textures = new HashMap<>(this.idle.asMap());
        if (state != State.IDLE) {
            this.active.ifPresent(set -> textures.putAll(set.asMap()));
        }
        if (state == State.BUILDING) {
            this.building.ifPresent(set -> textures.putAll(set.asMap()));
        }
        if (!textures.containsKey("particle")) {
            if (textures.containsKey("front")) {
                textures.put("particle", textures.get("front"));
            } else if (textures.containsKey("all")) {
                textures.put("particle", textures.get("all"));
            }
        }
        return Map.copyOf(textures);
    }

    public record TextureSet(
            Optional<Identifier> front,
            Optional<Identifier> back,
            Optional<Identifier> left,
            Optional<Identifier> right,
            Optional<Identifier> top,
            Optional<Identifier> bottom,
            Optional<Identifier> side,
            Optional<Identifier> particle,
            Optional<Identifier> all) {
        public static final TextureSet EMPTY = new TextureSet(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty());

        public static final MapCodec<TextureSet> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.optionalFieldOf("front").forGetter(TextureSet::front),
                Identifier.CODEC.optionalFieldOf("back").forGetter(TextureSet::back),
                Identifier.CODEC.optionalFieldOf("left").forGetter(TextureSet::left),
                Identifier.CODEC.optionalFieldOf("right").forGetter(TextureSet::right),
                Identifier.CODEC.optionalFieldOf("top").forGetter(TextureSet::top),
                Identifier.CODEC.optionalFieldOf("bottom").forGetter(TextureSet::bottom),
                Identifier.CODEC.optionalFieldOf("side").forGetter(TextureSet::side),
                Identifier.CODEC.optionalFieldOf("particle").forGetter(TextureSet::particle),
                Identifier.CODEC.optionalFieldOf("all").forGetter(TextureSet::all))
                .apply(instance, TextureSet::new));

        public boolean isEmpty() {
            return this.front.isEmpty()
                    && this.back.isEmpty()
                    && this.left.isEmpty()
                    && this.right.isEmpty()
                    && this.top.isEmpty()
                    && this.bottom.isEmpty()
                    && this.side.isEmpty()
                    && this.particle.isEmpty()
                    && this.all.isEmpty();
        }

        public Map<String, Identifier> asMap() {
            Map<String, Identifier> textures = new HashMap<>(16);
            this.all.ifPresent(texture -> textures.put("all", texture));
            this.front.ifPresent(texture -> textures.put("front", texture));
            this.top.ifPresent(texture -> textures.put("top", texture));
            this.bottom.ifPresent(texture -> textures.put("bottom", texture));
            this.particle.ifPresent(texture -> textures.put("particle", texture));
            this.side.ifPresent(texture -> textures.put("side", texture));

            Identifier side = this.side.orElse(null);
            Identifier back = this.back.orElse(side);
            Identifier left = this.left.orElse(side);
            Identifier right = this.right.orElse(side);
            if (back != null) {
                textures.put("back", back);
                textures.put("south", back);
            }
            if (left != null) {
                textures.put("left", left);
                textures.put("west", left);
            }
            if (right != null) {
                textures.put("right", right);
                textures.put("east", right);
            }
            this.front.ifPresent(texture -> textures.put("north", texture));
            this.top.ifPresent(texture -> textures.put("up", texture));
            this.bottom.ifPresent(texture -> textures.put("down", texture));
            return textures;
        }
    }
}
