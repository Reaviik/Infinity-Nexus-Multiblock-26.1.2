package com.Infinity.Nexus.Multiblock.client.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockModel;
import com.Infinity.Nexus.Multiblock.api.MultiblockTextures;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlock;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlockEntity;
import com.Infinity.Nexus.Multiblock.data.MultiblockDefinitions;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import org.slf4j.Logger;

public final class ControllerBlockStateModel implements DynamicBlockStateModel {
    private static final String MODEL_TYPE = "controller";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BlockStateModelPart idlePart;
    private final BlockStateModelPart activePart;
    private final BlockStateModelPart buildingPart;
    private final ResolvedModel idleModel;
    private final ResolvedModel activeModel;
    private final ResolvedModel buildingModel;
    private final ModelBaker modelBaker;
    private final ModelState modelState;
    private final Map<PartKey, BlockStateModelPart> parts = new ConcurrentHashMap<>();

    private ControllerBlockStateModel(
            BlockStateModelPart idlePart,
            BlockStateModelPart activePart,
            BlockStateModelPart buildingPart,
            ResolvedModel idleModel,
            ResolvedModel activeModel,
            ResolvedModel buildingModel,
            ModelBaker modelBaker,
            ModelState modelState) {
        this.idlePart = idlePart;
        this.activePart = activePart;
        this.buildingPart = buildingPart;
        this.idleModel = idleModel;
        this.activeModel = activeModel;
        this.buildingModel = buildingModel;
        this.modelBaker = modelBaker;
        this.modelState = modelState;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        parts.add(this.part(level, pos, state));
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return this.part(level, pos, state).particleMaterial();
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return this.part(level, pos, state).materialFlags();
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        MultiblockDefinition definition = definition(level, pos);
        MultiblockTextures textures = definition == null ? MultiblockTextures.EMPTY : definition.textures();
        MultiblockTextures.State visual = visualState(state);
        Identifier modelId = definition == null ? null : definition.model().resolve(visual).orElse(null);
        return new GeometryKey(this, textures, visual, modelId);
    }

    private BlockStateModelPart part(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return part(definition(level, pos), visualState(state));
    }

    public BlockStateModelPart part(MultiblockDefinition definition, MultiblockTextures.State visual) {
        BlockStateModelPart base = switch (visual) {
            case IDLE -> this.idlePart;
            case ACTIVE -> this.activePart;
            case BUILDING -> this.buildingPart;
        };
        MultiblockTextures textures = definition == null ? MultiblockTextures.EMPTY : definition.textures();
        Identifier modelId = definition == null ? null : definition.model().resolve(visual).orElse(null);
        if (modelId == null && textures.isEmpty()) {
            return base;
        }

        Map<String, Identifier> overrides = textures.resolve(visual);
        if (modelId == null && overrides.isEmpty()) {
            return base;
        }
        return this.parts.computeIfAbsent(
                new PartKey(visual, modelId, overrides),
                key -> this.bakePart(key.visual(), key.modelId(), key.textures(), base));
    }

    private BlockStateModelPart bakePart(
            MultiblockTextures.State visual,
            Identifier modelId,
            Map<String, Identifier> overrides,
            BlockStateModelPart base) {
        ResolvedModel model = modelId == null ? this.model(visual) : MultiblockModelResolver.resolve(modelId);
        if (model == null) {
            return base;
        }
        return bakePart(model, overrides, base, this.modelBaker, this.modelState);
    }

    private ResolvedModel model(MultiblockTextures.State visual) {
        return switch (visual) {
            case IDLE -> this.idleModel;
            case ACTIVE -> this.activeModel;
            case BUILDING -> this.buildingModel;
        };
    }

