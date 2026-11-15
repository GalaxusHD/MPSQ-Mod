package de.galaxushd.mpsqcamera;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
final class MpsqMobIcons {
 static boolean drawSpawnEgg(GuiGraphicsExtractor g,String id,int x,int y,int size){
  String name=switch(id){case "minecraft:illusioner"->"illusioner";case "minecraft:giant"->"giant";default->null;};
  Identifier texture=name==null?Identifier.fromNamespaceAndPath("minecraft","textures/item/"+id.substring(id.indexOf(':' )+1)+"_spawn_egg.png"):Identifier.fromNamespaceAndPath("mpsqcamera","textures/gui/spawn_eggs/"+name+".png");
  int inset=Math.max(1,size/18);return MpsqCenteredIcon.draw(g,texture,x+inset,y+inset,size-2*inset);
 }
 record Face(String texture,int u,int v,int w,int h,int tw,int th){}
 private static final Map<String,Face> FACES=Map.ofEntries(Map.entry("minecraft:zombie",new Face("textures/entity/zombie/zombie.png",8,8,8,8,64,64)),
Map.entry("minecraft:husk",new Face("textures/entity/zombie/husk.png",8,8,8,8,64,64)),
Map.entry("minecraft:drowned",new Face("textures/entity/zombie/drowned.png",8,8,8,8,64,64)),
Map.entry("minecraft:skeleton",new Face("textures/entity/skeleton/skeleton.png",8,8,8,8,64,32)),
Map.entry("minecraft:stray",new Face("textures/entity/skeleton/stray.png",8,8,8,8,64,32)),
Map.entry("minecraft:bogged",new Face("textures/entity/skeleton/bogged.png",8,8,8,8,64,32)),
Map.entry("minecraft:wither_skeleton",new Face("textures/entity/skeleton/wither_skeleton.png",8,8,8,8,64,32)),
Map.entry("minecraft:creeper",new Face("textures/entity/creeper/creeper.png",8,8,8,8,64,32)),
Map.entry("minecraft:enderman",new Face("textures/entity/enderman/enderman.png",8,8,8,8,64,32)),
Map.entry("minecraft:blaze",new Face("textures/entity/blaze/blaze.png",8,8,8,8,64,32)),
Map.entry("minecraft:allay",new Face("textures/entity/allay/allay.png",0,0,5,5,32,32)),
Map.entry("minecraft:piglin",new Face("textures/entity/piglin/piglin.png",8,8,8,8,64,64)),
Map.entry("minecraft:piglin_brute",new Face("textures/entity/piglin/piglin_brute.png",8,8,8,8,64,64)),
Map.entry("minecraft:zombified_piglin",new Face("textures/entity/piglin/zombified_piglin.png",8,8,8,8,64,64)),
Map.entry("minecraft:villager",new Face("textures/entity/villager/villager.png",8,8,8,10,64,64)),
Map.entry("minecraft:wandering_trader",new Face("textures/entity/wandering_trader/wandering_trader.png",8,8,8,10,64,64)),
Map.entry("minecraft:zombie_villager",new Face("textures/entity/zombie_villager/zombie_villager.png",8,8,8,10,64,64)),
Map.entry("minecraft:evoker",new Face("textures/entity/illager/evoker.png",8,8,8,10,64,64)),
Map.entry("minecraft:vindicator",new Face("textures/entity/illager/vindicator.png",8,8,8,10,64,64)),
Map.entry("minecraft:pillager",new Face("textures/entity/illager/pillager.png",8,8,8,10,64,64)),
Map.entry("minecraft:illusioner",new Face("textures/entity/illager/illusioner.png",8,8,8,10,64,64)),
Map.entry("minecraft:witch",new Face("textures/entity/witch/witch.png",8,8,8,10,64,128)),
Map.entry("minecraft:ghast",new Face("textures/entity/ghast/ghast.png",16,16,16,16,128,64)),
Map.entry("minecraft:slime",new Face("textures/entity/slime/slime.png",8,8,8,8,64,32)),
Map.entry("minecraft:spider",new Face("textures/entity/spider/spider.png",8,8,8,8,64,32)),
Map.entry("minecraft:cave_spider",new Face("textures/entity/spider/cave_spider.png",8,8,8,8,64,32)),
Map.entry("minecraft:cow",new Face("textures/entity/cow/cow_temperate.png",6,6,8,8,64,64)),
Map.entry("minecraft:pig",new Face("textures/entity/pig/pig_temperate.png",8,8,8,8,64,64)),
Map.entry("minecraft:sheep",new Face("textures/entity/sheep/sheep.png",6,6,6,6,64,32)),
Map.entry("minecraft:chicken",new Face("textures/entity/chicken/chicken_temperate.png",2,3,4,6,64,32)),
Map.entry("minecraft:cat",new Face("textures/entity/cat/cat_tabby.png",0,0,5,4,64,32)),
Map.entry("minecraft:fox",new Face("textures/entity/fox/fox.png",6,6,8,6,48,32)),
Map.entry("minecraft:bee",new Face("textures/entity/bee/bee.png",7,7,7,7,64,64)),
Map.entry("minecraft:iron_golem",new Face("textures/entity/iron_golem/iron_golem.png",8,8,8,10,128,128)),
Map.entry("minecraft:goat",new Face("textures/entity/goat/goat.png",2,2,5,7,64,64)),
Map.entry("minecraft:warden",new Face("textures/entity/warden/warden.png",16,16,16,16,128,128)));
 static boolean draw(GuiGraphicsExtractor g,String id,int x,int y,int size){var f=FACES.get(id);if(f==null)return false;g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("minecraft",f.texture),x,y,f.u,f.v,size,size,f.w,f.h,f.tw,f.th);return true;}
}

