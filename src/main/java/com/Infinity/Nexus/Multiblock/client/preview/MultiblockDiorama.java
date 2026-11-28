package com.Infinity.Nexus.Multiblock.client.preview;

import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockPattern;
import com.Infinity.Nexus.Multiblock.api.MultiblockTextures;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlock;
import com.Infinity.Nexus.Multiblock.client.model.ControllerBlockStateModel;
import com.mojang.blaze3d.platform.Lighting.Entry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class MultiblockDiorama {
    private static final int LIGHT = 0x00F000F0;
    private static final float TILT = 30.0F;

    private MultiblockDiorama() {}

    public static void register(RegisterPictureInPictureRenderersEvent event) {
        event.register(State.class, Renderer::new);
    }

    public static void draw(
            GuiGraphicsExtractor graphics,
            MultiblockStructureData structure,
            int layer,
            float time,
            float yaw,
            int x0,
            int y0,
            int x1,
            int y1,
            boolean boosterView) {
        Vector2f a = graphics.pose().transformPosition(x0, y0, new Vector2f());
        Vector2f b = graphics.pose().transformPosition(x1, y1, new Vector2f());
        int ax = Math.round(a.x);
        int ay = Math.round(a.y);
        int bx = Math.round(b.x);
        int by = Math.round(b.y);
        Bounds bounds = bounds(structure, boosterView);
        float span = bounds.spanXZ();
        float tall = bounds.tall();
        float scale = Math.min(
                (bx - ax) / (span * 1.05F),
                (by - ay) / (tall * 1.06F + 0.42F));
        graphics.submitPictureInPictureRenderState(new State(
                structure,
                layer,
                time,
                yaw,
                ax,
                ay,
                bx,
                by,
                scale,
                graphics.peekScissorStack(),
                boosterView));
    }

    private static List<MultiblockStructureData.Cell> viewCells(
            MultiblockStructureData structure,
            boolean boosterView) {
        return boosterView ? structure.boosterCells() : structure.cells();
    }

    private static Bounds bounds(MultiblockStructureData structure, boolean boosterView) {
        if (boosterView) {
            return Bounds.of(structure.boosterCells());
        }
        MultiblockPattern pattern = structure.definition().pattern();
        return new Bounds(0, pattern.sizeX() - 1, 0, pattern.sizeY() - 1, 0, pattern.sizeZ() - 1);
    }

    private record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        static Bounds of(List<MultiblockStructureData.Cell> cells) {
            if (cells.isEmpty()) {
                return new Bounds(0, 0, 0, 0, 0, 0);
            }
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (MultiblockStructureData.Cell cell : cells) {
                minX = Math.min(minX, cell.x());
                maxX = Math.max(maxX, cell.x());
                minY = Math.min(minY, cell.y());
                maxY = Math.max(maxY, cell.y());
                minZ = Math.min(minZ, cell.z());
                maxZ = Math.max(maxZ, cell.z());
            }
            return new Bounds(minX, maxX, minY, maxY, minZ, maxZ);
        }

        float centerX() {
            return (this.minX + this.maxX + 1) / 2.0F;
        }

        float centerY() {
            return (this.minY + this.maxY + 1) / 2.0F;
        }

        float centerZ() {
            return (this.minZ + this.maxZ + 1) / 2.0F;
        }

        float spanXZ() {
            float sizeX = this.maxX - this.minX + 1;
            float sizeZ = this.maxZ - this.minZ + 1;
            return Mth.sqrt(sizeX * sizeX + sizeZ * sizeZ);
        }

        float tall() {
            float sizeY = this.maxY - this.minY + 1;
            return sizeY * Mth.cos((float) (Math.PI / 6))
                    + spanXZ() * Mth.sin((float) (Math.PI / 6));
        }
    }

    private static @Nullable BlockState previewState(
            MultiblockStructureData.Cell cell,
            MultiblockPattern pattern,
            float time) {
        BlockState state = null;
        if (!cell.states().isEmpty()) {
            state = cell.states().get((int) (time / 20.0F) % cell.states().size());
        } else if (!cell.stacks().isEmpty()) {
            ItemStack stack = shownStack(cell, time);
            if (stack.getItem() instanceof BlockItem item) {
                state = item.getBlock().defaultBlockState();
            }
        }
        if (state == null) {
            return null;
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction out = cell.z() == pattern.sizeZ() - 1
                    ? Direction.SOUTH
                    : cell.z() == 0
                            ? Direction.NORTH
                            : cell.x() == 0
                                    ? Direction.WEST
                                    : cell.x() == pattern.sizeX() - 1
                                            ? Direction.EAST
                                            : Direction.SOUTH;
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, out);
        }
        return state;
    }

    private static ItemStack shownStack(MultiblockStructureData.Cell cell, float time) {
        if (cell.stacks().isEmpty()) {
            return ItemStack.EMPTY;
        }
        return cell.stacks().get((int) (time / 20.0F) % cell.stacks().size());
    }

    static final class Renderer extends PictureInPictureRenderer<State> {
        private final Map<BlockState, BlockModelRenderState> blocks = new HashMap<>();
        private final Map<ItemStack, ItemStackRenderState> items = new IdentityHashMap<>();
        private final BlockDisplayContext context = BlockDisplayContext.create();
        private @Nullable BlockModelSet blockModels;
        private @Nullable MultiblockStructureData itemStructure;
        private @Nullable MultiblockStructureData renderedStructure;
        private int renderedLayer = Integer.MIN_VALUE;
        private float renderedYaw = Float.NaN;
        private float renderedScale = Float.NaN;
        private float renderedTime = Float.NaN;
        private boolean renderedBoosterView;

        Renderer(BufferSource portBuffers) {
            super(portBuffers);
        }

        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected void renderToTexture(State state, PoseStack poseStack) {
            FeatureRenderDispatcher features =
                    Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
            renderToTexture(state, poseStack, features.getSubmitNodeStorage());
            features.renderAllFeatures();
            this.renderedStructure = state.structure();
            this.renderedLayer = state.layer();
            this.renderedYaw = state.yaw();
            this.renderedScale = state.scale();
            this.renderedTime = state.time();
            this.renderedBoosterView = state.boosterView();
        }

        @Override
        protected boolean textureIsReadyToBlit(State state) {
            return this.renderedStructure == state.structure()
                    && this.renderedLayer == state.layer()
                    && this.renderedYaw == state.yaw()
                    && this.renderedScale == state.scale()
                    && this.renderedBoosterView == state.boosterView()
                    && Math.abs(state.time() - this.renderedTime) < 1.0F;
        }

        private void renderToTexture(State state, PoseStack poseStack, SubmitNodeCollector collector) {
            Minecraft minecraft = Minecraft.getInstance();
            MultiblockStructureData structure = state.structure();
            if (this.itemStructure != structure) {
                this.items.clear();
                this.itemStructure = structure;
            }
            MultiblockPattern pattern = structure.definition().pattern();
            minecraft.gameRenderer.getLighting().setupFor(Entry.ITEMS_3D);

            float time = state.time();
            float yaw = time * 0.5F + TILT + state.yaw();
            Bounds bounds = bounds(structure, state.boosterView());
            poseStack.scale(1.0F, -1.0F, -1.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(TILT));
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());

            for (MultiblockStructureData.Cell cell : viewCells(structure, state.boosterView())) {
                if (!state.boosterView() && state.layer() >= 0 && cell.y() > state.layer()) {
                    continue;
                }
                BlockState blockState = previewState(cell, pattern, time);
                if (blockState != null) {
                    if (cell.anchor() && blockState.getBlock() instanceof MultiblockControllerBlock) {
                        this.renderController(
                                poseStack, collector, blockState, cell, structure.definition());
                    } else {
                        this.renderBlock(poseStack, collector, blockState, cell);
                    }
                } else {
                    ItemStack stack = shownStack(cell, time);
                    if (!stack.isEmpty()) {
                        this.renderItem(poseStack, collector, stack);
                    }
                }
            }
        }

        private void renderController(
                PoseStack poseStack,
                SubmitNodeCollector collector,
                BlockState state,
                MultiblockStructureData.Cell cell,
                MultiblockDefinition definition) {
            BlockStateModel model = Minecraft.getInstance()
                    .getModelManager()
                    .getBlockStateModelSet()
                    .get(state);
            if (!(model instanceof ControllerBlockStateModel controller)) {
                this.renderBlock(poseStack, collector, state, cell);
                return;
            }
            BlockStateModelPart part = controller.part(definition, MultiblockTextures.State.IDLE);
            poseStack.pushPose();
            poseStack.translate(cell.x() + 0.5F, cell.y() + 0.5F, cell.z() + 0.5F);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            collector.submitBlockModel(
                    poseStack,
                    (part.materialFlags() & 1) != 0
                            ? Sheets.translucentBlockSheet()
                            : Sheets.cutoutBlockSheet(),
                    List.of(part),
                    BlockModelRenderState.EMPTY_TINTS,
                    LIGHT,
                    OverlayTexture.NO_OVERLAY,
                    0);
            poseStack.popPose();
        }

        private void renderBlock(
                PoseStack poseStack,
                SubmitNodeCollector collector,
                BlockState state,
                MultiblockStructureData.Cell cell) {
            Minecraft minecraft = Minecraft.getInstance();
            BlockModelSet modelSet = minecraft.getModelManager().getBlockModelSet();
            if (modelSet != this.blockModels) {
                this.blocks.clear();
                this.blockModels = modelSet;
            }
            BlockModelRenderState block = this.blocks.get(state);
            if (block == null) {
                block = new BlockModelRenderState();
                minecraft.getBlockModelResolver().update(block, state, this.context);
                this.blocks.put(state, block);
            }
            poseStack.pushPose();
            poseStack.translate(cell.x() + 0.5F, cell.y() + 0.5F, cell.z() + 0.5F);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            block.submitMultiLayer(
                    poseStack,
                    collector,
                    LIGHT,
                    OverlayTexture.NO_OVERLAY,
                    0);
            poseStack.popPose();
        }

        private void renderItem(
                PoseStack poseStack,
                SubmitNodeCollector collector,
                ItemStack stack) {
            Minecraft minecraft = Minecraft.getInstance();
            ItemStackRenderState item = this.items.get(stack);
            if (item == null) {
                item = new ItemStackRenderState();
                this.updateItem(minecraft, item, stack);
                this.items.put(stack, item);
            } else if (item.isAnimated()) {
                this.updateItem(minecraft, item, stack);
            }
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            item.submit(poseStack, collector, LIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        private void updateItem(Minecraft minecraft, ItemStackRenderState item, ItemStack stack) {
            minecraft.getItemModelResolver()
                    .updateForTopItem(item, stack, ItemDisplayContext.NONE, minecraft.level, null, 0);
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2.0F;
        }

        @Override
        protected String getTextureLabel() {
            return "infinity nexus multiblock diorama";
        }
    }

    record State(
            MultiblockStructureData structure,
            int layer,
            float time,
            float yaw,
            int x0,
            int y0,
            int x1,
            int y1,
            float scale,
            boolean boosterView,
            @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {

        State(
                MultiblockStructureData structure,
                int layer,
                float time,
                float yaw,
                int x0,
                int y0,
                int x1,
                int y1,
                float scale,
                @Nullable ScreenRectangle scissorArea,
                boolean boosterView) {
            this(
                    structure,
                    layer,
                    time,
                    yaw,
                    x0,
                    y0,
                    x1,
                    y1,
                    scale,
                    boosterView,
                    scissorArea,
                    PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
        }
    }
}
