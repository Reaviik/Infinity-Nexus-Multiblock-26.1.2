package com.Infinity.Nexus.Multiblock.block;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.Infinity.Nexus.Core.itemStackHandler.RestrictedItemStackHandler;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockMenuLayout;
import com.Infinity.Nexus.Multiblock.api.SlotLock;
import com.Infinity.Nexus.Multiblock.api.addon.MultiblockResourceStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.model.data.ModelProperty;
import org.jspecify.annotations.Nullable;

public abstract class MultiblockControllerBase extends BlockEntity {
    public static final ModelProperty<Identifier> DEFINITION_PROPERTY = new ModelProperty<>();
    protected static final long AREA_ENTITY_SCAN_INTERVAL = 10L;
    protected static final int PROCESSING_SOUND_INTERVAL = 20;

    protected RestrictedItemStackHandler inventory;
    protected MultiblockMenuLayout menuLayout;
    protected @Nullable MultiblockEnergyStorage energyStorage;
    protected @Nullable MultiblockFluidHandler fluidHandler;
    protected boolean formed;
    protected boolean structureValidated;
    protected Rotation rotation = Rotation.NONE;
    protected BlockPos origin = BlockPos.ZERO;
    protected List<BlockPos> members = List.of();
    protected long lastValidationTick = -1L;
    protected boolean capabilitySyncPending;
    protected int[] inputSlots;
    protected int[] outputSlots;
    protected @Nullable Identifier definitionId;
    protected @Nullable MultiblockDefinition configuredDefinition;
    protected @Nullable CompoundTag pendingEnergy;
    protected @Nullable CompoundTag pendingFluid;
    protected final Map<String, SlotLock> slotLocks = new LinkedHashMap<>();
    protected final Map<String, String> fluidSlotTanks = new LinkedHashMap<>();
    protected final Set<String> drainedFluidSlots = new HashSet<>();
    protected final Map<String, MultiblockResourceStorage> resourceStorages = new LinkedHashMap<>();
    protected final Map<String, CompoundTag> pendingResources = new LinkedHashMap<>();

    protected MultiblockControllerBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}
