package com.Infinity.Nexus.Multiblock.api;

import java.util.Optional;

import net.minecraft.resources.Identifier;

public record MultiblockSounds(Optional<Identifier> processing, Optional<Identifier> finish) {
    public static final MultiblockSounds EMPTY = new MultiblockSounds(Optional.empty(), Optional.empty());

    public MultiblockSounds {
        processing = processing == null ? Optional.empty() : processing;
        finish = finish == null ? Optional.empty() : finish;
    }

    public boolean isEmpty() {
        return this.processing.isEmpty() && this.finish.isEmpty();
    }
}
