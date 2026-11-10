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
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeIngredient;
import com.Infinity.Nexus.Multiblock.recipe.MultiblockRecipeOutput;
import com.Infinity.Nexus.Multiblock.registry.ModBlocks;
import mezz.jei.api.gui.ITickTimer;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jspecify.annotations.Nullable;

public final class MultiblockJeiCategory extends AbstractRecipeCategory<MultiblockJeiRecipe> {
    private static final int WIDTH = 176;
    private static final int HEADER = 18;
    private static final int HEIGHT = 86 + HEADER;
    private static final int SLOT_SIZE = 18;
    private static final int CONTROLLER_X = 3;
    private static final int CONTROLLER_Y = 3;
    private static final String CONTROLLER_SLOT = "controller";
    private static final int TITLE_X = 20;
    private static final int TITLE_Y = 4;
    private static final long ENTITY_PAGE_MILLIS = 2000L;
    private static final int BAR_INSET = 1;
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID,
            "textures/gui/jei_base.png");

    private final IDrawable fallbackBackground;
    private final IGuiHelper guiHelper;
    private final Map<Identifier, LayoutCache> layouts = new HashMap<>();

    public MultiblockJeiCategory(
            IGuiHelper guiHelper,
            IRecipeType<MultiblockJeiRecipe> recipeType,
            ItemLike catalyst) {
        super(
                recipeType,
                Component.translatable("gui.infinity_nexus_multiblock.jei.category"),
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
        MultiblockMenuLayout layout = layout(recipe.definition());
        for (GuiElement element : layout.elements()) {
            if (element.type() == GuiElementType.FLUID_BAR
                    && fits(element.x(), element.y() + HEADER, element.width(), element.height())) {
                MultiblockGuiRender.drawFrame(guiGraphics, element.x(), element.y() + HEADER,
                        element.width(), element.height());
            }
        }
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MultiblockJeiRecipe jeiRecipe, IFocusGroup focuses) {
        MultiblockDefinition definition = jeiRecipe.definition();
        MultiblockRecipe recipe = jeiRecipe.recipe();
        MultiblockMenuLayout layout = layout(definition);
        List<MultiblockRecipeOutput> ingredientOutputs = recipe.outputs().stream()
                .filter(MultiblockJeiCategory::hasIngredientOutput)
                .toList();

        builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, CONTROLLER_X, CONTROLLER_Y)
                .setSlotName(CONTROLLER_SLOT)
                .setStandardSlotBackground()
                .addItemStacks(List.of(ModBlocks.controllerWithDefinition(definition.id())))
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(definition.displayName()));

        int inputIndex = 0;
        int outputIndex = 0;
        for (SlotPlacement placement : layout.slots()) {
            boolean input = isInputRole(placement.role());
            boolean output = placement.role() == ItemSlotRole.OUTPUT;
            int slotY = placement.y() + HEADER;
            if (!fits(placement.x(), slotY, SLOT_SIZE, SLOT_SIZE)) {
                if (input && inputIndex < recipe.ingredients().size()) {
                    builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                            .add(recipe.ingredients().get(inputIndex));
                }
                if (output && outputIndex < ingredientOutputs.size()) {
                    addInvisibleOutput(builder, ingredientOutputs.get(outputIndex));
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
                        slotY + 1);
                slot.setStandardSlotBackground();
                if (hasIngredient) {
                    MultiblockRecipeIngredient.Item itemInput = recipe.itemInputs().get(inputIndex);
                    addInputIngredient(slot, itemInput);
                    addItemCountTooltip(slot, itemInput.count());
                }
                inputIndex++;
            } else if (output) {
                boolean hasResult = outputIndex < ingredientOutputs.size();
                IRecipeSlotBuilder slot = builder.addSlot(
                        hasResult ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.RENDER_ONLY,
                        placement.x(),
                        slotY + 1);
                slot.setStandardSlotBackground();
                if (hasResult) {
                    addOutputIngredient(slot, ingredientOutputs.get(outputIndex));
                }
                outputIndex++;
            } else {
                builder.addSlot(RecipeIngredientRole.RENDER_ONLY, placement.x(), slotY + 1)
                        .setStandardSlotBackground();
            }
        }

        if (inputIndex == 0) {
            for (int index = 0; index < recipe.ingredients().size(); index++) {
                SlotPosition position = new SlotPosition(
                        8 + (index % 3) * SLOT_SIZE,
                        17 + HEADER + (index / 3) * SLOT_SIZE);
                if (!fits(position.x(), position.y(), SLOT_SIZE, SLOT_SIZE)) {
                    builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                            .add(recipe.ingredients().get(index));
                    continue;
                }
                IRecipeSlotBuilder slot = builder.addInputSlot(position.x(), position.y() + 1)
                        .setStandardSlotBackground();
                MultiblockRecipeIngredient.Item itemInput = recipe.itemInputs().get(index);
                addInputIngredient(slot, itemInput);
                addItemCountTooltip(slot, itemInput.count());
            }
        } else {
            for (int index = inputIndex; index < recipe.ingredients().size(); index++) {
                builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                        .add(recipe.ingredients().get(index));
            }
        }

        if (outputIndex == 0) {
            for (int index = 0; index < ingredientOutputs.size(); index++) {
                MultiblockRecipeOutput output = ingredientOutputs.get(index);
                SlotPosition position = new SlotPosition(120 + index * SLOT_SIZE, 35 + HEADER);
                if (index < 3 && fits(position.x(), position.y(), SLOT_SIZE, SLOT_SIZE)) {
                    IRecipeSlotBuilder slot = builder.addOutputSlot(position.x(), position.y() + 1)
                            .setStandardSlotBackground();
                    addOutputIngredient(slot, output);
                } else {
                    addInvisibleOutput(builder, output);
                }
            }
        } else {
            for (int index = outputIndex; index < ingredientOutputs.size(); index++) {
                addInvisibleOutput(builder, ingredientOutputs.get(index));
            }
        }

        for (int index = 0; index < recipe.fluidIngredients().size(); index++) {
            SizedFluidIngredient fluidIngredient = recipe.fluidIngredients().get(index);
            Optional<GuiElement> fluidBar = fluidBar(definition, layout, index);
            int x;
            int y;
            int width;
            int height;
            if (fluidBar.isPresent()
                    && fits(fluidBar.get().x(), fluidBar.get().y() + HEADER,
                            fluidBar.get().width(), fluidBar.get().height())) {
                GuiElement element = fluidBar.get();
                x = element.x() + BAR_INSET;
                y = element.y() + HEADER + BAR_INSET;
                width = Math.max(1, element.width() - BAR_INSET * 2);
                height = Math.max(1, element.height() - BAR_INSET * 2);
            } else {
                SlotPosition position = new SlotPosition(64, index == 0 ? 17 : 53);
                if (!fits(position.x(), position.y() + HEADER, 16, 16)) {
                    builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                            .addIngredients(NeoForgeTypes.FLUID_STACK, fluidStacks(fluidIngredient));
                    continue;
                }
                x = position.x();
                y = position.y() + HEADER;
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

        builder.getRecipeSlots().findSlotByName(CONTROLLER_SLOT).ifPresent(slot -> {
            slot.setPosition(0, 0);
            builder.addSlottedWidget(new ControllerWidget(slot), List.of(slot));
        });

        boolean progressAdded = false;
        for (GuiElement element : layout.elements()) {
            if (element.type() == GuiElementType.PROGRESS_BAR
                    && fits(element.x(), element.y() + HEADER, element.width(), element.height())) {
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

        List<EntityPreview> entityPreviews = new ArrayList<>();
        HolderGetter<EntityType<?>> entityRegistry = entityTypeRegistry();
        if (entityRegistry != null) {
            for (MultiblockRecipeIngredient.Entity input : recipe.entityInputs()) {
                for (EntityType<?> entityType : input.type().resolve(entityRegistry)) {
                    entityPreviews.add(new EntityPreview(entityType, false, input.count(), 1.0F));
                }
            }
            for (MultiblockRecipeOutput output : recipe.outputs()) {
                if (output instanceof MultiblockRecipeOutput.Entity entityOutput) {
                    for (EntityType<?> entityType : entityOutput.type().resolve(entityRegistry)) {
                        entityPreviews.add(new EntityPreview(
                                entityType,
                                true,
                                entityOutput.count(),
                                entityOutput.chance()));
                    }
                }
            }
        }
        if (!entityPreviews.isEmpty()) {
            for (GuiElement element : layout.elements()) {
                if (element.type() == GuiElementType.ENTITY
                        && fits(element.x(), element.y() + HEADER, element.width(), element.height())) {
                    builder.addWidget(new EntityWidget(List.copyOf(entityPreviews), element));
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
        int energyOutput = recipe.outputs().stream()
                .filter(output -> output instanceof MultiblockRecipeOutput.Energy)
                .mapToInt(output -> ((MultiblockRecipeOutput.Energy) output).amount())
                .sum();
        if (energyOutput > 0) {
            stats = stats.copy()
                    .append(" | ")
                    .append(Component.translatable(
                            "gui.infinity_nexus_multiblock.jei.energy_output",
                            energyOutput));
        }
        if (!recipe.entityInputs().isEmpty()) {
            stats = stats.copy()
                    .append(" | ")
                    .append(Component.translatable(
                            "gui.infinity_nexus_multiblock.jei.entity_inputs",
                            entityInputNames(recipe.entityInputs())));
        }
        stats = stats.copy()
                .append(" | ")
                .append(Component.translatable(
                        "gui.infinity_nexus_multiblock.jei.tier",
                        definition.tier()));
        builder.addText(definition.displayName(), WIDTH - TITLE_X, 10)
                .setPosition(TITLE_X, TITLE_Y)
                .setColor(0xFF404040)
                .setShadow(false);
        builder.addText(stats, WIDTH, 10)
                .setPosition(0, 74 + HEADER)
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
        if (arrow != null && fits(arrow.x(), arrow.y() + HEADER, arrow.width(), arrow.height())) {
            return new SlotPosition(arrow.x(), arrow.y() + HEADER);
        }
        return new SlotPosition(64, 35 + HEADER);
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

    private static boolean hasIngredientOutput(MultiblockRecipeOutput output) {
        return output instanceof MultiblockRecipeOutput.Item
                || output instanceof MultiblockRecipeOutput.Fluid;
    }

    private static void addOutputIngredient(IRecipeSlotBuilder slot, MultiblockRecipeOutput output) {
        if (output instanceof MultiblockRecipeOutput.Item item) {
            slot.add(item.stack());
            addChanceTooltip(slot, item.chance());
        } else if (output instanceof MultiblockRecipeOutput.Fluid fluid) {
            FluidStack stack = fluid.stack();
            slot.setFluidRenderer(stack.getAmount(), true, 16, 16);
            slot.add(stack.getFluid(), stack.getAmount());
            addChanceTooltip(slot, fluid.chance());
        }
    }

    private static void addChanceTooltip(IRecipeSlotBuilder slot, float chance) {
        if (chance >= 0.999F) {
            return;
        }
        slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(
                "gui.infinity_nexus_multiblock.jei.chance",
                Math.round(chance * 100.0F))));
    }

    private static void addInputIngredient(
            IRecipeSlotBuilder slot,
            MultiblockRecipeIngredient.Item input) {
        Ingredient ingredient = input.ingredient();
        if (ingredient.isCustom()) {
            slot.add(ingredient.display());
            return;
        }
        List<ItemStack> stacks = ingredient.items()
                .map(item -> new ItemStack(item.value(), input.count()))
                .toList();
        if (stacks.isEmpty()) {
            slot.add(ingredient);
            return;
        }
        stacks.forEach(slot::add);
    }

    private static void addItemCountTooltip(IRecipeSlotBuilder slot, int count) {
        if (count <= 1) {
            return;
        }
        slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable(
                "gui.infinity_nexus_multiblock.jei.amount",
                count)));
    }

    private static String entityInputNames(List<MultiblockRecipeIngredient.Entity> inputs) {
        return inputs.stream()
                .map(input -> input.type().displayName().getString())
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private static @Nullable HolderGetter<EntityType<?>> entityTypeRegistry() {
        Level level = Minecraft.getInstance().level;
        return level == null ? null : level.registryAccess().lookupOrThrow(Registries.ENTITY_TYPE);
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
        private final List<EntityPreview> previews;
        private final GuiElement element;
        private @Nullable List<Entity> entities;
        private int page;

        private EntityWidget(List<EntityPreview> previews, GuiElement element) {
            this.previews = previews;
            this.element = element;
        }

        @Override
        public ScreenPosition getPosition() {
            return new ScreenPosition(this.element.x(), this.element.y() + HEADER);
        }

        @Override
        public ScreenRectangle getScreenRectangle() {
            return new ScreenRectangle(this.element.x(), this.element.y() + HEADER,
                    this.element.width(), this.element.height());
        }

        @Override
        public void drawWidget(GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            int width = this.element.width();
            int height = this.element.height();
            MultiblockGuiRender.EntityPage page = MultiblockGuiRender.entityPage(
                    0, 0, width, height, this.previews.size(), currentPage());
            this.page = page.page();
            MultiblockGuiRender.drawFrame(graphics, 0, 0, width, height);
            if (this.entities == null) {
                List<Entity> created = new ArrayList<>(this.previews.size());
                for (EntityPreview preview : this.previews) {
                    created.add(MultiblockGuiRender.createEntity(preview.entityType()));
                }
                this.entities = created;
            }
            if (!this.entities.isEmpty()) {
                int originX = Math.round(graphics.pose().m20());
                int originY = Math.round(graphics.pose().m21());
                MultiblockGuiRender.renderEntities(graphics, this.entities,
                        originX, originY, width, height, this.page);
            }
            MultiblockGuiRender.drawPageIndicator(graphics, Minecraft.getInstance().font,
                    0, 0, width, height, page);
            MultiblockGuiRender.drawBorder(graphics, 0, 0, width, height);
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
            MultiblockGuiRender.EntityPage page = MultiblockGuiRender.entityPage(
                    0, 0, this.element.width(), this.element.height(), this.previews.size(), this.page);
            for (int index = 0; index < page.cells().size(); index++) {
                MultiblockGuiRender.EntityCell cell = page.cells().get(index);
                if (mouseX >= cell.x() && mouseX < cell.x() + cell.width()
                        && mouseY >= cell.y() && mouseY < cell.y() + cell.height()) {
                    EntityPreview preview = this.previews.get(page.firstIndex() + index);
                    tooltip.add(Component.translatable(preview.output()
                            ? "gui.infinity_nexus_multiblock.jei.entity_output"
                            : "gui.infinity_nexus_multiblock.jei.entity_input"));
                    tooltip.add(Component.empty()
                            .append(preview.entityType().getDescription())
                            .append(" x" + preview.count()));
                    if (preview.output() && preview.chance() < 0.999F) {
                        tooltip.add(Component.translatable(
                                "gui.infinity_nexus_multiblock.jei.chance",
                                Math.round(preview.chance() * 100.0F)));
                    }
                    return;
                }
            }
        }

        private int currentPage() {
            int pageCount = MultiblockGuiRender.pageCount(
                    this.element.width(), this.element.height(), this.previews.size());
            if (pageCount <= 1) {
                return 0;
            }
            return (int) ((System.currentTimeMillis() / ENTITY_PAGE_MILLIS) % pageCount);
        }
    }

    private record EntityPreview(EntityType<?> entityType, boolean output, int count, float chance) {
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
            return new ScreenPosition(element.x(), element.y() + HEADER);
        }

        @Override
        public ScreenRectangle getScreenRectangle() {
            return new ScreenRectangle(element.x(), element.y() + HEADER, element.width(), element.height());
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
            int innerWidth = Math.max(0, width - BAR_INSET * 2);
            int innerHeight = Math.max(0, height - BAR_INSET * 2);
            int filled = filled(innerWidth, innerHeight);
            switch (element.direction()) {
                case UP -> graphics.fill(BAR_INSET, BAR_INSET + innerHeight - filled,
                        BAR_INSET + innerWidth, BAR_INSET + innerHeight, element.color());
                case DOWN -> graphics.fill(BAR_INSET, BAR_INSET,
                        BAR_INSET + innerWidth, BAR_INSET + filled, element.color());
                case LEFT -> graphics.fill(BAR_INSET + innerWidth - filled, BAR_INSET,
                        BAR_INSET + innerWidth, BAR_INSET + innerHeight, element.color());
                case RIGHT -> graphics.fill(BAR_INSET, BAR_INSET,
                        BAR_INSET + filled, BAR_INSET + innerHeight, element.color());
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
            boolean hovered = mouseX >= 0 && mouseX < 18 && mouseY >= 0 && mouseY < 18;
            this.slot.draw(graphics, hovered);
        }

        @Override
        public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
            if (mouseX >= 0 && mouseX < 18 && mouseY >= 0 && mouseY < 18) {
                return Optional.of(new RecipeSlotUnderMouse(this.slot, getPosition()));
            }
            return Optional.empty();
        }
    }

    private record LayoutCache(MultiblockDefinition definition, MultiblockMenuLayout layout) {
    }
}
