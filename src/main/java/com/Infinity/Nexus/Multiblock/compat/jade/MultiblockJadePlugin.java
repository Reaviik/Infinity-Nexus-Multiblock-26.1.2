package com.Infinity.Nexus.Multiblock.compat.jade;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlock;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin(InfinityNexusMultiblock.MOD_ID)
public final class MultiblockJadePlugin implements IWailaPlugin {
    private static final Identifier DATA_UID = Identifier.fromNamespaceAndPath(
            InfinityNexusMultiblock.MOD_ID, "controller_assembly_data");
    private static final Identifier COMPONENT_UID = Identifier.fromNamespaceAndPath(
            InfinityNexusMultiblock.MOD_ID, "controller_assembly");
    private static final int BAR_SLOTS = 12;

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new ControllerDataProvider(), MultiblockControllerBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new ControllerStatusProvider(), MultiblockControllerBlock.class);
    }

    private static final class ControllerDataProvider implements IServerDataProvider<BlockAccessor> {
        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MultiblockControllerBlockEntity controller)) {
                return;
            }
            data.putBoolean("formed", controller.isFormed());
            data.putBoolean("assembling", controller.isAssembling());
            if (controller.isAssembling()) {
                data.putFloat("progress", controller.assemblyProgress());
            }
        }

        @Override
        public Identifier getUid() {
            return DATA_UID;
        }
    }

    private static final class ControllerStatusProvider implements IBlockComponentProvider {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("assembling")) {
                return;
            }
            if (data.getBooleanOr("assembling", false)) {
                tooltip.add(building(data.getFloatOr("progress", 0.0F)));
                return;
            }
            boolean formed = data.getBooleanOr("formed", false);
            tooltip.add(Component.translatable(formed
                    ? "jade.infinity_nexus_multiblock.formed"
                    : "jade.infinity_nexus_multiblock.unformed")
                    .withStyle(formed ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }

        @Override
        public Identifier getUid() {
            return COMPONENT_UID;
        }

        private static Component building(float progress) {
            float clamped = Mth.clamp(progress, 0.0F, 1.0F);
            int percent = Math.round(clamped * 100.0F);
            int filled = Math.round(clamped * BAR_SLOTS);
            int empty = BAR_SLOTS - filled;
            return Component.translatable(
                    "jade.infinity_nexus_multiblock.building",
                    "||".repeat(filled),
                    "=".repeat(empty),
                    percent)
                    .withStyle(ChatFormatting.YELLOW);
        }
    }
}
