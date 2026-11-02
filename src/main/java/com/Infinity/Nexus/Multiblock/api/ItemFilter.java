package com.Infinity.Nexus.Multiblock.api;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ItemFilter {
    public static final Codec<ItemFilter> CODEC = Codec.STRING.comapFlatMap(
            ItemFilter::parse,
            ItemFilter::serialize);

    private final Item item;
    private final TagKey<Item> tag;

    private ItemFilter(Item item, TagKey<Item> tag) {
        this.item = item;
        this.tag = tag;
    }

    public static ItemFilter item(Item item) {
        return new ItemFilter(Objects.requireNonNull(item, "item"), null);
    }

    public static ItemFilter tag(TagKey<Item> tag) {
        return new ItemFilter(null, Objects.requireNonNull(tag, "tag"));
    }

    public boolean matches(ItemStack stack) {
        return this.item != null ? stack.getItem() == this.item : stack.is(this.tag);
    }

    private String serialize() {
        return this.item != null
                ? BuiltInRegistries.ITEM.getKey(this.item).toString()
                : "#" + this.tag.location();
    }

    private static DataResult<ItemFilter> parse(String value) {
        if (value.startsWith("#")) {
            return Identifier.read(value.substring(1))
                    .map(id -> ItemFilter.tag(TagKey.create(Registries.ITEM, id)));
        }

        return Identifier.read(value).flatMap(id -> BuiltInRegistries.ITEM.getOptional(id)
                .<DataResult<ItemFilter>>map(item -> DataResult.success(ItemFilter.item(item)))
                .orElseGet(() -> DataResult.error(() -> "Unknown item: " + id)));
    }
}
