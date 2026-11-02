package com.Infinity.Nexus.Multiblock.api;

public record SlotPosition(int x, int y) {
    public SlotPosition {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("Slot position cannot be negative");
        }
    }
}
