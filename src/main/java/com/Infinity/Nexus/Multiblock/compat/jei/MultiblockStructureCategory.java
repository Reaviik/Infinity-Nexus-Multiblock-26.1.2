package com.Infinity.Nexus.Multiblock.compat.jei;

import java.util.List;
import java.util.Optional;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.client.preview.MultiblockPreview;
import com.Infinity.Nexus.Multiblock.client.preview.MultiblockStructureData;
import com.mojang.blaze3d.platform.InputConstants.Type;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class MultiblockStructureCategory extends AbstractRecipeCategory<MultiblockStructureData> {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 176;
    private static final int SCENE = 124;
    private static final int PER_ROW = 8;
    private static final int MAX_MATERIALS = 15;
    private static final String CONTROLLER_SLOT = "controller";
    private static final int CONTROLLER_X = 2;
    private static final int CONTROLLER_Y = 2;

    public MultiblockStructureCategory(
            IGuiHelper guiHelper,
            IRecipeType<MultiblockStructureData> recipeType,
            ItemLike icon) {
        super(
                recipeType,
                Component.translatable("jei.infinity_nexus_multiblock.multiblock_structure"),
                guiHelper.createDrawableItemLike(icon),
                WIDTH,
                HEIGHT);
    }

    @Override
    public void draw(
            MultiblockStructureData structure,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphicsExtractor graphics,
            double mouseX,
            double mouseY) {
        graphics.fill(0, 0, WIDTH, HEIGHT, 0xFF101010);
        graphics.fill(0, SCENE, WIDTH, HEIGHT, 0xFF1B1B1B);
        graphics.fill(0, SCENE, WIDTH, SCENE + 1, 0xFF3A3A3A);
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            MultiblockStructureData structure,
            IFocusGroup focuses) {
        List<MultiblockStructureData.Material> materials = structure.materials();
        int shown = Math.min(MAX_MATERIALS, materials.size());
        for (int index = 0; index < shown; index++) {
            MultiblockStructureData.Material material = materials.get(index);
            List<ItemStack> stacks = materialStacks(material);
            IRecipeSlotBuilder slot = builder
                    .addInputSlot(1 + index % PER_ROW * 18, SCENE + 4 + index / PER_ROW * 18)
                    .setStandardSlotBackground()
                    .addItemStacks(stacks);
            slot.addRichTooltipCallback((view, tooltip) -> {
                if (material.count() > 1) {
                    tooltip.add(Component.translatable(
                            "gui.infinity_nexus_multiblock.jei.structure.amount",
                            material.count()));
                }
                if (material.description() != null) {
                    tooltip.add(material.description().copy().withColor(0xFFAAAAAA));
                }
            });
        }

        for (int index = shown; index < materials.size(); index++) {
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                    .addItemStacks(materialStacks(materials.get(index)));
        }

        if (!structure.controller().isEmpty()) {
            IRecipeSlotBuilder controller = builder
                    .addInputSlot(CONTROLLER_X, CONTROLLER_Y)
                    .setSlotName(CONTROLLER_SLOT)
                    .setStandardSlotBackground()
                    .addItemStacks(List.of(structure.controller().copy()));
            controller.addRichTooltipCallback((view, tooltip) -> tooltip.add(
                    Component.translatable("gui.infinity_nexus_multiblock.jei.structure.controller")));
        }
    }

    @Override
    public void createRecipeExtras(
            IRecipeExtrasBuilder builder,
            MultiblockStructureData structure,
            IFocusGroup focuses) {
        Model model = new Model(new MultiblockPreview(structure));
        builder.addWidget(model);
        builder.addInputHandler(model);

        builder.getRecipeSlots().findSlotByName(CONTROLLER_SLOT).ifPresent(slot -> {
            slot.setPosition(0, 0);
            builder.addSlottedWidget(new ControllerWidget(slot), List.of(slot));
        });
    }

    @Override
    public Identifier getIdentifier(MultiblockStructureData structure) {
        Identifier id = structure.definition().id();
        return Identifier.fromNamespaceAndPath(
                InfinityNexusMultiblock.MOD_ID,
                "multiblock_structure/" + id.getNamespace() + "/" + id.getPath());
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    private static List<ItemStack> materialStacks(MultiblockStructureData.Material material) {
        int count = Math.max(1, material.count());
        return material.stacks().stream()
                .map(stack -> stack.copyWithCount(count))
                .toList();
    }

    private record ControllerWidget(IRecipeSlotDrawable slot) implements ISlottedRecipeWidget {
        @Override
        public ScreenPosition getPosition() {
            return new ScreenPosition(CONTROLLER_X, CONTROLLER_Y);
        }

        @Override
        public ScreenRectangle getScreenRectangle() {
            return new ScreenRectangle(CONTROLLER_X, CONTROLLER_Y, 18, 18);
        }

        @Override
        public void drawWidget(GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            this.slot.draw(graphics, this.slot.isMouseOver(mouseX, mouseY));
        }

        @Override
        public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
            if (this.slot.isMouseOver(mouseX, mouseY)) {
                return Optional.of(new RecipeSlotUnderMouse(this.slot, getPosition()));
            }
            return Optional.empty();
        }
    }

    private record Model(MultiblockPreview preview) implements IRecipeWidget, IJeiInputHandler {
        @Override
        public ScreenPosition getPosition() {
            return new ScreenPosition(0, 0);
        }

        @Override
        public ScreenRectangle getScreenRectangle() {
            return new ScreenRectangle(0, 0, WIDTH, SCENE);
        }

        @Override
        public ScreenRectangle getArea() {
            return new ScreenRectangle(0, 0, WIDTH, SCENE);
        }

        @Override
        public void drawWidget(GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            this.preview.render(
                    graphics,
                    0,
                    0,
                    WIDTH,
                    SCENE,
                    (int) mouseX,
                    (int) mouseY,
                    MultiblockPreview.animationTime());
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
            List<Component> lines = this.preview.tooltip(mouseX, mouseY);
            if (lines != null) {
                tooltip.addAll(lines);
            }
        }

        @Override
        public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
            if (overController(mouseX, mouseY)) {
                return false;
            }
            if (input.getKey().getType() != Type.MOUSE) {
                return false;
            }
            if (input.isSimulate()) {
                return this.preview.overScene(mouseX, mouseY)
                        || mouseY >= SCENE - MultiblockPreview.BAR && mouseY < SCENE;
            }
            return this.preview.click(mouseX, mouseY);
        }

        private static boolean overController(double mouseX, double mouseY) {
            return mouseX >= CONTROLLER_X
                    && mouseX < CONTROLLER_X + 18
                    && mouseY >= CONTROLLER_Y
                    && mouseY < CONTROLLER_Y + 18;
        }

        @Override
        public boolean handleMouseScrolled(
                double mouseX,
                double mouseY,
                double scrollDeltaX,
                double scrollDeltaY) {
            return this.preview.scroll(mouseX, mouseY, scrollDeltaY);
        }
    }
}
