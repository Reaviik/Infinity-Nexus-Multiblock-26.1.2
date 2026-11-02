package com.Infinity.Nexus.Multiblock.api;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

public final class FluidFilter {
    public static final Codec<FluidFilter> CODEC = Codec.STRING.flatXmap(
            FluidFilter::parse,
            filter -> DataResult.error(() -> "Fluid filters are load-only"));

    private final Fluid fluid;
    private final TagKey<Fluid> tag;

    private FluidFilter(Fluid fluid, TagKey<Fluid> tag) {
        this.fluid = fluid;
        this.tag = tag;
    }

    public static FluidFilter fluid(Fluid fluid) {
        return new FluidFilter(Objects.requireNonNull(fluid, "fluid"), null);
    }

    public static FluidFilter tag(TagKey<Fluid> tag) {
        return new FluidFilter(null, Objects.requireNonNull(tag, "tag"));
    }

    public boolean matches(FluidResource resource) {
        return this.fluid != null ? resource.getFluid() == this.fluid : resource.typeHolder().is(this.tag);
    }

    private static DataResult<FluidFilter> parse(String value) {
        if (value.startsWith("#")) {
            return Identifier.read(value.substring(1))
                    .map(id -> FluidFilter.tag(TagKey.create(Registries.FLUID, id)));
        }

        return Identifier.read(value).flatMap(id -> BuiltInRegistries.FLUID.getOptional(id)
                .<DataResult<FluidFilter>>map(fluid -> DataResult.success(FluidFilter.fluid(fluid)))
                .orElseGet(() -> DataResult.error(() -> "Unknown fluid: " + id)));
    }
}
