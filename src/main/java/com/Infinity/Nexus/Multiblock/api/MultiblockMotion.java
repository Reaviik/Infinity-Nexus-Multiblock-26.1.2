package com.Infinity.Nexus.Multiblock.api;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Uma parte movel de um multibloco. As {@code keys} apontam para simbolos do
 * pattern que saem do lugar e viram uma entidade enquanto uma receita valida
 * estiver rodando no controller.
 *
 * <p>O JSON fica dentro de {@code motions} na definicao do multibloco:</p>
 *
 * <pre>{@code
 * "motions": [
 *   {
 *     "id": "arm",
 *     "keys": ["A", "B"],
 *     "preserve_nbt": false,
 *     "damage_entities": true,
 *     "damage": 4.0,
 *     "damage_interval": 10,
 *     "damage_source": "minecraft:generic",
 *     "animation": {
 *       "loop": true,
 *       "duration": 100,
 *       "pivot": [1.5, 0.5, 1.5],
 *       "easing": "linear",
 *       "fill_speed": false,
 *       "keyframes": [
 *         { "time": 0,   "offset": [0, 0, 0], "rotation": [0, 0, 0] },
 *         { "time": 50,  "offset": [0, 2, 0], "rotation": [0, 45, 0] },
 *         { "time": 100, "offset": [0, 0, 0], "rotation": [0, 0, 0] }
 *       ]
 *     }
 *   }
 * ]
 * }</pre>
 *
 * <p>{@code offset} e' em blocos, {@code rotation} em graus nos eixos X/Y/Z e
 * {@code pivot} em blocos a partir do canto minimo da parte (0,0,0). O pivot
 * padrao e' o centro da caixa delimitadora.</p>
 */
