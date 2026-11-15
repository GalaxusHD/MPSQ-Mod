package de.galaxushd.mpsqcamera;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shared collision-shape grounding for the locally rendered companion pets. */
final class MpsqPetGrounding {
    private static final double SEARCH_BELOW = 2.0;
    private static final double SEARCH_ABOVE = 1.25;

    private MpsqPetGrounding() { }
    static double viewDistanceSquared(){double blocks=Minecraft.getInstance().options.simulationDistance().get()*16.0;return blocks*blocks;}

    /** Search a supported, fully clear landing within nine blocks of the owner. */
    static MpsqPetPath.Point safeSpawn(Minecraft client,double ox,double oy,double oz,double radius,double height){
        return safeSpawn(client,ox,oy,oz,radius,height,false);
    }
    static MpsqPetPath.Point safeSpawn(Minecraft client,double ox,double oy,double oz,double radius,double height,boolean random){
        if(client.level==null)return null;
        int startRing=random?java.util.concurrent.ThreadLocalRandom.current().nextInt(9):0,startDirection=java.util.concurrent.ThreadLocalRandom.current().nextInt(16);
        for(int offset=0;offset<9;offset++)for(int direction=0;direction<16;direction++){
            int ring=1+(startRing+offset)%9;
            double angle=(direction+startDirection)*Math.PI/8,x=ox+Math.cos(angle)*ring,z=oz+Math.sin(angle)*ring;
            java.util.List<AABB> boxes=new java.util.ArrayList<>();boolean loaded=true;
            for(int bx=Mth.floor(x-radius);bx<=Mth.floor(x+radius);bx++)for(int bz=Mth.floor(z-radius);bz<=Mth.floor(z+radius);bz++){
                if(!client.level.hasChunk(bx>>4,bz>>4)){loaded=false;continue;}
                for(int by=Math.max(client.level.getMinY(),Mth.floor(oy-4));by<=Mth.floor(oy+4+height);by++){
                    var pos=new BlockPos(bx,by,bz);for(var box:client.level.getBlockState(pos).getCollisionShape(client.level,pos).toAabbs())boxes.add(box.move(bx,by,bz));
                }
            }
            if(!loaded)continue;double y=spawnY(x,oy,z,radius,height,boxes);
            if(!Double.isFinite(y))continue;
            if(!client.level.getFluidState(BlockPos.containing(x,y+.02,z)).isEmpty())continue;
            return new MpsqPetPath.Point(x,y+.01,z);
        }
        return null;
    }

    static double spawnY(double x,double referenceY,double z,double radius,double height,java.util.List<AABB> boxes){
        java.util.TreeSet<Double> surfaces=new java.util.TreeSet<>(java.util.Comparator.<Double>comparingDouble(y->Math.abs(y-referenceY)).thenComparing(java.util.Comparator.reverseOrder()));
        for(var box:boxes)if(x+radius>box.minX&&x-radius<box.maxX&&z+radius>box.minZ&&z-radius<box.maxZ&&Math.abs(box.maxY-referenceY)<=4)surfaces.add(box.maxY);
        for(double y:surfaces){
            AABB body=new AABB(x-radius,y+.001,z-radius,x+radius,y+height,z+radius);
            boolean supported=false,clear=true;
            for(var box:boxes){if(body.intersects(box)){clear=false;break;}if(Math.abs(box.maxY-y)<.0001&&x>=box.minX&&x<=box.maxX&&z>=box.minZ&&z<=box.maxZ)supported=true;}
            if(clear&&supported)return y;
        }
        return Double.NaN;
    }

