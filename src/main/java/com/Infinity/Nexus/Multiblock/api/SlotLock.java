package com.Infinity.Nexus.Multiblock.api;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

public record SlotLock(String slotId, int slotIndex, ItemStack stack) {
    public static final Codec<SlotLock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("slot", "").forGetter(SlotLock::slotId),
            Codec.INT.optionalFieldOf("index", -1).forGetter(SlotLock::slotIndex),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(SlotLock::stack))
            .apply(instance, SlotLock::new));

    public SlotLock {
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(stack, "stack");
        if (slotIndex < -1) {
            throw new IllegalArgumentException("slotIndex cannot be less than -1");
        }
        stack = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    public boolean matches(ItemStack candidate) {
        return !this.stack.isEmpty()
                && !candidate.isEmpty()
                && ItemStack.isSameItemSameComponents(this.stack, candidate);
    }

    public String key() {
        return this.slotId.isBlank() ? "#" + this.slotIndex : this.slotId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof SlotLock other
                && this.slotIndex == other.slotIndex
                && this.slotId.equals(other.slotId)
                && ItemStack.isSameItemSameComponents(this.stack, other.stack);
    }

    @Override
    public int hashCode() {
        int result = this.slotId.hashCode();
        result = 31 * result + this.slotIndex;
        return 31 * result + ItemStack.hashItemAndComponents(this.stack);
    }
}