public record MultiblockMotion(
        String id,
        List<Character> keys,
        boolean preserveNbt,
        boolean damageEntities,
        float damage,
        int damageInterval,
        String damageSource,
        Animation animation) {

    public static final MultiblockMotion NONE = new MultiblockMotion(
            "", List.of(), false, false, 0.0F, 10, "minecraft:generic", Animation.NONE);

    public static final Codec<Character> SYMBOL_CODEC = Codec.STRING.flatXmap(
            value -> value.length() == 1
                    ? DataResult.success(value.charAt(0))
                    : DataResult.error(() -> "Expected a single character, got '" + value + "'"),
            value -> DataResult.success(String.valueOf(value)));

    public enum Easing {
        LINEAR,
        STEP,
        EASE_IN,
        EASE_OUT,
        EASE_IN_OUT;

        public static final Codec<Easing> CODEC = Codec.STRING.comapFlatMap(
                value -> {
                    for (Easing easing : values()) {
                        if (easing.name().toLowerCase(Locale.ROOT).equals(value)) {
                            return DataResult.success(easing);
                        }
                    }
                    return DataResult.error(() -> "Unknown easing '" + value + "'");
                },
                easing -> easing.name().toLowerCase(Locale.ROOT));

        public float apply(float value) {
            float t = Mth.clamp(value, 0.0F, 1.0F);
            return switch (this) {
                case LINEAR -> t;
                case STEP -> t < 1.0F ? 0.0F : 1.0F;
                case EASE_IN -> t * t;
                case EASE_OUT -> 1.0F - (1.0F - t) * (1.0F - t);
                case EASE_IN_OUT -> t < 0.5F
                        ? 2.0F * t * t
                        : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 2.0F) / 2.0F;
            };
        }
    }

    public record Keyframe(int time, Vec3 offset, Vec3 rotation, Optional<Easing> easing) {
        public static final Codec<Keyframe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("time").forGetter(Keyframe::time),
                Vec3.CODEC.optionalFieldOf("offset", Vec3.ZERO).forGetter(Keyframe::offset),
                Vec3.CODEC.optionalFieldOf("rotation", Vec3.ZERO).forGetter(Keyframe::rotation),
                Easing.CODEC.optionalFieldOf("easing").forGetter(Keyframe::easing))
                .apply(instance, Keyframe::new));

        public Keyframe {
            offset = offset == null ? Vec3.ZERO : offset;
            rotation = rotation == null ? Vec3.ZERO : rotation;
            easing = easing == null ? Optional.empty() : easing;
        }
    }

    public record Animation(
            boolean loop,
            int duration,
            List<Keyframe> keyframes,
            Easing easing,
            Optional<Vec3> pivot,
            boolean fillSpeed) {
        public static final Animation NONE = new Animation(
                false, 0, List.of(), Easing.LINEAR, Optional.empty(), false);

        public static final Codec<Animation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("loop", true).forGetter(Animation::loop),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("duration", 0).forGetter(Animation::duration),
                Keyframe.CODEC.listOf().optionalFieldOf("keyframes", List.of()).forGetter(Animation::keyframes),
                Easing.CODEC.optionalFieldOf("easing", Easing.LINEAR).forGetter(Animation::easing),
                Vec3.CODEC.optionalFieldOf("pivot").forGetter(Animation::pivot),
                Codec.BOOL.optionalFieldOf("fill_speed", false).forGetter(Animation::fillSpeed))
                .apply(instance, Animation::new));

        public Animation {
            keyframes = keyframes == null ? List.of() : List.copyOf(keyframes);
            easing = easing == null ? Easing.LINEAR : easing;
            pivot = pivot == null ? Optional.empty() : pivot;
        }

        /**
         * Multiplicador de velocidade da animacao para um nivel de
         * preenchimento de 0 a 1. Sem {@code fill_speed}, fica sempre em 1x.
         */
        public double speedFactor(double fill) {
            return this.fillSpeed ? 1.0D + Mth.clamp(fill, 0.0D, 1.0D) : 1.0D;
        }
    }

    public static final Codec<MultiblockMotion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(MultiblockMotion::id),
            SYMBOL_CODEC.listOf().fieldOf("keys").forGetter(MultiblockMotion::keys),
            Codec.BOOL.optionalFieldOf("preserve_nbt", false).forGetter(MultiblockMotion::preserveNbt),
            Codec.BOOL.optionalFieldOf("damage_entities", false).forGetter(MultiblockMotion::damageEntities),
            Codec.FLOAT.optionalFieldOf("damage", 4.0F).forGetter(MultiblockMotion::damage),
            Codec.intRange(1, Integer.MAX_VALUE)
                    .optionalFieldOf("damage_interval", 10).forGetter(MultiblockMotion::damageInterval),
            Codec.STRING.optionalFieldOf("damage_source", "minecraft:generic")
                    .forGetter(MultiblockMotion::damageSource),
            Animation.CODEC.optionalFieldOf("animation", Animation.NONE).forGetter(MultiblockMotion::animation))
            .apply(instance, MultiblockMotion::new));

    public MultiblockMotion {
        id = id == null ? "" : id;
        keys = keys == null ? List.of() : List.copyOf(keys);
        damageSource = damageSource == null || damageSource.isBlank()
                ? "minecraft:generic"
                : damageSource;
        animation = animation == null ? Animation.NONE : animation;
    }

    public MultiblockMotion(
            String id,
            List<Character> keys,
            boolean preserveNbt,
            boolean damageEntities,
            float damage,
            int damageInterval,
            Animation animation) {
        this(id, keys, preserveNbt, damageEntities, damage, damageInterval,
                "minecraft:generic", animation);
    }

    public boolean hasKey(char symbol) {
        return this.keys.contains(symbol);
    }

    /**
     * Amostra a animacao em {@code elapsedTicks}. O pivot devolvido fica no
     * sistema local do renderer (canto minimo em 0,0,0).
     */
    public Transform sample(long elapsedTicks, int sizeX, int sizeY, int sizeZ) {
        Vec3 pivot = this.animation.pivot()
                .orElseGet(() -> new Vec3(sizeX / 2.0D, sizeY / 2.0D, sizeZ / 2.0D));
        List<Keyframe> frames = this.animation.keyframes();
        int duration = this.animation.duration();
        if (duration <= 0 || frames.isEmpty()) {
            return new Transform(Vec3.ZERO, Vec3.ZERO, pivot);
        }

        long elapsed = elapsedTicks;
        if (this.animation.loop()) {
            elapsed = ((elapsed % duration) + duration) % duration;
        } else {
            elapsed = Math.min(elapsed, duration);
        }

        Keyframe previous = frames.getFirst();
        Keyframe next = null;
        for (int index = 1; index < frames.size(); index++) {
            Keyframe frame = frames.get(index);
            if (elapsed < frame.time()) {
                next = frame;
                break;
            }
            previous = frame;
        }
        if (next == null) {
            return new Transform(previous.offset(), previous.rotation(), pivot);
        }

        int span = next.time() - previous.time();
        float t = span <= 0 ? 1.0F : (float) (elapsed - previous.time()) / span;
        Easing easing = next.easing().orElse(this.animation.easing());
        float eased = easing.apply(t);
        return new Transform(
                lerp(previous.offset(), next.offset(), eased),
                lerp(previous.rotation(), next.rotation(), eased),
                pivot);
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, float delta) {
        return new Vec3(
                from.x() + (to.x() - from.x()) * delta,
                from.y() + (to.y() - from.y()) * delta,
                from.z() + (to.z() - from.z()) * delta);
    }

    public record Transform(Vec3 offset, Vec3 rotation, Vec3 pivot) {
    }
}
