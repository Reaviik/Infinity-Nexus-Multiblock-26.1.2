package com.Infinity.Nexus.Multiblock.network;

import java.util.LinkedHashMap;
import java.util.Map;

import com.Infinity.Nexus.Multiblock.InfinityNexusMultiblock;
import com.Infinity.Nexus.Multiblock.data.MultiblockDefinitionLoader;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MultiblockDefinitionSyncPayload(Map<Identifier, String> definitions) implements CustomPacketPayload {
    public static final Type<MultiblockDefinitionSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(InfinityNexusMultiblock.MOD_ID, "multiblock_definitions"));
    private static final int MAX_JSON_LENGTH = 2_097_152;

    public static final StreamCodec<RegistryFriendlyByteBuf, MultiblockDefinitionSyncPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public MultiblockDefinitionSyncPayload decode(RegistryFriendlyByteBuf buffer) {
                    int size = buffer.readVarInt();
                    Map<Identifier, String> definitions = new LinkedHashMap<>();
                    for (int i = 0; i < size; i++) {
                        definitions.put(buffer.readIdentifier(), buffer.readUtf(MAX_JSON_LENGTH));
                    }
                    return new MultiblockDefinitionSyncPayload(definitions);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MultiblockDefinitionSyncPayload payload) {
                    buffer.writeVarInt(payload.definitions().size());
                    payload.definitions().forEach((id, json) -> {
                        buffer.writeIdentifier(id);
                        buffer.writeUtf(json, MAX_JSON_LENGTH);
                    });
                }
            };

    @Override
    public Type<MultiblockDefinitionSyncPayload> type() {
        return TYPE;
    }

    public static MultiblockDefinitionSyncPayload create() {
        Map<Identifier, String> definitions = new LinkedHashMap<>();
        MultiblockDefinitionLoader.resolvedFiles().forEach((id, json) -> definitions.put(id, json.toString()));
        return new MultiblockDefinitionSyncPayload(definitions);
    }

    public static void handle(MultiblockDefinitionSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Map<Identifier, JsonObject> files = new LinkedHashMap<>();
            payload.definitions().forEach((id, json) -> {
                try {
                    JsonElement element = JsonParser.parseString(json);
                    if (element.isJsonObject()) {
                        files.put(id, element.getAsJsonObject());
                    }
                } catch (RuntimeException exception) {
                    InfinityNexusMultiblock.LOGGER.error("Failed to parse synced multiblock definition {}: {}", id, exception.getMessage());
                }
            });
            MultiblockDefinitionLoader.applyResolved(files);
        });
    }
}
