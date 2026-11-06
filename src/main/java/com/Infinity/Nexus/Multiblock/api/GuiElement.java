package com.Infinity.Nexus.Multiblock.api;

import java.util.Objects;
import java.util.Optional;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record GuiElement(
        GuiElementType type,
        int x,
        int y,
        int width,
        int height,
        String text,
        String slot,
        String capability,
        int color,
        boolean shadow,
        GuiDirection direction,
        int u,
        int v) {

    private static final Codec<Integer> COLOR_CODEC = Codec.either(Codec.INT, Codec.STRING)
            .xmap(
                    either -> either.map(value -> value, GuiElement::parseColor),
                    value -> Either.left(value));

    public static final int DEFAULT_TEXT_COLOR = 0xFF404040;
    public static final int DEFAULT_ENERGY_COLOR = 0xFFFF0000;
    public static final int DEFAULT_PROGRESS_COLOR = 0xFF00AA00;
    public static final int ENTITY_CELL_SIZE = 18;

    public static final Codec<GuiElement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GuiElementType.CODEC.fieldOf("type").forGetter(GuiElement::type),
            Codec.INT.optionalFieldOf("x", 0).forGetter(GuiElement::x),
            Codec.INT.optionalFieldOf("y", 0).forGetter(GuiElement::y),
            Codec.intRange(0, 4096).optionalFieldOf("width", 16).forGetter(GuiElement::width),
            Codec.intRange(0, 4096).optionalFieldOf("height", 16).forGetter(GuiElement::height),
            Codec.STRING.optionalFieldOf("text", "").forGetter(GuiElement::text),
            Codec.STRING.optionalFieldOf("slot", "").forGetter(GuiElement::slot),
            Codec.STRING.optionalFieldOf("capability", "").forGetter(GuiElement::capability),
            COLOR_CODEC.optionalFieldOf("color").forGetter(element -> element.color() == defaultColor(element.type())
                    ? Optional.empty()
                    : Optional.of(element.color())),
            Codec.BOOL.optionalFieldOf("shadow", true).forGetter(GuiElement::shadow),
            GuiDirection.CODEC.optionalFieldOf("direction", GuiDirection.UP).forGetter(GuiElement::direction),
            Codec.INT.optionalFieldOf("u", -1).forGetter(GuiElement::u),
            Codec.INT.optionalFieldOf("v", -1).forGetter(GuiElement::v))
            .apply(instance, (type, x, y, width, height, text, slot, capability, color, shadow, direction, u, v) ->
                    new GuiElement(
                            type,
                            x,
                            y,
                            width,
                            height,
                            text,
                            slot,
                            capability,
                            color.orElseGet(() -> defaultColor(type)),
                            shadow,
                            direction,
                            u,
                            v)));

    private static int defaultColor(GuiElementType type) {
        return switch (type) {
            case ENERGY_BAR -> DEFAULT_ENERGY_COLOR;
            case PROGRESS_BAR -> DEFAULT_PROGRESS_COLOR;
            default -> DEFAULT_TEXT_COLOR;
        };
    }

    public GuiElement {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(direction, "direction");
        if (color >>> 24 == 0) {
            color |= 0xFF000000;
        }
    }

    private static int parseColor(String value) {
        String text = value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("color cannot be empty");
        }

        boolean hex = text.startsWith("#") || text.startsWith("0x") || text.startsWith("0X");
        if (hex) {
            text = text.substring(text.startsWith("0x") || text.startsWith("0X") ? 2 : 1);
        } else if (text.matches("-?[0-9]+")) {
            try {
                return (int) Long.parseLong(text, 10);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("invalid color: " + value, exception);
            }
        } else {
            hex = true;
        }

        if (text.length() == 6) {
            text = "FF" + text;
        }
        if (text.length() != 8) {
            throw new IllegalArgumentException("color must have 6 or 8 hex digits: " + value);
        }
        try {
            return (int) Long.parseLong(text, 16);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid hex color: " + value, exception);
        }
    }

    public static GuiElement title(int x, int y, String text, int color, boolean shadow) {
        return new GuiElement(GuiElementType.TITLE, x, y, 0, 0, text, "", "", color, shadow, GuiDirection.UP, -1, -1);
    }

    public static GuiElement text(int x, int y, String text, int color, boolean shadow) {
        return new GuiElement(GuiElementType.TEXT, x, y, 0, 0, text, "", "", color, shadow, GuiDirection.UP, -1, -1);
    }

    public static GuiElement playerInventory(int x, int y, String text) {
        return new GuiElement(GuiElementType.PLAYER_INVENTORY, x, y, 0, 0, text, "", "", DEFAULT_TEXT_COLOR, false, GuiDirection.UP, -1, -1);
    }

    public static GuiElement slot(String slot, int x, int y) {
        return new GuiElement(GuiElementType.SLOT, x, y, 18, 18, "", slot, "", DEFAULT_TEXT_COLOR, false, GuiDirection.UP, -1, -1);
    }

    public static GuiElement energyBar(int x, int y, int width, int height, String capability, GuiDirection direction, int color) {
        return new GuiElement(GuiElementType.ENERGY_BAR, x, y, width, height, "", "", capability, color, false, direction, -1, -1);
    }

    public static GuiElement fluidBar(int x, int y, int width, int height, String capability) {
        return new GuiElement(GuiElementType.FLUID_BAR, x, y, width, height, "", "", capability, DEFAULT_TEXT_COLOR, false, GuiDirection.UP, -1, -1);
    }

    public static GuiElement progressBar(int x, int y, int width, int height, GuiDirection direction, int color) {
        return new GuiElement(GuiElementType.PROGRESS_BAR, x, y, width, height, "", "", "", color, false, direction, -1, -1);
    }

    public static GuiElement arrow(int x, int y, int width, int height, int u, int v, GuiDirection direction) {
        return new GuiElement(GuiElementType.ARROW, x, y, width, height, "", "", "", DEFAULT_TEXT_COLOR, false,
                direction, u, v);
    }

    public static GuiElement entity(int x, int y, int width, int height) {
        return new GuiElement(GuiElementType.ENTITY, x, y, width, height, "", "", "", DEFAULT_TEXT_COLOR, false,
                GuiDirection.UP, -1, -1);
    }
}
