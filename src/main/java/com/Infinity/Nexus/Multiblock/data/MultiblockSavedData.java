package com.Infinity.Nexus.Multiblock.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

public class MultiblockSavedData extends SavedData {
    public record Entry(BlockPos controller, BlockPos member) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("controller").forGetter(Entry::controller),
                BlockPos.CODEC.fieldOf("member").forGetter(Entry::member))
                .apply(instance, Entry::new));
    }

    public record Registration(boolean accepted, List<BlockPos> evicted) {
        public Registration {
            evicted = List.copyOf(evicted);
        }
    }

    private static final Codec<MultiblockSavedData> CODEC = Entry.CODEC.listOf()
            .xmap(MultiblockSavedData::new, MultiblockSavedData::entries);

    private static final SavedDataType<MultiblockSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "multiblock_index"),
            MultiblockSavedData::new,
            CODEC);

    private final Map<BlockPos, BlockPos> controllerByMember = new LinkedHashMap<>();
    private final Map<BlockPos, List<BlockPos>> membersByController = new LinkedHashMap<>();
    private final Map<BlockPos, Integer> priorityByController = new LinkedHashMap<>();

    public MultiblockSavedData() {}

    private MultiblockSavedData(List<Entry> entries) {
        for (Entry entry : entries) {
            this.addEntry(entry.controller(), entry.member());
        }
    }

    public static MultiblockSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public static @Nullable MultiblockSavedData getIfPresent(ServerLevel level) {
        return level.getDataStorage().get(TYPE);
    }

    public @Nullable BlockPos getController(BlockPos member) {
        return this.controllerByMember.get(member);
    }

    public List<BlockPos> getMembers(BlockPos controller) {
        List<BlockPos> members = this.membersByController.get(controller);
        return members == null ? List.of() : List.copyOf(members);
    }

    public Registration register(BlockPos controller, List<BlockPos> members, int priority) {
        List<BlockPos> existing = this.membersByController.get(controller);
        List<BlockPos> evicted = new ArrayList<>();

        for (BlockPos member : members) {
            BlockPos owner = this.controllerByMember.get(member);
            if (owner == null || owner.equals(controller) || evicted.contains(owner)) {
                continue;
            }
            int ownerPriority = this.priorityByController.getOrDefault(owner, 0);
            if (priority < ownerPriority
                    || (priority == ownerPriority && controller.asLong() > owner.asLong())) {
                return new Registration(false, List.of());
            }
            evicted.add(owner);
        }

        if (existing != null && existing.equals(members) && evicted.isEmpty()) {
            if (!Integer.valueOf(priority).equals(this.priorityByController.get(controller))) {
                this.priorityByController.put(controller, priority);
                this.setDirty();
            }
            return new Registration(true, List.of());
        }

        for (BlockPos owner : evicted) {
            this.unregisterController(owner);
        }
        this.unregisterController(controller);
        this.priorityByController.put(controller, priority);
        for (BlockPos member : members) {
            if (member.equals(controller)) {
                continue;
            }
            this.controllerByMember.put(member, controller);
            this.membersByController.computeIfAbsent(controller, ignored -> new ArrayList<>()).add(member);
        }
        this.setDirty();
        return new Registration(true, evicted);
    }

    public void unregister(BlockPos member) {
        BlockPos controller = this.controllerByMember.remove(member);
        if (controller == null) {
            return;
        }
        List<BlockPos> members = this.membersByController.get(controller);
        if (members != null) {
            members.remove(member);
            if (members.isEmpty()) {
                this.membersByController.remove(controller);
                this.priorityByController.remove(controller);
            }
        }
        this.setDirty();
    }

    public void unregisterController(BlockPos controller) {
        Integer oldPriority = this.priorityByController.remove(controller);
        List<BlockPos> members = this.membersByController.remove(controller);
        if (members == null) {
            if (oldPriority != null) {
                this.setDirty();
            }
            return;
        }
        for (BlockPos member : members) {
            this.controllerByMember.remove(member, controller);
        }
        this.setDirty();
    }

    private void addEntry(BlockPos controller, BlockPos member) {
        if (member.equals(controller)) {
            this.priorityByController.putIfAbsent(controller, 0);
            return;
        }
        this.priorityByController.putIfAbsent(controller, 0);
        if (this.controllerByMember.putIfAbsent(member, controller) == null) {
            this.membersByController.computeIfAbsent(controller, ignored -> new ArrayList<>()).add(member);
        }
    }

    public List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        this.membersByController.forEach((controller, members) -> {
            for (BlockPos member : members) {
                entries.add(new Entry(controller, member));
            }
        });
        return List.copyOf(entries);
    }
}
