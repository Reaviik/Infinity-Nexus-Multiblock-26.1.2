package com.Infinity.Nexus.Multiblock.api.addon.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface ResourceListRenderer {
    void renderIcon(GuiGraphicsExtractor graphics, int x, int y, int size, String resourceId, long amount);
}
