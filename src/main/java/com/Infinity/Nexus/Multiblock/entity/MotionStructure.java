package com.Infinity.Nexus.Multiblock.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Snapshot esparso de uma parte movel: cada bloco guarda a posicao relativa ao
 * canto minimo da caixa delimitadora. Serve tanto para o renderer (via sync)
 * quanto para devolver os blocos ao lugar quando a parte volta.
 */
public final class MotionStructure {
    private MotionStructure() {
    }

    public record StructureSnapshot(int sx, int sy, int sz, List<PartEntry> parts) {
        public static final StructureSnapshot EMPTY = new StructureSnapshot(0, 0, 0, List.of());

        public StructureSnapshot {
            parts = parts == null ? List.of() : List.copyOf(parts);
        }

        public boolean isEmpty() {
            return this.parts.isEmpty();
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("sx", this.sx);
            tag.putInt("sy", this.sy);
            tag.putInt("sz", this.sz);
            ListTag list = new ListTag();
            for (PartEntry part : this.parts) {
                CompoundTag entry = new CompoundTag();
                entry.putShort("x", (short) part.dx());
                entry.putShort("y", (short) part.dy());
                entry.putShort("z", (short) part.dz());
                entry.putInt("state", Block.BLOCK_STATE_REGISTRY.getId(part.state()));
                part.nbt().ifPresent(nbt -> entry.put("nbt", nbt.copy()));
                list.add(entry);
            }
            tag.put("parts", list);
            return tag;
        }

        public static StructureSnapshot fromTag(CompoundTag tag) {
            int sx = tag.getIntOr("sx", 0);
            int sy = tag.getIntOr("sy", 0);
            int sz = tag.getIntOr("sz", 0);
            List<PartEntry> parts = new ArrayList<>();
            tag.getList("parts").ifPresent(list -> {
                for (Tag element : list) {
                    if (!(element instanceof CompoundTag entry)) {
                        continue;
                    }
                    int dx = entry.getShortOr("x", (short) 0);
                    int dy = entry.getShortOr("y", (short) 0);
                    int dz = entry.getShortOr("z", (short) 0);
                    BlockState state = Block.BLOCK_STATE_REGISTRY.byId(entry.getIntOr("state", 0));
                    if (state == null) {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    parts.add(new PartEntry(dx, dy, dz, state, entry.getCompound("nbt")));
                }
            });
            return new StructureSnapshot(sx, sy, sz, parts);
        }
    }

    public record PartEntry(int dx, int dy, int dz, BlockState state, Optional<CompoundTag> nbt) {
        public static final Codec<PartEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("x").forGetter(PartEntry::dx),
                Codec.INT.fieldOf("y").forGetter(PartEntry::dy),
                Codec.INT.fieldOf("z").forGetter(PartEntry::dz),
                BlockState.CODEC.fieldOf("state").forGetter(PartEntry::state),
                CompoundTag.CODEC.optionalFieldOf("nbt").forGetter(PartEntry::nbt))
                .apply(instance, PartEntry::new));

        public PartEntry {
            state = state == null ? Blocks.AIR.defaultBlockState() : state;
            nbt = nbt == null ? Optional.empty() : nbt;
        }
    }
}
