package com.Infinity.Nexus.Multiblock.block;

import java.util.List;
import java.util.Objects;

import com.Infinity.Nexus.Multiblock.api.FluidTank;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class MultiblockFluidHandler extends FluidStacksResourceHandler {
    private final List<FluidTank> tanks;
    private final Runnable onChanged;

    public MultiblockFluidHandler(List<FluidTank> tanks, Runnable onChanged) {
        super(tanks.size(), tanks.stream().mapToInt(FluidTank::capacity).max().orElse(0));
        this.tanks = List.copyOf(tanks);
        this.onChanged = Objects.requireNonNull(onChanged, "onChanged");
    }

    public FluidTank tank(int index) {
        return this.tanks.get(index);
    }

    public int indexOf(String id) {
        for (int index = 0; index < this.tanks.size(); index++) {
            if (this.tanks.get(index).id().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    public ResourceHandler<FluidResource> manualAccess(int index) {
        Objects.checkIndex(index, size());
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public FluidResource getResource(int ignored) {
                return MultiblockFluidHandler.this.getResource(index);
            }

            @Override
            public long getAmountAsLong(int ignored) {
                return MultiblockFluidHandler.this.getAmountAsLong(index);
            }

            @Override
            public long getCapacityAsLong(int ignored, FluidResource resource) {
                return MultiblockFluidHandler.this.getCapacityAsLong(index, resource);
            }

            @Override
            public boolean isValid(int ignored, FluidResource resource) {
                return MultiblockFluidHandler.this.isValid(index, resource);
            }

            @Override
            public int insert(int ignored, FluidResource resource, int amount, TransactionContext transaction) {
                return insertUnlimited(index, resource, amount, transaction);
            }

            @Override
            public int extract(int ignored, FluidResource resource, int amount, TransactionContext transaction) {
                return extractUnlimited(index, resource, amount, transaction);
            }
        };
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return this.tanks.get(index).accepts(resource);
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return this.tanks.get(index).capacity();
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        int limit = this.tanks.get(index).maxReceive();
        return limit == 0 ? 0 : super.insert(index, resource, Math.min(amount, limit), transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        int limit = this.tanks.get(index).maxExtract();
        return limit == 0 ? 0 : super.extract(index, resource, Math.min(amount, limit), transaction);
    }

    private int insertUnlimited(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return super.insert(index, resource, amount, transaction);
    }

    private int extractUnlimited(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return super.extract(index, resource, amount, transaction);
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        this.onChanged.run();
    }
}
