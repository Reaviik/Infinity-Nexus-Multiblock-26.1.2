package com.Infinity.Nexus.Multiblock.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.Infinity.Nexus.Multiblock.api.BlockMatcher;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockPattern;
import com.Infinity.Nexus.Multiblock.block.MultiblockAssemblyHelper;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;

public final class MultiblockPreviewRenderer {
    private static final int MATCH_FILL = ARGB.color(0x40, 0x36, 0xC7, 0x5A);
    private static final int MATCH_LINE = ARGB.color(0xCC, 0x4A, 0xE0, 0x70);
    private static final int PLACE_FILL = ARGB.color(0x40, 0xFF, 0xC1, 0x07);
    private static final int PLACE_LINE = ARGB.color(0xCC, 0xFF, 0xD5, 0x4A);
    private static final int MISSING_FILL = ARGB.color(0x40, 0xFF, 0x7A, 0x1A);
    private static final int MISSING_LINE = ARGB.color(0xCC, 0xFF, 0x9F, 0x4A);
    private static final int BLOCKED_FILL = ARGB.color(0x4D, 0xE5, 0x39, 0x35);
    private static final int BLOCKED_LINE = ARGB.color(0xDD, 0xFF, 0x5A, 0x50);
    private static final int ANCHOR_FILL = ARGB.color(0x40, 0x29, 0xB6, 0xF6);
    private static final int ANCHOR_LINE = ARGB.color(0xCC, 0x67, 0xD4, 0xFF);
    private static final int PLACE_ALPHA = 0x66;
    private static final int MISSING_ALPHA = 0x66;
    private static final int ANCHOR_ALPHA = 0x99;
    private static final float LINE_WIDTH = 2.0F;

    private static @Nullable ModelBlockRenderer blockRenderer;

    private MultiblockPreviewRenderer() {}

    public static void onSubmit(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.screen != null || player.isSpectator()) {
            return;
        }

        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        HitResult hitResult = player.pick(player.blockInteractionRange()+20, partialTick, false);
        Preview preview = resolvePreview(player, level, hitResult, partialTick);
        if (preview == null || preview.cells().isEmpty()) {
            return;
        }

        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        BlockStateModelSet modelSet = minecraft.getModelManager().getBlockStateModelSet();
        ModelBlockRenderer renderer = blockRenderer(minecraft);

