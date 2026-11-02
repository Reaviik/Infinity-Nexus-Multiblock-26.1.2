package com.Infinity.Nexus.Multiblock.block;

import java.util.List;
import java.util.Objects;

import com.Infinity.Nexus.Multiblock.api.FluidTank;
import net.neoforged.neoforge.fluids.FluidStack;
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

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        this.onChanged.run();
    }
}
