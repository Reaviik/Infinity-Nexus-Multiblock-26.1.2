package com.Infinity.Nexus.Multiblock.api.addon;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Storage contract for an addon-provided resource kind (chemical, mana, essentia, ...).
 *
 * <p>The base mod never references mod-specific resource classes. An addon implements
 * this interface once per kind and registers a {@link MultiblockResourceType}.</p>
 */
public interface MultiblockResourceStorage {
    String kind();

    int size();

    String tankId(int index);

    long capacity(int index);

    long amount(int index);

    String resourceId(int index);

    boolean accepts(int index, String resourceId);

    default long space(int index, String resourceId) {
        return Math.max(0L, this.capacity(index) - this.amount(index));
    }

    default boolean perResourceSpace() {
        return false;
    }

    default boolean requiresSpaceToStart() {
        return false;
    }

    long insert(int index, String resourceId, long amount, boolean simulate);

    long extract(int index, String resourceId, long amount, boolean simulate);

    void set(int index, String resourceId, long amount);

    boolean allowsPort(int index, char symbol, BlockState state, Rotation rotation);

    List<StoredResource> snapshot();

    void restore(List<StoredResource> stored);

    void serialize(ValueOutput output);

    void deserialize(ValueInput input);

    default int indexOf(String tankId) {
        for (int index = 0; index < this.size(); index++) {
            if (this.tankId(index).equals(tankId)) {
                return index;
            }
        }
        return -1;
    }

    default ItemStack drainContainer(int index, ItemStack stack) {
        return stack;
    }

    default ItemStack fillContainer(int index, ItemStack stack) {
        return stack;
    }

    default Component displayName(int index) {
        String resourceId = this.resourceId(index);
        MultiblockResourceType type = MultiblockAddons.get(this.kind());
        if (resourceId.isEmpty() || type == null) {
            return Component.empty();
        }
        return type.displayName(resourceId);
    }

    default int color(int index) {
        MultiblockResourceType type = MultiblockAddons.get(this.kind());
        return type == null ? 0xFFFFFFFF : type.color(this.resourceId(index));
    }
}