        event.getSubmitNodeCollector().submitCustomGeometry(
                poseStack,
                RenderTypes.translucentMovingBlock(),
                (pose, buffer) -> renderModels(pose, buffer, preview.cells(), level, camera, renderer, modelSet));
        event.getSubmitNodeCollector().order(1).submitCustomGeometry(
                poseStack,
                RenderTypes.debugQuads(),
                (pose, buffer) -> renderFill(pose, buffer, preview.cells(), camera));
        event.getSubmitNodeCollector().order(1).submitCustomGeometry(
                poseStack,
                RenderTypes.linesTranslucent(),
                (pose, buffer) -> renderOutline(pose, buffer, preview.cells(), camera));
    }

    private static ModelBlockRenderer blockRenderer(Minecraft minecraft) {
        if (blockRenderer == null) {
            blockRenderer = new ModelBlockRenderer(false, false, minecraft.getBlockColors());
        }
        return blockRenderer;
    }

    private static @Nullable Preview resolvePreview(LocalPlayer player, ClientLevel level, HitResult hitResult, float partialTick) {
        if (hitResult instanceof BlockHitResult blockHit) {
            BlockState hitState = level.getBlockState(blockHit.getBlockPos());
            if (hitState.getBlock() instanceof MultiblockControllerBlock controller
                    && !hitState.getValue(MultiblockControllerBlock.FORMED)) {
                MultiblockDefinition definition = controller.definition();
                if (definition == null) {
                    return null;
                }
                return buildPreview(level, player, definition, blockHit.getBlockPos(),
                        MultiblockControllerBlock.rotationFromState(hitState), hitState, true);
            }
        }

        ItemStack held = heldController(player);
        if (held == null) {
            return null;
        }
        MultiblockControllerBlock controller = (MultiblockControllerBlock) ((BlockItem) held.getItem()).getBlock();
        MultiblockDefinition definition = controller.definition();
        if (definition == null) {
            return null;
        }

        BlockPos controllerPos;
        if (hitResult instanceof BlockHitResult blockHit) {
            BlockPos hitPos = blockHit.getBlockPos();
            BlockState hitState = level.getBlockState(hitPos);
            controllerPos = hitState.canBeReplaced() ? hitPos : hitPos.relative(blockHit.getDirection());
        } else {
            Vec3 target = player.getEyePosition(partialTick)
                    .add(player.getViewVector(partialTick).scale(player.blockInteractionRange()));
            controllerPos = BlockPos.containing(target);
        }
        BlockState anchorState = controller.defaultBlockState()
                .setValue(MultiblockControllerBlock.FACING, player.getDirection().getOpposite());
        Rotation rotation = MultiblockControllerBlock.rotationFromFacing(player.getDirection().getOpposite());
        return buildPreview(level, player, definition, controllerPos, rotation, anchorState, false);
    }

    private static @Nullable ItemStack heldController(LocalPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (isController(mainHand)) {
            return mainHand;
        }
        ItemStack offHand = player.getOffhandItem();
        return isController(offHand) ? offHand : null;
    }

    private static boolean isController(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof MultiblockControllerBlock;
    }

    private static Preview buildPreview(ClientLevel level, LocalPlayer player, MultiblockDefinition definition,
            BlockPos controllerPos, Rotation rotation, BlockState anchorState, boolean placed) {
        BlockPos origin = definition.origin(controllerPos, rotation);
        List<PreviewCell> cells = new ArrayList<>();
        Map<BlockMatcher, BlockState> expectedStates = new IdentityHashMap<>();
        Map<BlockMatcher, Boolean> itemAvailability = new IdentityHashMap<>();

        for (MultiblockPattern.PatternCell cell : definition.pattern().cells()) {
            BlockPos offset = new BlockPos(cell.x(), cell.y(), cell.z()).rotate(rotation);
            BlockPos pos = origin.offset(offset.getX(), offset.getY(), offset.getZ());
            BlockState state = level.getBlockState(pos);
            BlockState modelState = null;
            int fill;
            int line;
            int alpha = 0;
            boolean fallbackFill = false;

            if (cell.anchor()) {
                boolean validAnchor = placed || state.canBeReplaced();
                fill = validAnchor ? ANCHOR_FILL : BLOCKED_FILL;
                line = validAnchor ? ANCHOR_LINE : BLOCKED_LINE;
                if (!placed) {
                    modelState = anchorState;
                    alpha = ANCHOR_ALPHA;
                }
            } else if (cell.matcher().matches(state, rotation)) {
                fill = MATCH_FILL;
                line = MATCH_LINE;
            } else if (!state.canBeReplaced()) {
                fill = BLOCKED_FILL;
                line = BLOCKED_LINE;
            } else {
                BlockMatcher matcher = cell.matcher();
                BlockState expected = expectedStates.get(matcher);
                Boolean hasItem = itemAvailability.get(matcher);
                if (hasItem == null) {
                    BlockState found = MultiblockAssemblyHelper.findPlacementState(player, matcher, rotation);
                    hasItem = found != null || player.getAbilities().instabuild;
                    expected = found != null ? found : MultiblockAssemblyHelper.representativeState(matcher, rotation);
                    expectedStates.put(matcher, expected);
                    itemAvailability.put(matcher, hasItem);
                }
                if (hasItem) {
                    fill = PLACE_FILL;
                    line = PLACE_LINE;
                    alpha = PLACE_ALPHA;
                } else {
                    fill = MISSING_FILL;
                    line = MISSING_LINE;
                    alpha = MISSING_ALPHA;
                }
                modelState = expected;
                fallbackFill = modelState == null;
            }

            cells.add(new PreviewCell(pos, modelState, fill, line, alpha, fallbackFill));
        }
        return new Preview(cells);
    }

    private static void renderModels(PoseStack.Pose pose, VertexConsumer buffer, List<PreviewCell> cells,
            ClientLevel level, Vec3 camera, ModelBlockRenderer renderer, BlockStateModelSet modelSet) {
        PoseStack stack = new PoseStack();
        stack.last().set(pose);

        for (PreviewCell cell : cells) {
            BlockState state = cell.modelState();
            if (state == null || cell.alpha() <= 0) {
                continue;
            }

            BlockStateModel model = modelSet.get(state);
            int alphaColor = ARGB.color(cell.alpha(), 0xFF, 0xFF, 0xFF);
            stack.pushPose();
            stack.translate(cell.pos().getX() - camera.x, cell.pos().getY() - camera.y, cell.pos().getZ() - camera.z);
            renderer.tesselateBlock((offsetX, offsetY, offsetZ, quad, instance) -> {
                stack.pushPose();
                stack.translate(offsetX, offsetY, offsetZ);
                instance.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
                instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
                instance.multiplyColor(alphaColor);
                buffer.putBakedQuad(stack.last(), quad, instance);
                stack.popPose();
            }, 0.0F, 0.0F, 0.0F, level, cell.pos(), state, model, state.getSeed(cell.pos()));
            stack.popPose();
        }
    }

    private static void renderFill(PoseStack.Pose pose, VertexConsumer buffer, List<PreviewCell> cells, Vec3 camera) {
        for (PreviewCell cell : cells) {
            if (!cell.fallbackFill()) {
                continue;
            }
            double x0 = cell.pos().getX() - camera.x + 0.002;
            double y0 = cell.pos().getY() - camera.y + 0.002;
            double z0 = cell.pos().getZ() - camera.z + 0.002;
            double x1 = cell.pos().getX() + 0.998 - camera.x;
            double y1 = cell.pos().getY() + 0.998 - camera.y;
            double z1 = cell.pos().getZ() + 0.998 - camera.z;
            int color = cell.fill();

            quad(buffer, pose, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, color);
            quad(buffer, pose, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, color);
            quad(buffer, pose, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, color);
            quad(buffer, pose, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, color);
            quad(buffer, pose, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, color);
            quad(buffer, pose, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, color);
        }
    }

    private static void renderOutline(PoseStack.Pose pose, VertexConsumer buffer, List<PreviewCell> cells, Vec3 camera) {
        for (PreviewCell cell : cells) {
            double x0 = cell.pos().getX() - camera.x + 0.003;
            double y0 = cell.pos().getY() - camera.y + 0.003;
            double z0 = cell.pos().getZ() - camera.z + 0.003;
            double x1 = cell.pos().getX() + 0.997 - camera.x;
            double y1 = cell.pos().getY() + 0.997 - camera.y;
            double z1 = cell.pos().getZ() + 0.997 - camera.z;
            int color = cell.line();

            line(buffer, pose, x0, y0, z0, x1, y0, z0, color);
            line(buffer, pose, x1, y0, z0, x1, y0, z1, color);
            line(buffer, pose, x1, y0, z1, x0, y0, z1, color);
            line(buffer, pose, x0, y0, z1, x0, y0, z0, color);
            line(buffer, pose, x0, y1, z0, x1, y1, z0, color);
            line(buffer, pose, x1, y1, z0, x1, y1, z1, color);
            line(buffer, pose, x1, y1, z1, x0, y1, z1, color);
            line(buffer, pose, x0, y1, z1, x0, y1, z0, color);
            line(buffer, pose, x0, y0, z0, x0, y1, z0, color);
            line(buffer, pose, x1, y0, z0, x1, y1, z0, color);
            line(buffer, pose, x1, y0, z1, x1, y1, z1, color);
            line(buffer, pose, x0, y0, z1, x0, y1, z1, color);
        }
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose,
            double x0, double y0, double z0, double x1, double y1, double z1,
            double x2, double y2, double z2, double x3, double y3, double z3, int color) {
        vertex(buffer, pose, x0, y0, z0, color);
        vertex(buffer, pose, x1, y1, z1, color);
        vertex(buffer, pose, x2, y2, z2, color);
        vertex(buffer, pose, x3, y3, z3, color);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z, int color) {
        buffer.addVertex(pose, (float) x, (float) y, (float) z).setColor(color);
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose,
            double x0, double y0, double z0, double x1, double y1, double z1, int color) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = length == 0.0 ? 0.0F : (float) (dx / length);
        float ny = length == 0.0 ? 0.0F : (float) (dy / length);
        float nz = length == 0.0 ? 0.0F : (float) (dz / length);

        buffer.addVertex(pose, (float) x0, (float) y0, (float) z0)
                .setColor(color)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(LINE_WIDTH);
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(color)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(LINE_WIDTH);
    }

    private record Preview(List<PreviewCell> cells) {}

    private record PreviewCell(BlockPos pos, @Nullable BlockState modelState, int fill, int line, int alpha, boolean fallbackFill) {}
}
