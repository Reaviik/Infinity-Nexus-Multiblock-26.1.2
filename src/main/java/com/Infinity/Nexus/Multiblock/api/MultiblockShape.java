package com.Infinity.Nexus.Multiblock.api;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MultiblockShape {
    public static final MultiblockShape EMPTY = new MultiblockShape(List.of());
    public static final Codec<MultiblockShape> CODEC = Box.CODEC.listOf()
            .xmap(MultiblockShape::new, MultiblockShape::boxes);

    private final List<Box> boxes;
    private final VoxelShape[] shapes;

    public MultiblockShape(List<Box> boxes) {
        this.boxes = List.copyOf(boxes);
        this.shapes = new VoxelShape[Rotation.values().length];
        for (Rotation rotation : Rotation.values()) {
            this.shapes[rotation.ordinal()] = build(rotation);
        }
    }

    public List<Box> boxes() {
        return this.boxes;
    }

    public boolean isEmpty() {
        return this.boxes.isEmpty();
    }

    public VoxelShape voxelShape(Rotation rotation) {
        return this.shapes[rotation.ordinal()];
    }

    private VoxelShape build(Rotation rotation) {
        if (this.boxes.isEmpty()) {
            return Shapes.block();
        }
        VoxelShape shape = Shapes.empty();
        for (Box box : this.boxes) {
            shape = Shapes.or(shape, Shapes.create(rotate(box, rotation)));
        }
        return shape.optimize();
    }

    private static AABB rotate(Box box, Rotation rotation) {
        double minX = box.minX() / 16.0;
        double minY = box.minY() / 16.0;
        double minZ = box.minZ() / 16.0;
        double maxX = box.maxX() / 16.0;
        double maxY = box.maxY() / 16.0;
        double maxZ = box.maxZ() / 16.0;
        return switch (rotation) {
            case NONE -> new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            case CLOCKWISE_90 -> new AABB(1.0 - maxZ, minY, minX, 1.0 - minZ, maxY, maxX);
            case CLOCKWISE_180 -> new AABB(1.0 - maxX, minY, 1.0 - maxZ, 1.0 - minX, maxY, 1.0 - minZ);
            case COUNTERCLOCKWISE_90 -> new AABB(minZ, minY, 1.0 - maxX, maxZ, maxY, 1.0 - minX);
        };
    }

    private static DataResult<Box> toBox(List<Double> values) {
        if (values.size() != 6) {
            return DataResult.error(
                    () -> "A shape box needs 6 values: [min_x, min_y, min_z, max_x, max_y, max_z]");
        }
        Box box = new Box(values.get(0), values.get(1), values.get(2),
                values.get(3), values.get(4), values.get(5));
        if (!Double.isFinite(box.minX()) || !Double.isFinite(box.minY()) || !Double.isFinite(box.minZ())
                || !Double.isFinite(box.maxX()) || !Double.isFinite(box.maxY()) || !Double.isFinite(box.maxZ())
                || box.minX() < 0 || box.minY() < 0 || box.minZ() < 0
                || box.maxX() > 16 || box.maxY() > 16 || box.maxZ() > 16
                || box.minX() > box.maxX() || box.minY() > box.maxY() || box.minZ() > box.maxZ()) {
            return DataResult.error(
                    () -> "Shape boxes must be within 0..16 and min must not be greater than max");
        }
        return DataResult.success(box);
    }

    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public static final Codec<Box> CODEC = Codec.DOUBLE.listOf().flatXmap(
                MultiblockShape::toBox,
                box -> DataResult.success(List.of(
                        box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())));
    }
}
