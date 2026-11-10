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
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlockEntity;
import com.Infinity.Nexus.Multiblock.data.MultiblockDefinitions;
import com.Infinity.Nexus.Multiblock.registry.ModDataComponents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
    private static final long PREVIEW_CACHE_MILLIS = 100L;

    private static final Map<BlockMatcher, Material> materials = new IdentityHashMap<>();
    private static @Nullable MultiblockDefinition materialDefinition;
    private static Rotation materialRotation = Rotation.NONE;
    private static int materialInventoryRevision = Integer.MIN_VALUE;
    private static boolean materialInstabuild;

    private static @Nullable ModelBlockRenderer blockRenderer;
    private static @Nullable Preview cachedPreview;
    private static @Nullable PreviewKey cachedPreviewKey;
    private static long cachedPreviewAt;
    private static @Nullable Placement pinned;

    private MultiblockPreviewRenderer() {}

    public static void clearCaches() {
        materials.clear();
        materialDefinition = null;
        materialInventoryRevision = Integer.MIN_VALUE;
        cachedPreview = null;
        cachedPreviewKey = null;
        pinned = null;
    }

    public static void onAttack() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.screen != null || player.isSpectator()) {
            return;
        }
        if (pinned != null) {
            pinned = null;
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        HitResult hitResult = player.pick(player.blockInteractionRange() + 20, partialTick, false);
        Placement placement = resolvePlacement(player, level, hitResult, partialTick);
        if (placement != null) {
            pinned = placement;
        }
    }

    public static void onSubmit(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.screen != null || player.isSpectator()) {
            return;
        }

        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        HitResult hitResult = player.pick(player.blockInteractionRange()+20, partialTick, false);
        BlockStateModelSet modelSet = minecraft.getModelManager().getBlockStateModelSet();
        Placement placement = resolvePlacement(player, level, hitResult, partialTick);
        if (pinned != null && !validPin(player, level)) {
            pinned = null;
        }
        if (pinned != null) {
            placement = pinned;
        }
        if (placement == null) {
            return;
        }
        Preview preview = previewFor(level, player, placement, modelSet);
        if (preview == null || preview.cells().isEmpty()) {
            return;
        }

        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();

        event.getSubmitNodeCollector().submitCustomGeometry(
                poseStack,
                RenderTypes.translucentMovingBlock(),
                (pose, buffer) -> renderModels(pose, buffer, preview.cells(), camera));
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

    private static @Nullable Placement resolvePlacement(LocalPlayer player, ClientLevel level, HitResult hitResult,
            float partialTick) {
        if (hitResult instanceof BlockHitResult blockHit) {
            BlockState hitState = level.getBlockState(blockHit.getBlockPos());
            if (hitState.getBlock() instanceof MultiblockControllerBlock
                    && !hitState.getValue(MultiblockControllerBlock.FORMED)
                    && level.getBlockEntity(blockHit.getBlockPos())
                            instanceof MultiblockControllerBlockEntity blockEntity) {
                MultiblockDefinition definition = blockEntity.definition();
                if (definition == null) {
                    return null;
                }
                return new Placement(level.dimension(), definition.id(), blockHit.getBlockPos(),
                        MultiblockControllerBlock.rotationFromState(hitState), hitState, true);
            }
        }

        ItemStack held = heldController(player);
        if (held == null) {
            return null;
        }
        Identifier definitionId = held.get(ModDataComponents.MULTIBLOCK_DEFINITION.get());
        if (definitionId == null || MultiblockDefinitions.get(definitionId) == null) {
            return null;
        }
        MultiblockControllerBlock controller =
                (MultiblockControllerBlock) ((BlockItem) held.getItem()).getBlock();

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
        return new Placement(level.dimension(), definitionId, controllerPos, rotation, anchorState, false);
    }

    private static boolean validPin(LocalPlayer player, ClientLevel level) {
        Placement pin = pinned;
        if (pin == null || !pin.dimension().equals(level.dimension())
                || MultiblockDefinitions.get(pin.definitionId()) == null) {
            return false;
        }
        ItemStack held = heldController(player);
        return held != null && pin.definitionId().equals(held.get(ModDataComponents.MULTIBLOCK_DEFINITION.get()));
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

    private static @Nullable Preview previewFor(ClientLevel level, LocalPlayer player, Placement placement,
            BlockStateModelSet modelSet) {
        MultiblockDefinition definition = MultiblockDefinitions.get(placement.definitionId());
        if (definition == null) {
            return null;
        }
        PreviewKey key = new PreviewKey(level, definition, placement.controllerPos().immutable(),
                placement.rotation(), placement.placed(), placement.anchorState(), modelSet,
                player.getInventory().getTimesChanged(), player.getAbilities().instabuild);
        long now = System.currentTimeMillis();
        if (cachedPreview != null && key.equals(cachedPreviewKey)
                && now - cachedPreviewAt < PREVIEW_CACHE_MILLIS) {
            return cachedPreview;
        }
        Preview preview = buildPreview(level, player, definition, placement.controllerPos(), placement.rotation(),
                placement.anchorState(), placement.placed(), modelSet);
        cachedPreview = preview;
        cachedPreviewKey = key;
        cachedPreviewAt = now;
        return preview;
    }

    private static Preview buildPreview(ClientLevel level, LocalPlayer player, MultiblockDefinition definition,
            BlockPos controllerPos, Rotation rotation, BlockState anchorState, boolean placed,
            BlockStateModelSet modelSet) {
        int inventoryRevision = player.getInventory().getTimesChanged();
        boolean instabuild = player.getAbilities().instabuild;
        if (materialDefinition != definition || materialRotation != rotation
                || materialInventoryRevision != inventoryRevision || materialInstabuild != instabuild) {
            materials.clear();
            materialDefinition = definition;
            materialRotation = rotation;
            materialInventoryRevision = inventoryRevision;
            materialInstabuild = instabuild;
        }

        BlockPos origin = definition.origin(controllerPos, rotation);
        List<PreviewCell> cells = new ArrayList<>();

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
                Material material = materials.get(matcher);
                if (material == null) {
                    BlockState found = MultiblockAssemblyHelper.findPlacementState(player, matcher, rotation);
                    boolean hasItem = found != null || instabuild;
                    BlockState expected = found != null
                            ? found
                            : MultiblockAssemblyHelper.representativeState(matcher, rotation);
                    material = new Material(expected, hasItem);
                    materials.put(matcher, material);
                }
                if (material.hasItem()) {
                    fill = PLACE_FILL;
                    line = PLACE_LINE;
                    alpha = PLACE_ALPHA;
                } else {
                    fill = MISSING_FILL;
                    line = MISSING_LINE;
                    alpha = MISSING_ALPHA;
                }
                modelState = material.state();
                fallbackFill = modelState == null;
            }

            List<GhostQuad> quads = modelState != null && alpha > 0
                    ? tessellate(level, pos, modelState, modelSet)
                    : List.of();
            cells.add(new PreviewCell(pos, quads, fill, line, alpha, fallbackFill));
        }
        return new Preview(cells);
    }

    private static List<GhostQuad> tessellate(ClientLevel level, BlockPos pos, BlockState state,
            BlockStateModelSet modelSet) {
        List<GhostQuad> quads = new ArrayList<>();
        BlockStateModel model = modelSet.get(state);
        ModelBlockRenderer renderer = blockRenderer(Minecraft.getInstance());
        renderer.tesselateBlock(
                (offsetX, offsetY, offsetZ, quad, instance) ->
                        quads.add(new GhostQuad(offsetX, offsetY, offsetZ, quad, instance.getColor(0))),
                0.0F, 0.0F, 0.0F, level, pos, state, model, state.getSeed(pos));
        return quads;
    }

    private static void renderModels(PoseStack.Pose pose, VertexConsumer buffer, List<PreviewCell> cells, Vec3 camera) {
        PoseStack stack = new PoseStack();
        stack.last().set(pose);
        QuadInstance instance = new QuadInstance();
        instance.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);

        for (PreviewCell cell : cells) {
            if (cell.quads().isEmpty()) {
                continue;
            }

            int alphaColor = ARGB.color(cell.alpha(), 0xFF, 0xFF, 0xFF);
            stack.pushPose();
            stack.translate(cell.pos().getX() - camera.x, cell.pos().getY() - camera.y, cell.pos().getZ() - camera.z);
            for (GhostQuad ghost : cell.quads()) {
                instance.setColor(ghost.color());
                instance.multiplyColor(alphaColor);
                if (ghost.x() == 0.0F && ghost.y() == 0.0F && ghost.z() == 0.0F) {
                    buffer.putBakedQuad(stack.last(), ghost.quad(), instance);
                } else {
                    stack.pushPose();
                    stack.translate(ghost.x(), ghost.y(), ghost.z());
                    buffer.putBakedQuad(stack.last(), ghost.quad(), instance);
                    stack.popPose();
                }
            }
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

    private record Placement(ResourceKey<Level> dimension, Identifier definitionId, BlockPos controllerPos,
            Rotation rotation, BlockState anchorState, boolean placed) {}

    private record PreviewKey(ClientLevel level, MultiblockDefinition definition, BlockPos controllerPos,
            Rotation rotation, boolean placed, BlockState anchorState, BlockStateModelSet modelSet,
            int inventoryRevision, boolean instabuild) {}

    private record PreviewCell(BlockPos pos, List<GhostQuad> quads, int fill, int line, int alpha, boolean fallbackFill) {}

    private record GhostQuad(float x, float y, float z, BakedQuad quad, int color) {}

    private record Material(@Nullable BlockState state, boolean hasItem) {}
}
