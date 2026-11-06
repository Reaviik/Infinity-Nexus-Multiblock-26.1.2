package com.Infinity.Nexus.Multiblock.client;

import java.util.ArrayList;
import java.util.List;

import com.Infinity.Nexus.Multiblock.api.GuiElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class MultiblockGuiRender {
    public static final int FRAME_BACKGROUND = 0xFF000000;
    public static final int FRAME_BORDER = 0xFF808080;

    private static final int MAX_ENTITY_SCALE = 24;
    private static final float ENTITY_SCALE_FACTOR = 0.72F;
    private static final float ENTITY_OFFSET_Y = 0.0625F;
    private static final float ENTITY_VIEW_X_ANGLE = 1.15F;
    private static final float ENTITY_VIEW_Y_ANGLE = 0.45F;

    private MultiblockGuiRender() {
    }

    public static void drawFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, FRAME_BACKGROUND);
        drawBorder(graphics, x, y, width, height);
    }

    public static void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + 1, FRAME_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, FRAME_BORDER);
        graphics.fill(x, y, x + 1, y + height, FRAME_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, FRAME_BORDER);
    }

    public static @Nullable Entity createEntity(@Nullable EntityType<?> entityType) {
        Level level = Minecraft.getInstance().level;
        return entityType == null || level == null ? null : entityType.create(level, EntitySpawnReason.COMMAND);
    }

    public static void renderEntities(GuiGraphicsExtractor graphics, List<Entity> entities,
            int x, int y, int width, int height, int page) {
        EntityPage entityPage = entityPage(x, y, width, height, entities.size(), page);
        for (int index = 0; index < entityPage.cells().size(); index++) {
            Entity entity = entities.get(entityPage.firstIndex() + index);
            if (entity == null) {
                continue;
            }
            EntityCell cell = entityPage.cells().get(index);
            renderEntity(graphics, entity, cell.x(), cell.y(), cell.width(), cell.height());
        }
    }

    public static int pageCount(int width, int height, int count) {
        if (count <= 0 || width <= 0 || height <= 0) {
            return 0;
        }
        if (count == 1) {
            return 1;
        }
        int capacity = capacity(width, height);
        return (count + capacity - 1) / capacity;
    }

    public static EntityPage entityPage(int x, int y, int width, int height, int count, int page) {
        if (count <= 0 || width <= 0 || height <= 0) {
            return new EntityPage(0, 0, 0, 0, List.of());
        }
        if (count == 1) {
            return new EntityPage(0, 1, 1, 0, List.of(new EntityCell(x, y, width, height)));
        }

        int columns = Math.max(1, width / GuiElement.ENTITY_CELL_SIZE);
        int capacity = capacity(width, height);
        int pages = (count + capacity - 1) / capacity;
        int currentPage = Math.floorMod(page, pages);
        int firstIndex = currentPage * capacity;
        int pageSize = Math.min(capacity, count - firstIndex);
        List<EntityCell> cells = new ArrayList<>(pageSize);
        for (int index = 0; index < pageSize; index++) {
            int column = index % columns;
            int row = index / columns;
            cells.add(new EntityCell(
                    x + column * GuiElement.ENTITY_CELL_SIZE,
                    y + row * GuiElement.ENTITY_CELL_SIZE,
                    GuiElement.ENTITY_CELL_SIZE,
                    GuiElement.ENTITY_CELL_SIZE));
        }
        return new EntityPage(currentPage, pages, pageSize, firstIndex, List.copyOf(cells));
    }

    public static void drawPageIndicator(GuiGraphicsExtractor graphics, Font font,
            int x, int y, int width, int height, EntityPage page) {
        if (page.pageCount() <= 1) {
            return;
        }
        String text = (page.page() + 1) + "/" + page.pageCount();
        int textWidth = font.width(text);
        int textX = x + width - textWidth - 2;
        int textY = y + height - 9;
        graphics.fill(textX - 1, textY - 1, x + width - 1, y + height - 1, 0x99000000);
        graphics.text(font, Component.literal(text), textX, textY, 0xFFFFFFFF, true);
    }

    private static int capacity(int width, int height) {
        int columns = Math.max(1, width / GuiElement.ENTITY_CELL_SIZE);
        int rows = Math.max(1, height / GuiElement.ENTITY_CELL_SIZE);
        return columns * rows;
    }

    public static void renderEntity(GuiGraphicsExtractor graphics, Entity entity,
            int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        float entitySize = Math.max(0.1F, Math.max(entity.getBbWidth(), entity.getBbHeight()));
        int scale = (int) Math.floor(Math.min(width, height) * ENTITY_SCALE_FACTOR / entitySize);
        scale = Math.max(1, Math.min(MAX_ENTITY_SCALE, scale));
        if (entity instanceof LivingEntity living) {
            InventoryScreen.renderEntityInInventoryFollowsAngle(
                    graphics, x, y, x + width, y + height, scale, ENTITY_OFFSET_Y,
                    ENTITY_VIEW_X_ANGLE, ENTITY_VIEW_Y_ANGLE, living);
            return;
        }

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderState state = dispatcher.extractEntity(entity, 1.0F);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        float yaw = ENTITY_VIEW_X_ANGLE * 20.0F * (float) (Math.PI / 180.0);
        float pitch = ENTITY_VIEW_Y_ANGLE * 20.0F * (float) (Math.PI / 180.0);
        Quaternionf rotation = new Quaternionf()
                .rotateZ((float) Math.PI)
                .rotateY(yaw)
                .rotateX(pitch);
        Quaternionf overrideCameraAngle = new Quaternionf().rotateX(pitch);
        Vector3f translation = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + ENTITY_OFFSET_Y, 0.0F);
        graphics.entity(state, scale, translation, rotation, overrideCameraAngle, x, y, x + width, y + height);
    }

    public record EntityCell(int x, int y, int width, int height) {
    }

    public record EntityPage(int page, int pageCount, int pageSize, int firstIndex, List<EntityCell> cells) {
    }
}
