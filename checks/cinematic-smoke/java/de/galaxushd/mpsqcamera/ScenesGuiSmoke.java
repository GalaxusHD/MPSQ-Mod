package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.input.*;

final class ScenesGuiSmoke {
    private static MpsqScenes.Target target;
    private static MpsqCinematicScene markerScene;
    private static void cell(net.minecraft.client.gui.screens.Screen s,int index,int button){int left=(int)QuestGuiSmoke.get(s,"left"),top=(int)QuestGuiSmoke.get(s,"top");float scale=(float)QuestGuiSmoke.get(s,"uiScale");s.mouseClicked(new MouseButtonEvent(left+(19+index%9*18)*scale,top+(39+index/9*18)*scale,new MouseButtonInfo(button,0)),false);}
    static void run(ClientGameTestContext ctx){
        ctx.runOnClient(c->{try{
            String cacheUrl="local://scenes-regression/skin.png";
            String cacheHash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(cacheUrl.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var cacheDir=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("mpsq-world-data").resolve("scenes-test-"+java.util.UUID.randomUUID()+"-assets");
            java.nio.file.Files.createDirectories(cacheDir);var cacheFile=cacheDir.resolve(cacheHash+".bin");
            byte[] content={1,2,3,4};
            try{java.nio.file.Files.write(cacheFile,content);if(!java.util.Arrays.equals(content,MpsqLocalWorldStore.readCachedSkin(cacheUrl,8)))throw new AssertionError("Cross-world identical skin cache missing");}
            finally{java.nio.file.Files.deleteIfExists(cacheFile);java.nio.file.Files.deleteIfExists(cacheDir);}
            c.gui.setScreen(null);c.player.setYRot(0);c.player.setXRot(0);
            var row=new JsonObject();row.addProperty("id","scenes-smoke");row.addProperty("asset_id","self");row.addProperty("category","npc_skin_normal");row.addProperty("url","self");row.addProperty("display_name","Scenes NPC");row.addProperty("task_type","none");row.addProperty("x",c.player.getX()-.5);row.addProperty("y",c.player.getY());row.addProperty("z",c.player.getZ()+3.5);row.addProperty("yaw",180);
            var all=new JsonArray();all.add(row);var f=MpsqAccessoryRenderer.class.getDeclaredField("npcs");f.setAccessible(true);f.set(null,all);
            target=new MpsqScenes.Target("npc:scenes-smoke",null,row,MpsqScenes.fromRow(row));MpsqScenes.save(target,target.profile());
            if(MpsqScenes.target()==null)throw new AssertionError("Ray target missing");
            if(MpsqScenesClips.all().size()!=38)throw new AssertionError("Animation catalog");
            var profile=MpsqScenes.copy(target.profile());var npcReference=profile.npc;var previous=MpsqScenes.copy(profile);
            profile.npc.name="Changed";profile.scale=2;profile.restoreFrom(previous);
            if(profile.npc!=npcReference||!profile.npc.name.equals(previous.npc.name)||profile.scale!=previous.scale)throw new AssertionError("Scenes rollback lost child editor binding");
        }catch(Exception e){throw new AssertionError(e);}});
        ctx.setScreen(()->new MpsqScenesScreen(target));ctx.waitTicks(3);ctx.takeScreenshot("scenes-npc-edit");
        ctx.runOnClient(c->QuestGuiSmoke.button(c.gui.screen(),"Skins"));ctx.waitTicks(3);ctx.takeScreenshot("scenes-skins");
        ctx.runOnClient(c->c.gui.screen().onClose());
        ctx.runOnClient(c->QuestGuiSmoke.button(c.gui.screen(),"Aktionen"));ctx.waitTicks(3);ctx.takeScreenshot("scenes-animation-menu");
        ctx.runOnClient(c->{
            QuestGuiSmoke.button(c.gui.screen(),"Nächste Seite");
            if((int)QuestGuiSmoke.get(c.gui.screen(),"page")!=1)throw new AssertionError("Next animation page");
            if(((java.util.Map<?,?>)QuestGuiSmoke.get(c.gui.screen(),"previews")).size()!=7)throw new AssertionError("Final animation previews");
            QuestGuiSmoke.button(c.gui.screen(),"Vorherige Seite");
            var previews=(java.util.Map<?,?>)QuestGuiSmoke.get(c.gui.screen(),"previews");
            if(previews.size()!=31||previews.values().contains(null))throw new AssertionError("Missing original animation previews");
        });
        ctx.runOnClient(c->{cell(c.gui.screen(),5,com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);if(!MpsqScenes.stored("scenes-smoke").clip.endsWith("/dance1"))throw new AssertionError("Animation selection");c.gui.setScreen(null);});
        ctx.setScreen(()->new MpsqScenesScreen(new MpsqScenes.Target(target.key(),null,target.row(),MpsqScenes.stored("scenes-smoke"))));
        ctx.runOnClient(c->{QuestGuiSmoke.button(c.gui.screen(),"Aktionen");cell(c.gui.screen(),5,com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);if(!"TIMELINE".equals(QuestGuiSmoke.get(c.gui.screen(),"section")))throw new AssertionError("Right click must open timeline");c.gui.setScreen(null);});
        ctx.waitTicks(70);ctx.takeScreenshot("scenes-dance-player");
        ctx.runOnClient(c->{var p=MpsqScenes.copy(MpsqScenes.stored("scenes-smoke"));p.clip="scenes/scene_townsfolk_vol5/eat_sandwich_stand";p.npc.category="npc_skin_slim";MpsqScenes.save(target,p);});
        ctx.waitTicks(70);ctx.takeScreenshot("scenes-eat-slim-player");
        ctx.setScreen(()->new MpsqScenesScreen(new MpsqScenes.Target(target.key(),null,target.row(),MpsqScenes.stored("scenes-smoke"))));
        ctx.runOnClient(c->QuestGuiSmoke.button(c.gui.screen(),"Pfade"));ctx.waitTicks(2);ctx.takeScreenshot("scenes-path-menu");
        ctx.runOnClient(c->{var s=(MpsqScenesScreen)c.gui.screen();s.capturePosition(0);c.player.setPos(c.player.getX()+2,c.player.getY(),c.player.getZ());s.capturePosition(1);var p=MpsqScenes.stored("scenes-smoke");if(p.npc.route[0]==null||p.npc.route[1]==null)throw new AssertionError("Path save");var mid=MpsqScenes.position(p,1.5);if(Math.abs(mid.x-(p.npc.route[0].x+1))>.01)throw new AssertionError("Path movement");});
        ctx.runOnClient(c->{var screen=c.gui.screen();int left=(int)QuestGuiSmoke.get(screen,"left"),top=(int)QuestGuiSmoke.get(screen,"top");float scale=(float)QuestGuiSmoke.get(screen,"uiScale");screen.mouseClicked(new MouseButtonEvent(left+57*scale,top+75*scale,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT,0)),false);if(c.gui.screen()==screen)throw new AssertionError("Right-click path confirmation absent");QuestGuiSmoke.button(c.gui.screen(),"Löschen");if(MpsqScenes.stored("scenes-smoke").npc.route[1]!=null)throw new AssertionError("Path not deleted");var profile=MpsqScenes.copy(MpsqScenes.stored("scenes-smoke"));profile.npc.name="";profile.npc.rankTag="offizier";profile.npc.glow=false;MpsqScenes.save(target,profile);});
        ctx.runOnClient(c->{c.gui.setScreen(new MpsqScenesScreen(new MpsqScenes.Target(target.key(),null,target.row(),MpsqScenes.stored("scenes-smoke"))));QuestGuiSmoke.button(c.gui.screen(),"Skins");});ctx.waitTicks(15);ctx.takeScreenshot("npc-head-selection");
        ctx.runOnClient(c->{c.gui.screen().onClose();QuestGuiSmoke.button(c.gui.screen(),"Ausrüstung");QuestGuiSmoke.button(c.gui.screen(),"Helm: minecraft:air");});ctx.waitTicks(5);ctx.takeScreenshot("npc-helmet-list");
        ctx.runOnClient(c->{cell(c.gui.screen(),11,com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);var p=MpsqScenes.stored("scenes-smoke");if(!p.npc.equipment[0].contains("owl"))throw new AssertionError("Hat selection");c.gui.screen().onClose();QuestGuiSmoke.button(c.gui.screen(),"Einstellungen");});ctx.waitTicks(2);ctx.takeScreenshot("npc-name-glow-rank-settings");
        ctx.runOnClient(c->{var p=MpsqScenes.copy(MpsqScenes.stored("scenes-smoke"));p.scale=Float.NaN;try{MpsqScenes.save(target,p);throw new AssertionError("NaN scale accepted");}catch(IllegalArgumentException expected){}
            TeamStateStore.setSelf(new TeamProfile(java.util.UUID.randomUUID(),"Player",TeamRank.values()[0],null,true));if(MpsqScenes.allowed())throw new AssertionError("Officer gate");
            try{MpsqScenes.save(target,target.profile());throw new AssertionError("Unauthorized write");}catch(IllegalStateException expected){}
            TeamStateStore.setSelf(new TeamProfile(java.util.UUID.randomUUID(),"GuiCheck",TeamRank.SENIOR_OFFICER,null,true));
            var clean=MpsqScenes.copy(target.profile());clean.deleted=true;MpsqScenes.save(target,clean);if(!MpsqScenes.apply(target.row()).get("scenes_hidden").getAsBoolean())throw new AssertionError("Delete visibility");
            try{var f=MpsqAccessoryRenderer.class.getDeclaredField("npcs");f.setAccessible(true);f.set(null,new JsonArray());}catch(Exception e){throw new AssertionError(e);}
        });ctx.setScreen(()->null);
        ctx.runOnClient(c->{
            markerScene=new MpsqCinematicScene();markerScene.dimension=c.level.dimension().identifier().toString();
            var n=markerScene.npc;n.placed=true;n.category="npc_skin_normal";n.type="self";n.x=c.player.getX();n.y=c.player.getY();n.z=c.player.getZ()+4;n.yaw=180;
            n.route[0]=new MpsqCinematicScene.Waypoint(n.x,n.y,n.z,1);n.route[1]=new MpsqCinematicScene.Waypoint(n.x+2,n.y,n.z+2,3);n.route[2]=new MpsqCinematicScene.Waypoint(n.x+3,n.y,n.z+4,3);
            markerScene.points.add(new MpsqCinematicScene.Point(0,n.x-2,n.y+2,n.z,0,0));
            markerScene.points.add(new MpsqCinematicScene.Point(3,n.x,n.y+3,n.z+3,45,0));markerScene.points.getLast().transition="SMOOTH";
            markerScene.points.add(new MpsqCinematicScene.Point(6,n.x+3,n.y+2,n.z+3,90,0));markerScene.points.getLast().transition="SMOOTH";
            MpsqCinematicMarkers.toggle(markerScene);
        });ctx.waitTicks(5);ctx.takeScreenshot("npc-path-and-editor-actor");
        ctx.runOnClient(c->{if(MpsqCinematicActors.scenePose(markerScene.id)==null)throw new AssertionError("Marker NPC invisible");MpsqCinematicMarkers.toggle(markerScene);});
        ctx.waitTicks(2);ctx.runOnClient(c->{if(MpsqCinematicActors.scenePose(markerScene.id)!=null)throw new AssertionError("Marker NPC not removed");});
        System.out.println("SCENES_GUI_SMOKE_PASS");
    }
}

