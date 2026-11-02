package com.Infinity.Nexus.Multiblock.recipe;

import java.util.Objects;
import java.util.Optional;

import com.Infinity.Nexus.Multiblock.api.CapabilityPorts.PortSelector;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.fluids.FluidStack;

public sealed interface MultiblockRecipeOutput {
    MapCodec<MultiblockRecipeOutput> MAP_CODEC = Kind.CODEC.dispatchMap(
            "type",
            MultiblockRecipeOutput::kind,
            Kind::codec);
    Codec<MultiblockRecipeOutput> CODEC = MAP_CODEC.codec();

    StreamCodec<RegistryFriendlyByteBuf, MultiblockRecipeOutput> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MultiblockRecipeOutput decode(RegistryFriendlyByteBuf buffer) {
                    Kind kind = Kind.values()[buffer.readByte()];
                    return switch (kind) {
                        case ITEM -> new Item(ItemStackTemplate.STREAM_CODEC.decode(buffer));
                        case FLUID -> new Fluid(FluidStack.STREAM_CODEC.decode(buffer));
                        case ENTITY -> Entity.STREAM_CODEC.decode(buffer);
                    };
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MultiblockRecipeOutput output) {
                    buffer.writeByte(output.kind().ordinal());
                    switch (output) {
                        case Item item -> ItemStackTemplate.STREAM_CODEC.encode(buffer, item.stack());
                        case Fluid fluid -> FluidStack.STREAM_CODEC.encode(buffer, fluid.stack());
                        case Entity entity -> Entity.STREAM_CODEC.encode(buffer, entity);
                    }
                }
            };

    Kind kind();

    enum Kind {
        ITEM("item"),
        FLUID("fluid"),
        ENTITY("entity");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        private MapCodec<? extends MultiblockRecipeOutput> codec() {
            return switch (this) {
                case ITEM -> Item.CODEC;
                case FLUID -> Fluid.CODEC;
                case ENTITY -> Entity.CODEC;
            };
        }

        private static final Codec<Kind> CODEC = Codec.STRING.flatXmap(
                value -> {
                    for (Kind kind : values()) {
                        if (kind.id.equals(value)) {
                            return DataResult.success(kind);
                        }
                    }
                    return DataResult.error(() -> "Unknown recipe output type: " + value);
                },
                kind -> DataResult.success(kind.id));
    }

    record Item(ItemStackTemplate stack) implements MultiblockRecipeOutput {
        private static final MapCodec<Item> CODEC = ItemStackTemplate.MAP_CODEC.xmap(Item::new, Item::stack);

        public Item {
            Objects.requireNonNull(stack, "stack");
        }

        @Override
        public Kind kind() {
            return Kind.ITEM;
        }
    }

    record Fluid(FluidStack stack) implements MultiblockRecipeOutput {
        private static final MapCodec<Fluid> CODEC = FluidStack.MAP_CODEC.xmap(Fluid::new, Fluid::stack);

        public Fluid {
            Objects.requireNonNull(stack, "stack");
            if (stack.isEmpty() || stack.getAmount() < 1) {
                throw new IllegalArgumentException("Fluid recipe output must contain a positive amount");
            }
        }

        @Override
        public Kind kind() {
            return Kind.FLUID;
        }
    }

    record Entity(EntityType<?> entityType, String spawn) implements MultiblockRecipeOutput {
        private static final MapCodec<Entity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                EntityType.CODEC.fieldOf("id").forGetter(Entity::entityType),
                Codec.STRING.validate(Entity::validateSpawn).fieldOf("spawn").forGetter(Entity::spawn))
                .apply(instance, Entity::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, Entity> STREAM_CODEC = StreamCodec.composite(
                EntityType.STREAM_CODEC, Entity::entityType,
                ByteBufCodecs.STRING_UTF8, Entity::spawn,
                Entity::new);

        public Entity {
            Objects.requireNonNull(entityType, "entityType");
            spawn = spawn == null ? "" : spawn.trim();
        }

        public Optional<PortSelector> selector() {
            return PortSelector.parse(this.spawn);
        }

        private static DataResult<String> validateSpawn(String value) {
            String spawn = value == null ? "" : value.trim();
            return PortSelector.parse(spawn).isPresent()
                    ? DataResult.success(spawn)
                    : DataResult.error(() -> "Invalid entity spawn selector: " + spawn);
        }

        @Override
        public Kind kind() {
            return Kind.ENTITY;
        }
    }
}
