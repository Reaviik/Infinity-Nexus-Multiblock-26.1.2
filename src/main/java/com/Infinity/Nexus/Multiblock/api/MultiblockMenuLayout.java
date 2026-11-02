package com.Infinity.Nexus.Multiblock.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public record MultiblockMenuLayout(
        @Nullable Identifier texture,
        int width,
        int height,
        int textureWidth,
        int textureHeight,
        List<GuiElement> elements,
        List<SlotPlacement> slots,
        int playerInventoryX,
        int playerInventoryY,
        int inventorySlotCount) {

    public static final Identifier DEFAULT_TEXTURE =
            Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "textures/gui/gui_base.png");

    private static final int SLOT_SIZE = 18;
    private static final int DEFAULT_WIDTH = 176;
    private static final int DEFAULT_HEIGHT = 166;
    private static final int DEFAULT_SLOT_Y = 17;
    private static final int DEFAULT_PLAYER_INVENTORY_X = 8;
    private static final int DEFAULT_PLAYER_INVENTORY_Y = 84;
    private static final int PLAYER_INVENTORY_WIDTH = 162;
    private static final int PLAYER_INVENTORY_HEIGHT = 76;

    public static final Codec<MultiblockMenuLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("texture").forGetter(layout -> java.util.Optional.ofNullable(layout.texture())),
            Codec.intRange(1, 4096).optionalFieldOf("width", 176).forGetter(MultiblockMenuLayout::width),
            Codec.intRange(1, 4096).optionalFieldOf("height", 166).forGetter(MultiblockMenuLayout::height),
            Codec.intRange(1, 4096).optionalFieldOf("texture_width", 256).forGetter(MultiblockMenuLayout::textureWidth),
            Codec.intRange(1, 4096).optionalFieldOf("texture_height", 256).forGetter(MultiblockMenuLayout::textureHeight),
            GuiElement.CODEC.listOf().optionalFieldOf("elements", List.of()).forGetter(MultiblockMenuLayout::elements),
            SlotPlacement.CODEC.listOf().optionalFieldOf("slots", List.of()).forGetter(MultiblockMenuLayout::slots),
            Codec.INT.optionalFieldOf("player_inventory_x", 8).forGetter(MultiblockMenuLayout::playerInventoryX),
            Codec.INT.optionalFieldOf("player_inventory_y", 84).forGetter(MultiblockMenuLayout::playerInventoryY),
            Codec.INT.optionalFieldOf("inventory_slot_count", 0).forGetter(MultiblockMenuLayout::inventorySlotCount))
            .apply(instance, (texture, width, height, textureWidth, textureHeight, elements, slots,
                    playerInventoryX, playerInventoryY, inventorySlotCount) -> new MultiblockMenuLayout(
                            texture.orElse(null), width, height, textureWidth, textureHeight, elements, slots,
                            playerInventoryX, playerInventoryY, inventorySlotCount)));

    public MultiblockMenuLayout {
        elements = List.copyOf(elements);
        slots = List.copyOf(slots);
        if (inventorySlotCount < 0) {
            throw new IllegalArgumentException("inventorySlotCount cannot be negative");
        }
    }

    public static MultiblockMenuLayout create(@Nullable MultiblockDefinition definition) {
        if (definition == null) {
            return createDefault(null);
        }
        MultiblockGui gui = definition.gui().orElse(null);
        if (gui != null && !gui.elements().isEmpty()) {
            return createCustom(definition, gui);
        }
        return createDefault(definition);
    }

    private static MultiblockMenuLayout createDefault(@Nullable MultiblockDefinition definition) {
        int configuredRows = definition == null ? 3 : definition.inventoryRows();
        List<ItemSlot> itemSlots = definition == null ? List.of() : definition.itemSlots();
        int slotCount = itemSlots.isEmpty() ? configuredRows * 9 : itemSlots.size();
        boolean hasEnergy = definition != null && definition.energy().isPresent();
        List<FluidTank> tanks = definition == null ? List.of() : definition.fluidTanks();
        boolean hasProgress = definition != null
                && definition.type() == MultiblockType.RECIPE
                && definition.recipeType().isPresent();
        int barCount = (hasEnergy ? 1 : 0) + tanks.size();
        boolean reservedRail = barCount > 0 || hasProgress;
        int columns = reservedRail ? 8 : 9;

        List<GuiElement> elements = new ArrayList<>();
        elements.add(GuiElement.title(8, 6, "", GuiElement.DEFAULT_TEXT_COLOR, false));

        int barRows = Math.max(1, (barCount + 2) / 3);
        int barHeight = Math.max(8, (64 - (barRows - 1) * 4) / barRows);
        int barIndex = 0;
        if (hasEnergy) {
            String energyId = definition.energy().map(EnergyCapability::id).orElse("");
            elements.add(GuiElement.energyBar(
                    barX(barIndex), barY(barIndex, barHeight), 3, barHeight,
                    energyId, GuiDirection.UP, GuiElement.DEFAULT_ENERGY_COLOR));
            barIndex++;
        }
        for (FluidTank tank : tanks) {
            elements.add(GuiElement.fluidBar(barX(barIndex), barY(barIndex, barHeight), 3, barHeight, tank.id()));
            barIndex++;
        }
        if (hasProgress) {
            elements.add(GuiElement.progressBar(
                    DEFAULT_WIDTH - 5, DEFAULT_SLOT_Y, 3, 54, GuiDirection.UP, GuiElement.DEFAULT_PROGRESS_COLOR));
        }

        List<SlotPlacement> placements = new ArrayList<>();
        List<SlotPosition> usedPositions = new ArrayList<>();
        int maxSlotBottom = DEFAULT_SLOT_Y;
        for (int index = 0; index < slotCount; index++) {
            ItemSlot itemSlot = index < itemSlots.size() ? itemSlots.get(index) : null;
            SlotPosition position = itemSlot == null ? null : itemSlot.position();
            SlotPosition placed = position != null && fitsDefaultSlot(position)
                    ? position
                    : nextAutoSlot(columns, usedPositions);
            int x = placed.x();
            int y = placed.y();
            usedPositions.add(placed);
            maxSlotBottom = Math.max(maxSlotBottom, y + SLOT_SIZE);
            String slotId = itemSlot == null ? "" : itemSlot.id();
            int limit = itemSlot == null ? 64 : Math.min(64, itemSlot.limit());
            ItemSlotRole role = itemSlot == null ? ItemSlotRole.INPUT : itemSlot.role();
            ItemFilter filter = itemSlot == null ? null : itemSlot.filter();
            elements.add(GuiElement.slot(slotId, x, y));
            placements.add(new SlotPlacement(slotId, index, x, y, limit, role, filter));
        }

        int playerY = Math.max(DEFAULT_PLAYER_INVENTORY_Y, maxSlotBottom + 13);
        int height = Math.max(DEFAULT_HEIGHT, playerY + 82);
        elements.add(GuiElement.playerInventory(DEFAULT_PLAYER_INVENTORY_X, playerY, ""));
        Identifier texture = height == DEFAULT_HEIGHT ? DEFAULT_TEXTURE : null;

        return new MultiblockMenuLayout(
                texture,
                DEFAULT_WIDTH,
                height,
                256,
                256,
                elements,
                placements,
                DEFAULT_PLAYER_INVENTORY_X,
                playerY,
                slotCount);
    }

    private static boolean fitsDefaultSlot(SlotPosition position) {
        return position.x() >= 0
                && position.y() >= 0
                && position.x() + SLOT_SIZE <= DEFAULT_WIDTH
                && position.y() + SLOT_SIZE <= DEFAULT_HEIGHT;
    }

    private static SlotPosition nextAutoSlot(int columns, List<SlotPosition> usedPositions) {
        for (int row = 0; ; row++) {
            for (int column = 0; column < columns; column++) {
                int x = 8 + column * SLOT_SIZE;
                int y = DEFAULT_SLOT_Y + row * SLOT_SIZE;
                SlotPosition candidate = new SlotPosition(x, y);
                if (!overlapsAny(candidate, usedPositions)) {
                    return candidate;
                }
            }
        }
    }

    private static boolean overlapsAny(SlotPosition candidate, List<SlotPosition> usedPositions) {
        for (SlotPosition used : usedPositions) {
            if (candidate.x() < used.x() + SLOT_SIZE
                    && candidate.x() + SLOT_SIZE > used.x()
                    && candidate.y() < used.y() + SLOT_SIZE
                    && candidate.y() + SLOT_SIZE > used.y()) {
                return true;
            }
        }
        return false;
    }

    private static int barX(int index) {
        return DEFAULT_WIDTH - 11 - (index % 3) * 5;
    }

    private static int barY(int index, int barHeight) {
        return 5 + (index / 3) * (barHeight + 4);
    }

    private static MultiblockMenuLayout createCustom(MultiblockDefinition definition, MultiblockGui gui) {
        List<ItemSlot> itemSlots = definition.itemSlots();
        boolean usesSlotElements = gui.elements().stream()
                .anyMatch(element -> element.type() == GuiElementType.SLOT);
        if (itemSlots.isEmpty() && usesSlotElements) {
            throw new IllegalArgumentException("Custom gui elements with slot entries require item_slots");
        }

        Map<String, Integer> indexById = new LinkedHashMap<>();
        for (int index = 0; index < itemSlots.size(); index++) {
            indexById.put(itemSlots.get(index).id(), index);
        }

        boolean usesSlotPositions = itemSlots.stream().anyMatch(slot -> slot.position() != null);
        if (usesSlotElements && usesSlotPositions) {
            throw new IllegalArgumentException(
                    "Define slot positions in item_slots x/y or gui slot elements, not both");
        }

        List<GuiElement> elements = new ArrayList<>();
        List<SlotPlacement> placements = new ArrayList<>();
        Set<String> placedSlots = new HashSet<>();
        GuiElement playerInventory = null;

        if (!usesSlotElements) {
            for (int index = 0; index < itemSlots.size(); index++) {
                ItemSlot itemSlot = itemSlots.get(index);
                SlotPosition position = itemSlot.position();
                if (position == null) {
                    continue;
                }
                GuiElement element = GuiElement.slot(itemSlot.id(), position.x(), position.y());
                requireInside(element, gui.width(), gui.height(), SLOT_SIZE, SLOT_SIZE);
                elements.add(element);
                placements.add(createPlacement(itemSlot, index, position.x(), position.y()));
                placedSlots.add(itemSlot.id());
            }
        }

        for (GuiElement element : gui.elements()) {
            switch (element.type()) {
                case SLOT -> {
                    if (element.slot().isBlank()) {
                        throw new IllegalArgumentException("Slot elements need a slot id");
                    }
                    Integer index = indexById.get(element.slot());
                    if (index == null) {
                        throw new IllegalArgumentException("Unknown item slot id in gui: " + element.slot());
                    }
                    if (!placedSlots.add(element.slot())) {
                        throw new IllegalArgumentException("Duplicate slot element for item slot: " + element.slot());
                    }
                    requireInside(element, gui.width(), gui.height(), SLOT_SIZE, SLOT_SIZE);
                    ItemSlot itemSlot = itemSlots.get(index);
                    elements.add(element);
                    placements.add(createPlacement(itemSlot, index, element.x(), element.y()));
                }
                case PLAYER_INVENTORY -> {
                    if (playerInventory != null) {
                        throw new IllegalArgumentException("gui can only contain one player_inventory element");
                    }
                    requireInside(element, gui.width(), gui.height(), PLAYER_INVENTORY_WIDTH, PLAYER_INVENTORY_HEIGHT);
                    playerInventory = element;
                    elements.add(element);
                }
                case ENERGY_BAR -> {
                    EnergyCapability energy = definition.energy()
                            .orElseThrow(() -> new IllegalArgumentException("gui has an energy_bar but the multiblock has no energy capability"));
                    if (element.capability().isBlank() || !element.capability().equals(energy.id())) {
                        throw new IllegalArgumentException("Unknown energy capability in gui: " + element.capability());
                    }
                    requireBarInside(element, gui.width(), gui.height());
                    elements.add(element);
                }
                case FLUID_BAR -> {
                    boolean knownTank = definition.fluidTanks().stream()
                            .anyMatch(tank -> tank.id().equals(element.capability()));
                    if (element.capability().isBlank() || !knownTank) {
                        throw new IllegalArgumentException("Unknown fluid tank id in gui: " + element.capability());
                    }
                    requireBarInside(element, gui.width(), gui.height());
                    elements.add(element);
                }
                case PROGRESS_BAR, ARROW -> {
                    if (definition.type() != MultiblockType.RECIPE) {
                        throw new IllegalArgumentException("gui has a " + element.type().getSerializedName()
                                + " but the multiblock is not a recipe multiblock");
                    }
                    requireBarInside(element, gui.width(), gui.height());
                    elements.add(element);
                }
                case ENTITY -> {
                    if (definition.type() != MultiblockType.RECIPE) {
                        throw new IllegalArgumentException("gui has an entity element but the multiblock is not a recipe multiblock");
                    }
                    requireSizedElement(element, gui.width(), gui.height());
                    elements.add(element);
                }
                case TITLE, TEXT -> elements.add(element);
            }
        }

        if (playerInventory == null) {
            throw new IllegalArgumentException("Custom gui elements require a player_inventory element");
        }

        Identifier texture = gui.texture();
        if (texture == null && gui.width() == DEFAULT_WIDTH && gui.height() == DEFAULT_HEIGHT) {
            texture = DEFAULT_TEXTURE;
        }
        int playerX = playerInventory.x();
        int playerY = playerInventory.y();
        if (DEFAULT_TEXTURE.equals(texture)
                && gui.width() >= DEFAULT_WIDTH
                && gui.height() >= DEFAULT_HEIGHT) {
            playerX = DEFAULT_PLAYER_INVENTORY_X;
            playerY = DEFAULT_PLAYER_INVENTORY_Y;
        }

        return new MultiblockMenuLayout(
                texture,
                gui.width(),
                gui.height(),
                gui.textureWidth(),
                gui.textureHeight(),
                elements,
                placements,
                playerX,
                playerY,
                itemSlots.size());
    }

    private static SlotPlacement createPlacement(ItemSlot itemSlot, int index, int x, int y) {
        return new SlotPlacement(
                itemSlot.id(),
                index,
                x,
                y,
                Math.min(64, itemSlot.limit()),
                itemSlot.role(),
                itemSlot.filter());
    }

    private static void requireBarInside(GuiElement element, int width, int height) {
        if (element.width() <= 0 || element.height() <= 0) {
            throw new IllegalArgumentException("gui bar elements need positive width and height");
        }
        requireInside(element, width, height, element.width(), element.height());
    }

    private static void requireSizedElement(GuiElement element, int width, int height) {
        if (element.width() <= 0 || element.height() <= 0) {
            throw new IllegalArgumentException("gui " + element.type().getSerializedName()
                    + " elements need positive width and height");
        }
        requireInside(element, width, height, element.width(), element.height());
    }

    private static void requireInside(GuiElement element, int width, int height, int elementWidth, int elementHeight) {
        if (element.x() < 0 || element.y() < 0
                || element.x() + elementWidth > width
                || element.y() + elementHeight > height) {
            throw new IllegalArgumentException("gui element " + element.type().getSerializedName()
                    + " at " + element.x() + "," + element.y() + " exceeds gui bounds " + width + "x" + height);
        }
    }

    public record SlotPlacement(String id, int index, int x, int y, int limit,
            ItemSlotRole role, @Nullable ItemFilter filter) {
        public static final Codec<SlotPlacement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("id", "").forGetter(SlotPlacement::id),
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("index").forGetter(SlotPlacement::index),
                Codec.INT.fieldOf("x").forGetter(SlotPlacement::x),
                Codec.INT.fieldOf("y").forGetter(SlotPlacement::y),
                Codec.intRange(1, 64).optionalFieldOf("limit", 64).forGetter(SlotPlacement::limit),
                ItemSlotRole.CODEC.optionalFieldOf("role", ItemSlotRole.INPUT).forGetter(SlotPlacement::role),
                ItemFilter.CODEC.optionalFieldOf("filter")
                        .forGetter(placement -> java.util.Optional.ofNullable(placement.filter())))
                .apply(instance, (id, index, x, y, limit, role, filter) -> new SlotPlacement(
                        id, index, x, y, limit, role, filter.orElse(null))));

        public SlotPlacement {
            if (limit < 1 || limit > 64) {
                throw new IllegalArgumentException("Slot placement limit must be between 1 and 64");
            }
            Objects.requireNonNull(role, "role");
        }
    }
}
