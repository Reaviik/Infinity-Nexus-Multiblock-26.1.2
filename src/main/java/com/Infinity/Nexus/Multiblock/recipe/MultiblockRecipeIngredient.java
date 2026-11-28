package com.Infinity.Nexus.Multiblock.recipe;

import java.util.Objects;
import java.util.Optional;

import com.Infinity.Nexus.Multiblock.api.CapabilityPorts.PortSelector;
import com.Infinity.Nexus.Multiblock.api.addon.MultiblockAddons;
import com.Infinity.Nexus.Multiblock.api.addon.MultiblockResourceType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

public sealed interface MultiblockRecipeIngredient {
    MapCodec<MultiblockRecipeIngredient> MAP_CODEC = Codec.STRING.dispatchMap(
            "type",
            MultiblockRecipeIngredient::typeId,
            id -> switch (id) {
                case "item" -> Item.CODEC;
                case "fluid" -> Fluid.CODEC;
                case "entity" -> Entity.CODEC;
                case "energy" -> Energy.CODEC;
                default -> {
                    MultiblockResourceType type = MultiblockAddons.get(id);
                    yield type == null ? Resource.codec(id) : type.ingredientCodec();
                }
            });
    Codec<MultiblockRecipeIngredient> CODEC = MAP_CODEC.codec();

