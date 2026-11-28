package com.Infinity.Nexus.Multiblock.item;

import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.data.MultiblockDefinitions;
import com.Infinity.Nexus.Multiblock.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class MultiblockControllerItem extends BlockItem {
    public MultiblockControllerItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Identifier definitionId = stack.get(ModDataComponents.MULTIBLOCK_DEFINITION.get());
        if (definitionId == null) {
            return super.getName(stack);
        }
        MultiblockDefinition definition = MultiblockDefinitions.get(definitionId);
        if (definition != null) {
            return definition.displayName();
        }
        String key = "multiblock." + definitionId.getNamespace() + "." + definitionId.getPath();
        return Component.translatableWithFallback(key, definitionId.getPath());
    }
}
