package com.Infinity.Nexus.Multiblock.api;

import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

public record FluidTank(
        String id,
        int capacity,
        int maxReceive,
        int maxExtract,
        @Nullable FluidFilter filter) {

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

    public boolean accepts(FluidResource resource) {
        return this.filter == null || this.filter.matches(resource);
    }
}
