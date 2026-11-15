package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.phys.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

final class SeagullDropsSmoke {
    static void run(ClientGameTestContext ctx){
        ctx.runOnClient(c->{
            MpsqAccessoryRenderer.cinematicClips(MpsqSeagullDrops.URL);
            var hit=MpsqSeagullDrops.hit(new AABB(0,0,0,1,1,1),new Vec3(1.4,10,.5),new Vec3(1.4,-10,.5),.5,.125);
            if(hit==null||Math.abs(hit.y-1)>.001)throw new AssertionError("Swept hitbox missed edge/high-speed hit");
            if(MpsqSeagullDrops.hit(new AABB(0,0,0,1,1,1),new Vec3(2,10,.5),new Vec3(2,-10,.5),.5,.125)!=null)throw new AssertionError("False hit");
            var block=BlockPos.containing(c.player.getX()+5,c.player.getY()+2,c.player.getZ());
            var old=c.level.getBlockState(block);c.level.setBlock(block,Blocks.STONE.defaultBlockState(),3);
            var d=new MpsqSeagullDrops.Drop(new Vec3(block.getX()+.5,block.getY()+5,block.getZ()+.5),1,0);
            for(int i=0;i<100&&!d.impacted;i++)MpsqSeagullDrops.step(d);
            if(!d.impacted||d.entityHit||Math.abs(d.position.y-block.getY()-1)>.001||!d.clip().equals("plop"))throw new AssertionError("Block impact");
            for(int i=0;i<4;i++)MpsqSeagullDrops.step(d);
            if(!d.clip().equals("idle_landed")||d.expired())throw new AssertionError("Landed transition");
            for(int i=0;i<198;i++)MpsqSeagullDrops.step(d);
            if(d.expired())throw new AssertionError("Puddle removed before ten seconds");
            for(int i=0;i<2;i++)MpsqSeagullDrops.step(d);
            if(!d.expired())throw new AssertionError("Puddle lifetime");
            c.level.setBlock(block,old,3);
            var playerDrop=new MpsqSeagullDrops.Drop(c.player.position().add(0,4,0),1,0);
            for(int i=0;i<100&&!playerDrop.impacted;i++)MpsqSeagullDrops.step(playerDrop);
            if(!playerDrop.entityHit||!playerDrop.clip().equals("plop"))throw new AssertionError("Player impact");
            for(int i=0;i<4;i++)MpsqSeagullDrops.step(playerDrop);
            if(!playerDrop.expired())throw new AssertionError("Entity impact left a puddle");
            MpsqSeagullDrops.reset();
            var n=new MpsqCinematicScene.Npc();n.category="npc_model";n.type="nm_seagull";
            var action=new MpsqCinematicScene.NpcAction();action.clip=MpsqSeagullDrops.ACTION;action.time=0;n.actions.add(action);
            var pose=new MpsqCinematicActors.Pose(c.player.getX()+2,c.player.getY()+3,c.player.getZ()-3,0,0,0,"walk",0);
            MpsqSeagullDrops.timeline("play:test",n,pose,1,.025);
            for(int i=0;i<100;i++)MpsqSeagullDrops.timeline("play:test",n,pose,1,.025);
            try{var field=MpsqSeagullDrops.class.getDeclaredField("drops");field.setAccessible(true);if(((java.util.List<?>)field.get(null)).size()!=1)throw new AssertionError("Duplicate or missing timeline drop");}catch(ReflectiveOperationException e){throw new AssertionError(e);}
            c.gui.setScreen(null);c.player.setYRot(-146.31f);c.player.setXRot(30);
            System.out.println("SEAGULL_COLLISION_TIMELINE_LIFETIME_PASS");
        });
        ctx.waitTicks(30);
        ctx.runOnClient(c->{if(!MpsqAccessoryRenderer.cinematicClips(MpsqSeagullDrops.URL).containsAll(java.util.List.of("idle","plop","idle_landed")))throw new AssertionError("Drop model did not load");});
        ctx.takeScreenshot("seagull-drop-landed");
        ctx.runOnClient(c->MpsqSeagullDrops.reset());
    }
}
