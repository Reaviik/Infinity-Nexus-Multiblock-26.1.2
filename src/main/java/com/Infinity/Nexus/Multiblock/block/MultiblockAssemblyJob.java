package com.Infinity.Nexus.Multiblock.block;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.Infinity.Nexus.Multiblock.api.BlockMatcher;
import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

public final class MultiblockAssemblyJob {
    public enum TickResult {
        RUNNING,
        COMPLETE,
        PARTIAL
    }

    private enum Phase {
        SCAN,
        PLACE
    }

    private final MultiblockDefinition definition;
    private final BlockPos controllerPos;
    private final Rotation rotation;
    private final BlockPos origin;
    private final UUID playerId;
    private final boolean creative;
    private final List<MultiblockPattern.PatternCell> cells;
    private final int totalCells;
    private final List<Target> pending = new ArrayList<>();
    private final Set<BlockPos> blockedPositions = new LinkedHashSet<>();
    private final Map<BlockMatcher, BlockState> representativeStates = new IdentityHashMap<>();

    private Phase phase = Phase.SCAN;
    private int scanCursor;
    private int placeCursor;
    private int consecutiveFailures;
    private int blocked;
    private int placed;
    private int failed;
    private int correct;

    public MultiblockAssemblyJob(
            MultiblockDefinition definition,
            BlockPos controllerPos,
            Rotation rotation,
            UUID playerId,
            boolean creative) {
        this.definition = definition;
        this.controllerPos = controllerPos;
        this.rotation = rotation;
        this.origin = definition.origin(controllerPos, rotation);
        this.playerId = playerId;
        this.creative = creative;
        this.cells = definition.pattern().cells();
        this.totalCells = (int) this.cells.stream().filter(cell -> !cell.anchor()).count();
    }

    public UUID playerId() {
        return this.playerId;
    }

    public boolean isCreative() {
        return this.creative;
    }

    public int placed() {
        return this.placed;
    }

    public int blocked() {
        return this.blocked;
    }

    public List<BlockPos> blockedPositions() {
        return List.copyOf(this.blockedPositions);
    }

    public int failed() {
        return this.failed;
    }

    public int totalCells() {
        return this.totalCells;
    }

    public int completedCells() {
        return this.correct + this.placed;
    }

    public float progress() {
        if (this.totalCells <= 0) {
            return 1.0F;
        }
        return Math.min(1.0F, (float) this.completedCells() / this.totalCells);
    }

    public int pendingCount() {
        return this.pending.size();
    }

    public TickResult tick(
            Level level,
            @Nullable ServerPlayer player,
            int blocksPerTick,
            int maxFailedScansPerTick) {
        boolean scanComplete = this.phase == Phase.PLACE;
        if (this.phase == Phase.SCAN) {
            int scanned = 0;
            while (this.scanCursor < this.cells.size() && scanned < blocksPerTick) {
                MultiblockPattern.PatternCell cell = this.cells.get(this.scanCursor++);
                scanned++;
                if (cell.anchor()) {
                    continue;
                }
                BlockPos offset = new BlockPos(cell.x(), cell.y(), cell.z()).rotate(this.rotation);
                BlockPos pos = this.origin.offset(offset.getX(), offset.getY(), offset.getZ());
                BlockState current = level.getBlockState(pos);
                if (cell.matcher().matches(current, this.rotation)) {
                    this.correct++;
                    continue;
                }
                if (!current.canBeReplaced()) {
                    this.blocked++;
                    this.blockedPositions.add(pos);
                    continue;
                }
                this.pending.add(new Target(pos, cell.matcher()));
            }
            if (this.scanCursor >= this.cells.size()) {
                this.phase = Phase.PLACE;
                scanComplete = true;
            }
        }

        if (!this.pending.isEmpty()) {
            List<Slot> sources = this.creative ? List.of() : this.collectSources(level, player);
            int successes = 0;
            int failures = 0;
            int limit = this.pending.size();
            int checks = 0;
            boolean soundPlayed = false;
            while (!this.pending.isEmpty()
                    && successes < blocksPerTick
                    && failures < maxFailedScansPerTick
                    && checks < limit) {
                if (this.placeCursor >= this.pending.size()) {
                    this.placeCursor = 0;
                }
                Target target = this.pending.get(this.placeCursor);
                BlockState current = level.getBlockState(target.pos());
                if (target.matcher().matches(current, this.rotation)) {
                    this.correct++;
                    this.pending.remove(this.placeCursor);
                    continue;
                }
                if (!current.canBeReplaced()) {
                    this.pending.remove(this.placeCursor);
                    this.blocked++;
                    this.blockedPositions.add(target.pos());
                    continue;
                }

                BlockState placement;
                Slot chosen = null;
                if (this.creative) {
                    placement = this.representativeState(target.matcher());
                } else {
                    placement = null;
                    for (Slot source : sources) {
                        BlockState candidate = source.placement(target.matcher(), this.rotation);
                        if (candidate != null) {
                            placement = candidate;
                            chosen = source;
                            break;
                        }
                    }
                }

                if (placement == null) {
                    this.advance();
                    failures++;
                    checks++;
                    this.consecutiveFailures++;
                    continue;
                }

                if (level.setBlock(target.pos(), placement, Block.UPDATE_ALL)) {
                    if (!soundPlayed) {
                        playPlaceSound(level, target.pos(), placement, player);
                        soundPlayed = true;
                    }
                    if (chosen != null) {
                        chosen.consume();
                    }
                    this.pending.remove(this.placeCursor);
                    this.placed++;
                    successes++;
                    checks++;
                    this.consecutiveFailures = 0;
                } else {
                    this.advance();
                    failures++;
                    checks++;
                    this.consecutiveFailures++;
                    this.failed++;
                }
            }

            if (!this.creative) {
                boolean playerChanged = false;
                for (Slot source : sources) {
                    playerChanged |= source.commit();
                }
                if (playerChanged && player != null) {
                    player.getInventory().setChanged();
                }
            }
        }

        if (scanComplete) {
            if (this.pending.isEmpty()) {
                return TickResult.COMPLETE;
            }
            if (this.consecutiveFailures >= this.pending.size()) {
                return TickResult.PARTIAL;
            }
        }
        return TickResult.RUNNING;
    }

