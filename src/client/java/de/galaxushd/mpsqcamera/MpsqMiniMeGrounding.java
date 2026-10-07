package de.galaxushd.mpsqcamera;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

/** Collision-shape grounding for freely roaming Mini-Me companions. */
final class MpsqMiniMeGrounding {
    private MpsqMiniMeGrounding() { }

    static double surfaceY(MinecraftClient client, double x, double referenceY, double z) {
        if (client.world == null || !Double.isFinite(referenceY)) return Double.NaN;
        int bx = MathHelper.floor(x), bz = MathHelper.floor(z);
        int minY = Math.max(client.world.getBottomY(), MathHelper.floor(referenceY - 3.0));
        // Entity positions are already constrained to the dimension's build range.
        // Avoid World#getTopY() here: in Yarn 1.21.8 it requires a heightmap and X/Z.
        int maxY = MathHelper.floor(referenceY + 1.25);
        double result = Double.NaN, nearest = Double.POSITIVE_INFINITY;
        for (int by = minY; by <= maxY; by++) {
            if (!client.world.isChunkLoaded(bx >> 4, bz >> 4)) continue;
            BlockPos pos = new BlockPos(bx, by, bz);
            for (Box box : client.world.getBlockState(pos).getCollisionShape(client.world, pos).getBoundingBoxes()) {
                if (x < bx + box.minX - 1.0e-5 || x > bx + box.maxX + 1.0e-5
                        || z < bz + box.minZ - 1.0e-5 || z > bz + box.maxZ + 1.0e-5) continue;
                double candidate = by + box.maxY;
                double delta = Math.abs(candidate - referenceY);
                if (delta < nearest) { result = candidate; nearest = delta; }
            }
        }
        return result;
    }

    static boolean blocked(MinecraftClient client, double x, double feetY, double z) {
        if (client.world == null) return true;
        Box body = new Box(x - 0.27, feetY + 0.02, z - 0.27, x + 0.27, feetY + 1.35, z + 0.27);
        for (int bx = MathHelper.floor(body.minX); bx <= MathHelper.floor(body.maxX); bx++)
            for (int by = MathHelper.floor(body.minY); by <= MathHelper.floor(body.maxY); by++)
                for (int bz = MathHelper.floor(body.minZ); bz <= MathHelper.floor(body.maxZ); bz++) {
                    if (!client.world.isChunkLoaded(bx >> 4, bz >> 4)) return true;
                    BlockPos pos = new BlockPos(bx, by, bz);
                    for (Box shape : client.world.getBlockState(pos).getCollisionShape(client.world, pos).getBoundingBoxes())
                        if (body.intersects(shape.offset(bx, by, bz))) return true;
                }
        return false;
    }
}
