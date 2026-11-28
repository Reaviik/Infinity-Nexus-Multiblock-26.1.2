package com.Infinity.Nexus.Multiblock.client;

import java.util.ArrayList;
import java.util.List;

import com.Infinity.Nexus.Multiblock.api.MultiblockMotion;
import com.Infinity.Nexus.Multiblock.entity.MotionStructure.PartEntry;
import com.Infinity.Nexus.Multiblock.entity.MotionStructure.StructureSnapshot;
import com.Infinity.Nexus.Multiblock.entity.MultiblockMotionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/**
 * Desenha o snapshot esparso da parte movel e aplica a animacao (offset +
 * rotacao em torno do pivot) configurada no JSON.
 */
public class MotionEntityRenderer extends EntityRenderer<MultiblockMotionEntity, MotionEntityRenderer.MotionRenderState> {

    public record Entry(MovingBlockRenderState block, int dx, int dy, int dz) {
    }

    public static class MotionRenderState extends EntityRenderState {
        public final List<Entry> entries = new ArrayList<>();
        public int sx;
        public int sy;
        public int sz;
        public int skippedSpecial;
        public float offsetX;
        public float offsetY;
        public float offsetZ;
        public float rotationX;
        public float rotationY;
        public float rotationZ;
        public float pivotX;
        public float pivotY;
        public float pivotZ;
    }

    public MotionEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.5F;
    }

    @Override
    public MotionRenderState createRenderState() {
        return new MotionRenderState();
    }

    @Override
    public void extractRenderState(MultiblockMotionEntity entity, MotionRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.entries.clear();
        state.skippedSpecial = 0;
        state.entityType = entity.getType();
        StructureSnapshot snapshot = entity.structure();
        state.sx = snapshot.sx();
        state.sy = snapshot.sy();
        state.sz = snapshot.sz();
        if (snapshot.isEmpty()) {
            return;
        }

        MultiblockMotion.Transform transform = entity.motion().sample(
                entity.elapsedTicks(), snapshot.sx(), snapshot.sy(), snapshot.sz());
        state.offsetX = (float) transform.offset().x();
        state.offsetY = (float) transform.offset().y();
        state.offsetZ = (float) transform.offset().z();
        state.rotationX = (float) transform.rotation().x();
        state.rotationY = (float) transform.rotation().y();
        state.rotationZ = (float) transform.rotation().z();
        state.pivotX = (float) (transform.pivot().x() - snapshot.sx() / 2.0D);
        state.pivotY = (float) transform.pivot().y();
        state.pivotZ = (float) (transform.pivot().z() - snapshot.sz() / 2.0D);

        if (!(entity.level() instanceof ClientLevel level)) {
            return;
        }
        for (PartEntry part : snapshot.parts()) {
            BlockState blockState = part.state();
            if (blockState.isAir()) {
                continue;
            }
            if (blockState.getRenderShape() != RenderShape.MODEL) {
                state.skippedSpecial++;
                continue;
            }
            MovingBlockRenderState moving = new MovingBlockRenderState();
            BlockPos bp = BlockPos.containing(
                    entity.getX() + (part.dx() - snapshot.sx() / 2.0D) + state.offsetX,
                    entity.getY() + part.dy() + state.offsetY,
                    entity.getZ() + (part.dz() - snapshot.sz() / 2.0D) + state.offsetZ);
            moving.blockState = blockState;
            moving.blockPos = bp;
            moving.randomSeedPos = bp;
            moving.biome = level.getBiome(bp);
            moving.cardinalLighting = level.cardinalLighting();
            moving.lightEngine = level.getLightEngine();
            state.entries.add(new Entry(moving, part.dx(), part.dy(), part.dz()));
        }
    }

    @Override
    public void submit(MotionRenderState state, PoseStack poseStack,
            SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(state.offsetX, state.offsetY, state.offsetZ);
        poseStack.translate(state.pivotX, state.pivotY, state.pivotZ);
        poseStack.mulPose(new Quaternionf()
                .rotateX((float) Math.toRadians(state.rotationX))
                .rotateY((float) Math.toRadians(state.rotationY))
                .rotateZ((float) Math.toRadians(state.rotationZ)));
        poseStack.translate(-state.pivotX, -state.pivotY, -state.pivotZ);
        for (Entry entry : state.entries) {
            poseStack.pushPose();
            poseStack.translate(entry.dx() - state.sx / 2.0F, entry.dy(), entry.dz() - state.sz / 2.0F);
            collector.submitMovingBlock(poseStack, entry.block());
            poseStack.popPose();
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    protected AABB getBoundingBoxForCulling(MultiblockMotionEntity entity) {
        return entity.getBoundingBox().inflate(8.0);
    }
}
