package com.Infinity.Nexus.Multiblock.block;

import java.util.ArrayList;
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

    public ResourceHandler<FluidResource> indexedAccess(List<Integer> indexes) {
        List<Integer> tanks = List.copyOf(indexes);
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return tanks.size();
            }

            @Override
            public FluidResource getResource(int slot) {
                return MultiblockFluidHandler.this.getResource(tanks.get(slot));
            }

            @Override
            public long getAmountAsLong(int slot) {
                return MultiblockFluidHandler.this.getAmountAsLong(tanks.get(slot));
            }

            @Override
            public long getCapacityAsLong(int slot, FluidResource resource) {
                return MultiblockFluidHandler.this.getCapacityAsLong(tanks.get(slot), resource);
            }

            @Override
            public boolean isValid(int slot, FluidResource resource) {
                return MultiblockFluidHandler.this.isValid(tanks.get(slot), resource);
            }

            @Override
            public int insert(int slot, FluidResource resource, int amount, TransactionContext transaction) {
                return MultiblockFluidHandler.this.insert(tanks.get(slot), resource, amount, transaction);
            }

            @Override
            public int extract(int slot, FluidResource resource, int amount, TransactionContext transaction) {
                return MultiblockFluidHandler.this.extract(tanks.get(slot), resource, amount, transaction);
            }
        };
    }

    public ResourceHandler<FluidResource> manualAccess(int index) {
        Objects.checkIndex(index, size());
        return manualAccess(List.of(index));
    }

    public ResourceHandler<FluidResource> manualAccess(List<Integer> indexes) {
        List<Integer> tanks = List.copyOf(indexes);
        for (int tank : tanks) {
            Objects.checkIndex(tank, size());
        }
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return tanks.size();
            }

            @Override
            public FluidResource getResource(int slot) {
                return MultiblockFluidHandler.this.getResource(tanks.get(slot));
            }

            @Override
            public long getAmountAsLong(int slot) {
                return MultiblockFluidHandler.this.getAmountAsLong(tanks.get(slot));
            }

            @Override
            public long getCapacityAsLong(int slot, FluidResource resource) {
                return MultiblockFluidHandler.this.getCapacityAsLong(tanks.get(slot), resource);
            }

            @Override
            public boolean isValid(int slot, FluidResource resource) {
                return MultiblockFluidHandler.this.isValid(tanks.get(slot), resource);
            }

            @Override
            public int insert(int slot, FluidResource resource, int amount, TransactionContext transaction) {
                return insertUnlimited(tanks.get(slot), resource, amount, transaction);
            }

            @Override
            public int extract(int slot, FluidResource resource, int amount, TransactionContext transaction) {
                return extractUnlimited(tanks.get(slot), resource, amount, transaction);
            }
        };
    }

    public ResourceHandler<FluidResource> manualAccessAll() {
        List<Integer> indexes = new ArrayList<>(size());
        for (int index = 0; index < size(); index++) {
            indexes.add(index);
        }
        return manualAccess(indexes);
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
