package com.Infinity.Nexus.Multiblock.block;

import java.util.Objects;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

public final class MultiblockEnergyStorage extends SimpleEnergyHandler {
    private final String id;
    private final Runnable onChanged;

    public MultiblockEnergyStorage(String id, int capacity, int maxReceive, int maxExtract, Runnable onChanged) {
        super(capacity, maxReceive, maxExtract);
        this.id = Objects.requireNonNull(id, "id");
        this.onChanged = Objects.requireNonNull(onChanged, "onChanged");
    }

    public String id() {
        return this.id;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        this.onChanged.run();
    }
}
