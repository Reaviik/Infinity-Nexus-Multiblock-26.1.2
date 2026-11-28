package com.Infinity.Nexus.Multiblock.entity;

import java.util.ArrayList;
import java.util.List;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.MultiblockMotion;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlockEntity;
import com.Infinity.Nexus.Multiblock.entity.MotionStructure.PartEntry;
import com.Infinity.Nexus.Multiblock.entity.MotionStructure.StructureSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Parte movel de um multibloco. Guarda o snapshot dos blocos (posicoes
 * relativas + BlockState + NBT opcional) e a configuracao de animacao
 * sincronizada. Imortal, sem fisica, sem colisao e sem inventario.
 */
public class MultiblockMotionEntity extends Entity {

    private static final EntityDataAccessor<StructureSnapshot> STRUCTURE =
            SynchedEntityData.defineId(MultiblockMotionEntity.class, ModEntities.STRUCTURE_SNAPSHOT.get());
    private static final EntityDataAccessor<MultiblockMotion> MOTION =
            SynchedEntityData.defineId(MultiblockMotionEntity.class, ModEntities.MOTION.get());
    private static final EntityDataAccessor<Long> START_TIME =
            SynchedEntityData.defineId(MultiblockMotionEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> ANIMATION_TICKS =
            SynchedEntityData.defineId(MultiblockMotionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ANIMATION_SPEED =
            SynchedEntityData.defineId(MultiblockMotionEntity.class, EntityDataSerializers.FLOAT);

    private BlockPos originMin = BlockPos.ZERO;
    private BlockPos controllerPos = BlockPos.ZERO;
    private @Nullable Identifier definitionId;
    private boolean returning;
    private int damageCooldown;
    private double animationTicks;

    public MultiblockMotionEntity(EntityType<? extends MultiblockMotionEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STRUCTURE, StructureSnapshot.EMPTY);
        builder.define(MOTION, MultiblockMotion.NONE);
        builder.define(START_TIME, 0L);
        builder.define(ANIMATION_TICKS, 0.0F);
        builder.define(ANIMATION_SPEED, 1.0F);
    }

    public void configure(
            StructureSnapshot snapshot,
            BlockPos originMin,
            BlockPos controllerPos,
            @Nullable Identifier definitionId,
            MultiblockMotion motion) {
        this.entityData.set(STRUCTURE, snapshot);
        this.entityData.set(MOTION, motion);
        this.entityData.set(START_TIME, this.level().getGameTime());
        this.entityData.set(ANIMATION_TICKS, 0.0F);
        this.entityData.set(ANIMATION_SPEED, 1.0F);
        this.animationTicks = 0.0D;
        this.originMin = originMin.immutable();
        this.controllerPos = controllerPos.immutable();
        this.definitionId = definitionId;
        this.damageCooldown = 0;
        double centerX = originMin.getX() + snapshot.sx() / 2.0D;
        double centerZ = originMin.getZ() + snapshot.sz() / 2.0D;
        this.setPos(centerX, originMin.getY(), centerZ);
        this.refreshDimensions();
    }

    public StructureSnapshot structure() {
        return this.entityData.get(STRUCTURE);
    }

    public MultiblockMotion motion() {
        return this.entityData.get(MOTION);
    }

    public long elapsedTicks() {
        return (long) Math.floor(this.entityData.get(ANIMATION_TICKS));
    }

    public float animationSpeed() {
        return this.entityData.get(ANIMATION_SPEED);
    }

    public void setAnimationSpeed(float speed) {
        this.entityData.set(ANIMATION_SPEED, Mth.clamp(speed, 0.0F, 8.0F));
    }

    public BlockPos originMin() {
        return this.originMin;
    }

    public BlockPos controllerPos() {
        return this.controllerPos;
    }

    public @Nullable Identifier definitionId() {
        return this.definitionId;
    }

    public boolean isReturning() {
        return this.returning;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel serverLevel) || this.returning) {
            return;
        }
        this.animationTicks += this.animationSpeed();
        this.entityData.set(ANIMATION_TICKS, (float) this.animationTicks);
        if (!serverLevel.isLoaded(this.controllerPos)) {
            return;
        }
        if (!(serverLevel.getBlockEntity(this.controllerPos) instanceof MultiblockControllerBlockEntity controller)) {
            this.returnToOrigin("controller missing");
            return;
        }
        if (!controller.acceptsMotion(this)) {
            this.returnToOrigin("controller no longer tracks this motion");
            return;
        }
        if (this.motion().damageEntities()) {
            if (--this.damageCooldown <= 0) {
                this.damageCooldown = this.motion().damageInterval();
                this.damageNearbyEntities(serverLevel, controller);
            }
        }
    }

    private void damageNearbyEntities(ServerLevel serverLevel, MultiblockControllerBlockEntity controller) {
        StructureSnapshot snapshot = this.structure();
        float damage = controller.motionDamage(this.motion().damage());
        if (snapshot.isEmpty() || damage <= 0.0F) {
            return;
        }
        MultiblockMotion.Transform transform = this.motion().sample(
                this.elapsedTicks(), snapshot.sx(), snapshot.sy(), snapshot.sz());
        Vec3 offset = transform.offset();
        double minX = this.originMin.getX() + offset.x();
        double minY = this.originMin.getY() + offset.y();
        double minZ = this.originMin.getZ() + offset.z();
        AABB box = new AABB(
                minX, minY, minZ,
                minX + snapshot.sx(), minY + snapshot.sy(), minZ + snapshot.sz());
        DamageSource source = this.resolveDamageSource(serverLevel);
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, box)) {
            if (target.isSpectator() || !target.isAlive()) {
                continue;
            }
            target.hurtServer(serverLevel, source, damage);
        }
    }

    private DamageSource resolveDamageSource(ServerLevel level) {
        Identifier id = Identifier.tryParse(this.motion().damageSource());
        if (id != null) {
            if (id.toString().equals("minecraft:player_attack")) {
                Player player = level.getNearestPlayer(this, 16.0D);
                if (player != null) {
                    return level.damageSources().playerAttack(player);
                }
            }
            try {
                return level.damageSources().source(ResourceKey.create(Registries.DAMAGE_TYPE, id));
            } catch (RuntimeException ignored) {
                // Unknown damage type: fall back to player/generic below.
            }
        }
        Player player = level.getNearestPlayer(this, 16.0D);
        return player != null
                ? level.damageSources().playerAttack(player)
                : level.damageSources().generic();
    }

    /** Chamado pelo controller quando nao ha mais receita valida. */
    public void requestReturn() {
        if (this.level().isClientSide() || this.returning) {
            return;
        }
        this.returning = true;
        this.restoreParts();
        this.discard();
    }

    private void returnToOrigin(String reason) {
        if (this.returning) {
            return;
        }
        InfinityNexusMultiblock.LOGGER.debug(
                "Returning multiblock motion '{}' ({}): {}", this.motion().id(), this.getUUID(), reason);
        this.returning = true;
        this.restoreParts();
        this.discard();
    }

    private void restoreParts() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        StructureSnapshot snapshot = this.structure();
        for (PartEntry part : snapshot.parts()) {
            BlockPos pos = this.originMin.offset(part.dx(), part.dy(), part.dz());
            serverLevel.setBlock(pos, part.state(), Block.UPDATE_ALL);
            part.nbt().ifPresent(tag -> {
                BlockEntity blockEntity = BlockEntity.loadStatic(
                        pos, part.state(), tag, serverLevel.registryAccess());
                if (blockEntity != null) {
                    serverLevel.setBlockEntity(blockEntity);
                }
            });
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide() && !this.returning && reason.shouldDestroy()) {
            this.returning = true;
            MultiblockControllerBlockEntity controller = this.findController();
            if (controller != null && controller.acceptsMotion(this)) {
                controller.onMotionEntityRemoved(this);
            } else {
                InfinityNexusMultiblock.LOGGER.warn(
                        "Multiblock motion entity {} (motion '{}') lost its controller at {}; restoring parts at {}",
                        this.getUUID(), this.motion().id(), this.controllerPos, this.originMin);
            }
            this.restoreParts();
        }
        super.remove(reason);
    }

    private @Nullable MultiblockControllerBlockEntity findController() {
        if (!(this.level() instanceof ServerLevel serverLevel) || !serverLevel.isLoaded(this.controllerPos)) {
            return null;
        }
        return serverLevel.getBlockEntity(this.controllerPos) instanceof MultiblockControllerBlockEntity controller
                ? controller
                : null;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        StructureSnapshot snapshot = this.structure();
        if (snapshot.sx() <= 0 || snapshot.sy() <= 0 || snapshot.sz() <= 0) {
            return super.getDimensions(pose);
        }
        return EntityDimensions.fixed(Math.max(snapshot.sx(), snapshot.sz()), snapshot.sy());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (STRUCTURE.equals(accessor)) {
            this.refreshDimensions();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        StructureSnapshot snapshot = this.structure();
        var parts = output.list("Parts", PartEntry.CODEC);
        for (PartEntry part : snapshot.parts()) {
            parts.add(part);
        }
        output.putInt("SX", snapshot.sx());
        output.putInt("SY", snapshot.sy());
        output.putInt("SZ", snapshot.sz());
        output.store("Motion", MultiblockMotion.CODEC, this.motion());
        output.putLong("StartTime", this.entityData.get(START_TIME));
        output.putDouble("AnimationTicks", this.animationTicks);
        output.putFloat("AnimationSpeed", this.animationSpeed());
        output.putInt("OX", this.originMin.getX());
        output.putInt("OY", this.originMin.getY());
        output.putInt("OZ", this.originMin.getZ());
        output.putInt("CPX", this.controllerPos.getX());
        output.putInt("CPY", this.controllerPos.getY());
        output.putInt("CPZ", this.controllerPos.getZ());
        if (this.definitionId != null) {
            output.store("Definition", Identifier.CODEC, this.definitionId);
        }
        output.putBoolean("Returning", this.returning);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        List<PartEntry> parts = new ArrayList<>();
        for (PartEntry part : input.listOrEmpty("Parts", PartEntry.CODEC)) {
            parts.add(part);
        }
        StructureSnapshot snapshot = new StructureSnapshot(
                input.getIntOr("SX", 0),
                input.getIntOr("SY", 0),
                input.getIntOr("SZ", 0),
                parts);
        this.entityData.set(STRUCTURE, snapshot);
        this.entityData.set(MOTION, input.read("Motion", MultiblockMotion.CODEC).orElse(MultiblockMotion.NONE));
        long startTime = input.getLongOr("StartTime", 0L);
        this.entityData.set(START_TIME, startTime);
        double ticks = input.getDoubleOr("AnimationTicks", -1.0D);
        if (ticks < 0.0D) {
            long now = this.level() == null ? startTime : this.level().getGameTime();
            ticks = Math.max(0L, now - startTime);
        }
        this.animationTicks = ticks;
        this.entityData.set(ANIMATION_TICKS, (float) ticks);
        this.entityData.set(ANIMATION_SPEED, input.getFloatOr("AnimationSpeed", 1.0F));
        this.originMin = new BlockPos(
                input.getIntOr("OX", 0),
                input.getIntOr("OY", 0),
                input.getIntOr("OZ", 0));
        this.controllerPos = new BlockPos(
                input.getIntOr("CPX", 0),
                input.getIntOr("CPY", 0),
                input.getIntOr("CPZ", 0));
        this.definitionId = input.read("Definition", Identifier.CODEC).orElse(null);
        this.returning = input.getBooleanOr("Returning", false);
        this.refreshDimensions();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }
}
