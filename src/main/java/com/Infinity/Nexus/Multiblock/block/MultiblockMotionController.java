package com.Infinity.Nexus.Multiblock.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockMotion;
import com.Infinity.Nexus.Multiblock.api.MultiblockPattern;
import com.Infinity.Nexus.Multiblock.entity.ModEntities;
import com.Infinity.Nexus.Multiblock.entity.MotionStructure.PartEntry;
import com.Infinity.Nexus.Multiblock.entity.MotionStructure.StructureSnapshot;
import com.Infinity.Nexus.Multiblock.entity.MultiblockMotionEntity;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

final class MultiblockMotionController {
    private final MultiblockControllerBlockEntity owner;
    private final Map<String, UUID> motionEntities = new LinkedHashMap<>();
    private boolean active;
    private Set<Character> activeKeys = Set.of();

    MultiblockMotionController(MultiblockControllerBlockEntity owner) {
        this.owner = owner;
    }

    Set<Character> keysFor(MultiblockDefinition definition) {
        return this.active ? this.activeKeys : Set.of();
    }

    Set<Character> keys(@Nullable MultiblockDefinition definition) {
        if (definition == null || definition.motions().isEmpty()) {
            return Set.of();
        }
        Set<Character> keys = new LinkedHashSet<>();
        for (MultiblockMotion motion : definition.motions()) {
            keys.addAll(motion.keys());
        }
        return Set.copyOf(keys);
    }

    void ensureStarted(ServerLevel serverLevel, MultiblockDefinition definition) {
        if (definition.motions().isEmpty()) {
            this.stop();
            return;
        }
        if (this.active) {
            if (this.motionEntities.isEmpty()) {
                this.captureAll(serverLevel, definition);
            }
            return;
        }
        this.active = true;
        this.activeKeys = this.keys(definition);
        this.captureAll(serverLevel, definition);
        if (this.motionEntities.isEmpty()) {
            this.active = false;
            this.activeKeys = Set.of();
        }
        this.owner.setChanged();
    }

    void tickStorage(ServerLevel serverLevel, @Nullable MultiblockDefinition definition) {
        if (definition == null || !definition.type().isStorage()) {
            return;
        }
        if (!this.owner.isFormed()) {
            this.stop();
            return;
        }
        this.ensureStarted(serverLevel, definition);
        double fill = this.owner.storage().fillRatio();
        for (MultiblockMotion motion : definition.motions()) {
            UUID uuid = this.motionEntities.get(motion.id());
            if (uuid == null) {
                continue;
            }
            if (serverLevel.getEntity(uuid) instanceof MultiblockMotionEntity entity) {
                entity.setAnimationSpeed((float) motion.animation().speedFactor(fill));
            }
        }
    }

    private void captureAll(ServerLevel serverLevel, MultiblockDefinition definition) {
        for (MultiblockMotion motion : definition.motions()) {
            this.capture(serverLevel, definition, motion);
        }
    }