    /** Returns the nearest walkable collision surface around the requested Y, or NaN. */
    static double groundY(Minecraft client, double x, double referenceY, double z) {
        if (client.level == null || !Double.isFinite(referenceY)) return Double.NaN;
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int minY = Math.max(client.level.getMinY(), Mth.floor(referenceY - SEARCH_BELOW));
        // Entity positions are already constrained to the dimension's build range.
        // Avoid World#getTopY() here: in Yarn 1.21.8 it requires a heightmap and X/Z.
        int maxY = Mth.floor(referenceY + SEARCH_ABOVE);
        double best = Double.NaN;
        double bestDelta = Double.POSITIVE_INFINITY;

        for (int by = minY; by <= maxY; by++) {
            BlockPos pos = new BlockPos(blockX, by, blockZ);
            if (!client.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            VoxelShape shape = client.level.getBlockState(pos).getCollisionShape(client.level, pos);
            for (AABB local : shape.toAabbs()) {
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

    /** Resolve one-block steps using the footprint, rather than waiting for the centre to hit a wall. */
    static double stepY(Minecraft client, double x, double y, double z, double nx, double nz,
                        double radius, double height) {
        java.util.List<AABB> boxes = new java.util.ArrayList<>();
        for (int bx=Mth.floor(Math.min(x,nx)-radius); bx<=Mth.floor(Math.max(x,nx)+radius); bx++)
            for (int bz=Mth.floor(Math.min(z,nz)-radius); bz<=Mth.floor(Math.max(z,nz)+radius); bz++)
                for (int by=Mth.floor(y-1.1)-1; by<=Mth.floor(y+1+height); by++) {
                    BlockPos pos=new BlockPos(bx,by,bz);
                    if (!client.level.hasChunk(bx>>4,bz>>4)) return Double.NaN;
                    for (AABB local:client.level.getBlockState(pos).getCollisionShape(client.level,pos).toAabbs())
                        boxes.add(local.move(bx,by,bz));
                }
        return stepY(x,y,z,nx,nz,radius,height,boxes);
    }

    static double stepY(double x, double y, double z, double nx, double nz,
                        double radius, double height, java.util.List<AABB> boxes) {
        java.util.TreeSet<Double> heights=new java.util.TreeSet<>();
        for (AABB box:boxes) {
            if (nx+radius>box.minX && nx-radius<box.maxX && nz+radius>box.minZ && nz-radius<box.maxZ
                    && box.maxY-y<=1.0001 && y-box.maxY<=1.1) heights.add(box.maxY);
        }
        for (double top:heights.descendingSet()) {
            AABB destination=new AABB(nx-radius,top+.001,nz-radius,nx+radius,top+height,nz+radius);
            AABB lift=new AABB(x-radius,y+.001,z-radius,x+radius,Math.max(y,top)+height,z+radius);
            AABB sweep=new AABB(Math.min(x,nx)-radius,Math.max(y,top)+.001,Math.min(z,nz)-radius,Math.max(x,nx)+radius,Math.max(y,top)+height,Math.max(z,nz)+radius);
            boolean blocked=false;
            for (AABB box:boxes) if (destination.intersects(box) || sweep.intersects(box) || (top>y && lift.intersects(box))) {
                blocked=true; break;
            }
            if (!blocked) return top;
        }
        return Double.NaN;
    }

    /** Tests the pet's body against actual collision boxes; partial blocks stay walkable. */
    static boolean blocked(Minecraft client, double x, double feetY, double z,
                           double radius, double height) {
        if (client.level == null) return true;
        AABB body = new AABB(x - radius, feetY + 0.015, z - radius,
                x + radius, feetY + height, z + radius);
        int minX = Mth.floor(body.minX), maxX = Mth.floor(body.maxX);
        int minY = Mth.floor(body.minY), maxY = Mth.floor(body.maxY);
        int minZ = Mth.floor(body.minZ), maxZ = Mth.floor(body.maxZ);
        for (int bx = minX; bx <= maxX; bx++) for (int by = minY; by <= maxY; by++)
            for (int bz = minZ; bz <= maxZ; bz++) {
                BlockPos pos = new BlockPos(bx, by, bz);
                if (!client.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return true;
                for (AABB local : client.level.getBlockState(pos).getCollisionShape(client.level, pos).toAabbs()) {
                    AABB worldBox = local.move(bx, by, bz);
                    if (body.intersects(worldBox)) return true;
                }
            }
        return false;
    }
}
