package com.Infinity.Nexus.Multiblock.api;

import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

public record FluidTank(
        String id,
        int capacity,
        int maxReceive,
        int maxExtract,
        @Nullable FluidFilter filter,
        CapabilityPorts.@Nullable PortSelector port) {

    public FluidTank {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Fluid tank id cannot be blank");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("Fluid tank capacity must be at least 1");
        }
        if (maxReceive < 0 || maxExtract < 0) {
            throw new IllegalArgumentException("Fluid tank transfer limits cannot be negative");
        }
    }

    public FluidTank(String id, int capacity, int maxReceive, int maxExtract, @Nullable FluidFilter filter) {
        this(id, capacity, maxReceive, maxExtract, filter, null);
    }

    public boolean accepts(FluidResource resource) {
        return this.filter == null || this.filter.matches(resource);
    }

    public boolean allowsPort(char symbol, BlockState state, Rotation rotation) {
        return this.port == null || this.port.matches(symbol, state, rotation);
    }
}
