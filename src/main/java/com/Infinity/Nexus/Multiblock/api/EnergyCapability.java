package com.Infinity.Nexus.Multiblock.api;

public record EnergyCapability(
        String id,
        int capacity,
        int maxReceive,
        int maxExtract) {

    public EnergyCapability {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Energy capability id cannot be blank");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("Energy capacity must be at least 1");
        }
        if (maxReceive < 0 || maxExtract < 0) {
            throw new IllegalArgumentException("Energy transfer limits cannot be negative");
        }
    }
}
