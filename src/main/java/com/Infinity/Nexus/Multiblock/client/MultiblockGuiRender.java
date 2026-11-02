package com.Infinity.Nexus.Multiblock.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
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

    private static final int MAX_ENTITY_SCALE = 30;
    private static final float ENTITY_OFFSET_Y = 0.0625F;
    private static final float ENTITY_VIEW_X_ANGLE = 1.15F;
    private static final float ENTITY_VIEW_Y_ANGLE = 0.45F;

    private MultiblockGuiRender() {
    }

    public static void drawFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, FRAME_BACKGROUND);
        graphics.fill(x, y, x + width, y + 1, FRAME_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, FRAME_BORDER);
        graphics.fill(x, y, x + 1, y + height, FRAME_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, FRAME_BORDER);
    }

    public static @Nullable Entity createEntity(@Nullable EntityType<?> entityType) {
        Level level = Minecraft.getInstance().level;
        return entityType == null || level == null ? null : entityType.create(level, EntitySpawnReason.COMMAND);
    }

    public static void renderEntity(GuiGraphicsExtractor graphics, Entity entity,
            int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int scale = Math.max(4, Math.min(MAX_ENTITY_SCALE, Math.min(width, height)));
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
}
