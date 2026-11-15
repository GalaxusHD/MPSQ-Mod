package de.galaxushd.mpsqcamera;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** Collision-shape grounding for freely roaming Mini-Me companions. */
final class MpsqMiniMeGrounding {
    private MpsqMiniMeGrounding() { }

    static double surfaceY(Minecraft client, double x, double referenceY, double z) {
        if (client.level == null || !Double.isFinite(referenceY)) return Double.NaN;
        int bx = Mth.floor(x), bz = Mth.floor(z);
        int minY = Math.max(client.level.getMinY(), Mth.floor(referenceY - 3.0));
        // Entity positions are already constrained to the dimension's build range.
        // Avoid World#getTopY() here: in Yarn 1.21.8 it requires a heightmap and X/Z.
        int maxY = Mth.floor(referenceY + 1.25);
        double result = Double.NaN, nearest = Double.POSITIVE_INFINITY;
        for (int by = minY; by <= maxY; by++) {
            if (!client.level.hasChunk(bx >> 4, bz >> 4)) continue;
            BlockPos pos = new BlockPos(bx, by, bz);
            for (AABB box : client.level.getBlockState(pos).getCollisionShape(client.level, pos).toAabbs()) {
                if (x < bx + box.minX - 1.0e-5 || x > bx + box.maxX + 1.0e-5
                        || z < bz + box.minZ - 1.0e-5 || z > bz + box.maxZ + 1.0e-5) continue;
                double candidate = by + box.maxY;
                double delta = Math.abs(candidate - referenceY);
                if (delta < nearest) { result = candidate; nearest = delta; }
            }
        }
        return result;
    }

    static boolean blocked(Minecraft client, double x, double feetY, double z) {
        if (client.level == null) return true;
        AABB body = new AABB(x - 0.27, feetY + 0.02, z - 0.27, x + 0.27, feetY + 1.35, z + 0.27);
        for (int bx = Mth.floor(body.minX); bx <= Mth.floor(body.maxX); bx++)
            for (int by = Mth.floor(body.minY); by <= Mth.floor(body.maxY); by++)
                for (int bz = Mth.floor(body.minZ); bz <= Mth.floor(body.maxZ); bz++) {
                    if (!client.level.hasChunk(bx >> 4, bz >> 4)) return true;
                    BlockPos pos = new BlockPos(bx, by, bz);
                    for (AABB shape : client.level.getBlockState(pos).getCollisionShape(client.level, pos).toAabbs())
                        if (body.intersects(shape.move(bx, by, bz))) return true;
                }
        return false;
    }
}