    private void capture(ServerLevel serverLevel, MultiblockDefinition definition, MultiblockMotion motion) {
        if (this.motionEntities.containsKey(motion.id())) {
            return;
        }
        char upgradeKey = definition.pattern().upgradeKey();
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        List<CompoundTag> tags = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (MultiblockPattern.PatternCell cell : definition.pattern().cells()) {
            if (cell.anchor() || cell.symbol() == upgradeKey || !motion.keys().contains(cell.symbol())) {
                continue;
            }
            BlockPos offset = new BlockPos(cell.x(), cell.y(), cell.z()).rotate(this.owner.rotation);
            BlockPos pos = this.owner.origin.offset(offset.getX(), offset.getY(), offset.getZ());
            positions.add(pos);
            states.add(serverLevel.getBlockState(pos));
            CompoundTag nbt = null;
            if (motion.preserveNbt()) {
                BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
                if (blockEntity != null) {
                    nbt = blockEntity.saveWithFullMetadata(serverLevel.registryAccess());
                }
            }
            tags.add(nbt);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (positions.isEmpty()) {
            return;
        }
        int sx = maxX - minX + 1;
        int sy = maxY - minY + 1;
        int sz = maxZ - minZ + 1;
        BlockPos originMin = new BlockPos(minX, minY, minZ);
        List<PartEntry> parts = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            parts.add(new PartEntry(
                    pos.getX() - minX,
                    pos.getY() - minY,
                    pos.getZ() - minZ,
                    states.get(i),
                    Optional.ofNullable(tags.get(i))));
            serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        StructureSnapshot snapshot = new StructureSnapshot(sx, sy, sz, List.copyOf(parts));
        MultiblockMotionEntity entity = ModEntities.MOTION_ENTITY.get().create(serverLevel, EntitySpawnReason.TRIGGERED);
        if (entity == null) {
            this.restorePartsDirect(serverLevel, originMin, parts);
            return;
        }
        entity.configure(snapshot, originMin, this.owner.getBlockPos(), this.owner.definitionId, motion);
        if (!serverLevel.addFreshEntity(entity)) {
            this.restorePartsDirect(serverLevel, originMin, parts);
            return;
        }
        this.motionEntities.put(motion.id(), entity.getUUID());
    }

    private void restorePartsDirect(ServerLevel serverLevel, BlockPos originMin, List<PartEntry> parts) {
        for (PartEntry part : parts) {
            BlockPos pos = originMin.offset(part.dx(), part.dy(), part.dz());
            serverLevel.setBlock(pos, part.state(), Block.UPDATE_CLIENTS);
            part.nbt().ifPresent(tag -> {
                BlockEntity blockEntity = BlockEntity.loadStatic(
                        pos, part.state(), tag, serverLevel.registryAccess());
                if (blockEntity != null) {
                    serverLevel.setBlockEntity(blockEntity);
                }
            });
        }
    }

    void stop() {
        if (this.motionEntities.isEmpty() && !this.active) {
            return;
        }
        ServerLevel serverLevel = this.owner.serverLevel();
        if (serverLevel != null) {
            for (UUID uuid : this.motionEntities.values()) {
                if (serverLevel.getEntity(uuid) instanceof MultiblockMotionEntity entity) {
                    entity.requestReturn();
                }
            }
        }
        this.motionEntities.clear();
        this.active = false;
        this.activeKeys = Set.of();
        this.owner.setChanged();
    }

    void save(ValueOutput output) {
        output.putBoolean("MotionsActive", this.active);
        if (!this.motionEntities.isEmpty()) {
            Map<String, String> map = new LinkedHashMap<>();
            for (Map.Entry<String, UUID> entry : this.motionEntities.entrySet()) {
                map.put(entry.getKey(), entry.getValue().toString());
            }
            output.store(
                    "MotionEntities",
                    Codec.unboundedMap(Codec.STRING, Codec.STRING),
                    map);
        }
    }

    void load(ValueInput input, @Nullable MultiblockDefinition definition) {
        this.motionEntities.clear();
        input.read("MotionEntities", Codec.unboundedMap(Codec.STRING, Codec.STRING))
                .orElse(Map.of())
                .forEach((key, value) -> {
                    try {
                        this.motionEntities.put(key, UUID.fromString(value));
                    } catch (IllegalArgumentException ignored) {
                    }
                });
        this.active = input.getBooleanOr("MotionsActive", false);
        this.activeKeys = this.keys(definition);
    }

    boolean accepts(MultiblockMotionEntity entity) {
        MultiblockDefinition definition = this.owner.definition();
        if (definition == null || this.owner.definitionId == null || entity.definitionId() == null) {
            return false;
        }
        if (!entity.definitionId().equals(this.owner.definitionId)) {
            return false;
        }
        String motionId = entity.motion().id();
        UUID tracked = this.motionEntities.get(motionId);
        return tracked != null && tracked.equals(entity.getUUID())
                && definition.motions().stream().anyMatch(motion -> motion.id().equals(motionId));
    }

    void onRemoved(MultiblockMotionEntity entity) {
        if (this.motionEntities.remove(entity.motion().id(), entity.getUUID())) {
            this.owner.setChanged();
        }
    }
}
