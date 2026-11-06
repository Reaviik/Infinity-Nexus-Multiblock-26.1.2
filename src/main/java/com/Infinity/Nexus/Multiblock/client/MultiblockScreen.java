package com.Infinity.Nexus.Multiblock.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.Infinity.Nexus.Core.renderer.FluidTankRenderer;
import com.Infinity.Nexus.Multiblock.api.GuiDirection;
import com.Infinity.Nexus.Multiblock.api.GuiElement;
import com.Infinity.Nexus.Multiblock.api.GuiElementType;
import com.Infinity.Nexus.Multiblock.api.MultiblockMenuLayout;
import com.Infinity.Nexus.Multiblock.api.SlotLock;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlockEntity;
import com.Infinity.Nexus.Multiblock.block.MultiblockEnergyStorage;
import com.Infinity.Nexus.Multiblock.block.MultiblockFluidHandler;
import com.Infinity.Nexus.Multiblock.menu.MultiblockMenu;
import com.Infinity.Nexus.Multiblock.menu.MultiblockSlot;
import com.Infinity.Nexus.Multiblock.network.SetFluidSlotTankPayload;
import com.Infinity.Nexus.Multiblock.network.SetSlotLockPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

public class MultiblockScreen extends AbstractContainerScreen<MultiblockMenu> {
    private static final int PANEL_COLOR = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int SLOT_SPRITE_U = 176;
    private static final int SLOT_SPRITE_V = 0;
    private static final int SLOT_SPRITE_SIZE = 18;
    private static final int TEXT_X_OFFSET = -1;
    private static final int TEXT_Y_OFFSET = 1;
    private static final int PIN_SIZE = 3;
    private static final int PIN_OFFSET_X = 13;
    private static final int PIN_OFFSET_Y = 0;
    private static final int PIN_LOCKED_COLOR = 0xFFFFC107;
    private static final int PIN_LOCKED_HOVER_COLOR = 0xFFFFF3B0;
    private static final int PIN_UNLOCKED_COLOR = 0x88555555;
    private static final int PIN_UNLOCKED_HOVER_COLOR = 0xCCFFFFFF;
    private static final int FLUID_PIN_COLOR = 0xFF29B6F6;
    private static final int FLUID_PIN_HOVER_COLOR = 0xFF81D4FA;
    private static final int FLUID_PIN_CENTER_COLOR = 0xFF0D47A1;
    private static final int GHOST_FADE_COLOR = 0xCC8B8B8B;
    private static final int BAR_INSET = 1;

    private final Map<String, FluidTankRenderer> fluidRenderers = new HashMap<>();
    private final Map<GuiElement, Integer> entityPages = new HashMap<>();
    private List<MultiblockControllerBlockEntity.AreaEntityCount> cachedAreaEntityCounts = List.of();
    private List<EntityType<?>> cachedRecipeEntityTypes = List.of();
    private List<EntityPreview> entityPreviews = List.of();
    private List<Entity> previewEntities = List.of();

