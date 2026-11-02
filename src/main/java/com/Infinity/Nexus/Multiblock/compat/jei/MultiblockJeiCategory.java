package com.Infinity.Nexus.Multiblock.compat.jei;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.api.GuiDirection;
import com.Infinity.Nexus.Multiblock.api.GuiElement;
import com.Infinity.Nexus.Multiblock.api.GuiElementType;
import com.Infinity.Nexus.Multiblock.api.ItemSlotRole;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockMenuLayout;
import com.Infinity.Nexus.Multiblock.api.MultiblockMenuLayout.SlotPlacement;
import com.Infinity.Nexus.Multiblock.api.SlotPosition;
import com.Infinity.Nexus.Multiblock.client.MultiblockGuiRender;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipe;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeOutput;
import mezz.jei.api.gui.ITickTimer;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jspecify.annotations.Nullable;

public final class MultiblockJeiCategory extends AbstractRecipeCategory<MultiblockJeiRecipe> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 86;
    private static final int SLOT_SIZE = 18;
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "textures/gui/jei_base.png");

    private final IDrawable fallbackBackground;
    private final IGuiHelper guiHelper;
    private final Map<Identifier, LayoutCache> layouts = new HashMap<>();

    public MultiblockJeiCategory(
            IGuiHelper guiHelper,
            IRecipeType<MultiblockJeiRecipe> recipeType,
            Identifier definitionId,
            ItemLike catalyst) {
        super(
                recipeType,
                title(definitionId),
                guiHelper.createDrawableItemLike(catalyst),
                WIDTH,
                HEIGHT);
        this.fallbackBackground = guiHelper.drawableBuilder(TEXTURE, 0, 0, WIDTH, HEIGHT)
                .setTextureSize(WIDTH, HEIGHT)
                .build();
        this.guiHelper = guiHelper;
    }

    @Override
    public void draw(
            MultiblockJeiRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphicsExtractor guiGraphics,
            double mouseX,
            double mouseY) {
        this.fallbackBackground.draw(guiGraphics);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MultiblockJeiRecipe jeiRecipe, IFocusGroup focuses) {
        MultiblockDefinition definition = jeiRecipe.definition();
        MultiblockRecipe recipe = jeiRecipe.recipe();
        MultiblockMenuLayout layout = layout(definition);

        int inputIndex = 0;
        int outputIndex = 0;
        for (SlotPlacement placement : layout.slots()) {
            boolean input = isInputRole(placement.role());
            boolean output = placement.role() == ItemSlotRole.OUTPUT;
            if (!fits(placement.x(), placement.y(), SLOT_SIZE, SLOT_SIZE)) {
                if (input && inputIndex < recipe.ingredients().size()) {
                    builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                            .add(recipe.ingredients().get(inputIndex));
                }
                if (output && outputIndex == 0) {
                    addInvisibleOutput(builder, recipe.output());
                }
                inputIndex += input ? 1 : 0;
                outputIndex += output ? 1 : 0;
                continue;
            }
            if (input) {
                boolean hasIngredient = inputIndex < recipe.ingredients().size();
                IRecipeSlotBuilder slot = builder.addSlot(
                        hasIngredient ? RecipeIngredientRole.INPUT : RecipeIngredientRole.RENDER_ONLY,
                        placement.x(),
                        placement.y());
                slot.setStandardSlotBackground();
                if (hasIngredient) {
                    slot.add(recipe.ingredients().get(inputIndex));
                }
                inputIndex++;
            } else if (output) {
                boolean hasResult = outputIndex == 0 && hasIngredientOutput(recipe.output());
                IRecipeSlotBuilder slot = builder.addSlot(
                        hasResult ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.RENDER_ONLY,
                        placement.x(),
                        placement.y());
                slot.setStandardSlotBackground();
                if (hasResult) {
                    addOutputIngredient(slot, recipe.output());
                }
                outputIndex++;
            } else {
                builder.addSlot(RecipeIngredientRole.RENDER_ONLY, placement.x(), placement.y())
                        .setStandardSlotBackground();
            }
        }

        if (inputIndex == 0) {
            for (int index = 0; index < recipe.ingredients().size(); index++) {
                SlotPosition position = new SlotPosition(
                        8 + (index % 3) * SLOT_SIZE,
                        17 + (index / 3) * SLOT_SIZE);
                if (!fits(position.x(), position.y(), SLOT_SIZE, SLOT_SIZE)) {
                    builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                            .add(recipe.ingredients().get(index));
                    continue;
                }
                builder.addInputSlot(position.x(), position.y())
                        .setStandardSlotBackground()
                        .add(recipe.ingredients().get(index));
            }
        } else {
            for (int index = inputIndex; index < recipe.ingredients().size(); index++) {
                builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                        .add(recipe.ingredients().get(index));
            }
        }

        if (outputIndex == 0 && hasIngredientOutput(recipe.output())) {
            SlotPosition output = new SlotPosition(120, 35);
            if (fits(output.x(), output.y(), SLOT_SIZE, SLOT_SIZE)) {
                IRecipeSlotBuilder slot = builder.addOutputSlot(output.x(), output.y())
                        .setStandardSlotBackground();
                addOutputIngredient(slot, recipe.output());
            } else {
                addInvisibleOutput(builder, recipe.output());
            }
        }

        List<SlotPlacement> fluidSlots = layout.slots().stream()
                .filter(slot -> slot.role() == ItemSlotRole.FLUID)
                .toList();
        for (int index = 0; index < recipe.fluidIngredients().size(); index++) {
            SizedFluidIngredient fluidIngredient = recipe.fluidIngredients().get(index);
            Optional<GuiElement> fluidBar = fluidBar(definition, layout, index);
            int x;
            int y;
            int width;
            int height;
            if (fluidBar.isPresent()
                    && fits(fluidBar.get().x(), fluidBar.get().y(), fluidBar.get().width(), fluidBar.get().height())) {
                GuiElement element = fluidBar.get();
                x = element.x();
                y = element.y();
                width = element.width();
                height = element.height();
            } else {
                SlotPosition position = itemPosition(fluidSlots, index, 64, index == 0 ? 17 : 53);
                if (!fits(position.x(), position.y(), 16, 16)) {
                    builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                            .addIngredients(NeoForgeTypes.FLUID_STACK, fluidStacks(fluidIngredient));
                    continue;
                }
                x = position.x();
                y = position.y();
                width = 16;
                height = 16;
            }
            IRecipeSlotBuilder slot = builder.addInputSlot(x, y)
                    .setFluidRenderer(fluidIngredient.amount(), true, width, height);
            for (Holder<Fluid> fluid : fluidIngredient.ingredient().fluids()) {
                slot.add(fluid.value(), fluidIngredient.amount());
            }
        }
    }

    @Override
    public void createRecipeExtras(
            IRecipeExtrasBuilder builder,
            MultiblockJeiRecipe jeiRecipe,
            IFocusGroup focuses) {
        MultiblockDefinition definition = jeiRecipe.definition();
        MultiblockRecipe recipe = jeiRecipe.recipe();
        MultiblockMenuLayout layout = layout(definition);
        int time = recipe.time();
        int animationTicks = Math.max(1, time);

        boolean progressAdded = false;
        for (GuiElement element : layout.elements()) {
            if (element.type() == GuiElementType.PROGRESS_BAR
                    && fits(element.x(), element.y(), element.width(), element.height())) {
                builder.addWidget(new ProgressBarWidget(layout, element, animationTicks, this.guiHelper));
                progressAdded = true;
            }
        }

        GuiElement arrow = findElement(layout, GuiElementType.ARROW).orElse(null);
        if (arrow != null) {
            SlotPosition arrowPosition = arrowPosition(arrow);
            builder.addAnimatedRecipeArrowWidget(animationTicks).setPosition(arrowPosition.x(), arrowPosition.y());
        } else if (!progressAdded) {
            SlotPosition arrowPosition = arrowPosition(null);
            builder.addAnimatedRecipeArrowWidget(animationTicks).setPosition(arrowPosition.x(), arrowPosition.y());
        }

        if (recipe.output() instanceof MultiblockRecipeOutput.Entity entityOutput) {
            for (GuiElement element : layout.elements()) {
                if (element.type() == GuiElementType.ENTITY
                        && fits(element.x(), element.y(), element.width(), element.height())) {
                    builder.addWidget(new EntityWidget(entityOutput.entityType(), element));
                }
            }
        }

        Component stats = Component.translatable(
                "gui.infinity_nexus_multiblock.jei.time",
                formatTime(time));
        if (recipe.energy() > 0 || definition.energy().isPresent()) {
            stats = stats.copy()
                    .append(" | ")
                    .append(Component.translatable(
                            "gui.infinity_nexus_multiblock.jei.energy",
                            recipe.energy()));
        }
        stats = stats.copy()
                .append(" | ")
                .append(Component.translatable(
                        "gui.infinity_nexus_multiblock.jei.tier",
                        definition.tier()));
        builder.addText(stats, WIDTH, 10)
                .setPosition(0, 74)
                .setTextAlignment(HorizontalAlignment.CENTER)
                .setColor(0xFF404040)
                .setShadow(false);

    }

    @Override
    public Identifier getIdentifier(MultiblockJeiRecipe recipe) {
        Identifier recipeId = recipe.id();
        Identifier definitionId = recipe.definition().id();
        return Identifier.fromNamespaceAndPath(
                recipeId.getNamespace(),
                recipeId.getPath() + "/" + definitionId.getNamespace() + "/" + definitionId.getPath());
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    private MultiblockMenuLayout layout(MultiblockDefinition definition) {
        LayoutCache cached = this.layouts.get(definition.id());
        if (cached != null && cached.definition() == definition) {
            return cached.layout();
        }
        MultiblockMenuLayout layout = MultiblockMenuLayout.create(definition);
        this.layouts.put(definition.id(), new LayoutCache(definition, layout));
        return layout;
    }

    private static Component title(Identifier definitionId) {
        String key = "gui." + definitionId.getNamespace() + "." + definitionId.getPath();
        return Component.translatableWithFallback(key, definitionId.getPath());
    }

    private static Optional<GuiElement> fluidBar(
            MultiblockDefinition definition,
            MultiblockMenuLayout layout,
            int index) {
        List<GuiElement> fluidBars = layout.elements().stream()
                .filter(element -> element.type() == GuiElementType.FLUID_BAR)
                .toList();
        if (index < definition.fluidTanks().size()) {
            String tankId = definition.fluidTanks().get(index).id();
            for (GuiElement bar : fluidBars) {
                if (bar.capability().equals(tankId)) {
                    return Optional.of(bar);
                }
            }
        }
        return index < fluidBars.size() ? Optional.of(fluidBars.get(index)) : Optional.empty();
    }

    private static SlotPosition arrowPosition(GuiElement arrow) {
        if (arrow != null && fits(arrow.x(), arrow.y(), arrow.width(), arrow.height())) {
            return new SlotPosition(arrow.x(), arrow.y());
        }
        return new SlotPosition(64, 35);
    }

    private static Optional<GuiElement> findElement(
            MultiblockMenuLayout layout,
            GuiElementType type) {
        return layout.elements().stream()
                .filter(element -> element.type() == type)
                .findFirst();
    }

    private static boolean isInputRole(ItemSlotRole role) {
        return role == ItemSlotRole.INPUT;
    }

    private static boolean fits(int x, int y, int width, int height) {
        return x >= 0
                && y >= 0
                && x + width <= WIDTH
                && y + height <= HEIGHT;
    }

    private static SlotPosition itemPosition(
            List<SlotPlacement> slots,
            int index,
            int defaultX,
            int defaultY) {
        if (index < slots.size()) {
            SlotPlacement slot = slots.get(index);
            return new SlotPosition(slot.x(), slot.y());
        }
        return new SlotPosition(defaultX, defaultY);
    }

    private static boolean hasIngredientOutput(MultiblockRecipeOutput output) {
        return output instanceof MultiblockRecipeOutput.Item
                || output instanceof MultiblockRecipeOutput.Fluid;
    }

    private static void addOutputIngredient(IRecipeSlotBuilder slot, MultiblockRecipeOutput output) {
        if (output instanceof MultiblockRecipeOutput.Item item) {
            slot.add(item.stack());
        } else if (output instanceof MultiblockRecipeOutput.Fluid fluid) {
            FluidStack stack = fluid.stack();
            slot.setFluidRenderer(stack.getAmount(), true, 16, 16);
            slot.add(stack.getFluid(), stack.getAmount());
        }
    }

    private static void addInvisibleOutput(IRecipeLayoutBuilder builder, MultiblockRecipeOutput output) {
        if (output instanceof MultiblockRecipeOutput.Item item) {
            builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
                    .add(item.stack());
        } else if (output instanceof MultiblockRecipeOutput.Fluid fluid) {
            builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
                    .addIngredients(NeoForgeTypes.FLUID_STACK, List.of(fluid.stack()));
        }
    }

    private static List<FluidStack> fluidStacks(SizedFluidIngredient fluidIngredient) {
        List<FluidStack> stacks = new ArrayList<>();
        for (Holder<Fluid> fluid : fluidIngredient.ingredient().fluids()) {
            stacks.add(new FluidStack(fluid.value(), fluidIngredient.amount()));
        }
        return stacks;
    }

    private static String formatTime(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }

    private static final class EntityWidget implements IRecipeWidget {
        private final EntityType<?> entityType;
        private final GuiElement element;
        private @Nullable Entity entity;

        private EntityWidget(EntityType<?> entityType, GuiElement element) {
            this.entityType = entityType;
            this.element = element;
        }

        @Override
        public ScreenPosition getPosition() {
            return new ScreenPosition(this.element.x(), this.element.y());
        }

        @Override
        public ScreenRectangle getScreenRectangle() {
            return new ScreenRectangle(this.element.x(), this.element.y(),
                    this.element.width(), this.element.height());
        }

        @Override
        public void drawWidget(GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            int width = this.element.width();
            int height = this.element.height();
            MultiblockGuiRender.drawFrame(graphics, 0, 0, width, height);
            if (this.entity == null) {
                this.entity = MultiblockGuiRender.createEntity(this.entityType);
            }
            if (this.entity != null && width > 4 && height > 4) {
                int frameX = Math.round(graphics.pose().m20());
                int frameY = Math.round(graphics.pose().m21());
                MultiblockGuiRender.renderEntity(graphics, this.entity, frameX + 2, frameY + 2,
                        width - 4, height - 4);
            }
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
            tooltip.add(this.entityType.getDescription());
        }
    }

    private static final class ProgressBarWidget implements IRecipeWidget {
        private final MultiblockMenuLayout layout;
        private final GuiElement element;
        private final ITickTimer timer;

        private ProgressBarWidget(
                MultiblockMenuLayout layout,
                GuiElement element,
                int ticksPerCycle,
                IGuiHelper guiHelper) {
            this.layout = layout;
            this.element = element;
            int maxValue = isVertical(element.direction()) ? element.height() : element.width();
            this.timer = guiHelper.createTickTimer(
                    Math.max(1, ticksPerCycle),
                    Math.max(1, maxValue),
                    false);
        }

        @Override
        public ScreenPosition getPosition() {
            return new ScreenPosition(element.x(), element.y());
        }

        @Override
        public ScreenRectangle getScreenRectangle() {
            return new ScreenRectangle(element.x(), element.y(), element.width(), element.height());
        }

        @Override
        public void drawWidget(GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            int width = element.width();
            int height = element.height();
            if (drawsBarBackground()) {
                drawBarBackground(graphics, width, height);
            } else {
                blitTexture(graphics, width, height);
            }
            drawColorFill(graphics, width, height);
        }

        private void drawColorFill(GuiGraphicsExtractor graphics, int width, int height) {
            int filled = filled(width, height);
            switch (element.direction()) {
                case UP -> graphics.fill(0, height - filled, width, height, element.color());
                case DOWN -> graphics.fill(0, 0, width, filled, element.color());
                case LEFT -> graphics.fill(width - filled, 0, width, height, element.color());
                case RIGHT -> graphics.fill(0, 0, filled, height, element.color());
            }
        }

        private int filled(int width, int height) {
            int max = timer.getMaxValue();
            int value = Math.max(0, Math.min(max, timer.getValue()));
            return isVertical(element.direction())
                    ? Math.max(0, Math.min(height, (int) ((long) value * height / max)))
                    : Math.max(0, Math.min(width, (int) ((long) value * width / max)));
        }

        private void blitTexture(GuiGraphicsExtractor graphics, int width, int height) {
            Identifier texture = layout.texture();
            if (texture == null) {
                return;
            }
            blit(graphics, texture, 0, 0, sourceU(element), sourceV(element),
                    width, height, width, height);
        }

        private void blit(
                GuiGraphicsExtractor graphics,
                Identifier texture,
                int x,
                int y,
                int u,
                int v,
                int width,
                int height,
                int sourceWidth,
                int sourceHeight) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v,
                    width, height, sourceWidth, sourceHeight,
                    layout.textureWidth(), layout.textureHeight());
        }

        private boolean drawsBarBackground() {
            return layout.texture() == null || MultiblockMenuLayout.DEFAULT_TEXTURE.equals(layout.texture());
        }

        private static void drawBarBackground(GuiGraphicsExtractor graphics, int width, int height) {
            MultiblockGuiRender.drawFrame(graphics, 0, 0, width, height);
        }

        private static boolean isVertical(GuiDirection direction) {
            return direction == GuiDirection.UP || direction == GuiDirection.DOWN;
        }

        private static int sourceU(GuiElement element) {
            return element.u() >= 0 ? element.u() : element.x();
        }

        private static int sourceV(GuiElement element) {
            return element.v() >= 0 ? element.v() : element.y();
        }
    }

    private record LayoutCache(MultiblockDefinition definition, MultiblockMenuLayout layout) {}
}
