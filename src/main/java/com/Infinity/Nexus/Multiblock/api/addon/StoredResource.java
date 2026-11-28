package com.Infinity.Nexus.Multiblock.api.addon;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record StoredResource(String kind, String tank, String id, long amount) {
    public static final Codec<StoredResource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("kind").forGetter(StoredResource::kind),
            Codec.STRING.fieldOf("tank").forGetter(StoredResource::tank),
            Codec.STRING.optionalFieldOf("id", "").forGetter(StoredResource::id),
            Codec.LONG.optionalFieldOf("amount", 0L).forGetter(StoredResource::amount))
            .apply(instance, StoredResource::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StoredResource> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, StoredResource::kind,
                    ByteBufCodecs.STRING_UTF8, StoredResource::tank,
                    ByteBufCodecs.STRING_UTF8, StoredResource::id,
                    ByteBufCodecs.VAR_LONG, StoredResource::amount,
                    StoredResource::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, List<StoredResource>> LIST_STREAM_CODEC =
            STREAM_CODEC.apply(ByteBufCodecs.list());

    public StoredResource {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("Stored resource kind cannot be blank");
        }
        if (tank == null || tank.isBlank()) {
            throw new IllegalArgumentException("Stored resource tank cannot be blank");
        }
        if (id == null) {
            id = "";
        }
        if (amount < 0L) {
            throw new IllegalArgumentException("Stored resource amount cannot be negative");
        }
        if (amount == 0L) {
            id = "";
        }
    }
}