    private static void playPlaceSound(Level level, BlockPos pos, BlockState state, @Nullable ServerPlayer player) {
        SoundType soundType = state.getSoundType(level, pos, player);
        level.playSound(
                null,
                pos,
                soundType.getPlaceSound(),
                SoundSource.BLOCKS,
                (soundType.getVolume() + 1.0F) / 2.0F,
                soundType.getPitch() * 0.8F);
    }

    private void advance() {
        if (this.pending.isEmpty()) {
            return;
        }
        this.placeCursor++;
        if (this.placeCursor >= this.pending.size()) {
            this.placeCursor = 0;
        }
    }

    private @Nullable BlockState representativeState(BlockMatcher matcher) {
        BlockState cached = this.representativeStates.get(matcher);
        if (cached != null) {
            return cached;
        }
        BlockState state = MultiblockAssemblyHelper.representativeState(matcher, this.rotation);
        if (state != null) {
            this.representativeStates.put(matcher, state);
        }
        return state;
    }

    private List<Slot> collectSources(Level level, @Nullable ServerPlayer player) {
        List<Slot> sources = new ArrayList<>();
        if (player != null) {
            List<ItemStack> playerStacks = new ArrayList<>(player.getInventory().getNonEquipmentItems());
            ItemStack offhand = player.getOffhandItem();
            boolean offhandListed = false;
            for (ItemStack stack : playerStacks) {
                if (stack == offhand) {
                    offhandListed = true;
                    break;
                }
            }
            if (!offhand.isEmpty() && !offhandListed) {
                playerStacks.add(offhand);
            }
            for (ItemStack stack : playerStacks) {
                if (!stack.isEmpty()) {
                    sources.add(Slot.player(stack));
                }
            }
        }

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = this.controllerPos.relative(direction);
            ResourceHandler<ItemResource> handler = level.getCapability(
                    Capabilities.Item.BLOCK, neighbor, direction.getOpposite());
            if (handler == null) {
                BlockEntity blockEntity = level.getBlockEntity(neighbor);
                if (blockEntity instanceof Container container) {
                    handler = VanillaContainerWrapper.ofUncached(container);
                }
            }
            if (handler == null) {
                continue;
            }
            int size = handler.size();
            for (int index = 0; index < size; index++) {
                ItemResource resource = handler.getResource(index);
                if (resource.isEmpty()) {
                    continue;
                }
                int amount = handler.getAmountAsInt(index);
                if (amount > 0) {
                    sources.add(Slot.handler(handler, index, resource, amount));
                }
            }
        }
        return sources;
    }

    private record Target(BlockPos pos, BlockMatcher matcher) {}

    private static final class Slot {
        private final @Nullable ItemStack stack;
        private final @Nullable ResourceHandler<ItemResource> handler;
        private final int index;
        private final @Nullable ItemResource resource;
        private int remaining;
        private int consumed;
        private @Nullable Boolean extractable;

        private Slot(
                @Nullable ItemStack stack,
                @Nullable ResourceHandler<ItemResource> handler,
                int index,
                @Nullable ItemResource resource,
                int remaining) {
            this.stack = stack;
            this.handler = handler;
            this.index = index;
            this.resource = resource;
            this.remaining = remaining;
        }

        static Slot player(ItemStack stack) {
            return new Slot(stack, null, -1, null, stack.getCount());
        }

        static Slot handler(ResourceHandler<ItemResource> handler, int index, ItemResource resource, int amount) {
            return new Slot(null, handler, index, resource, amount);
        }

        @Nullable BlockState placement(BlockMatcher matcher, Rotation rotation) {
            if (this.remaining <= 0) {
                return null;
            }
            if (this.stack != null) {
                return MultiblockAssemblyHelper.placementState(this.stack, matcher, rotation);
            }
            if (this.handler == null || this.resource == null || !this.canExtract()) {
                return null;
            }
            return MultiblockAssemblyHelper.placementState(this.resource.toStack(1), matcher, rotation);
        }

        private boolean canExtract() {
            if (this.extractable != null) {
                return this.extractable;
            }
            if (this.handler == null || this.resource == null || this.index < 0) {
                this.extractable = false;
                return false;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                this.extractable = this.handler.extract(this.index, this.resource, 1, transaction) > 0;
            }
            return this.extractable;
        }

        void consume() {
            this.remaining--;
            this.consumed++;
        }

        boolean commit() {
            if (this.consumed <= 0) {
                return false;
            }
            if (this.stack != null) {
                this.stack.shrink(this.consumed);
                return true;
            }
            if (this.handler != null && this.resource != null) {
                try (Transaction transaction = Transaction.openRoot()) {
                    int extracted = this.handler.extract(this.index, this.resource, this.consumed, transaction);
                    if (extracted > 0) {
                        transaction.commit();
                    }
                }
            }
            return false;
        }
    }
}
