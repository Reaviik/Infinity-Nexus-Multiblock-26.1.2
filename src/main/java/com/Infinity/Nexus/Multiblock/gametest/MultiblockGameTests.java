package com.Infinity.Nexus.Multiblock.gametest;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import java.util.List;
import java.util.Set;

import com.Infinity.Nexus.Multiblock.api.FluidTank;
import com.Infinity.Nexus.Multiblock.api.GuiElement;
import com.Infinity.Nexus.Multiblock.api.GuiElementType;
import com.Infinity.Nexus.Multiblock.api.ItemSlot;
import com.Infinity.Nexus.Multiblock.api.ItemSlotRole;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockGui;
import com.Infinity.Nexus.Multiblock.api.MultiblockMenuLayout;
import com.Infinity.Nexus.Multiblock.api.MultiblockPattern;
import com.Infinity.Nexus.Multiblock.api.MultiblockType;
import com.Infinity.Nexus.Multiblock.api.SlotLock;
import com.Infinity.Nexus.Multiblock.api.SlotPosition;
import com.Infinity.Nexus.Multiblock.block.MultiblockItemHandler;
import com.Infinity.Nexus.Multiblock.registry.ModBlocks;
import com.Infinity.Nexus.Multiblock.registry.ModDataComponents;
import com.mojang.serialization.MapCodec;
import java.util.EnumSet;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.server.loading.ServerModLoader;

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
            assertEarlySlotPositions(helper);
            assertFluidOnlyGui(helper);
            assertSlotLockBehavior(helper);
            assertControllerRecipe(helper);
            helper.succeed();
        }

        private static void assertControllerRecipe(GameTestHelper helper) {
            Identifier definitionId = Identifier.fromNamespaceAndPath(
                    InfinityNexusMultiblock.MOD_ID, "fabrica_de_voxel_cube1");
            Identifier recipeId = Identifier.fromNamespaceAndPath(
                    InfinityNexusMultiblock.MOD_ID, "controller/fabrica_de_voxel_cube1");
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
