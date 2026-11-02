package com.Infinity.Nexus.Multiblock.data;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

public final class MultiblockReloadListeners {
    private static final Identifier DEFINITIONS_LISTENER =
            Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "multiblock_definitions");

    private MultiblockReloadListeners() {}

    public static void onAddServerReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(DEFINITIONS_LISTENER, new MultiblockDefinitionLoader());
    }
}
