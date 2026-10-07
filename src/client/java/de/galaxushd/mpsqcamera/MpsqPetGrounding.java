package de.galaxushd.mpsqcamera;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;

/** Shared collision-shape grounding for the locally rendered companion pets. */
final class MpsqPetGrounding {
    private static final double SEARCH_BELOW = 2.0;
    private static final double SEARCH_ABOVE = 1.25;

    private MpsqPetGrounding() { }

    /** Returns the nearest walkable collision surface around the requested Y, or NaN. */
    static double groundY(MinecraftClient client, double x, double referenceY, double z) {
        if (client.world == null || !Double.isFinite(referenceY)) return Double.NaN;
        int blockX = MathHelper.floor(x);
        int blockZ = MathHelper.floor(z);
        int minY = Math.max(client.world.getBottomY(), MathHelper.floor(referenceY - SEARCH_BELOW));
        // Entity positions are already constrained to the dimension's build range.
        // Avoid World#getTopY() here: in Yarn 1.21.8 it requires a heightmap and X/Z.
        int maxY = MathHelper.floor(referenceY + SEARCH_ABOVE);
        double best = Double.NaN;
        double bestDelta = Double.POSITIVE_INFINITY;

        for (int by = minY; by <= maxY; by++) {
            BlockPos pos = new BlockPos(blockX, by, blockZ);
            if (!client.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            VoxelShape shape = client.world.getBlockState(pos).getCollisionShape(client.world, pos);
            for (Box local : shape.getBoundingBoxes()) {
                if (x < blockX + local.minX - 1.0e-5 || x > blockX + local.maxX + 1.0e-5
                        || z < blockZ + local.minZ - 1.0e-5 || z > blockZ + local.maxZ + 1.0e-5) continue;
                double surface = by + local.maxY;
                double delta = Math.abs(surface - referenceY);
                if (delta < bestDelta) {
                    best = surface;
                    bestDelta = delta;
                }
            }
        }
        return best;
    }

    /** Tests the pet's body against actual collision boxes; partial blocks stay walkable. */
    static boolean blocked(MinecraftClient client, double x, double feetY, double z,
                           double radius, double height) {
        if (client.world == null) return true;
        Box body = new Box(x - radius, feetY + 0.015, z - radius,
                x + radius, feetY + height, z + radius);
        int minX = MathHelper.floor(body.minX), maxX = MathHelper.floor(body.maxX);
        int minY = MathHelper.floor(body.minY), maxY = MathHelper.floor(body.maxY);
        int minZ = MathHelper.floor(body.minZ), maxZ = MathHelper.floor(body.maxZ);
        for (int bx = minX; bx <= maxX; bx++) for (int by = minY; by <= maxY; by++)
            for (int bz = minZ; bz <= maxZ; bz++) {
                BlockPos pos = new BlockPos(bx, by, bz);
                if (!client.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return true;
                for (Box local : client.world.getBlockState(pos).getCollisionShape(client.world, pos).getBoundingBoxes()) {
                    Box worldBox = local.offset(bx, by, bz);
                    if (body.intersects(worldBox)) return true;
                }
            }
        return false;
    }
}
