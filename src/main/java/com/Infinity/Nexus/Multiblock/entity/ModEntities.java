package com.Infinity.Nexus.Multiblock.entity;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.MultiblockMotion;
import com.Infinity.Nexus.Multiblock.entity.MotionStructure.StructureSnapshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModEntities {
    private ModEntities() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, InfinityNexusMultiblock.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<MultiblockMotionEntity>> MOTION_ENTITY =
            ENTITIES.register("multiblock_motion", () -> EntityType.Builder
                    .of(MultiblockMotionEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .fireImmune()
                    .noLootTable()
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "multiblock_motion"))));

    public static final DeferredRegister<EntityDataSerializer<?>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, InfinityNexusMultiblock.MOD_ID);

    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<StructureSnapshot>>
            STRUCTURE_SNAPSHOT = SERIALIZERS.register("multiblock_structure", () -> EntityDataSerializer.forValueType(
                    ByteBufCodecs.COMPOUND_TAG.map(StructureSnapshot::fromTag, StructureSnapshot::toTag)));

    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<MultiblockMotion>>
            MOTION = SERIALIZERS.register("multiblock_motion", () -> EntityDataSerializer.forValueType(
                    ByteBufCodecs.COMPOUND_TAG.map(
                            tag -> MultiblockMotion.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(),
                            motion -> {
                                Tag encoded = MultiblockMotion.CODEC.encodeStart(NbtOps.INSTANCE, motion).getOrThrow();
                                return encoded instanceof CompoundTag compound ? compound : new CompoundTag();
                            })));

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        SERIALIZERS.register(modEventBus);
    }
}
