package com.Infinity.Nexus.Multiblock.api.addon;

public record ResourceTankInfo(String id, long capacity) {
    public ResourceTankInfo {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Resource tank id cannot be blank");
        }
        if (capacity < 1L) {
            throw new IllegalArgumentException("Resource tank capacity must be at least 1");
        }
    }
}
