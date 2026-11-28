package com.Infinity.Nexus.Multiblock.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

public sealed interface EntityTypeSelector permits EntityTypeSelector.Direct, EntityTypeSelector.Tag {
    Codec<TagKey<EntityType<?>>> TAG_CODEC = Codec.STRING.comapFlatMap(
            EntityTypeSelector::parseTag,
            tag -> "#" + tag.location());

    StreamCodec<RegistryFriendlyByteBuf, EntityTypeSelector> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EntityTypeSelector decode(RegistryFriendlyByteBuf buffer) {
            if (buffer.readBoolean()) {
                return new Tag(TagKey.create(Registries.ENTITY_TYPE, Identifier.STREAM_CODEC.decode(buffer)));
            }
            return new Direct(EntityType.STREAM_CODEC.decode(buffer));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, EntityTypeSelector selector) {
            switch (selector) {
                case Direct direct -> {
                    buffer.writeBoolean(false);
                    EntityType.STREAM_CODEC.encode(buffer, direct.entityType());
                }
                case Tag tag -> {
                    buffer.writeBoolean(true);
                    Identifier.STREAM_CODEC.encode(buffer, tag.key().location());
                }
            }
        }
    };

    Optional<EntityType<?>> direct();

    Optional<TagKey<EntityType<?>>> tag();

    boolean matches(EntityType<?> entityType);

    List<EntityType<?>> resolve(HolderGetter<EntityType<?>> registry);

    Optional<EntityType<?>> pick(HolderGetter<EntityType<?>> registry, RandomSource random);

    Component displayName();

    private static DataResult<TagKey<EntityType<?>>> parseTag(String value) {
        String id = value.startsWith("#") ? value.substring(1) : value;
        return Identifier.read(id).map(location -> TagKey.create(Registries.ENTITY_TYPE, location));
    }

    record Direct(EntityType<?> entityType) implements EntityTypeSelector {
        public Direct {
            Objects.requireNonNull(entityType, "entityType");
        }

        @Override
        public Optional<EntityType<?>> direct() {
            return Optional.of(this.entityType);
        }

        @Override
        public Optional<TagKey<EntityType<?>>> tag() {
            return Optional.empty();
        }

        @Override
        public boolean matches(EntityType<?> entityType) {
            return entityType == this.entityType;
        }

        @Override
        public List<EntityType<?>> resolve(HolderGetter<EntityType<?>> registry) {
            return List.of(this.entityType);
        }

        @Override
        public Optional<EntityType<?>> pick(HolderGetter<EntityType<?>> registry, RandomSource random) {
            return Optional.of(this.entityType);
        }

        @Override
        public Component displayName() {
            return this.entityType.getDescription();
        }
    }

    record Tag(TagKey<EntityType<?>> key) implements EntityTypeSelector {
        public Tag {
            Objects.requireNonNull(key, "key");
        }

        @Override
        public Optional<EntityType<?>> direct() {
            return Optional.empty();
        }

        @Override
        public Optional<TagKey<EntityType<?>>> tag() {
            return Optional.of(this.key);
        }

        @Override
        public boolean matches(EntityType<?> entityType) {
            return entityType.getTags().anyMatch(this.key::equals);
        }

        @Override
        public List<EntityType<?>> resolve(HolderGetter<EntityType<?>> registry) {
            var set = registry.get(this.key);
            if (set.isEmpty()) {
                return List.of();
            }
            List<EntityType<?>> types = new ArrayList<>();
            for (var holder : set.get()) {
                types.add(holder.value());
            }
            return List.copyOf(types);
        }

        @Override
        public Optional<EntityType<?>> pick(HolderGetter<EntityType<?>> registry, RandomSource random) {
            return registry.getRandomElementOf(this.key, random).map(holder -> holder.value());
        }

        @Override
        public Component displayName() {
            return Component.literal("#" + this.key.location());
        }
    }
}
