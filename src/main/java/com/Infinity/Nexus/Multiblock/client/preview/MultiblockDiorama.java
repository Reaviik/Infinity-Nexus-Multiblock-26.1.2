package com.Infinity.Nexus.Multiblock.client.preview;

import java.util.ArrayList;
import java.util.List;

import com.Infinity.Nexus.Multiblock.api.MultiblockPattern;
import com.mojang.blaze3d.platform.Lighting.Entry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
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
            int y1) {
        Vector2f a = graphics.pose().transformPosition(x0, y0, new Vector2f());
        Vector2f b = graphics.pose().transformPosition(x1, y1, new Vector2f());
        int ax = Math.round(a.x);
        int ay = Math.round(a.y);
        int bx = Math.round(b.x);
        int by = Math.round(b.y);
        MultiblockPattern pattern = structure.definition().pattern();
        float span = Mth.sqrt(pattern.sizeX() * pattern.sizeX() + pattern.sizeZ() * pattern.sizeZ());
        float tall = pattern.sizeY() * Mth.cos((float) (Math.PI / 6))
                + span * Mth.sin((float) (Math.PI / 6));
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
                graphics.peekScissorStack()));
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
        private final BlockModelRenderState block = new BlockModelRenderState();
        private final List<ItemStackRenderState> items = new ArrayList<>();
        private final BlockDisplayContext context = BlockDisplayContext.create();

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
        }

        private void renderToTexture(State state, PoseStack poseStack, SubmitNodeCollector collector) {
            Minecraft minecraft = Minecraft.getInstance();
            MultiblockStructureData structure = state.structure();
            MultiblockPattern pattern = structure.definition().pattern();
            minecraft.gameRenderer.getLighting().setupFor(Entry.ITEMS_3D);

            float time = state.time();
            float yaw = time * 0.5F + TILT + state.yaw();
            poseStack.scale(1.0F, -1.0F, -1.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(TILT));
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.translate(-pattern.sizeX() / 2.0F, -pattern.sizeY() / 2.0F, -pattern.sizeZ() / 2.0F);

            int itemIndex = 0;
            for (MultiblockStructureData.Cell cell : structure.cells()) {
                if (state.layer() >= 0 && cell.y() > state.layer()) {
                    continue;
                }
                BlockState blockState = previewState(cell, pattern, time);
                if (blockState != null) {
                    this.renderBlock(poseStack, collector, blockState, cell);
                } else {
                    ItemStack stack = shownStack(cell, time);
                    if (!stack.isEmpty()) {
                        itemIndex = this.renderItem(poseStack, collector, stack, itemIndex);
                    }
                }
            }
        }

        private void renderBlock(
                PoseStack poseStack,
                SubmitNodeCollector collector,
                BlockState state,
                MultiblockStructureData.Cell cell) {
            Minecraft minecraft = Minecraft.getInstance();
            poseStack.pushPose();
            poseStack.translate(cell.x() + 0.5F, cell.y() + 0.5F, cell.z() + 0.5F);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            minecraft.getBlockModelResolver().update(this.block, state, this.context);
            this.block.submitMultiLayer(
                    poseStack,
                    collector,
                    LIGHT,
                    OverlayTexture.NO_OVERLAY,
                    0);
            poseStack.popPose();
        }

        private int renderItem(
                PoseStack poseStack,
                SubmitNodeCollector collector,
                ItemStack stack,
                int itemIndex) {
            Minecraft minecraft = Minecraft.getInstance();
            if (itemIndex == this.items.size()) {
                this.items.add(new ItemStackRenderState());
            }
            ItemStackRenderState item = this.items.get(itemIndex);
            minecraft.getItemModelResolver()
                    .updateForTopItem(item, stack, ItemDisplayContext.NONE, minecraft.level, null, 0);
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            item.submit(poseStack, collector, LIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
            return itemIndex + 1;
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
                @Nullable ScreenRectangle scissorArea) {
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
                    scissorArea,
                    PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
        }
    }
}
