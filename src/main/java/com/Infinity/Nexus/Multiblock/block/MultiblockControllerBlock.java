package com.Infinity.Nexus.Multiblock.block;

import java.util.List;
import java.util.function.Supplier;

import com.Infinity.Nexus.Multiblock.registry.ModDataComponents;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class MultiblockControllerBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final BooleanProperty PROCESSING = BooleanProperty.create("processing");

    private final Supplier<BlockEntityType<?>> blockEntityType;

    public MultiblockControllerBlock(Supplier<BlockEntityType<?>> blockEntityType, Properties properties) {
        super(properties);
        this.blockEntityType = blockEntityType;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FORMED, false)
                .setValue(PROCESSING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new MultiblockControllerBlock(this.blockEntityType, properties));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED, PROCESSING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!context.getItemInHand().has(ModDataComponents.MULTIBLOCK_DEFINITION.get())) {
            return null;
        }
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(FORMED, false)
                .setValue(PROCESSING, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        Identifier definitionId = stack.get(ModDataComponents.MULTIBLOCK_DEFINITION.get());
        if (definitionId != null
                && level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
            controller.setDefinitionId(definitionId);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockControllerBlockEntity(this.blockEntityType.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return (tickLevel, tickPos, tickState, blockEntity) -> {
                if (blockEntity instanceof MultiblockControllerBlockEntity controller) {
                    controller.clientTick();
                }
            };
        }
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof MultiblockControllerBlockEntity controller) {
                controller.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (tryExport(level, pos, player)) {
            return InteractionResult.SUCCESS;
        }
        if (!state.getValue(FORMED)) {
            if (!level.isClientSide()) {
                if (player.isSecondaryUseActive()) {
                    player.sendOverlayMessage(Component.translatable("message.infinity_nexus_multiblock.not_formed"));
                } else if (level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
                    controller.tryAssemble(player);
                } else {
                    player.sendOverlayMessage(Component.translatable("message.infinity_nexus_multiblock.not_formed"));
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
            player.openMenu(controller);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (tryExport(level, pos, player)) {
            return InteractionResult.SUCCESS;
        }
        if (!state.getValue(FORMED) && !player.isSecondaryUseActive()) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
                controller.tryAssemble(player);
            }
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    private static boolean tryExport(Level level, BlockPos pos, Player player) {
        if (!player.isCreative() || player.isSpectator() || !player.isSecondaryUseActive()) {
            return false;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
            controller.exportStructure(player);
        }
        return true;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
            controller.onNeighborChanged();
        }
    }

    @Override
    public boolean shouldChangedStateKeepBlockEntity(BlockState oldState) {
        return oldState.is(this);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        ItemStack stack = new ItemStack(this);
        if (level.getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller
                && controller.definitionId() != null) {
            stack.set(ModDataComponents.MULTIBLOCK_DEFINITION.get(), controller.definitionId());
        }
        return stack;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof MultiblockControllerBlockEntity controller
                && controller.definitionId() != null) {
            ItemStack stack = new ItemStack(this);
            stack.set(ModDataComponents.MULTIBLOCK_DEFINITION.get(), controller.definitionId());
            return List.of(stack);
        }
        return List.of(new ItemStack(this));
    }

    public static Rotation rotationFromFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            case SOUTH -> Rotation.NONE;
            case WEST -> Rotation.CLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static Rotation rotationFromState(BlockState state) {
        return rotationFromFacing(state.getValue(FACING));
    }

    public static Rotation inverseRotation(Rotation rotation) {
        return switch (rotation) {
            case NONE -> Rotation.NONE;
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case CLOCKWISE_180 -> Rotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
        };
    }
}
