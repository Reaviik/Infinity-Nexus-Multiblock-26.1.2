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
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;

public sealed interface MultiblockRecipeOutput {
    Codec<Float> CHANCE_CODEC = Codec.floatRange(0.0F, 1.0F);
    MapCodec<MultiblockRecipeOutput> MAP_CODEC = Codec.STRING.dispatchMap(
            "type",
            MultiblockRecipeOutput::typeId,
            id -> switch (id) {
                case "item" -> Item.CODEC;
                case "fluid" -> Fluid.CODEC;
                case "entity" -> Entity.CODEC;
                case "energy" -> Energy.CODEC;
                default -> {
                    MultiblockResourceType type = MultiblockAddons.get(id);
                    yield type == null ? Resource.codec(id) : type.outputCodec();
                }
            });
    Codec<MultiblockRecipeOutput> CODEC = MAP_CODEC.codec();

    StreamCodec<RegistryFriendlyByteBuf, MultiblockRecipeOutput> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MultiblockRecipeOutput decode(RegistryFriendlyByteBuf buffer) {
                    String type = buffer.readUtf();
                    return switch (type) {
                        case "item" -> Item.STREAM_CODEC.decode(buffer);
                        case "fluid" -> Fluid.STREAM_CODEC.decode(buffer);
                        case "entity" -> Entity.STREAM_CODEC.decode(buffer);
                        case "energy" -> Energy.STREAM_CODEC.decode(buffer);
                        default -> new Resource(
                                type,
                                buffer.readUtf(),
                                buffer.readVarInt(),
                                buffer.readUtf(),
                                buffer.readFloat());
                    };
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MultiblockRecipeOutput output) {
                    buffer.writeUtf(output.typeId());
                    switch (output) {
                        case Item item -> Item.STREAM_CODEC.encode(buffer, item);
                        case Fluid fluid -> Fluid.STREAM_CODEC.encode(buffer, fluid);
                        case Entity entity -> Entity.STREAM_CODEC.encode(buffer, entity);
                        case Energy energy -> Energy.STREAM_CODEC.encode(buffer, energy);
                        case Resource resource -> {
                            buffer.writeUtf(resource.id());
                            buffer.writeVarInt(resource.amount());
                            buffer.writeUtf(resource.tank());
                            buffer.writeFloat(resource.chance());
                        }
                    }
                }
            };

    Kind kind();

    default String typeId() {
        return this.kind().id();
    }

    float chance();

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

    record Item(ItemStackTemplate stack, float chance) implements MultiblockRecipeOutput {
        private static final MapCodec<Item> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ItemStackTemplate.MAP_CODEC.forGetter(Item::stack),
                CHANCE_CODEC.optionalFieldOf("chance", 1.0F).forGetter(Item::chance))
                .apply(instance, Item::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, Item> STREAM_CODEC = StreamCodec.composite(
                ItemStackTemplate.STREAM_CODEC, Item::stack,
                ByteBufCodecs.FLOAT, Item::chance,
                Item::new);

        public Item {
            Objects.requireNonNull(stack, "stack");
            validateChance(chance);
        }

        public Item(ItemStackTemplate stack) {
            this(stack, 1.0F);
        }

        @Override
        public Kind kind() {
            return Kind.ITEM;
        }
    }

    record Fluid(FluidStackTemplate template, float chance) implements MultiblockRecipeOutput {
        private static final MapCodec<Fluid> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                FluidStackTemplate.MAP_CODEC.forGetter(Fluid::template),
                CHANCE_CODEC.optionalFieldOf("chance", 1.0F).forGetter(Fluid::chance))
                .apply(instance, Fluid::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, Fluid> STREAM_CODEC = StreamCodec.composite(
                FluidStackTemplate.STREAM_CODEC, Fluid::template,
                ByteBufCodecs.FLOAT, Fluid::chance,
                Fluid::new);

        public Fluid {
            Objects.requireNonNull(template, "template");
            validateChance(chance);
        }

        public Fluid(FluidStack stack) {
            this(FluidStackTemplate.fromNonEmptyStack(stack), 1.0F);
        }

        public FluidStack stack() {
            return this.template.create();
        }

        @Override
        public Kind kind() {
            return Kind.FLUID;
        }
    }

    record Entity(EntityTypeSelector type, String spawn, int count, float chance) implements MultiblockRecipeOutput {
        private static final MapCodec<Entity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                EntityType.CODEC.optionalFieldOf("id").forGetter(entity -> entity.type().direct()),
                EntityTypeSelector.TAG_CODEC.optionalFieldOf("tag").forGetter(entity -> entity.type().tag()),
                Codec.STRING.validate(Entity::validateSpawn).optionalFieldOf("spawn", "")
                        .forGetter(Entity::spawn),
                Codec.intRange(1, 99).optionalFieldOf("count", 1).forGetter(Entity::count),
                CHANCE_CODEC.optionalFieldOf("chance", 1.0F).forGetter(Entity::chance))
                .apply(instance, Entity::fromCodec));

        private static final StreamCodec<RegistryFriendlyByteBuf, Entity> STREAM_CODEC = StreamCodec.composite(
                EntityTypeSelector.STREAM_CODEC, Entity::type,
                ByteBufCodecs.STRING_UTF8, Entity::spawn,
                ByteBufCodecs.VAR_INT, Entity::count,
                ByteBufCodecs.FLOAT, Entity::chance,
                Entity::new);

        public Entity {
            Objects.requireNonNull(type, "type");
            spawn = spawn == null ? "" : spawn.trim();
            if (count < 1) {
                throw new IllegalArgumentException("Entity recipe output count must be positive");
            }
            validateChance(chance);
        }

        public Entity(EntityType<?> entityType, String spawn, int count, float chance) {
            this(new EntityTypeSelector.Direct(entityType), spawn, count, chance);
        }

        public Entity(EntityType<?> entityType, String spawn, float chance) {
            this(entityType, spawn, 1, chance);
        }

        public Entity(EntityType<?> entityType, String spawn) {
            this(entityType, spawn, 1, 1.0F);
        }

        public Entity(TagKey<EntityType<?>> tag, String spawn, int count, float chance) {
            this(new EntityTypeSelector.Tag(tag), spawn, count, chance);
        }

        public Entity(TagKey<EntityType<?>> tag, String spawn) {
            this(tag, spawn, 1, 1.0F);
        }

        public Optional<PortSelector> selector() {
            return PortSelector.parse(this.spawn);
        }

        private static Entity fromCodec(
                Optional<EntityType<?>> entityType,
                Optional<TagKey<EntityType<?>>> tag,
                String spawn,
                int count,
                float chance) {
            if (entityType.isPresent() == tag.isPresent()) {
                throw new IllegalArgumentException("Entity recipe output must define exactly one of 'id' or 'tag'");
            }
            EntityTypeSelector type = entityType.<EntityTypeSelector>map(EntityTypeSelector.Direct::new)
                    .or(() -> tag.map(EntityTypeSelector.Tag::new))
                    .orElseThrow();
            return new Entity(type, spawn, count, chance);
        }

        private static DataResult<String> validateSpawn(String value) {
            String spawn = value == null ? "" : value.trim();
            return spawn.isEmpty() || PortSelector.parse(spawn).isPresent()
                    ? DataResult.success(spawn)
                    : DataResult.error(() -> "Invalid entity spawn selector: " + spawn);
        }

        @Override
        public Kind kind() {
            return Kind.ENTITY;
        }
    }

    record Energy(int amount, float chance) implements MultiblockRecipeOutput {
        private static final MapCodec<Energy> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(Energy::amount),
                CHANCE_CODEC.optionalFieldOf("chance", 1.0F).forGetter(Energy::chance))
                .apply(instance, Energy::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, Energy> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Energy::amount,
                ByteBufCodecs.FLOAT, Energy::chance,
                Energy::new);

        public Energy {
            if (amount < 1) {
                throw new IllegalArgumentException("Energy recipe output amount must be positive");
            }
            validateChance(chance);
        }

        public Energy(int amount) {
            this(amount, 1.0F);
        }

        @Override
        public Kind kind() {
            return Kind.ENERGY;
        }
    }

    record Resource(String resourceKind, String id, int amount, String tank, float chance)
            implements MultiblockRecipeOutput {
        public Resource {
            if (resourceKind == null || resourceKind.isBlank()) {
                throw new IllegalArgumentException("Resource recipe output kind cannot be blank");
            }
            MultiblockResourceType type = MultiblockAddons.get(resourceKind);
            if (type == null) {
                throw new IllegalArgumentException("Unknown resource recipe output kind: " + resourceKind);
            }
            if (id == null) {
                id = "";
            }
            if (amount < 1) {
                throw new IllegalArgumentException("Resource recipe output amount must be positive");
            }
            if (!id.isEmpty()) {
                DataResult<String> validated = type.validateResourceId(id);
                if (validated.error().isPresent()) {
                    throw new IllegalArgumentException(validated.error().get().message());
                }
            }
            tank = tank == null ? "" : tank.trim();
            validateChance(chance);
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
                    Codec.intRange(1, Integer.MAX_VALUE).fieldOf("amount").forGetter(Resource::amount),
                    Codec.STRING.optionalFieldOf("tank", "").forGetter(Resource::tank),
                    CHANCE_CODEC.optionalFieldOf("chance", 1.0F).forGetter(Resource::chance))
                    .apply(instance, (id, amount, tank, chance) -> new Resource(resourceKind, id, amount, tank, chance)));
        }

        public Resource(String resourceKind, String id, int amount) {
            this(resourceKind, id, amount, "", 1.0F);
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

    private static void validateChance(float chance) {
        if (chance < 0.0F || chance > 1.0F) {
            throw new IllegalArgumentException("Recipe output chance must be between 0 and 1");
        }
    }
}
