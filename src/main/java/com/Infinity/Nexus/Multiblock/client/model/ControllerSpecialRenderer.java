package com.Infinity.Nexus.Multiblock.client.model;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockModel;
import com.Infinity.Nexus.Multiblock.api.MultiblockTextures;
import com.Infinity.Nexus.Multiblock.data.MultiblockDefinitions;
import com.Infinity.Nexus.Multiblock.registry.ModDataComponents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.slf4j.Logger;

public final class ControllerSpecialRenderer implements SpecialModelRenderer<ControllerSpecialRenderer.ControllerAppearance> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier MODEL_ID =
            Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "block/multiblock_controller");

    private final BlockStateModelPart idlePart;
    private final ResolvedModel idleModel;
    private final ModelBaker modelBaker;
    private final Map<ItemPartKey, BlockStateModelPart> parts = new ConcurrentHashMap<>();

    private ControllerSpecialRenderer(BlockStateModelPart idlePart, ResolvedModel idleModel, ModelBaker modelBaker) {
        this.idlePart = idlePart;
        this.idleModel = idleModel;
        this.modelBaker = modelBaker;
    }

    @Override
    public ControllerAppearance extractArgument(ItemStack stack) {
        Identifier id = stack.get(ModDataComponents.MULTIBLOCK_DEFINITION.get());
        if (id == null) {
            return ControllerAppearance.EMPTY;
        }
        MultiblockDefinition definition = MultiblockDefinitions.get(id);
        return definition == null
                ? ControllerAppearance.EMPTY
                : new ControllerAppearance(definition.model(), definition.textures());
    }

    @Override
    public void submit(
            ControllerAppearance appearance,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            int overlay,
            boolean foil,
            int outlineColor) {
        BlockStateModelPart part = this.part(appearance);
        collector.submitBlockModel(
                poseStack,
                (part.materialFlags() & 1) != 0
                        ? Sheets.translucentBlockSheet()
                        : Sheets.cutoutBlockSheet(),
                List.of(part),
                BlockModelRenderState.EMPTY_TINTS,
                light,
                overlay,
                outlineColor);
    }

    @Override
    public void getExtents(java.util.function.Consumer<Vector3fc> consumer) {
        consumer.accept(new Vector3f(0.0F, 0.0F, 0.0F));
        consumer.accept(new Vector3f(1.0F, 1.0F, 1.0F));
    }

    private BlockStateModelPart part(ControllerAppearance appearance) {
        MultiblockModel customModel = appearance == null ? MultiblockModel.EMPTY : appearance.model();
        MultiblockTextures textures = appearance == null ? MultiblockTextures.EMPTY : appearance.textures();
        Identifier modelId = customModel.resolve(MultiblockTextures.State.IDLE).orElse(null);
        ResolvedModel model = modelId == null ? this.idleModel : MultiblockModelResolver.resolve(modelId);
        if (model == null) {
            model = this.idleModel;
        }
        if (model == null) {
            return this.idlePart;
        }
        Map<String, Identifier> overrides = textures.resolve(MultiblockTextures.State.IDLE);
        if (modelId == null && overrides.isEmpty()) {
            return this.idlePart;
        }

        ResolvedModel resolvedModel = model;
        return this.parts.computeIfAbsent(
                new ItemPartKey(modelId, overrides),
                key -> ControllerBlockStateModel.bakePart(
                        resolvedModel,
                        key.textures(),
                        this.idlePart,
                        this.modelBaker,
                        BlockModelRotation.IDENTITY));
    }

    public record ControllerAppearance(MultiblockModel model, MultiblockTextures textures) {
        public static final ControllerAppearance EMPTY =
                new ControllerAppearance(MultiblockModel.EMPTY, MultiblockTextures.EMPTY);
    }

    private record ItemPartKey(Identifier modelId, Map<String, Identifier> textures) {
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<ControllerAppearance> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        public static void register(RegisterSpecialModelRendererEvent event) {
            event.register(Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "controller"), MAP_CODEC);
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked<ControllerAppearance>> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<ControllerAppearance> bake(SpecialModelRenderer.BakingContext context) {
            if (!(context instanceof ItemModel.BakingContext itemContext)) {
                LOGGER.warn("Cannot bake the multiblock controller item model without an item model baking context");
                return null;
            }
            ModelBaker modelBaker = itemContext.blockModelBaker();
            ResolvedModel idleModel = modelBaker.getModel(MODEL_ID);
            BlockStateModelPart idlePart = SimpleModelWrapper.bake(modelBaker, MODEL_ID, BlockModelRotation.IDENTITY);
            return new ControllerSpecialRenderer(idlePart, idleModel, modelBaker);
        }
    }
}