    @SuppressWarnings("deprecation")
    public static BlockStateModelPart bakePart(
            ResolvedModel model,
            Map<String, Identifier> overrides,
            BlockStateModelPart base,
            ModelBaker modelBaker,
            ModelState modelState) {
        TextureSlots.Resolver resolver = new TextureSlots.Resolver();
        TextureSlots.Data.Builder builder = new TextureSlots.Data.Builder();
        overrides.forEach((slot, texture) -> builder.addTexture(slot, new Material(texture)));
        resolver.addFirst(builder.build());
        for (ResolvedModel current = model; current != null; current = current.parent()) {
            resolver.addLast(current.wrapped().textureSlots());
        }

        TextureSlots textureSlots = resolver.resolve(model);
        QuadCollection quads = model.getTopGeometry().bake(textureSlots, modelBaker, modelState, model);
        for (BakedQuad quad : quads.getAll()) {
            TextureAtlasSprite sprite = quad.materialInfo().sprite();
            if (!sprite.atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS)) {
                LOGGER.warn(
                        "Ignoring custom textures for multiblock controller model {}: {} is outside the block atlas",
                        model.debugName(),
                        sprite.contents().name());
                return base;
            }
        }
        if (quads.getAll().isEmpty()) {
            return base;
        }
        Material.Baked particle = ResolvedModel.resolveParticleMaterial(textureSlots, modelBaker, model);
        if (!particle.sprite().atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS)) {
            LOGGER.warn(
                    "Ignoring custom textures for multiblock controller model {}: particle {} is outside the block atlas",
                    model.debugName(),
                    particle.sprite().contents().name());
            return base;
        }
        return new SimpleModelWrapper(forceTranslucent(quads), model.getTopAmbientOcclusion(), particle);
    }

    private static QuadCollection forceTranslucent(QuadCollection quads) {
        QuadCollection.Builder builder = new QuadCollection.Builder();
        for (BakedQuad quad : quads.getQuads(null)) {
            builder.addUnculledFace(translucent(quad));
        }
        for (Direction direction : Direction.values()) {
            for (BakedQuad quad : quads.getQuads(direction)) {
                builder.addCulledFace(direction, translucent(quad));
            }
        }
        return builder.build();
    }

    private static BakedQuad translucent(BakedQuad quad) {
        BakedQuad.MaterialInfo info = quad.materialInfo();
        BakedQuad.MaterialInfo material = new BakedQuad.MaterialInfo(
                info.sprite(),
                ChunkSectionLayer.TRANSLUCENT,
                Sheets.translucentBlockItemSheet(),
                info.tintIndex(),
                info.shade(),
                info.lightEmission());
        return new BakedQuad(
                quad.position0(),
                quad.position1(),
                quad.position2(),
                quad.position3(),
                quad.packedUV0(),
                quad.packedUV1(),
                quad.packedUV2(),
                quad.packedUV3(),
                quad.direction(),
                material);
    }

    private static MultiblockDefinition definition(BlockAndTintGetter level, BlockPos pos) {
        Identifier id = level.getModelData(pos).get(MultiblockControllerBlockEntity.DEFINITION_PROPERTY);
        if (id == null && level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
            id = controller.definitionId();
        }
        return id == null ? null : MultiblockDefinitions.get(id);
    }

    private static MultiblockTextures.State visualState(BlockState state) {
        if (isBuilding(state)) {
            return MultiblockTextures.State.BUILDING;
        }
        return isActive(state) ? MultiblockTextures.State.ACTIVE : MultiblockTextures.State.IDLE;
    }

    private static boolean isActive(BlockState state) {
        return state.hasProperty(MultiblockControllerBlock.PROCESSING)
                && state.getValue(MultiblockControllerBlock.PROCESSING);
    }

    private static boolean isBuilding(BlockState state) {
        return state.hasProperty(MultiblockControllerBlock.BUILDING)
                && state.getValue(MultiblockControllerBlock.BUILDING);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        parts.add(this.idlePart);
    }

    @Override
    @SuppressWarnings("deprecation")
    public Material.Baked particleMaterial() {
        return this.idlePart.particleMaterial();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int materialFlags() {
        return this.idlePart.materialFlags();
    }

    private record PartKey(
            MultiblockTextures.State visual,
            Identifier modelId,
            Map<String, Identifier> textures) {
    }

    private record GeometryKey(
            ControllerBlockStateModel model,
            MultiblockTextures textures,
            MultiblockTextures.State visual,
            Identifier modelId) {
    }

    public record Unbaked(Variant model, Optional<Identifier> activeModel, Optional<Identifier> buildingModel) implements CustomUnbakedBlockStateModel {
        public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Variant.MAP_CODEC.forGetter(Unbaked::model),
                Identifier.CODEC.optionalFieldOf("active_model").forGetter(Unbaked::activeModel),
                Identifier.CODEC.optionalFieldOf("building_model").forGetter(Unbaked::buildingModel))
                .apply(instance, Unbaked::new));

        public static void register(RegisterBlockStateModels event) {
            event.registerModel(Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, MODEL_TYPE), CODEC);
        }

        @Override
        public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
            return CODEC;
        }

        @Override
        public BlockStateModel bake(ModelBaker modelBakery) {
            ResolvedModel idle = modelBakery.getModel(this.model.modelLocation());
            ResolvedModel active = this.activeModel.map(modelBakery::getModel).orElse(idle);
            ResolvedModel building = this.buildingModel.map(modelBakery::getModel).orElse(active);
            ModelState state = this.model.modelState().asModelState();
            BlockStateModelPart idlePart = SimpleModelWrapper.bake(modelBakery, idle, state);
            BlockStateModelPart activePart = active == idle
                    ? idlePart
                    : SimpleModelWrapper.bake(modelBakery, active, state);
            BlockStateModelPart buildingPart = building == active
                    ? activePart
                    : building == idle
                            ? idlePart
                            : SimpleModelWrapper.bake(modelBakery, building, state);
            return new ControllerBlockStateModel(
                    idlePart, activePart, buildingPart, idle, active, building, modelBakery, state);
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(this.model.modelLocation());
            this.activeModel.ifPresent(resolver::markDependency);
            this.buildingModel.ifPresent(resolver::markDependency);
        }
    }
}
