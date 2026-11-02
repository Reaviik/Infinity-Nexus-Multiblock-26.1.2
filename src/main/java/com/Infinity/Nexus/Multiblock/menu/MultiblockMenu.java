package com.Infinity.Nexus.Multiblock.menu;

import com.Infinity.Nexus.Multiblock.api.MultiblockMenuLayout;
import com.Infinity.Nexus.Multiblock.api.SlotLock;
import com.Infinity.Nexus.Multiblock.block.MultiblockControllerBlockEntity;
import com.Infinity.Nexus.Multiblock.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class MultiblockMenu extends AbstractContainerMenu {
    private static final int PLAYER_INVENTORY_ROWS = 3;
    private static final int PLAYER_HOTBAR_SIZE = 9;

    private final MultiblockControllerBlockEntity blockEntity;
    private final MultiblockMenuLayout layout;
    private final @Nullable EntityType<?> outputEntityType;
    private int clientProgress;
    private int clientMaxProgress;
    private final ContainerData progressData = new ContainerData() {
        @Override
        public int get(int index) {
            MultiblockControllerBlockEntity controller = MultiblockMenu.this.blockEntity;
            boolean serverSide = controller.getLevel() != null && !controller.getLevel().isClientSide();
            return switch (index) {
                case 0 -> serverSide ? controller.progress() : MultiblockMenu.this.clientProgress;
                case 1 -> serverSide ? controller.maxProgress() : MultiblockMenu.this.clientMaxProgress;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> MultiblockMenu.this.clientProgress = value;
                case 1 -> MultiblockMenu.this.clientMaxProgress = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public MultiblockMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, readBlockEntity(inventory, data), readLayout(data), readEntityType(data));
    }

    public MultiblockMenu(int containerId, Inventory inventory, MultiblockControllerBlockEntity blockEntity,
            MultiblockMenuLayout layout) {
        this(containerId, inventory, blockEntity, layout, blockEntity.previewEntityOutputType());
    }

    private MultiblockMenu(int containerId, Inventory inventory, MultiblockControllerBlockEntity blockEntity,
            MultiblockMenuLayout layout, @Nullable EntityType<?> outputEntityType) {
        super(ModMenus.CONTROLLER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.layout = layout;
        this.outputEntityType = outputEntityType;

        for (MultiblockMenuLayout.SlotPlacement placement : layout.slots()) {
            this.addSlot(new MultiblockSlot(blockEntity, placement.index(), placement.x(), placement.y(),
                    placement.limit(), placement.role(), placement.filter()));
        }

        int playerX = layout.playerInventoryX();
        int playerY = layout.playerInventoryY();
        for (int row = 0; row < PLAYER_INVENTORY_ROWS; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, 9 + row * 9 + column, playerX + column * 18, playerY + row * 18));
            }
        }
        for (int column = 0; column < PLAYER_HOTBAR_SIZE; column++) {
            this.addSlot(new Slot(inventory, column, playerX + column * 18, playerY + 58));
        }
        this.addDataSlots(this.progressData);
    }

    private static MultiblockControllerBlockEntity readBlockEntity(Inventory inventory, RegistryFriendlyByteBuf data) {
        if (data == null) {
            throw new IllegalStateException("Missing multiblock menu data");
        }
        BlockPos pos = data.readBlockPos();
        if (inventory.player.level().getBlockEntity(pos) instanceof MultiblockControllerBlockEntity controller) {
            return controller;
        }
        throw new IllegalStateException("Missing multiblock controller at " + pos);
    }

    private static MultiblockMenuLayout readLayout(RegistryFriendlyByteBuf data) {
        CompoundTag tag = data.readNbt();
        if (tag == null) {
            throw new IllegalStateException("Missing multiblock gui layout");
        }
        return MultiblockMenuLayout.CODEC.parse(NbtOps.INSTANCE, tag)
                .getOrThrow(message -> new IllegalStateException("Invalid multiblock gui layout: " + message));
    }

    private static @Nullable EntityType<?> readEntityType(RegistryFriendlyByteBuf data) {
        String rawId = data.readUtf();
        if (rawId.isBlank()) {
            return null;
        }
        Identifier id = Identifier.tryParse(rawId);
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    public MultiblockControllerBlockEntity blockEntity() {
        return this.blockEntity;
    }

    public MultiblockMenuLayout layout() {
        return this.layout;
    }

    public @Nullable SlotLock slotLock(int slotIndex) {
        return this.blockEntity.slotLockAt(slotIndex);
    }

    public @Nullable EntityType<?> outputEntityType() {
        return this.outputEntityType;
    }

    public int progress() {
        return this.progressData.get(0);
    }

    public int maxProgress() {
        return this.progressData.get(1);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this.blockEntity, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = this.layout.slots().size();

        if (index < machineSlots) {
            if (!this.moveItemStackTo(stack, machineSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, machineSlots, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
