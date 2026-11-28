package com.Infinity.Nexus.Multiblock.gametest;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.*;
import com.Infinity.Nexus.Multiblock.block.*;
import com.Infinity.Nexus.Multiblock.data.MultiblockDefinitions;
import com.Infinity.Nexus.Multiblock.entity.MotionStructure.PartEntry;
import com.Infinity.Nexus.Multiblock.entity.MotionStructure.StructureSnapshot;
import com.Infinity.Nexus.Multiblock.recipe.*;
import com.Infinity.Nexus.Multiblock.registry.ModBlocks;
import com.Infinity.Nexus.Multiblock.registry.ModDataComponents;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.server.loading.ServerModLoader;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.*;

public final class MultiblockGameTests {
    private static final Identifier ENVIRONMENT_ID = Identifier.fromNamespaceAndPath(
            InfinityNexusMultiblock.MOD_ID, "multiblock");
    private static final Identifier MAQUINA_TESTE_LAYOUT_ID = Identifier.fromNamespaceAndPath(
            InfinityNexusMultiblock.MOD_ID, "maquina_teste_layout");

    private MultiblockGameTests() {
    }

    public static void register(RegisterGameTestsEvent event) {
        if (!ServerModLoader.isGameTestServer()) {
            return;
        }
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                ENVIRONMENT_ID, new TestEnvironmentDefinition.AllOf());
        event.registerTest(
                MAQUINA_TESTE_LAYOUT_ID,
                new MaquinaTesteLayoutTest(new TestData<>(
                        environment,
                        Identifier.withDefaultNamespace("empty"),
                        20,
                        1,
                        true)));
    }

    private static final class MaquinaTesteLayoutTest extends GameTestInstance {
        private MaquinaTesteLayoutTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            MultiblockDefinition definition = MultiblockDefinition.builder(Identifier.fromNamespaceAndPath(
                            InfinityNexusMultiblock.MOD_ID, "maquina_teste"))
                    .type(MultiblockType.STORAGE)
                    .pattern(MultiblockPattern.builder().layer("C").build())
                    .itemSlots(List.of(
                            new ItemSlot("slot_0", ItemSlotRole.INPUT, null, 64, Set.of(), Set.of()),
                            new ItemSlot("slot_1", ItemSlotRole.OUTPUT, null, 64, Set.of(), Set.of())))
                    .build();
            MultiblockMenuLayout layout = MultiblockMenuLayout.create(definition);
            if (layout.elements().stream().noneMatch(element -> element.type() == GuiElementType.PLAYER_INVENTORY)) {
                throw helper.assertionException(Component.literal("Fallback GUI is missing the player inventory"));
            }
            if (layout.width() != 176 || layout.height() != 166) {
                throw helper.assertionException(Component.literal(
                        "Fallback GUI must be 176x166, got " + layout.width() + "x" + layout.height()));
            }
            if (layout.slots().size() != definition.itemSlots().size()) {
                throw helper.assertionException(Component.literal(
                        "Fallback GUI slot count mismatch: " + layout.slots().size()
                                + " != " + definition.itemSlots().size()));
            }
            for (MultiblockMenuLayout.SlotPlacement slot : layout.slots()) {
                if (slot.x() < 0 || slot.y() < 0
                        || slot.x() + 18 > layout.width()
                        || slot.y() + 18 > layout.height()) {
                    throw helper.assertionException(Component.literal(
                            "Fallback GUI slot '" + slot.id() + "' is outside " + layout.width() + "x" + layout.height()));
                }
            }
            for (GuiElement element : layout.elements()) {
                if (element.width() <= 0 || element.height() <= 0) {
                    continue;
                }
                if (element.x() < 0 || element.y() < 0
                        || element.x() + element.width() > layout.width()
                        || element.y() + element.height() > layout.height()) {
                    throw helper.assertionException(Component.literal(
                            "Fallback GUI element '" + element.type().getSerializedName()
                                    + "' is outside " + layout.width() + "x" + layout.height()));
                }
            }
            assertDefinitionTextures(helper);
            assertDefinitionModel(helper);
            assertMultiblockShape(helper);
            assertEarlySlotPositions(helper);
            assertFluidOnlyGui(helper);
            assertBoosterButtonDefaults(helper);
            assertFluidPortBinding(helper);
            assertSlotLockBehavior(helper);
            assertEntityTagSelector(helper);
            assertControllerRecipe(helper);
            assertMultiblockRecipeLoads(helper);
            assertEntityOnlyOutputRecipe(helper);
            assertIncrementalAssembly(helper);
            assertUpgradeMath(helper);
            assertUpgradePattern(helper);
            assertMotionDefinition(helper);
            assertMotionSampling(helper);
            assertMotionPatternIgnore(helper);
            assertMotionSnapshot(helper);
            assertUpgradeCounting(helper);
            assertUpgradeOutput(helper);
            helper.succeed();
        }

        private static void assertEntityTagSelector(GameTestHelper helper) {
            HolderGetter<EntityType<?>> registry = helper.getLevel().registryAccess()
                    .lookupOrThrow(Registries.ENTITY_TYPE);
            EntityTypeSelector undead = new EntityTypeSelector.Tag(EntityTypeTags.UNDEAD);
            if (!undead.matches(EntityType.ZOMBIE)
                    || !undead.matches(EntityType.SKELETON)
                    || undead.matches(EntityType.PIG)) {
                throw helper.assertionException(Component.literal(
                        "Entity tag selector must match only undead entity types"));
            }
            List<EntityType<?>> resolved = undead.resolve(registry);
            if (resolved.isEmpty() || !resolved.contains(EntityType.ZOMBIE)) {
                throw helper.assertionException(Component.literal(
                        "Entity tag selector failed to resolve #minecraft:undead"));
            }
            if (undead.pick(registry, helper.getLevel().getRandom()).isEmpty()) {
                throw helper.assertionException(Component.literal(
                        "Entity tag selector failed to pick a random entity type"));
            }
            TagKey<EntityType<?>> chickens = TagKey.create(Registries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath("c", "chickens"));
            if (!new EntityTypeSelector.Tag(chickens).resolve(registry).contains(EntityType.CHICKEN)) {
                throw helper.assertionException(Component.literal(
                        "Entity tag #c:chickens must resolve to minecraft:chicken"));
            }
            MultiblockRecipeIngredient.Entity input = new MultiblockRecipeIngredient.Entity(
                    EntityTypeTags.UNDEAD, "", 1);
            var encoded = MultiblockRecipeIngredient.CODEC.encodeStart(NbtOps.INSTANCE, input).getOrThrow();
            MultiblockRecipeIngredient decoded = MultiblockRecipeIngredient.CODEC
                    .parse(NbtOps.INSTANCE, encoded)
                    .getOrThrow();
            if (!(decoded instanceof MultiblockRecipeIngredient.Entity entity)
                    || entity.type().tag().isEmpty()) {
                throw helper.assertionException(Component.literal(
                        "Entity tag recipe input failed to round-trip through its codec"));
            }
        }

        private static void assertControllerRecipe(GameTestHelper helper) {
            Identifier definitionId = Identifier.fromNamespaceAndPath(
                    InfinityNexusMultiblock.MOD_ID, "fabrica_de_voxel_cube");
            Identifier recipeId = Identifier.fromNamespaceAndPath(
                    InfinityNexusMultiblock.MOD_ID, "controller/fabrica_de_voxel_cube");
            ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> recipeKey =
                    ResourceKey.create(Registries.RECIPE, recipeId);
            RecipeHolder<?> holder = helper.getLevel().getServer().getRecipeManager()
                    .byKey(recipeKey)
                    .orElse(null);
            if (holder == null) {
                List<String> known = helper.getLevel().getServer().getRecipeManager().getRecipes().stream()
                        .map(recipe -> recipe.id().identifier().toString())
                        .filter(id -> id.startsWith(InfinityNexusMultiblock.MOD_ID + ":"))
                        .sorted()
                        .toList();
                throw helper.assertionException(Component.literal(
                        "Generated controller recipe is missing: " + recipeId
                                + "; total: " + helper.getLevel().getServer().getRecipeManager().getRecipes().size()
                                + "; mod recipes: " + known));
            }
            if (!(holder.value() instanceof ShapedRecipe shapedRecipe)) {
                throw helper.assertionException(Component.literal(
                        "Generated controller recipe is not shaped: " + recipeId));
            }

            ItemStack result = shapedRecipe.assemble(CraftingInput.EMPTY);
            if (!result.is(ModBlocks.CONTROLLER_ITEM.get())) {
                throw helper.assertionException(Component.literal(
                        "Generated controller recipe has the wrong result item: " + result));
            }
            if (result.getCount() != 1) {
                throw helper.assertionException(Component.literal(
                        "Generated controller recipe must output exactly one item, got " + result.getCount()));
            }
            Identifier componentDefinition = result.get(ModDataComponents.MULTIBLOCK_DEFINITION.get());
            if (!definitionId.equals(componentDefinition)) {
                throw helper.assertionException(Component.literal(
                        "Generated controller recipe has definition " + componentDefinition
                                + " instead of " + definitionId));
            }
        }

        private static void assertDefinitionTextures(GameTestHelper helper) {
            Identifier definitionId = Identifier.fromNamespaceAndPath(
                    InfinityNexusMultiblock.MOD_ID, "fabrica_de_voxel_cube");
            MultiblockDefinition definition = MultiblockDefinitions.get(definitionId);
            if (definition == null) {
                throw helper.assertionException(Component.literal(
                        "Multiblock definition is missing: " + definitionId));
            }

            MultiblockTextures textures = definition.textures();
            if (textures.isEmpty()) {
                throw helper.assertionException(Component.literal(
                        "Multiblock definition is missing its configured textures: " + definitionId));
            }

            Identifier stone = Identifier.parse("minecraft:block/stone");
            Identifier smoothStone = Identifier.parse("minecraft:block/smooth_stone");
            Identifier oakPlanks = Identifier.parse("minecraft:block/oak_planks");
            Identifier oakLog = Identifier.parse("minecraft:block/oak_log");
            Map<String, Identifier> idle = textures.resolve(false);
            Map<String, Identifier> active = textures.resolve(true);
            Map<String, Identifier> building = textures.resolve(MultiblockTextures.State.BUILDING);
            if (!stone.equals(idle.get("front"))
                    || !oakPlanks.equals(idle.get("side"))
                    || !oakPlanks.equals(idle.get("left"))
                    || !oakPlanks.equals(idle.get("right"))
                    || !oakPlanks.equals(idle.get("back"))
                    || !stone.equals(idle.get("north"))
                    || !oakPlanks.equals(idle.get("west"))
                    || !oakPlanks.equals(idle.get("east"))
                    || !oakPlanks.equals(idle.get("south"))
                    || !stone.equals(idle.get("particle"))) {
                throw helper.assertionException(Component.literal(
                        "Idle textures resolved incorrectly: " + idle));
            }
            if (!smoothStone.equals(active.get("front"))
                    || !oakLog.equals(active.get("side"))
                    || !oakLog.equals(active.get("left"))
                    || !oakLog.equals(active.get("right"))
                    || !oakLog.equals(active.get("back"))
                    || !smoothStone.equals(active.get("north"))
                    || !oakLog.equals(active.get("west"))
                    || !oakLog.equals(active.get("east"))
                    || !oakLog.equals(active.get("south"))) {
                throw helper.assertionException(Component.literal(
                        "Active textures did not overlay the idle textures: " + active));
            }
            if (!smoothStone.equals(building.get("front"))
                    || !oakLog.equals(building.get("side"))) {
                throw helper.assertionException(Component.literal(
                        "Building textures must fall back to the active textures: " + building));
            }

            Identifier front = Identifier.parse("minecraft:block/front_test");
            MultiblockTextures particleFallback = new MultiblockTextures(
                    new MultiblockTextures.TextureSet(
                            Optional.of(front),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty()),
                    Optional.empty(),
                    Optional.empty());
            if (!front.equals(particleFallback.resolve(false).get("particle"))) {
                throw helper.assertionException(Component.literal(
                        "Resolved textures must fall back from particle to front"));
            }

            Identifier all = Identifier.parse("minecraft:block/all_test");
            MultiblockTextures allFallback = new MultiblockTextures(
                    new MultiblockTextures.TextureSet(
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.of(all)),
                    Optional.empty(),
                    Optional.empty());
            Map<String, Identifier> allResolved = allFallback.resolve(false);
            if (!all.equals(allResolved.get("all")) || !all.equals(allResolved.get("particle"))) {
                throw helper.assertionException(Component.literal(
                        "Resolved textures must expose and fall back from all: " + allResolved));
            }

            Identifier buildingFront = Identifier.parse("minecraft:block/building_front_test");
            MultiblockTextures explicitBuilding = new MultiblockTextures(
                    new MultiblockTextures.TextureSet(
                            Optional.of(stone),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty()),
                    Optional.empty(),
                    Optional.of(new MultiblockTextures.TextureSet(
                            Optional.of(buildingFront),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty())));
            if (!buildingFront.equals(explicitBuilding.resolve(MultiblockTextures.State.BUILDING).get("front"))) {
                throw helper.assertionException(Component.literal(
                        "Explicit building textures must override the idle/active fallback"));
            }
            if (!stone.equals(explicitBuilding.resolve(MultiblockTextures.State.IDLE).get("front"))) {
                throw helper.assertionException(Component.literal(
                        "Building textures must not leak into the idle state"));
            }
            var buildingEncoded = MultiblockTextures.CODEC.codec()
                    .encodeStart(JsonOps.INSTANCE, explicitBuilding)
                    .getOrThrow();
            MultiblockTextures buildingDecoded = MultiblockTextures.CODEC.codec()
                    .parse(JsonOps.INSTANCE, buildingEncoded)
                    .getOrThrow();
            if (!explicitBuilding.equals(buildingDecoded)) {
                throw helper.assertionException(Component.literal(
                        "Building textures failed to round-trip through their codec: "
                                + explicitBuilding + " != " + buildingDecoded));
            }

            var encoded = MultiblockTextures.CODEC.codec()
                    .encodeStart(JsonOps.INSTANCE, textures)
                    .getOrThrow();
            MultiblockTextures decoded = MultiblockTextures.CODEC.codec()
                    .parse(JsonOps.INSTANCE, encoded)
                    .getOrThrow();
            if (!textures.equals(decoded)) {
                throw helper.assertionException(Component.literal(
                        "Multiblock textures failed to round-trip through their codec: "
                                + textures + " != " + decoded));
            }
        }

        private static void assertDefinitionModel(GameTestHelper helper) {
            Identifier definitionId = Identifier.fromNamespaceAndPath(
                    InfinityNexusMultiblock.MOD_ID, "model_test");
            MultiblockDefinition definition = MultiblockDefinitions.get(definitionId);
            if (definition == null) {
                throw helper.assertionException(Component.literal(
                        "Multiblock definition is missing: " + definitionId));
            }

            Identifier idle = Identifier.parse("minecraft:block/furnace");
            Identifier active = Identifier.parse("minecraft:block/furnace_on");
            Identifier building = Identifier.parse("minecraft:block/crafting_table");
            MultiblockModel model = definition.model();
            if (!idle.equals(model.resolve(MultiblockTextures.State.IDLE).orElse(null))
                    || !active.equals(model.resolve(MultiblockTextures.State.ACTIVE).orElse(null))
                    || !building.equals(model.resolve(MultiblockTextures.State.BUILDING).orElse(null))) {
                throw helper.assertionException(Component.literal(
                        "Multiblock model states resolved incorrectly: " + model));
            }

            MultiblockModel idleOnly = new MultiblockModel(Optional.of(idle), Optional.empty(), Optional.empty());
            if (!idle.equals(idleOnly.resolve(MultiblockTextures.State.ACTIVE).orElse(null))
                    || !idle.equals(idleOnly.resolve(MultiblockTextures.State.BUILDING).orElse(null))) {
                throw helper.assertionException(Component.literal(
                        "Multiblock model must fall back from active/building to idle: " + idleOnly));
            }

            MultiblockModel activeOnly = new MultiblockModel(Optional.of(idle), Optional.of(active), Optional.empty());
            if (!active.equals(activeOnly.resolve(MultiblockTextures.State.BUILDING).orElse(null))) {
                throw helper.assertionException(Component.literal(
                        "Multiblock model must fall back from building to active: " + activeOnly));
            }

            var encoded = MultiblockModel.CODEC.codec()
                    .encodeStart(JsonOps.INSTANCE, model)
                    .getOrThrow();
            MultiblockModel decoded = MultiblockModel.CODEC.codec()
                    .parse(JsonOps.INSTANCE, encoded)
                    .getOrThrow();
            if (!model.equals(decoded)) {
                throw helper.assertionException(Component.literal(
                        "Multiblock model failed to round-trip through its codec: "
                                + model + " != " + decoded));
            }
        }

        private static void assertMultiblockShape(GameTestHelper helper) {
            MultiblockShape shape = MultiblockShape.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseString("[[0,0,0,4,16,8]]"))
                    .getOrThrow();
            if (shape.isEmpty()) {
                throw helper.assertionException(Component.literal("Multiblock shape must not be empty"));
            }
            var bounds = shape.voxelShape(Rotation.NONE).bounds();
            if (!near(bounds.minX, 0.0) || !near(bounds.maxX, 0.25)
                    || !near(bounds.minY, 0.0) || !near(bounds.maxY, 1.0)
                    || !near(bounds.minZ, 0.0) || !near(bounds.maxZ, 0.5)) {
                throw helper.assertionException(Component.literal(
                        "Multiblock shape bounds are wrong: " + bounds));
            }
            var rotated = shape.voxelShape(Rotation.CLOCKWISE_90).bounds();
            if (!near(rotated.minX, 0.5) || !near(rotated.maxX, 1.0)
                    || !near(rotated.minZ, 0.0) || !near(rotated.maxZ, 0.25)) {
                throw helper.assertionException(Component.literal(
                        "Multiblock shape rotation is wrong: " + rotated));
            }
            var encoded = MultiblockShape.CODEC.encodeStart(JsonOps.INSTANCE, shape).getOrThrow();
            MultiblockShape decoded = MultiblockShape.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
            if (!shape.boxes().equals(decoded.boxes())) {
                throw helper.assertionException(Component.literal(
                        "Multiblock shape failed to round-trip through its codec: "
                                + shape.boxes() + " != " + decoded.boxes()));
            }
            if (MultiblockShape.EMPTY.voxelShape(Rotation.NONE).isEmpty()) {
                throw helper.assertionException(Component.literal(
                        "An empty multiblock shape must keep the full block shape"));
            }
        }

        private static boolean near(double value, double expected) {
            return Math.abs(value - expected) < 1.0E-6;
        }

        private static void assertMultiblockRecipeLoads(GameTestHelper helper) {
            Identifier recipeId = Identifier.fromNamespaceAndPath(
                    InfinityNexusMultiblock.MOD_ID, "recipe_1");
            ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> recipeKey =
                    ResourceKey.create(Registries.RECIPE, recipeId);
            RecipeHolder<?> holder = helper.getLevel().getServer().getRecipeManager()
                    .byKey(recipeKey)
                    .orElse(null);
            if (!(holder != null && holder.value() instanceof MultiblockRecipe recipe)) {
                throw helper.assertionException(Component.literal(
                        "Multiblock recipe failed to load: " + recipeId));
            }
            if (recipe.entityInputs().stream().noneMatch(input -> input.type().matches(EntityType.PIG))) {
                throw helper.assertionException(Component.literal(
                        "Multiblock recipe is missing its pig input: " + recipeId));
            }
            if (recipe.itemInputs().stream().noneMatch(input -> input.count() == 12
                    && input.ingredient().test(new ItemStack(Items.DIRT)))) {
                throw helper.assertionException(Component.literal(
                        "Multiblock recipe is missing its 12 dirt input: " + recipeId));
            }
            List<ItemStack> splitDirt = List.of(
                    new ItemStack(Items.DIRT, 7),
                    new ItemStack(Items.DIRT, 7),
                    new ItemStack(Items.DIRT, 7),
                    new ItemStack(Items.DIRT, 7),
                    new ItemStack(Items.DIRT, 7),
                    new ItemStack(Items.DIRT, 7),
                    new ItemStack(Items.DIRT, 7),
                    new ItemStack(Items.DIRT, 7),
                    new ItemStack(Items.DIRT, 8));
            List<MultiblockRecipe.ItemAllocation> dirtAllocations = recipe.findItemInputs(splitDirt);
            if (dirtAllocations == null
                    || dirtAllocations.size() < 2
                    || dirtAllocations.stream().mapToInt(MultiblockRecipe.ItemAllocation::count).sum() != 12) {
                throw helper.assertionException(Component.literal(
                        "Multiblock recipe must accept dirt split across input slots: " + recipeId));
            }
            for (MultiblockRecipeOutput output : recipe.outputs()) {
                if (output instanceof MultiblockRecipeOutput.Fluid fluidOutput) {
                    FluidStack stack = fluidOutput.stack();
                    if (stack.isEmpty()) {
                        throw helper.assertionException(Component.literal(
                                "Multiblock recipe fluid output decoded incorrectly: " + recipeId));
                    }
                }
            }
        }

        private static void assertEntityOnlyOutputRecipe(GameTestHelper helper) {
            var json = JsonParser.parseString("""
                    {
                      "definition": "infinity_nexus_multiblock:fabrica_de_voxel_cube",
                      "time": 20,
                      "inputs": [{"type": "energy", "amount": 100}],
                      "outputs": [{"type": "entity", "id": "minecraft:chicken"}]
                    }
                    """).getAsJsonObject();
            MultiblockRecipe recipe = MultiblockRecipe.codec().codec()
                    .parse(JsonOps.INSTANCE, json)
                    .getOrThrow();
            if (!recipe.itemInputs().isEmpty()) {
                throw helper.assertionException(Component.literal(
                        "Entity-only output recipe must not require item inputs"));
            }
            if (!(recipe.outputs().getFirst() instanceof MultiblockRecipeOutput.Entity entity)) {
                throw helper.assertionException(Component.literal(
                        "Entity-only output recipe failed to decode its entity output"));
            }
            if (entity.type().direct().isEmpty()) {
                throw helper.assertionException(Component.literal(
                        "Entity-only output recipe lost its entity type"));
            }
            if (!recipe.matches(new MultiblockRecipeInput(List.of(), List.of()), helper.getLevel())) {
                throw helper.assertionException(Component.literal(
                        "Entity-only output recipe must match an energy-only input"));
            }
        }

        private static void assertIncrementalAssembly(GameTestHelper helper) {
            MultiblockDefinition definition = MultiblockDefinition.builder(Identifier.fromNamespaceAndPath(
                            InfinityNexusMultiblock.MOD_ID, "incremental_assembly_test"))
                    .type(MultiblockType.STORAGE)
                    .pattern(MultiblockPattern.builder()
                            .matcher('S', Blocks.STONE)
                            .layer("CSSS")
                            .build())
                    .build();

            BlockPos controller = helper.absolutePos(new BlockPos(1, 2, 1));
            BlockPos[] targets = {
                    controller.offset(1, 0, 0),
                    controller.offset(2, 0, 0),
                    controller.offset(3, 0, 0)
            };
            for (BlockPos target : targets) {
                helper.getLevel().setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }

            BlockPos chestPos = controller.relative(Direction.NORTH);
            helper.getLevel().setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            if (!(helper.getLevel().getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
                throw helper.assertionException(Component.literal("Assembly test chest was not created"));
            }
            chest.setItem(0, new ItemStack(Items.STONE, 3));
            chest.setChanged();

            MultiblockAssemblyJob job = new MultiblockAssemblyJob(
                    definition, controller, Rotation.NONE, UUID.randomUUID(), false);
            int previousPlaced = 0;
            MultiblockAssemblyJob.TickResult result = MultiblockAssemblyJob.TickResult.RUNNING;
            for (int tick = 0; tick < 32 && result == MultiblockAssemblyJob.TickResult.RUNNING; tick++) {
                result = job.tick(helper.getLevel(), null, 1, 10);
                if (job.placed() - previousPlaced > 1) {
                    throw helper.assertionException(Component.literal(
                            "Assembly job placed more than one block in a single tick"));
                }
                previousPlaced = job.placed();
                if (countStone(chest) != 3 - job.placed()) {
                    throw helper.assertionException(Component.literal(
                            "Assembly job must consume exactly the placed blocks at the end of each tick"));
                }
            }
            if (result != MultiblockAssemblyJob.TickResult.COMPLETE || job.placed() != targets.length) {
                throw helper.assertionException(Component.literal(
                        "Adjacent container assembly did not complete: " + result + " / " + job.placed()));
            }
            for (BlockPos target : targets) {
                if (!helper.getLevel().getBlockState(target).is(Blocks.STONE)) {
                    throw helper.assertionException(Component.literal(
                            "Assembly job did not place stone at " + target));
                }
            }

            helper.getLevel().setBlock(targets[1], Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
            MultiblockAssemblyJob blockedJob = new MultiblockAssemblyJob(
                    definition, controller, Rotation.NONE, UUID.randomUUID(), false);
            blockedJob.tick(helper.getLevel(), null, 16, 10);
            if (!blockedJob.blockedPositions().equals(List.of(targets[1]))) {
                throw helper.assertionException(Component.literal(
                        "Blocked assembly positions were not collected: " + blockedJob.blockedPositions()));
            }

            BlockPos creativeController = helper.absolutePos(new BlockPos(1, 2, 5));
            for (int x = 1; x <= 3; x++) {
                helper.getLevel().setBlock(
                        creativeController.offset(x, 0, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            BlockPos creativeChestPos = creativeController.relative(Direction.NORTH);
            helper.getLevel().setBlock(creativeChestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            if (!(helper.getLevel().getBlockEntity(creativeChestPos) instanceof ChestBlockEntity creativeChest)) {
                throw helper.assertionException(Component.literal("Creative assembly test chest was not created"));
            }
            creativeChest.setItem(0, new ItemStack(Items.STONE, 3));
            creativeChest.setChanged();

            MultiblockAssemblyJob creativeJob = new MultiblockAssemblyJob(
                    definition, creativeController, Rotation.NONE, UUID.randomUUID(), true);
            result = MultiblockAssemblyJob.TickResult.RUNNING;
            for (int tick = 0; tick < 32 && result == MultiblockAssemblyJob.TickResult.RUNNING; tick++) {
                result = creativeJob.tick(helper.getLevel(), null, 1, 10);
            }
            if (result != MultiblockAssemblyJob.TickResult.COMPLETE || creativeJob.placed() != 3) {
                throw helper.assertionException(Component.literal(
                        "Creative assembly did not complete: " + result + " / " + creativeJob.placed()));
            }
            if (countStone(creativeChest) != 3) {
                throw helper.assertionException(Component.literal(
                        "Creative assembly must not consume items"));
            }
        }

        private static int countStone(Container container) {
            int count = 0;
            for (int index = 0; index < container.getContainerSize(); index++) {
                ItemStack stack = container.getItem(index);
                if (stack.is(Items.STONE)) {
                    count += stack.getCount();
                }
            }
            return count;
        }

        private static void assertSlotLockBehavior(GameTestHelper helper) {
            ItemStack diamond = new ItemStack(Items.DIAMOND);
            SlotLock itemLock = new SlotLock("input", 0, diamond.copyWithCount(5));
            if (itemLock.stack().getCount() != 1) {
                throw helper.assertionException(Component.literal("SlotLock must normalize its stack count to 1"));
            }

            SlotLock decodedItem = SlotLock.CODEC
                    .parse(NbtOps.INSTANCE, SlotLock.CODEC.encodeStart(NbtOps.INSTANCE, itemLock).getOrThrow())
                    .getOrThrow();
            SlotLock emptyLock = new SlotLock("input", 0, ItemStack.EMPTY);
            SlotLock decodedEmpty = SlotLock.CODEC
                    .parse(NbtOps.INSTANCE, SlotLock.CODEC.encodeStart(NbtOps.INSTANCE, emptyLock).getOrThrow())
                    .getOrThrow();
            if (!itemLock.equals(decodedItem)) {
                throw helper.assertionException(Component.literal(
                        "SlotLock item codec mismatch: " + itemLock + " != " + decodedItem));
            }
            if (!emptyLock.equals(decodedEmpty)) {
                throw helper.assertionException(Component.literal(
                        "SlotLock empty codec mismatch: " + emptyLock + " != " + decodedEmpty));
            }

            SlotLock[] activeLock = {emptyLock};
            MultiblockItemHandler handler = new MultiblockItemHandler(
                    List.of(new ItemSlot("input", ItemSlotRole.INPUT, null, 64,
                            EnumSet.allOf(Direction.class), EnumSet.allOf(Direction.class))),
                    slot -> activeLock[0]);
            handler.setItem(0, diamond);
            if (!handler.getItem(0).isEmpty()) {
                throw helper.assertionException(Component.literal("An empty lock must reject item placement"));
            }

            activeLock[0] = itemLock;
            handler.setItem(0, diamond);
            if (handler.getItem(0).isEmpty()) {
                throw helper.assertionException(Component.literal("A matching item lock must allow placement"));
            }
            handler.setItem(0, new ItemStack(Items.DIRT));
            if (!ItemStack.isSameItem(handler.getItem(0), diamond)) {
                throw helper.assertionException(Component.literal("A mismatched item lock must reject placement"));
            }

            ItemStack namedDiamond = diamond.copy();
            namedDiamond.set(DataComponents.CUSTOM_NAME, Component.literal("Locked"));
            if (handler.canPlaceItem(0, namedDiamond)) {
                throw helper.assertionException(Component.literal("A lock must compare item components"));
            }

            handler.removeItem(0, 1);
            handler.setItem(0, diamond.copyWithCount(3));
            if (handler.getItem(0).getCount() != 3) {
                throw helper.assertionException(Component.literal("A matching lock must allow stacking"));
            }
        }

        private static void assertFluidOnlyGui(GameTestHelper helper) {
            MultiblockDefinition definition = MultiblockDefinition.builder(Identifier.fromNamespaceAndPath(
                            InfinityNexusMultiblock.MOD_ID, "fluid_only_gui_test"))
                    .type(MultiblockType.STORAGE)
                    .pattern(MultiblockPattern.builder().layer("C").build())
                    .fluidTanks(List.of(new FluidTank("water_tank", 1000, 1000, 0, null)))
                    .gui(new MultiblockGui(null, 176, 166, 256, 256, List.of(
                            GuiElement.playerInventory(8, 84, ""),
                            GuiElement.fluidBar(160, 5, 3, 64, "water_tank"))))
                    .build();
            MultiblockMenuLayout layout = MultiblockMenuLayout.create(definition);
            if (!layout.slots().isEmpty()) {
                throw helper.assertionException(Component.literal(
                        "Fluid-only GUI must not require item slots, got " + layout.slots().size()));
            }
            if (layout.elements().stream().noneMatch(element -> element.type() == GuiElementType.FLUID_BAR)) {
                throw helper.assertionException(Component.literal("Fluid-only GUI is missing the fluid bar"));
            }
        }

        private static void assertBoosterButtonDefaults(GameTestHelper helper) {
            GuiElement parsed = GuiElement.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"booster_button\"}"))
                    .getOrThrow();
            if (parsed.width() != GuiElement.BOOSTER_BUTTON_SIZE
                    || parsed.height() != GuiElement.BOOSTER_BUTTON_SIZE
                    || !"B".equals(parsed.text())
                    || parsed.color() != GuiElement.DEFAULT_BOOSTER_COLOR) {
                throw helper.assertionException(Component.literal(
                        "booster_button defaults are wrong: " + parsed));
            }
        }

        private static void assertFluidPortBinding(GameTestHelper helper) {
            CapabilityPorts.PortSelector port = CapabilityPorts.PortSelector.symbol('F');
            FluidTank restricted = new FluidTank("water_tank", 1000, 100, 40, null, port);
            FluidTank shared = new FluidTank("shared_tank", 1000, 200, 200, null);
            if (!restricted.allowsPort('F', Blocks.IRON_BLOCK.defaultBlockState(), Rotation.NONE)
                    || restricted.allowsPort('X', Blocks.IRON_BLOCK.defaultBlockState(), Rotation.NONE)
                    || !shared.allowsPort('X', Blocks.IRON_BLOCK.defaultBlockState(), Rotation.NONE)) {
                throw helper.assertionException(Component.literal(
                        "Fluid tank port selectors do not match the expected pattern keys"));
            }

            MultiblockFluidHandler handler = new MultiblockFluidHandler(List.of(restricted, shared), () -> {
            });
            ResourceHandler<FluidResource> view = handler.indexedAccess(List.of(0));
            FluidResource water = FluidResource.of(new FluidStack(Fluids.WATER, 1));
            try (Transaction transaction = Transaction.open(null)) {
                int inserted = view.insert(0, water, 500, transaction);
                transaction.commit();
                if (inserted != 100) {
                    throw helper.assertionException(Component.literal(
                            "A restricted fluid port ignored max_receive: " + inserted));
                }
            }
            if (view.size() != 1
                    || handler.getAmountAsLong(0) != 100L
                    || handler.getAmountAsLong(1) != 0L) {
                throw helper.assertionException(Component.literal(
                        "indexedAccess must expose only the selected tank"));
            }
            try (Transaction transaction = Transaction.open(null)) {
                int extracted = view.extract(0, water, 500, transaction);
                transaction.commit();
                if (extracted != 40) {
                    throw helper.assertionException(Component.literal(
                            "A restricted fluid port ignored max_extract: " + extracted));
                }
            }
            ResourceHandler<FluidResource> manual = handler.manualAccess(List.of(0));
            try (Transaction transaction = Transaction.open(null)) {
                int inserted = manual.insert(0, water, 500, transaction);
                transaction.commit();
                if (inserted != 500) {
                    throw helper.assertionException(Component.literal(
                            "Manual fluid access respected max_receive: " + inserted));
                }
            }
            try (Transaction transaction = Transaction.open(null)) {
                int extracted = manual.extract(0, water, 500, transaction);
                transaction.commit();
                if (extracted != 500) {
                    throw helper.assertionException(Component.literal(
                            "Manual fluid access respected max_extract: " + extracted));
                }
            }
        }

        private static void assertEarlySlotPositions(GameTestHelper helper) {
            MultiblockDefinition definition = MultiblockDefinition.builder(Identifier.fromNamespaceAndPath(
                            InfinityNexusMultiblock.MOD_ID, "early_slot_positions_test"))
                    .type(MultiblockType.STORAGE)
                    .pattern(MultiblockPattern.builder().layer("C").build())
                    .itemSlots(List.of(
                            slot("slot_0", ItemSlotRole.INPUT, 14, 14),
                            slot("slot_1", ItemSlotRole.INPUT, 14, 34),
                            slot("slot_2", ItemSlotRole.OUTPUT, 80, 24)))
                    .build();
            List<MultiblockMenuLayout.SlotPlacement> slots = MultiblockMenuLayout.create(definition).slots();
            int[][] expected = {{14, 14}, {14, 34}, {80, 24}};
            for (int index = 0; index < expected.length; index++) {
                MultiblockMenuLayout.SlotPlacement slot = slots.get(index);
                if (slot.x() != expected[index][0] || slot.y() != expected[index][1]) {
                    throw helper.assertionException(Component.literal(
                            "Fallback GUI ignored slot position " + slot.id()
                                    + ": expected " + expected[index][0] + "," + expected[index][1]
                                    + " but got " + slot.x() + "," + slot.y()));
                }
            }
        }

        private static void assertUpgradeMath(GameTestHelper helper) {
            MultiblockUpgrade output = new MultiblockUpgrade(
                    BlockMatcher.of(Blocks.GOLD_BLOCK),
                    MultiblockUpgrade.Modifier.OUTPUT,
                    MultiblockUpgrade.Mode.ADDITIVE,
                    0.5D,
                    4);
            MultiblockUpgrade speed = new MultiblockUpgrade(
                    BlockMatcher.of(Blocks.IRON_BLOCK),
                    MultiblockUpgrade.Modifier.SPEED,
                    MultiblockUpgrade.Mode.FLAT,
                    20.0D,
                    4);
            MultiblockUpgrade thread = new MultiblockUpgrade(
                    BlockMatcher.of(Blocks.DIAMOND_BLOCK),
                    MultiblockUpgrade.Modifier.THREAD,
                    MultiblockUpgrade.Mode.FLAT,
                    2.0D,
                    4);
            MultiblockUpgrade efficiency = new MultiblockUpgrade(
                    BlockMatcher.of(Blocks.EMERALD_BLOCK),
                    MultiblockUpgrade.Modifier.EFFICIENCY,
                    MultiblockUpgrade.Mode.ADDITIVE,
                    0.25D,
                    4);
            MultiblockUpgrade energy = new MultiblockUpgrade(
                    BlockMatcher.of(Blocks.LAPIS_BLOCK),
                    MultiblockUpgrade.Modifier.ENERGY,
                    MultiblockUpgrade.Mode.ADDITIVE,
                    0.1D,
                    4);
            MultiblockUpgrade damage = new MultiblockUpgrade(
                    BlockMatcher.of(Blocks.NETHERITE_BLOCK),
                    MultiblockUpgrade.Modifier.DAMAGE,
                    MultiblockUpgrade.Mode.ADDITIVE,
                    0.25D,
                    4);
            List<MultiblockUpgrade> upgrades = List.of(output, speed, thread, efficiency, energy, damage);
            int[] counts = {2, 1, 1, 2, 3, 2};

            UpgradeBonus outputBonus = MultiblockUpgradeMath.combine(
                    upgrades, MultiblockUpgrade.Modifier.OUTPUT, counts);
            if (!near(outputBonus.multiplier(), 2.0D) || !near(outputBonus.flat(), 0.0D)) {
                throw helper.assertionException(Component.literal(
                        "Output booster math is wrong: " + outputBonus));
            }
            MultiblockUpgradeMath.ScaledAmount scaled = MultiblockUpgradeMath.scaleOutput(
                    10, outputBonus, false, helper.getLevel().getRandom());
            if (scaled.whole() != 20) {
                throw helper.assertionException(Component.literal(
                        "Output booster did not scale the output: " + scaled.whole()));
            }

            UpgradeBonus speedBonus = MultiblockUpgradeMath.combine(
                    upgrades, MultiblockUpgrade.Modifier.SPEED, counts);
            if (MultiblockUpgradeMath.effectiveTicks(100, speedBonus) != 80) {
                throw helper.assertionException(Component.literal(
                        "Speed booster did not reduce the duration"));
            }

            UpgradeBonus threadBonus = MultiblockUpgradeMath.combine(
                    upgrades, MultiblockUpgrade.Modifier.THREAD, counts);
            if (MultiblockUpgradeMath.threads(threadBonus) != 3) {
                throw helper.assertionException(Component.literal(
                        "Thread booster did not increase the batch size"));
            }

            UpgradeBonus efficiencyBonus = MultiblockUpgradeMath.combine(
                    upgrades, MultiblockUpgrade.Modifier.EFFICIENCY, counts);
            if (MultiblockUpgradeMath.scaledInput(10, efficiencyBonus) != 5) {
                throw helper.assertionException(Component.literal(
                        "Efficiency booster did not reduce the input"));
            }
            if (MultiblockUpgradeMath.scaledInput(1, efficiencyBonus) != 1) {
                throw helper.assertionException(Component.literal(
                        "Efficiency booster must never reduce a one-unit input below one"));
            }

            UpgradeBonus energyBonus = MultiblockUpgradeMath.combine(
                    upgrades, MultiblockUpgrade.Modifier.ENERGY, counts);
            if (MultiblockUpgradeMath.effectiveEnergy(1000L, energyBonus) != 700L) {
                throw helper.assertionException(Component.literal(
                        "Energy booster did not reduce the energy cost"));
            }

            UpgradeBonus damageBonus = MultiblockUpgradeMath.combine(
                    upgrades, MultiblockUpgrade.Modifier.DAMAGE, counts);
            if (!near(MultiblockUpgradeMath.scaledDamage(4.0F, damageBonus), 6.0D)) {
                throw helper.assertionException(Component.literal(
                        "Damage booster did not increase the motion damage: " + damageBonus));
            }
        }

        private static void assertUpgradeCounting(GameTestHelper helper) {
            Identifier id = Identifier.fromNamespaceAndPath(
                    InfinityNexusMultiblock.MOD_ID, "steam_generator");
            MultiblockDefinition definition = MultiblockDefinitions.get(id);
            if (definition == null) {
                throw helper.assertionException(Component.literal(
                        "steam_generator definition was not loaded"));
            }
            if (definition.upgrades().size() != 1) {
                throw helper.assertionException(Component.literal(
                        "steam_generator must load one upgrade, got " + definition.upgrades().size()));
            }
            if (definition.upgradeKey().isEmpty() || definition.upgradeKey().get() != 'U') {
                throw helper.assertionException(Component.literal(
                        "steam_generator must use upgrade key U"));
            }

            BlockPos controller = helper.absolutePos(new BlockPos(1, 2, 8));
            helper.getLevel().setBlock(
                    controller,
                    ModBlocks.CONTROLLER.get().defaultBlockState()
                            .setValue(MultiblockControllerBlock.FACING, Direction.SOUTH),
                    Block.UPDATE_ALL);
            if (!(helper.getLevel().getBlockEntity(controller) instanceof MultiblockControllerBlockEntity be)) {
                throw helper.assertionException(Component.literal(
                        "steam_generator controller block entity was not created"));
            }
            be.setDefinitionId(id);
            placeSteamGeneratorShell(helper, controller);
            be.validateStructure();
            if (!be.isFormed()) {
                throw helper.assertionException(Component.literal(
                        "steam_generator structure did not form"));
            }

            List<BlockPos> positions = definition.upgradeMembers(controller, Rotation.NONE);
            if (positions.size() != 4) {
                throw helper.assertionException(Component.literal(
                        "steam_generator must expose four upgrade cells, got " + positions));
            }
            for (BlockPos pos : positions) {
                helper.getLevel().setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            }
            be.validateStructure();
            int[] counts = be.upgradeCounts();
            if (counts.length != 1 || counts[0] != 4) {
                throw helper.assertionException(Component.literal(
                        "steam_generator gold boosters were not counted: "
                                + java.util.Arrays.toString(counts) + " at " + be.upgradeMembers()));
            }
        }

        private static void assertUpgradeOutput(GameTestHelper helper) {
            Identifier id = Identifier.fromNamespaceAndPath(
                    InfinityNexusMultiblock.MOD_ID, "steam_generator");
            MultiblockDefinition definition = MultiblockDefinitions.get(id);
            if (definition == null) {
                throw helper.assertionException(Component.literal(
                        "steam_generator definition was not loaded"));
            }

            BlockPos controller = helper.absolutePos(new BlockPos(1, 2, 9));
            helper.getLevel().setBlock(
                    controller,
                    ModBlocks.CONTROLLER.get().defaultBlockState()
                            .setValue(MultiblockControllerBlock.FACING, Direction.SOUTH),
                    Block.UPDATE_ALL);
            if (!(helper.getLevel().getBlockEntity(controller) instanceof MultiblockControllerBlockEntity be)) {
                throw helper.assertionException(Component.literal(
                        "steam_generator controller block entity was not created"));
            }
            be.setDefinitionId(id);
            placeSteamGeneratorShell(helper, controller);
            be.validateStructure();
            if (!be.isFormed()) {
                throw helper.assertionException(Component.literal(
                        "steam_generator structure did not form"));
            }

            for (BlockPos pos : definition.upgradeMembers(controller, Rotation.NONE)) {
                helper.getLevel().setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            }
            be.validateStructure();
            int[] counts = be.upgradeCounts();
            if (counts.length != 1 || counts[0] != 4) {
                throw helper.assertionException(Component.literal(
                        "steam_generator gold boosters were not counted: "
                                + java.util.Arrays.toString(counts)));
            }

            be.inventory().setItem(0, new ItemStack(Items.COAL, 1));
            for (int tick = 0; tick < 40; tick++) {
                be.serverTick();
            }
            var storage = be.energyStorage();
            if (storage == null) {
                throw helper.assertionException(Component.literal(
                        "steam_generator has no energy storage"));
            }
            long energy = storage.getAmountAsLong();
            if (energy != 600L) {
                throw helper.assertionException(Component.literal(
                        "steam_generator with four gold boosters produced " + energy
                                + " FE instead of 600; counts="
                                + java.util.Arrays.toString(be.upgradeCounts())));
            }
        }

        private static void assertUpgradePattern(GameTestHelper helper) {
            MultiblockPattern pattern = MultiblockPattern.builder()
                    .matcher('S', Blocks.STONE)
                    .upgradeKey('U')
                    .layer("CSU")
                    .build();
            if (!pattern.hasUpgradeKey() || pattern.upgradeKey() != 'U') {
                throw helper.assertionException(Component.literal(
                        "Multiblock pattern lost its upgrade key"));
            }
            if (pattern.upgradeCells().size() != 1) {
                throw helper.assertionException(Component.literal(
                        "Multiblock pattern must collect the upgrade cells"));
            }
            if (pattern.members(BlockPos.ZERO, Rotation.NONE).size() != 1) {
                throw helper.assertionException(Component.literal(
                        "Upgrade cells must not be part of the required structure"));
            }
            List<BlockPos> upgradePositions = pattern.upgradeMembers(BlockPos.ZERO, Rotation.NONE);
            if (upgradePositions.size() != 1 || !upgradePositions.getFirst().equals(new BlockPos(2, 0, 0))) {
                throw helper.assertionException(Component.literal(
                        "Upgrade cell position is wrong: " + upgradePositions));
            }
        }

        private static void assertMotionDefinition(GameTestHelper helper) {
            MultiblockPattern pattern = MultiblockPattern.builder()
                    .matcher('S', Blocks.STONE)
                    .matcher('A', Blocks.STONE)
                    .layer("CSA")
                    .build();
            MultiblockMotion motion = new MultiblockMotion(
                    "arm", List.of('A'), false, false, 4.0F, 10,
                    new MultiblockMotion.Animation(true, 20,
                            List.of(
                                    new MultiblockMotion.Keyframe(0, Vec3.ZERO, Vec3.ZERO, Optional.empty()),
                                    new MultiblockMotion.Keyframe(20,
                                            new Vec3(0, 2, 0), new Vec3(0, 90, 0), Optional.empty())),
                            MultiblockMotion.Easing.LINEAR, Optional.empty(), false));
            MultiblockDefinition definition = MultiblockDefinition.builder(Identifier.fromNamespaceAndPath(
                            InfinityNexusMultiblock.MOD_ID, "motion_teste"))
                    .type(MultiblockType.RECIPE)
                    .pattern(pattern)
                    .motions(List.of(motion))
                    .build();
            if (definition.motions().size() != 1 || !definition.motionKeys().contains('A')) {
                throw helper.assertionException(Component.literal(
                        "Multiblock motion was not stored on the definition"));
            }
            boolean rejected = false;
            try {
                MultiblockDefinition.builder(Identifier.fromNamespaceAndPath(
                                InfinityNexusMultiblock.MOD_ID, "motion_invalid"))
                        .pattern(pattern)
                        .motions(List.of(new MultiblockMotion("bad", List.of('C'), false, false,
                                0.0F, 10, MultiblockMotion.Animation.NONE)))
                        .build();
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            if (!rejected) {
                throw helper.assertionException(Component.literal(
                        "Multiblock motion accepted the anchor key"));
            }
            boolean invalidDamageSourceRejected = false;
            try {
                MultiblockDefinition.builder(Identifier.fromNamespaceAndPath(
                                InfinityNexusMultiblock.MOD_ID, "motion_invalid_damage"))
                        .pattern(pattern)
                        .motions(List.of(new MultiblockMotion("bad_damage", List.of('S'), false, false,
                                0.0F, 10, "not an id", MultiblockMotion.Animation.NONE)))
                        .build();
            } catch (IllegalStateException expected) {
                invalidDamageSourceRejected = true;
            }
            if (!invalidDamageSourceRejected) {
                throw helper.assertionException(Component.literal(
                        "Multiblock motion accepted an invalid damage_source"));
            }
            var parsed = MultiblockMotion.CODEC.listOf().parse(
                    JsonOps.INSTANCE,
                    JsonParser.parseString("""
                            [{"id":"arm","keys":["A"],"damage_source":"minecraft:magic",
                            "animation":{"duration":20,"fill_speed":true,
                            "keyframes":[{"time":0},{"time":20,"offset":[0,2,0]}]}}]
                            """))
                    .getOrThrow();
            if (parsed.size() != 1 || !parsed.getFirst().keys().contains('A')
                    || !parsed.getFirst().damageSource().equals("minecraft:magic")
                    || parsed.getFirst().animation().duration() != 20
                    || !parsed.getFirst().animation().fillSpeed()) {
                throw helper.assertionException(Component.literal(
                        "Multiblock motion JSON did not decode"));
            }
            if (Math.abs(parsed.getFirst().animation().speedFactor(0.5D) - 1.5D) > 1.0E-6D) {
                throw helper.assertionException(Component.literal(
                        "Multiblock motion fill speed factor is wrong"));
            }
        }

        private static void assertMotionSampling(GameTestHelper helper) {
            MultiblockMotion motion = new MultiblockMotion("arm", List.of('A'), false, false, 0.0F, 10,
                    new MultiblockMotion.Animation(false, 10,
                            List.of(
                                    new MultiblockMotion.Keyframe(0, Vec3.ZERO, Vec3.ZERO, Optional.empty()),
                                    new MultiblockMotion.Keyframe(10,
                                            new Vec3(0, 2, 0), new Vec3(0, 90, 0), Optional.empty())),
                            MultiblockMotion.Easing.LINEAR, Optional.empty(), false));
            MultiblockMotion.Transform start = motion.sample(0, 2, 2, 2);
            MultiblockMotion.Transform middle = motion.sample(5, 2, 2, 2);
            MultiblockMotion.Transform end = motion.sample(10, 2, 2, 2);
            if (!near(start.offset(), Vec3.ZERO)
                    || !near(middle.offset(), new Vec3(0, 1, 0))
                    || !near(end.offset(), new Vec3(0, 2, 0))) {
                throw helper.assertionException(Component.literal(
                        "Multiblock motion offset sampling is wrong"));
            }
            if (!near(middle.rotation(), new Vec3(0, 45, 0))) {
                throw helper.assertionException(Component.literal(
                        "Multiblock motion rotation sampling is wrong"));
            }
            if (!near(start.pivot(), new Vec3(1, 1, 1))) {
                throw helper.assertionException(Component.literal(
                        "Multiblock motion default pivot is wrong"));
            }
        }

        private static void assertMotionPatternIgnore(GameTestHelper helper) {
            MultiblockPattern pattern = MultiblockPattern.builder()
                    .matcher('S', Blocks.STONE)
                    .matcher('A', Blocks.STONE)
                    .layer("CSA")
                    .build();
            BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
            helper.setBlock(new BlockPos(1, 1, 0), Blocks.STONE);
            helper.setBlock(new BlockPos(2, 1, 0), Blocks.AIR);
            boolean without = pattern.matches(helper.getLevel(), origin, Rotation.NONE, Set.of());
            boolean with = pattern.matches(helper.getLevel(), origin, Rotation.NONE, Set.of('A'));
            if (without || !with) {
                throw helper.assertionException(Component.literal(
                        "Multiblock motion keys were not ignored while moving"));
            }
        }

        private static void assertMotionSnapshot(GameTestHelper helper) {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("test", 7);
            StructureSnapshot snapshot = new StructureSnapshot(2, 1, 1, List.of(
                    new PartEntry(0, 0, 0, Blocks.STONE.defaultBlockState(), Optional.of(nbt)),
                    new PartEntry(1, 0, 0, Blocks.GLASS.defaultBlockState(), Optional.empty())));
            StructureSnapshot decoded = StructureSnapshot.fromTag(snapshot.toTag());
            if (decoded.sx() != 2 || decoded.sy() != 1 || decoded.sz() != 1
                    || decoded.parts().size() != 2) {
                throw helper.assertionException(Component.literal(
                        "Multiblock motion snapshot did not round-trip"));
            }
            PartEntry first = decoded.parts().getFirst();
            if (!first.state().is(Blocks.STONE) || first.nbt().orElseThrow().getIntOr("test", 0) != 7) {
                throw helper.assertionException(Component.literal(
                        "Multiblock motion snapshot lost the block state or NBT"));
            }
        }

        private static void placeSteamGeneratorShell(GameTestHelper helper, BlockPos controller) {
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    helper.getLevel().setBlock(
                            controller.offset(dx, 0, dz),
                            Blocks.WAXED_COPPER_BLOCK.defaultBlockState(),
                            Block.UPDATE_ALL);
                }
            }
        }

        private static boolean near(Vec3 actual, Vec3 expected) {
            return actual.distanceToSqr(expected) < 1.0E-4;
        }

        private static ItemSlot slot(String id, ItemSlotRole role, int x, int y) {
            return new ItemSlot(id, role, null, 64, Set.of(), Set.of(), new SlotPosition(x, y));
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return MapCodec.unit(this);
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal(
                    "maquina_teste fallback gui layout, explicit slot positions and generated controller recipe");
        }
    }
}
