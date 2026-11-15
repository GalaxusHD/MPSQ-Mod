package de.galaxushd.mpsqcamera;
import net.minecraft.world.entity.Entity;
/** Shared custom palette and per-viewer dismissal for every NPC renderer. */
public final class MpsqNpcGlow {
 private static final String COLOR_TAG="mpsq_glow:";
 static String key(String id){return id!=null&&id.startsWith("npc:")?id.substring(4):id;}
 static int color(String task,String type){return switch(task){case "quest"->0xFF2149C4;case "accessories"->0xFF8027B0;case "tutorial"->0xFFC3971F;default->MpsqAccessoryRenderer.WUMPUS_ASSET_ID.equals(type)?0xFF7582E3:0xFFEC2F53;};}
 static int color(MpsqCinematicScene.Npc n){return color(n.scenesTask,n.type);}
 static String lockedTag(MpsqCinematicScene.Npc n){return switch(n.scenesTask){case "quest"->"quests";case "accessories"->"accessories";case "tutorial"->"wiki";default->MpsqAccessoryRenderer.WUMPUS_ASSET_ID.equals(n.type)?"discord":"";};}
 static String tag(MpsqCinematicScene.Npc n){String locked=lockedTag(n);return locked.isBlank()?n.rankTag:locked;}
 static boolean enabled(String id,MpsqCinematicScene.Npc n){return n.glow&&!MpsqNpcVisitStore.isGlowDisabled(key(id));}
 static void clicked(String id){MpsqNpcVisitStore.setGlowEnabled(key(id),false,true);}
 static void rearm(String id){MpsqNpcVisitStore.setGlowEnabled(key(id),true,true);}
 static void apply(Entity entity,String id,MpsqCinematicScene.Npc n){String tag=COLOR_TAG+Integer.toHexString(color(n));if(!entity.entityTags().contains(tag)){for(String old:java.util.List.copyOf(entity.entityTags()))if(old.startsWith(COLOR_TAG))entity.removeTag(old);entity.addTag(tag);}boolean on=enabled(id,n);entity.setGlowingTag(on);if(on)entity.addTag("mpsq_npc_glow_on");else entity.removeTag("mpsq_npc_glow_on");}
 public static boolean isNpc(Entity e){return e!=null&&e.entityTags().contains("mpsq_cinematic_actor");}
 public static boolean visibleGlow(Entity e){return e.entityTags().contains("mpsq_npc_glow_on")&&TeamVisibilitySettings.inArea()&&clear(e.getBoundingBox());}
 static boolean clear(net.minecraft.world.phys.AABB box){
  var c=net.minecraft.client.Minecraft.getInstance();if(c.level==null||c.player==null)return false;
  var eye=c.gameRenderer.mainCamera().position();
  for(double x:new double[]{box.minX+.01,box.maxX-.01})for(double y:new double[]{box.minY+.08,box.maxY-.02})for(double z:new double[]{box.minZ+.01,box.maxZ-.01}){
   var hit=c.level.clip(new net.minecraft.world.level.ClipContext(eye,new net.minecraft.world.phys.Vec3(x,y,z),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,c.player));
   if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)return false;
  }return true;
 }
 static boolean clear(double x,double y,double z,float scale){return clear(new net.minecraft.world.phys.AABB(x-.5*scale,y,z-.5*scale,x+.5*scale,y+2*scale,z+.5*scale));}
 public static Integer entityColor(Entity entity){for(String tag:entity.entityTags())if(tag.startsWith(COLOR_TAG))try{return (int)Long.parseLong(tag.substring(COLOR_TAG.length()),16);}catch(NumberFormatException ignored){}return null;}
}