    public MultiblockScreen(MultiblockMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.layout().width(), menu.layout().height());
    }

    @Override
    protected void init() {
        super.init();
        this.inventoryLabelY = 10000;
        this.titleLabelY = 10000;
    }

    @Override
    protected void renderSlotContents(GuiGraphicsExtractor graphics, ItemStack itemStack, Slot slot,
            @Nullable String itemCount) {
        if (itemStack.isEmpty()
                && slot instanceof MultiblockSlot multiblockSlot
                && multiblockSlot.isInput()) {
            SlotLock lock = this.menu.slotLock(slot.getSlotIndex());
            if (lock != null && !lock.stack().isEmpty()) {
                int seed = slot.x + slot.y * this.imageWidth;
                graphics.fakeItem(lock.stack(), slot.x, slot.y, seed);
                return;
            }
        }
        super.renderSlotContents(graphics, itemStack, slot, itemCount);
    }

    @Override
    protected void extractSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractSlots(graphics, mouseX, mouseY);
        this.extractSlotLockOverlays(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            MultiblockSlot pinSlot = this.pinSlotAt(event.x(), event.y());
            if (pinSlot != null) {
                boolean locked = this.menu.slotLock(pinSlot.getSlotIndex()) == null;
                ClientPacketDistributor.sendToServer(new SetSlotLockPayload(
                        this.menu.blockEntity().getBlockPos(), pinSlot.getSlotIndex(), locked));
                return true;
            }
            MultiblockSlot fluidPinSlot = this.fluidPinSlotAt(event.x(), event.y());
            if (fluidPinSlot != null) {
                MultiblockFluidHandler fluids = this.menu.blockEntity().fluidHandler();
                if (fluids == null || fluids.size() <= 1) {
                    return true;
                }
                int tankIndex = this.menu.blockEntity().fluidSlotTankIndex(fluidPinSlot.getSlotIndex());
                int nextTank = tankIndex < 0 ? 0 : (tankIndex + 1) % fluids.size();
                ClientPacketDistributor.sendToServer(new SetFluidSlotTankPayload(
                        this.menu.blockEntity().getBlockPos(), fluidPinSlot.getSlotIndex(), nextTank));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int count = entitiesForDisplay().size();
            for (GuiElement element : this.menu.layout().elements()) {
                if (element.type() != GuiElementType.ENTITY) {
                    continue;
                }
                int x = this.leftPos + element.x();
                int y = this.topPos + element.y();
                if (!isMouseOver((int) mouseX, (int) mouseY, x, y, element.width(), element.height())) {
                    continue;
                }
                int pageCount = MultiblockGuiRender.pageCount(element.width(), element.height(), count);
                if (pageCount <= 1) {
                    return false;
                }
                int page = Math.floorMod(
                        this.entityPages.getOrDefault(element, 0) + (scrollY < 0 ? 1 : -1),
                        pageCount);
                this.entityPages.put(element, page);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void extractSlotLockOverlays(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.nextStratum();
        for (Slot slot : this.menu.slots) {
            if (!(slot instanceof MultiblockSlot multiblockSlot) || !multiblockSlot.isInput()) {
                continue;
            }
            SlotLock lock = this.menu.slotLock(slot.getSlotIndex());
            if (lock != null && !lock.stack().isEmpty() && slot.getItem().isEmpty()) {
                graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GHOST_FADE_COLOR);
            }
        }
        for (Slot slot : this.menu.slots) {
            if (!(slot instanceof MultiblockSlot multiblockSlot) || !multiblockSlot.isInput()) {
                continue;
            }
            boolean locked = this.menu.slotLock(slot.getSlotIndex()) != null;
            boolean hovered = this.isMouseOverPin(slot, mouseX, mouseY);
            drawPin(graphics, slot.x + PIN_OFFSET_X, slot.y + PIN_OFFSET_Y, locked, hovered);
        }
        MultiblockFluidHandler fluids = this.menu.blockEntity().fluidHandler();
        if (fluids != null && fluids.size() > 1) {
            for (Slot slot : this.menu.slots) {
                if (!(slot instanceof MultiblockSlot multiblockSlot) || !multiblockSlot.isFluid()) {
                    continue;
                }
                boolean hovered = this.isMouseOverPin(slot, mouseX, mouseY);
                drawFluidPin(graphics, slot.x + PIN_OFFSET_X, slot.y + PIN_OFFSET_Y, hovered);
            }
        }
    }

    private @Nullable MultiblockSlot pinSlotAt(double mouseX, double mouseY) {
        for (Slot slot : this.menu.slots) {
            if (slot instanceof MultiblockSlot multiblockSlot
                    && multiblockSlot.isInput()
                    && this.isMouseOverPin(slot, mouseX, mouseY)) {
                return multiblockSlot;
            }
        }
        return null;
    }

    private @Nullable MultiblockSlot fluidPinSlotAt(double mouseX, double mouseY) {
        MultiblockFluidHandler fluids = this.menu.blockEntity().fluidHandler();
        if (fluids == null || fluids.size() <= 1) {
            return null;
        }
        for (Slot slot : this.menu.slots) {
            if (slot instanceof MultiblockSlot multiblockSlot
                    && multiblockSlot.isFluid()
                    && this.isMouseOverPin(slot, mouseX, mouseY)) {
                return multiblockSlot;
            }
        }
        return null;
    }

    private boolean isMouseOverPin(Slot slot, double mouseX, double mouseY) {
        int x = this.leftPos + slot.x + PIN_OFFSET_X;
        int y = this.topPos + slot.y + PIN_OFFSET_Y;
        return mouseX >= x && mouseX < x + PIN_SIZE
                && mouseY >= y && mouseY < y + PIN_SIZE;
    }

    private static void drawPin(GuiGraphicsExtractor graphics, int x, int y, boolean locked, boolean hovered) {
        int color = locked
                ? (hovered ? PIN_LOCKED_HOVER_COLOR : PIN_LOCKED_COLOR)
                : (hovered ? PIN_UNLOCKED_HOVER_COLOR : PIN_UNLOCKED_COLOR);
        if (locked) {
            graphics.fill(x, y, x + PIN_SIZE, y + PIN_SIZE, color);
            graphics.fill(x + 1, y + 1, x + 2, y + 2, 0xFF6B4F00);
        } else {
            graphics.fill(x, y, x + PIN_SIZE, y + 1, color);
            graphics.fill(x, y + PIN_SIZE - 1, x + PIN_SIZE, y + PIN_SIZE, color);
            graphics.fill(x, y, x + 1, y + PIN_SIZE, color);
            graphics.fill(x + PIN_SIZE - 1, y, x + PIN_SIZE, y + PIN_SIZE, color);
        }
    }

    private static void drawFluidPin(GuiGraphicsExtractor graphics, int x, int y, boolean hovered) {
        graphics.fill(x, y, x + PIN_SIZE, y + PIN_SIZE,
                hovered ? FLUID_PIN_HOVER_COLOR : FLUID_PIN_COLOR);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, FLUID_PIN_CENTER_COLOR);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        MultiblockMenuLayout layout = this.menu.layout();
        int x = this.leftPos;
        int y = this.topPos;

        if (layout.texture() != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, layout.texture(), x, y, 0.0F, 0.0F,
                    layout.width(), layout.height(), layout.textureWidth(), layout.textureHeight());
        } else {
            drawPanel(graphics, x, y, layout.width(), layout.height());
        }

        if (layout.texture() == null || isDefaultGui(layout)) {
            drawSlotBackgrounds(graphics, x, y, layout);
        }

        drawBars(graphics, x, y, layout);
        drawEntityElements(graphics, x, y, layout);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean hasTitle = false;
        for (GuiElement element : this.menu.layout().elements()) {
            switch (element.type()) {
                case TITLE -> {
                    hasTitle = true;
                    Component title = element.text().isBlank() ? this.title : Component.translatable(element.text());
                    drawGuiText(graphics, title, element.x(), element.y(), element.color(), element.shadow());
                }
                case TEXT -> {
                    if (!element.text().isBlank()) {
                        drawGuiText(graphics, Component.translatable(element.text()), element.x(), element.y(),
                                element.color(), element.shadow());
                    }
                }
                case PLAYER_INVENTORY -> {
                    Component title = element.text().isBlank()
                            ? this.playerInventoryTitle
                            : Component.translatable(element.text());
                    drawGuiText(graphics, title, element.x(), element.y() - 13, element.color(), element.shadow());
                }
                default -> {
                }
            }
        }
        if (!hasTitle) {
            drawGuiText(graphics, this.title, 8, 6, 0xFF404040, false);
        }
    }

    private void drawGuiText(GuiGraphicsExtractor graphics, Component text, int x, int y, int color, boolean shadow) {
        graphics.text(this.font, text, x + TEXT_X_OFFSET, y + TEXT_Y_OFFSET, color, shadow);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        MultiblockSlot pinSlot = this.pinSlotAt(mouseX, mouseY);
        if (pinSlot != null) {
            SlotLock lock = this.menu.slotLock(pinSlot.getSlotIndex());
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(lock == null
                    ? "gui.infinity_nexus_multiblock.slot_lock.lock"
                    : "gui.infinity_nexus_multiblock.slot_lock.unlock"));
            if (lock != null) {
                lines.add(lock.stack().isEmpty()
                        ? Component.translatable("gui.infinity_nexus_multiblock.slot_lock.empty")
                        : Component.translatable("gui.infinity_nexus_multiblock.slot_lock.item",
                                lock.stack().getHoverName()));
            }
            graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), ItemStack.EMPTY, mouseX, mouseY);
            return;
        }

        MultiblockSlot fluidPinSlot = this.fluidPinSlotAt(mouseX, mouseY);
        if (fluidPinSlot != null) {
            MultiblockControllerBlockEntity blockEntity = this.menu.blockEntity();
            MultiblockFluidHandler fluids = blockEntity.fluidHandler();
            int tankIndex = blockEntity.fluidSlotTankIndex(fluidPinSlot.getSlotIndex());
            List<Component> lines = new ArrayList<>();
            if (fluids == null || tankIndex < 0) {
                lines.add(Component.translatable("gui.infinity_nexus_multiblock.fluid_slot.empty"));
            } else {
                var tank = fluids.tank(tankIndex);
                lines.add(Component.translatable("gui.infinity_nexus_multiblock.fluid_slot.tank", tank.id()));
                lines.add(Component.translatable("gui.infinity_nexus_multiblock.fluid_slot.tank_index",
                        tankIndex + 1, fluids.size()));
                lines.add(Component.translatable("gui.infinity_nexus_multiblock.fluid_slot.amount",
                        fluids.getAmountAsInt(tankIndex), tank.capacity()));
                if (fluids.size() > 1) {
                    lines.add(Component.translatable("gui.infinity_nexus_multiblock.fluid_slot.cycle"));
                }
            }
            graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), ItemStack.EMPTY, mouseX, mouseY);
            return;
        }

        super.extractTooltip(graphics, mouseX, mouseY);
        MultiblockControllerBlockEntity blockEntity = this.menu.blockEntity();

        for (GuiElement element : this.menu.layout().elements()) {
            int x = this.leftPos + element.x();
            int y = this.topPos + element.y();
            if (!isMouseOver(mouseX, mouseY, x, y, element.width(), element.height())) {
                continue;
            }

            if (element.type() == GuiElementType.ENERGY_BAR) {
                MultiblockEnergyStorage energy = blockEntity.energyStorage();
                if (energy != null) {
                    Component tooltip = Component.translatable("gui.infinity_nexus_multiblock.energy")
                            .append(": " + energy.getAmountAsInt() + " / " + energy.getCapacityAsInt() + " FE");
                    graphics.setTooltipForNextFrame(this.font, List.of(tooltip), Optional.empty(), ItemStack.EMPTY,
                            mouseX, mouseY);
                }
            } else if (element.type() == GuiElementType.FLUID_BAR) {
                MultiblockFluidHandler fluids = blockEntity.fluidHandler();
                if (fluids != null) {
                    int index = fluids.indexOf(element.capability());
                    if (index >= 0) {
                        FluidStack stack = fluids.copyToList().get(index);
                        FluidTankRenderer renderer = this.fluidRenderer(element, fluids.tank(index).capacity());
                        graphics.setTooltipForNextFrame(this.font,
                                renderer.getTooltip(stack, TooltipFlag.Default.NORMAL), Optional.empty(),
                                ItemStack.EMPTY, mouseX, mouseY);
                    }
                }
            } else if (element.type() == GuiElementType.PROGRESS_BAR || element.type() == GuiElementType.ARROW) {
                int max = this.menu.maxProgress();
                int progress = this.menu.progress();
                int percent = max > 0 ? (int) Math.min(100L, (long) progress * 100L / max) : 0;
                Component tooltip = Component.translatable("gui.infinity_nexus_multiblock.progress")
                        .append(": " + percent + "%");
                List<Component> lines = ModList.get().isLoaded("jei")
                        ? List.of(tooltip, Component.translatable(
                                "gui.infinity_nexus_multiblock.jei.show_recipes"))
                        : List.of(tooltip);
                graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), ItemStack.EMPTY,
                        mouseX, mouseY);
            } else if (element.type() == GuiElementType.ENTITY) {
                List<EntityPreview> previews = entityPreviews();
                int page = entityPage(element, previews.size());
                MultiblockGuiRender.EntityPage pageInfo = MultiblockGuiRender.entityPage(
                        x, y, element.width(), element.height(), previews.size(), page);
                for (int index = 0; index < pageInfo.cells().size(); index++) {
                    MultiblockGuiRender.EntityCell cell = pageInfo.cells().get(index);
                    if (!isMouseOver(mouseX, mouseY, cell.x(), cell.y(), cell.width(), cell.height())) {
                        continue;
                    }
                    EntityPreview preview = previews.get(pageInfo.firstIndex() + index);
                    Component name = preview.type().getDescription();
                    List<Component> lines = preview.count() > 0
                            ? List.of(Component.empty().append(name).append(" x" + preview.count()))
                            : List.of(name);
                    graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), ItemStack.EMPTY,
                            mouseX, mouseY);
                    return;
                }
            }
        }
    }

    private void drawBars(GuiGraphicsExtractor graphics, int originX, int originY, MultiblockMenuLayout layout) {
        MultiblockControllerBlockEntity blockEntity = this.menu.blockEntity();
        for (GuiElement element : layout.elements()) {
            if (element.type() == GuiElementType.ENERGY_BAR) {
                drawEnergyBar(graphics, layout, blockEntity, element, originX + element.x(), originY + element.y());
            } else if (element.type() == GuiElementType.FLUID_BAR) {
                drawFluidBar(graphics, layout, blockEntity, element, originX + element.x(), originY + element.y());
            } else if (element.type() == GuiElementType.PROGRESS_BAR) {
                drawProgressBar(graphics, layout, element, originX + element.x(), originY + element.y());
            } else if (element.type() == GuiElementType.ARROW) {
                drawArrow(graphics, layout, element, originX + element.x(), originY + element.y());
            }
        }
    }

    private void drawEntityElements(GuiGraphicsExtractor graphics, int originX, int originY,
            MultiblockMenuLayout layout) {
        List<EntityPreview> previews = entityPreviews();
        List<Entity> entities = this.previewEntities;
        for (GuiElement element : layout.elements()) {
            if (element.type() != GuiElementType.ENTITY) {
                continue;
            }
            int x = originX + element.x();
            int y = originY + element.y();
            int page = entityPage(element, previews.size());
            MultiblockGuiRender.EntityPage pageInfo = MultiblockGuiRender.entityPage(
                    x, y, element.width(), element.height(), previews.size(), page);
            MultiblockGuiRender.drawFrame(graphics, x, y, element.width(), element.height());
            if (!previews.isEmpty()) {
                MultiblockGuiRender.renderEntities(graphics, entities, x, y,
                        element.width(), element.height(), page);
            }
            MultiblockGuiRender.drawPageIndicator(graphics, this.font, x, y,
                    element.width(), element.height(), pageInfo);
            MultiblockGuiRender.drawBorder(graphics, x, y, element.width(), element.height());
        }
    }

    private int entityPage(GuiElement element, int count) {
        int pageCount = MultiblockGuiRender.pageCount(element.width(), element.height(), count);
        if (pageCount <= 1) {
            this.entityPages.remove(element);
            return 0;
        }
        int page = Math.floorMod(this.entityPages.getOrDefault(element, 0), pageCount);
        this.entityPages.put(element, page);
        return page;
    }

    private List<Entity> entitiesForDisplay() {
        this.refreshEntityPreviews();
        return this.previewEntities;
    }

    private List<EntityPreview> entityPreviews() {
        this.refreshEntityPreviews();
        return this.entityPreviews;
    }

    private void refreshEntityPreviews() {
        MultiblockControllerBlockEntity blockEntity = this.menu.blockEntity();
        List<MultiblockControllerBlockEntity.AreaEntityCount> areaCounts = blockEntity.areaEntityCounts();
        List<EntityType<?>> recipeTypes = blockEntity.activeEntityTypes();
        if (areaCounts.equals(this.cachedAreaEntityCounts)
                && recipeTypes.equals(this.cachedRecipeEntityTypes)) {
            return;
        }
        this.cachedAreaEntityCounts = List.copyOf(areaCounts);
        this.cachedRecipeEntityTypes = List.copyOf(recipeTypes);
        this.entityPages.clear();
        List<EntityPreview> previews = new ArrayList<>();
        List<Entity> entities = new ArrayList<>();
        List<EntityType<?>> addedTypes = new ArrayList<>();
        for (MultiblockControllerBlockEntity.AreaEntityCount entry : areaCounts) {
            Entity entity = MultiblockGuiRender.createEntity(entry.type());
            if (entity != null) {
                previews.add(new EntityPreview(entry.type(), entry.count()));
                entities.add(entity);
                addedTypes.add(entry.type());
            }
        }
        for (EntityType<?> type : recipeTypes) {
            if (addedTypes.contains(type)) {
                continue;
            }
            Entity entity = MultiblockGuiRender.createEntity(type);
            if (entity != null) {
                previews.add(new EntityPreview(type, 0));
                entities.add(entity);
                addedTypes.add(type);
            }
        }
        this.entityPreviews = List.copyOf(previews);
        this.previewEntities = List.copyOf(entities);
    }

    private void drawEnergyBar(GuiGraphicsExtractor graphics, MultiblockMenuLayout layout,
            MultiblockControllerBlockEntity blockEntity, GuiElement element, int x, int y) {
        if (drawsBarBackground(layout)) {
            drawBarBackground(graphics, x, y, element.width(), element.height());
        }
        MultiblockEnergyStorage energy = blockEntity.energyStorage();
        if (energy == null || energy.getCapacityAsInt() <= 0) {
            return;
        }

        int innerX = x + BAR_INSET;
        int innerY = y + BAR_INSET;
        int innerWidth = Math.max(0, element.width() - BAR_INSET * 2);
        int innerHeight = Math.max(0, element.height() - BAR_INSET * 2);
        int filled = (int) Math.min(energy.getCapacityAsInt(),
                (long) energy.getAmountAsInt()
                        * (element.direction() == GuiDirection.LEFT || element.direction() == GuiDirection.RIGHT
                                ? innerWidth
                                : innerHeight)
                        / energy.getCapacityAsInt());

        switch (element.direction()) {
            case UP -> graphics.fill(innerX, innerY + innerHeight - filled,
                    innerX + innerWidth, innerY + innerHeight, element.color());
            case DOWN -> graphics.fill(innerX, innerY,
                    innerX + innerWidth, innerY + filled, element.color());
            case LEFT -> graphics.fill(innerX + innerWidth - filled, innerY,
                    innerX + innerWidth, innerY + innerHeight, element.color());
            case RIGHT -> graphics.fill(innerX, innerY,
                    innerX + filled, innerY + innerHeight, element.color());
        }
    }

    private void drawFluidBar(GuiGraphicsExtractor graphics, MultiblockMenuLayout layout,
            MultiblockControllerBlockEntity blockEntity, GuiElement element, int x, int y) {
        if (drawsBarBackground(layout)) {
            drawBarBackground(graphics, x, y, element.width(), element.height());
        }
        MultiblockFluidHandler fluids = blockEntity.fluidHandler();
        if (fluids == null) {
            return;
        }

        int index = fluids.indexOf(element.capability());
        if (index < 0) {
            return;
        }

        FluidStack stack = fluids.copyToList().get(index);
        int innerWidth = Math.max(0, element.width() - BAR_INSET * 2);
        int innerHeight = Math.max(0, element.height() - BAR_INSET * 2);
        if (innerWidth <= 0 || innerHeight <= 0) {
            return;
        }
        this.fluidRenderer(element, fluids.tank(index).capacity(), innerWidth, innerHeight)
                .render(graphics, x + BAR_INSET, y + BAR_INSET, stack);
    }

    private void drawProgressBar(GuiGraphicsExtractor graphics, MultiblockMenuLayout layout,
            GuiElement element, int x, int y) {
        if (drawsBarBackground(layout)) {
            drawBarBackground(graphics, x, y, element.width(), element.height());
        } else {
            blitElementTexture(graphics, layout, element, x, y);
        }
        int max = this.menu.maxProgress();
        if (max <= 0) {
            return;
        }

        int innerX = x + BAR_INSET;
        int innerY = y + BAR_INSET;
        int innerWidth = Math.max(0, element.width() - BAR_INSET * 2);
        int innerHeight = Math.max(0, element.height() - BAR_INSET * 2);
        int filled = Math.max(0, Math.min(innerWidth,
                (int) ((long) this.menu.progress() * innerWidth / max)));
        if (element.direction() == GuiDirection.DOWN || element.direction() == GuiDirection.UP) {
            filled = Math.max(0, Math.min(innerHeight,
                    (int) ((long) this.menu.progress() * innerHeight / max)));
        }

        switch (element.direction()) {
            case UP -> graphics.fill(innerX, innerY + innerHeight - filled,
                    innerX + innerWidth, innerY + innerHeight, element.color());
            case DOWN -> graphics.fill(innerX, innerY,
                    innerX + innerWidth, innerY + filled, element.color());
            case LEFT -> graphics.fill(innerX + innerWidth - filled, innerY,
                    innerX + innerWidth, innerY + innerHeight, element.color());
            case RIGHT -> graphics.fill(innerX, innerY,
                    innerX + filled, innerY + innerHeight, element.color());
        }
    }

    private void drawArrow(GuiGraphicsExtractor graphics, MultiblockMenuLayout layout,
            GuiElement element, int x, int y) {
        Identifier texture = layout.texture();
        int max = this.menu.maxProgress();
        if (texture == null || max <= 0) {
            return;
        }

        int width = element.width();
        int height = element.height();
        int progress = Math.max(0, Math.min(max, this.menu.progress()));
        int u = sourceU(element);
        int v = sourceV(element);
        switch (element.direction()) {
            case RIGHT -> {
                int filled = (int) ((long) progress * width / max);
                if (filled > 0) {
                    blit(graphics, layout, texture, x, y, u, v, filled, height, filled, height);
                }
            }
            case LEFT -> {
                int filled = (int) ((long) progress * width / max);
                if (filled > 0) {
                    blit(graphics, layout, texture, x + width - filled, y, u + width - filled, v,
                            filled, height, filled, height);
                }
            }
            case UP -> {
                int filled = (int) ((long) progress * height / max);
                if (filled > 0) {
                    blit(graphics, layout, texture, x, y + height - filled, u, v + height - filled,
                            width, filled, width, filled);
                }
            }
            case DOWN -> {
                int filled = (int) ((long) progress * height / max);
                if (filled > 0) {
                    blit(graphics, layout, texture, x, y, u, v, width, filled, width, filled);
                }
            }
        }
    }

    private static void blitElementTexture(GuiGraphicsExtractor graphics, MultiblockMenuLayout layout,
            GuiElement element, int x, int y) {
        blit(graphics, layout, layout.texture(), x, y, sourceU(element), sourceV(element),
                element.width(), element.height(), element.width(), element.height());
    }

    private static void blit(GuiGraphicsExtractor graphics, MultiblockMenuLayout layout, Identifier texture,
            int x, int y, int u, int v, int width, int height, int sourceWidth, int sourceHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v,
                width, height, sourceWidth, sourceHeight, layout.textureWidth(), layout.textureHeight());
    }

    private static int sourceU(GuiElement element) {
        return element.u() >= 0 ? element.u() : element.x();
    }

    private static int sourceV(GuiElement element) {
        return element.v() >= 0 ? element.v() : element.y();
    }

    private static void drawBarBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        MultiblockGuiRender.drawFrame(graphics, x, y, width, height);
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_COLOR);
        graphics.fill(x, y, x + width, y + 1, PANEL_LIGHT);
        graphics.fill(x, y, x + 1, y + height, PANEL_LIGHT);
        graphics.fill(x, y + height - 1, x + width, y + height, PANEL_DARK);
        graphics.fill(x + width - 1, y, x + width, y + height, PANEL_DARK);
    }

    private static void drawSlotBackgrounds(GuiGraphicsExtractor graphics, int originX, int originY,
            MultiblockMenuLayout layout) {
        for (GuiElement element : layout.elements()) {
            if (element.type() == GuiElementType.SLOT) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, MultiblockMenuLayout.DEFAULT_TEXTURE,
                        originX + element.x() - 1, originY + element.y(),
                        SLOT_SPRITE_U, SLOT_SPRITE_V, SLOT_SPRITE_SIZE, SLOT_SPRITE_SIZE,
                        SLOT_SPRITE_SIZE, SLOT_SPRITE_SIZE, 256, 256);
            }
        }
    }

    private static boolean drawsBarBackground(MultiblockMenuLayout layout) {
        return layout.texture() == null || isDefaultGui(layout);
    }

    private static boolean isDefaultGui(MultiblockMenuLayout layout) {
        return MultiblockMenuLayout.DEFAULT_TEXTURE.equals(layout.texture());
    }

    private FluidTankRenderer fluidRenderer(GuiElement element, int capacity) {
        return this.fluidRenderer(element, capacity, element.width(), element.height());
    }

    private FluidTankRenderer fluidRenderer(GuiElement element, int capacity, int width, int height) {
        String key = element.capability() + ":" + width + "x" + height + ":" + capacity;
        return this.fluidRenderers.computeIfAbsent(key,
                ignored -> new FluidTankRenderer(capacity, true, width, height));
    }

    private static boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record EntityPreview(EntityType<?> type, int count) {
    }
}
