package com.Infinity.Nexus.Multiblock.client.preview;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

public final class MultiblockPreview {
    public static final int BAR = 14;
    private static final long ANIMATION_PERIOD_MS = 36_000L;
    private static @Nullable MultiblockPreview hovered;
    private static final int FRAME = 0xFF8A6C04;
    private static final int BACK = 0xFF0B0B0B;
    private static final int BAR_BACK = 0xFF151515;
    private static final int TEXT = 0xFFE8D8A8;
    private static final int MUTED = 0xFF9A9A9A;
    private static final int ARROW = 0xFFD8C690;
    private static final int ARROW_HOVER = 0xFFFFE680;
    private static final int ARROW_W = 16;

    private final MultiblockStructureData structure;
    private int layer = -1;
    private float yaw;
    private int x;
    private int y;
    private int width;
    private int height;
    private @Nullable Screen screen;

    public MultiblockPreview(MultiblockStructureData structure) {
        this.structure = structure;
    }

    public static float animationTime() {
        return (float) (System.currentTimeMillis() % ANIMATION_PERIOD_MS) / 50.0F;
    }

    public static @Nullable MultiblockPreview hoveredOn(Screen screen) {
        MultiblockPreview preview = hovered;
        return preview != null && preview.screen == screen ? preview : null;
    }

    public static void clearHovered(Screen screen) {
        if (hovered != null && hovered.screen == screen) {
            hovered = null;
        }
    }

    public MultiblockStructureData structure() {
        return this.structure;
    }

    public int layer() {
        return this.layer;
    }

    public @Nullable List<Component> render(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY,
            float time) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.screen = Minecraft.getInstance().screen;
        if (this.overScene(mouseX, mouseY)) {
            hovered = this;
        } else if (hovered == this) {
            hovered = null;
        }
        Font font = Minecraft.getInstance().font;
        int sceneBottom = y + height - BAR;
        graphics.fill(x, y, x + width, y + height, FRAME);
        graphics.fill(x + 1, y + 1, x + width - 1, sceneBottom, BACK);
        graphics.fill(x + 1, sceneBottom, x + width - 1, y + height - 1, BAR_BACK);

        MultiblockDiorama.draw(
                graphics,
                this.structure,
                this.layer,
                time,
                this.yaw,
                x + 1,
                y + 1,
                x + width - 1,
                sceneBottom);

        int count = this.structure.definition().pattern().sizeY();
        Component label = this.layer == -1
                ? Component.translatable("gui.infinity_nexus_multiblock.structure.complete")
                : Component.translatable(
                        "gui.infinity_nexus_multiblock.structure.layer",
                        this.layer + 1,
                        count);
        int textY = sceneBottom + 3;
        graphics.centeredText(font, label, x + width / 2, textY, this.layer == -1 ? MUTED : TEXT);

        int pipsWidth = count * 4 - 1;
        int px = x + width - 16 - 4 - pipsWidth;
        for (int i = 0; i < count; i++) {
            int color = this.layer == -1 || i <= this.layer ? ARROW_HOVER : ARROW;
            graphics.fill(px + i * 4, sceneBottom + 7 - 1, px + i * 4 + 3, sceneBottom + 7 + 2, color);
        }

        arrow(graphics, x + 2, sceneBottom, false, this.overArrow(mouseX, mouseY, false));
        arrow(graphics, x + width - ARROW_W - 2, sceneBottom, true, this.overArrow(mouseX, mouseY, true));
        return this.tooltip(mouseX, mouseY);
    }

    public @Nullable List<Component> tooltip(double mouseX, double mouseY) {
        if (this.overArrow(mouseX, mouseY, false)) {
            return List.of(Component.translatable("gui.infinity_nexus_multiblock.structure.previous"));
        }
        if (this.overArrow(mouseX, mouseY, true)) {
            return List.of(Component.translatable("gui.infinity_nexus_multiblock.structure.next"));
        }
        if (this.overScene(mouseX, mouseY)) {
            return List.of(Component.translatable("gui.infinity_nexus_multiblock.structure.hint").withColor(MUTED));
        }
        return null;
    }

    private static void arrow(GuiGraphicsExtractor graphics, int ax, int top, boolean right, boolean hovered) {
        int color = hovered ? ARROW_HOVER : ARROW;
        int cy = top + 7;
        for (int i = 0; i < 4; i++) {
            int col = right ? ax + 6 + i : ax + 9 - i;
            graphics.fill(col, cy - 3 + i, col + 1, cy + 4 - i, color);
        }
    }

    private boolean overArrow(double mouseX, double mouseY, boolean right) {
        int ax = right ? this.x + this.width - ARROW_W - 2 : this.x + 2;
        int top = this.y + this.height - BAR;
        return mouseX >= ax
                && mouseX < ax + ARROW_W
                && mouseY >= top
                && mouseY < this.y + this.height;
    }

    public boolean click(double mouseX, double mouseY) {
        int count = this.structure.definition().pattern().sizeY();
        if (this.overArrow(mouseX, mouseY, true)) {
            this.layer = this.layer == -1 ? 0 : (this.layer + 1 >= count ? -1 : this.layer + 1);
        } else if (this.overArrow(mouseX, mouseY, false)) {
            this.layer = this.layer == -1 ? count - 1 : this.layer - 1;
        } else {
            return false;
        }

        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.4F, 0.5F));
        return true;
    }

    public boolean overScene(double mouseX, double mouseY) {
        return mouseX >= this.x
                && mouseX < this.x + this.width
                && mouseY >= this.y
                && mouseY < this.y + this.height - BAR;
    }

    public void drag(double dragX) {
        this.yaw = Mth.wrapDegrees(this.yaw + (float) dragX * 1.6F);
    }

    public boolean scroll(double mouseX, double mouseY, double amount) {
        if (!this.overScene(mouseX, mouseY) || amount == 0.0) {
            return false;
        }
        int count = this.structure.definition().pattern().sizeY();
        if (amount > 0.0) {
            this.layer = this.layer == -1 ? 0 : Math.min(count - 1, this.layer + 1);
        } else {
            this.layer = this.layer <= 0 ? -1 : this.layer - 1;
        }
        return true;
    }
}
