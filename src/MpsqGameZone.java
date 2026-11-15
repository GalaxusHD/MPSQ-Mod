package de.galaxushd.mpsqcamera;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/** Reusable axis-aligned game zone defined by two opposite block corners. */
public record MpsqGameZone(BlockPos first, BlockPos second) {
    public MpsqGameZone {
        first = first == null ? BlockPos.ZERO : first.immutable();
        second = second == null ? first : second.immutable();
    }

    public boolean contains(Entity entity) {
        if (entity == null) return false;
        var box = entity.getBoundingBox();
        return box.minX >= minX() && box.maxX <= maxX()
                && box.minY >= minY() && box.maxY <= maxY()
                && box.minZ >= minZ() && box.maxZ <= maxZ();
    }

    public double minX() { return Math.min(first.getX(), second.getX()); }
    public boolean containsFeet(Entity entity){
        return entity!=null&&containsFeet(entity.getX(),entity.getY(),entity.getZ());
    }
    public boolean containsFeet(double x,double y,double z){
        return x>=minX()&&x<maxX()&&y>=minY()&&y<maxY()&&z>=minZ()&&z<maxZ();
    }
    public double minY() { return Math.min(first.getY(), second.getY()); }
    public double minZ() { return Math.min(first.getZ(), second.getZ()); }
    public double maxX() { return Math.max(first.getX(), second.getX()) + 1.0; }
    public double maxY() { return Math.max(first.getY(), second.getY()) + 1.0; }
    public double maxZ() { return Math.max(first.getZ(), second.getZ()) + 1.0; }
}
