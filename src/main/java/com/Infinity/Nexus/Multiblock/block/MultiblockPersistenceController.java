package com.Infinity.Nexus.Multiblock.block;

import java.util.Map;

import com.Infinity.Nexus.Multiblock.api.MultiblockDefinition;
import com.Infinity.Nexus.Multiblock.api.MultiblockMenuLayout;
import com.Infinity.Nexus.Multiblock.api.addon.MultiblockResourceStorage;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

final class MultiblockPersistenceController {
    private final MultiblockControllerBlockEntity owner;

    MultiblockPersistenceController(MultiblockControllerBlockEntity owner) {
        this.owner = owner;
    }

    void save(ValueOutput output) {
        if (this.owner.definitionId != null) {
            output.store("Definition", Identifier.CODEC, this.owner.definitionId);
        }
        output.putBoolean("Formed", this.owner.formed);
        output.store("Rotation", Rotation.CODEC, this.owner.rotation);
        output.store("Origin", BlockPos.CODEC, this.owner.origin);
        output.store("Members", BlockPos.CODEC.listOf(), this.owner.members);
        this.owner.recipe().save(output);
        this.owner.motions().save(output);
        this.owner.slots().save(output);
        if (this.owner.energyStorage != null) {
            this.owner.energyStorage.serialize(output.child("energy"));
        } else if (this.owner.pendingEnergy != null) {
            output.store("energy", CompoundTag.CODEC, this.owner.pendingEnergy);
        }
        if (this.owner.fluidHandler != null) {
            this.owner.fluidHandler.serialize(output.child("fluid"));
        } else if (this.owner.pendingFluid != null) {
            output.store("fluid", CompoundTag.CODEC, this.owner.pendingFluid);
        }
        if (!this.owner.resourceStorages.isEmpty()) {
            ValueOutput resources = output.child("resources");
            for (Map.Entry<String, MultiblockResourceStorage> entry : this.owner.resourceStorages.entrySet()) {
                entry.getValue().serialize(resources.child(entry.getKey()));
            }
        }
        if (!this.owner.pendingResources.isEmpty()) {
            output.store(
                    "PendingResources",
                    Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC),
                    Map.copyOf(this.owner.pendingResources));
        }
    }

    void load(ValueInput input) {
        this.owner.definitionId = input.read("Definition", Identifier.CODEC).orElse(null);
        this.owner.formed = input.getBooleanOr("Formed", false);
        this.owner.structureValidated = false;
        this.owner.rotation = input.read("Rotation", Rotation.CODEC).orElse(Rotation.NONE);
        this.owner.origin = input.read("Origin", BlockPos.CODEC).orElse(BlockPos.ZERO);
        this.owner.members = input.read("Members", BlockPos.CODEC.listOf()).orElse(java.util.List.of());
        this.owner.recipe().load(input);
        this.owner.motions().load(input, this.owner.definition());
        this.owner.slots().load(input);
        this.owner.pendingResources.clear();
        this.owner.pendingResources.putAll(input.read(
                "PendingResources",
                Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC)).orElse(Map.of()));
        input.read("resources", CompoundTag.CODEC).ifPresent(resources -> {
            for (String kind : resources.keySet()) {
                resources.getCompound(kind)
                        .ifPresent(tag -> this.owner.pendingResources.putIfAbsent(kind, tag));
            }
        });
        MultiblockDefinition definition = this.owner.definition();
        if (definition != null) {
            this.owner.menuLayout = MultiblockMenuLayout.create(definition);
        }
        this.owner.ensureConfigured();
        this.owner.slots().sanitize();
        if (this.owner.energyStorage != null) {
            input.child("energy").ifPresent(this.owner.energyStorage::deserialize);
        } else {
            this.owner.pendingEnergy = input.read("energy", CompoundTag.CODEC).orElse(null);
        }
        if (this.owner.fluidHandler != null) {
            input.child("fluid").ifPresent(this.owner.fluidHandler::deserialize);
        } else {
            this.owner.pendingFluid = input.read("fluid", CompoundTag.CODEC).orElse(null);
        }
        this.owner.afterLoad();
    }
}
