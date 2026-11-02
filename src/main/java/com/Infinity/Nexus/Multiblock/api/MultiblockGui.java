package com.Infinity.Nexus.Multiblock.api;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public record MultiblockGui(
        @Nullable Identifier texture,
        int width,
        int height,
        int textureWidth,
        int textureHeight,
        List<GuiElement> elements) {

    public static final Codec<MultiblockGui> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("texture").forGetter(gui -> Optional.ofNullable(gui.texture())),
            Codec.intRange(1, 4096).optionalFieldOf("width", 176).forGetter(MultiblockGui::width),
            Codec.intRange(1, 4096).optionalFieldOf("height", 166).forGetter(MultiblockGui::height),
            Codec.intRange(1, 4096).optionalFieldOf("texture_width", 256).forGetter(MultiblockGui::textureWidth),
            Codec.intRange(1, 4096).optionalFieldOf("texture_height", 256).forGetter(MultiblockGui::textureHeight),
            GuiElement.CODEC.listOf().optionalFieldOf("elements", List.of()).forGetter(MultiblockGui::elements))
            .apply(instance, (texture, width, height, textureWidth, textureHeight, elements) ->
                    new MultiblockGui(texture.orElse(null), width, height, textureWidth, textureHeight, elements)));

    public MultiblockGui {
        elements = List.copyOf(elements);
    }
}