    StreamCodec<RegistryFriendlyByteBuf, MultiblockRecipeIngredient> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MultiblockRecipeIngredient decode(RegistryFriendlyByteBuf buffer) {
                    String type = buffer.readUtf();
                    return switch (type) {
                        case "item" -> Item.STREAM_CODEC.decode(buffer);
                        case "fluid" -> Fluid.STREAM_CODEC.decode(buffer);
                        case "entity" -> Entity.STREAM_CODEC.decode(buffer);
                        case "energy" -> Energy.STREAM_CODEC.decode(buffer);
                        default -> new Resource(type, buffer.readUtf(), buffer.readVarInt());
                    };
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MultiblockRecipeIngredient ingredient) {
                    buffer.writeUtf(ingredient.typeId());
                    switch (ingredient) {
                        case Item item -> Item.STREAM_CODEC.encode(buffer, item);
                        case Fluid fluid -> Fluid.STREAM_CODEC.encode(buffer, fluid);
                        case Entity entity -> Entity.STREAM_CODEC.encode(buffer, entity);
                        case Energy energy -> Energy.STREAM_CODEC.encode(buffer, energy);
                        case Resource resource -> {
                            buffer.writeUtf(resource.id());
                            buffer.writeVarInt(resource.amount());
                        }
                    }
                }
            };

    Kind kind();

    default String typeId() {
        return this.kind().id();
    }

    enum Kind {
        ITEM("item"),
        FLUID("fluid"),
        ENTITY("entity"),
        ENERGY("energy"),
        RESOURCE("resource");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }
    }

    record Item(Ingredient ingredient, int count) implements MultiblockRecipeIngredient {
        private static final MapCodec<Item> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(Item::ingredient),
                Codec.intRange(1, 99).optionalFieldOf("count", 1).forGetter(Item::count))
                .apply(instance, Item::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, Item> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, Item::ingredient,
                ByteBufCodecs.VAR_INT, Item::count,
                Item::new);

        public Item {
            Objects.requireNonNull(ingredient, "ingredient");
            if (count < 1) {
                throw new IllegalArgumentException("Item recipe input count must be positive");
            }
        }

        public Item(Ingredient ingredient) {
            this(ingredient, 1);
        }

        @Override
        public Kind kind() {
            return Kind.ITEM;
        }
    }

    record Fluid(SizedFluidIngredient ingredient) implements MultiblockRecipeIngredient {
        private static final MapCodec<Fluid> CODEC = MapCodec.assumeMapUnsafe(
                SizedFluidIngredient.CODEC.xmap(Fluid::new, Fluid::ingredient));

        private static final StreamCodec<RegistryFriendlyByteBuf, Fluid> STREAM_CODEC =
                SizedFluidIngredient.STREAM_CODEC.map(Fluid::new, Fluid::ingredient);

        public Fluid {
            Objects.requireNonNull(ingredient, "ingredient");
            if (ingredient.amount() < 1) {
                throw new IllegalArgumentException("Fluid recipe input amount must be positive");
            }
        }

        @Override
        public Kind kind() {
            return Kind.FLUID;
        }
    }

    record Entity(EntityTypeSelector type, String spawn, int count) implements MultiblockRecipeIngredient {
        private static final MapCodec<Entity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                EntityType.CODEC.optionalFieldOf("id").forGetter(entity -> entity.type().direct()),
                EntityTypeSelector.TAG_CODEC.optionalFieldOf("tag").forGetter(entity -> entity.type().tag()),
                Codec.STRING.validate(Entity::validateSpawn).optionalFieldOf("spawn", "").forGetter(Entity::spawn),
                Codec.intRange(1, 99).optionalFieldOf("count", 1).forGetter(Entity::count))
                .apply(instance, Entity::fromCodec));

        private static final StreamCodec<RegistryFriendlyByteBuf, Entity> STREAM_CODEC = StreamCodec.composite(
                EntityTypeSelector.STREAM_CODEC, Entity::type,
                ByteBufCodecs.STRING_UTF8, Entity::spawn,
                ByteBufCodecs.VAR_INT, Entity::count,
                Entity::new);

        public Entity {
            Objects.requireNonNull(type, "type");
            spawn = spawn == null ? "" : spawn.trim();
            if (!spawn.isEmpty() && PortSelector.parse(spawn).isEmpty()) {
                throw new IllegalArgumentException("Invalid entity input selector: " + spawn);
            }
            if (count < 1) {
                throw new IllegalArgumentException("Entity recipe input count must be positive");
            }
        }

        public Entity(EntityType<?> entityType, String spawn, int count) {
            this(new EntityTypeSelector.Direct(entityType), spawn, count);
        }

        public Entity(EntityType<?> entityType, String spawn) {
            this(entityType, spawn, 1);
        }

        public Entity(TagKey<EntityType<?>> tag, String spawn, int count) {
            this(new EntityTypeSelector.Tag(tag), spawn, count);
        }

        public Entity(TagKey<EntityType<?>> tag, String spawn) {
            this(tag, spawn, 1);
        }

        public Optional<PortSelector> selector() {
            return PortSelector.parse(this.spawn);
        }

        private static Entity fromCodec(
                Optional<EntityType<?>> entityType,
                Optional<TagKey<EntityType<?>>> tag,
                String spawn,
                int count) {
            if (entityType.isPresent() == tag.isPresent()) {
                throw new IllegalArgumentException("Entity recipe input must define exactly one of 'id' or 'tag'");
            }
            EntityTypeSelector type = entityType.<EntityTypeSelector>map(EntityTypeSelector.Direct::new)
                    .or(() -> tag.map(EntityTypeSelector.Tag::new))
                    .orElseThrow();
            return new Entity(type, spawn, count);
        }

        private static DataResult<String> validateSpawn(String value) {
            String spawn = value == null ? "" : value.trim();
            return spawn.isEmpty() || PortSelector.parse(spawn).isPresent()
                    ? DataResult.success(spawn)
                    : DataResult.error(() -> "Invalid entity input selector: " + spawn);
        }

        @Override
        public Kind kind() {
            return Kind.ENTITY;
        }
    }

    record Energy(int amount) implements MultiblockRecipeIngredient {
        private static final MapCodec<Energy> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(Energy::amount))
                .apply(instance, Energy::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, Energy> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public Energy decode(RegistryFriendlyByteBuf buffer) {
                        return new Energy(buffer.readVarInt());
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, Energy energy) {
                        buffer.writeVarInt(energy.amount());
                    }
                };

        public Energy {
            if (amount < 1) {
                throw new IllegalArgumentException("Energy recipe input amount must be positive");
            }
        }

        @Override
        public Kind kind() {
            return Kind.ENERGY;
        }
    }

    record Resource(String resourceKind, String id, int amount) implements MultiblockRecipeIngredient {
        public Resource {
            if (resourceKind == null || resourceKind.isBlank()) {
                throw new IllegalArgumentException("Resource recipe input kind cannot be blank");
            }
            MultiblockResourceType type = MultiblockAddons.get(resourceKind);
            if (type == null) {
                throw new IllegalArgumentException("Unknown resource recipe input kind: " + resourceKind);
            }
            if (amount < 1) {
                throw new IllegalArgumentException("Resource recipe input amount must be positive");
            }
            if (id == null) {
                id = "";
            }
            if (!id.isEmpty()) {
                DataResult<String> validated = type.validateResourceId(id);
                if (validated.error().isPresent()) {
                    throw new IllegalArgumentException(validated.error().get().message());
                }
            }
        }

        public static MapCodec<Resource> codec(String resourceKind) {
            return codec(resourceKind, "id", true);
        }

        public static MapCodec<Resource> codec(String resourceKind, String idField, boolean idRequired) {
            MapCodec<String> idCodec = idRequired
                    ? Codec.STRING.fieldOf(idField)
                    : Codec.STRING.optionalFieldOf(idField, "");
            return RecordCodecBuilder.mapCodec(instance -> instance.group(
                    idCodec.forGetter(Resource::id),
                    Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(Resource::amount))
                    .apply(instance, (id, amount) -> new Resource(resourceKind, id, amount)));
        }

        @Override
        public Kind kind() {
            return Kind.RESOURCE;
        }

        @Override
        public String typeId() {
            return this.resourceKind;
        }
    }
}
