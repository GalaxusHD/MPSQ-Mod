package de.galaxushd.mpsqcamera;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.input.*;
public class CinematicGuiSmoke implements FabricClientGameTest {
    private MpsqCinematicScene fixture;
    private MpsqCinematicScreen screen(){
        var s=new MpsqCinematicScreen(null);
        try{var f=MpsqCinematicScreen.class.getDeclaredField("scenes");f.setAccessible(true);f.set(s,new java.util.ArrayList<>(java.util.List.of(fixture)));}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
        return s;
    }
    private static void click(net.minecraft.client.gui.screens.Screen s,int panelWidth,int panelHeight,int x,int y){
        s.mouseClicked(new MouseButtonEvent((s.width-panelWidth)/2+x,(s.height-panelHeight)/2+y,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false);
    }
    private static void right(net.minecraft.client.gui.screens.Screen s,int w,int h,int x,int y){
        s.mouseClicked(new MouseButtonEvent((s.width-w)/2+x,(s.height-h)/2+y,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT,0)),false);
    }
    public void runTest(ClientGameTestContext ctx){
        try(var world=ctx.worldBuilder().create()){
            world.getConnection().waitForChunksRender();
            ctx.runOnClient(c->{
                ModConfig.mpsqEnabled=true;
                try{
                    var field=MpsqZoneCatalog.class.getDeclaredField("zones");field.setAccessible(true);
                    @SuppressWarnings("unchecked") var zones=(java.util.List<MpsqZoneCatalog.Entry>)field.get(null);
                    zones.clear();var p=c.player.blockPosition();zones.add(new MpsqZoneCatalog.Entry(MpsqZoneCatalog.MASTER,new MpsqGameZone(p.offset(-60,-10,-60),p.offset(60,10,60))));
                    if(!TeamVisibilitySettings.inArea())throw new AssertionError("Viewer inside area");
                    TeamVisibilitySettings.requirePlacement(c.player.getX(),c.player.getY(),c.player.getZ());
                    var outside=p.offset(100,0,0);
                    try{TeamVisibilitySettings.requirePlacement(outside.getX(),outside.getY(),outside.getZ());throw new AssertionError("Outside placement accepted");}catch(IllegalArgumentException expected){}
                    TeamVisibilitySettings.requireScreenPlacement(LocalScreenStore.ScreenInputType.LINK,outside,outside);
                    try{TeamVisibilitySettings.requireScreenPlacement(LocalScreenStore.ScreenInputType.CAMERA,p,outside);throw new AssertionError("Cross-boundary screen accepted");}catch(IllegalArgumentException expected){}
                    var original=c.player.position();c.player.setPos(outside.getX(),outside.getY(),outside.getZ());
                    if(TeamVisibilitySettings.inArea()||!TeamVisibilitySettings.visible())throw new AssertionError("Viewer outside must only hide scoped features");
                    c.player.setPos(original);
                    ModConfig.mpsqEnabled=false;
                    if(TeamVisibilitySettings.inArea()||TeamVisibilitySettings.visible())throw new AssertionError("Disabled gate");
                    try{TeamVisibilitySettings.requireScreenPlacement(LocalScreenStore.ScreenInputType.LINK,p,p);throw new AssertionError("Disabled cinema placement accepted");}catch(IllegalArgumentException expected){}
                    ModConfig.mpsqEnabled=true;
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}
                TeamStateStore.setSelf(new TeamProfile(java.util.UUID.randomUUID(),"GuiCheck",TeamRank.SENIOR_OFFICER,null,true));
                var scene=new MpsqCinematicScene();scene.name="GUI Regression";scene.dimension=c.level.dimension().identifier().toString();
                scene.points.add(new MpsqCinematicScene.Point(0,c.player.getX(),c.player.getEyeY(),c.player.getZ(),0,0));
                scene.points.add(new MpsqCinematicScene.Point(5,c.player.getX()+2,c.player.getEyeY(),c.player.getZ(),0,0));
                fixture=scene; // In-memory fixture only; never write cinematic storage.
            });
            for(int i=0;i<3;i++){
                ctx.setScreen(this::screen);ctx.waitTicks(2);
                ctx.runOnClient(c->{
                    var s=c.gui.screen();click(s,176,222,16,26);
                    try{
                        var f=MpsqCinematicScreen.class.getDeclaredField("scene");f.setAccessible(true);
                        if(f.get(s)==null)throw new AssertionError("Saved scene did not open");
                    }catch(ReflectiveOperationException e){throw new AssertionError(e);}
                });ctx.waitTicks(2);
                if(i==2)ctx.takeScreenshot("cinematic-custom-timeline");
                ctx.runOnClient(c->c.gui.screen().onClose());ctx.waitTicks(2);
            }
            ctx.setScreen(this::screen);ctx.waitTicks(2);
            ctx.runOnClient(c->{right(c.gui.screen(),176,222,16,26);if(!(c.gui.screen() instanceof MpsqCinematicScreen.TextInput))throw new AssertionError("Right click must rename");c.gui.screen().onClose();});ctx.waitTicks(2);
            ctx.runOnClient(c->click(c.gui.screen(),176,222,16,26));ctx.waitTicks(2);
            ctx.runOnClient(c->{click(c.gui.screen(),176,131,34,44);if(!c.gui.screen().getClass().getSimpleName().equals("PointEditor"))throw new AssertionError("Left click must edit point");c.gui.screen().onClose();});ctx.waitTicks(2);
            ctx.runOnClient(c->{right(c.gui.screen(),176,131,34,44);if(!(c.gui.screen() instanceof net.minecraft.client.gui.screens.ConfirmScreen))throw new AssertionError("Right click must confirm point deletion");});
            ctx.setScreen(this::screen);ctx.waitTicks(2);ctx.runOnClient(c->click(c.gui.screen(),176,222,16,26));ctx.waitTicks(2);
            ctx.runOnClient(c->click(c.gui.screen(),176,131,16,98));ctx.waitTicks(2);
            ctx.takeScreenshot("cinematic-custom-sounds");
            ctx.runOnClient(c->click(c.gui.screen(),121,41,16,26));ctx.waitTicks(2);
            ctx.runOnClient(c->click(c.gui.screen(),176,131,16,62));ctx.waitTicks(10);
            ctx.waitTicks(10);
            ctx.takeScreenshot("cinematic-custom-npc");
            ctx.runOnClient(c->{if(!(c.gui.screen() instanceof MpsqScenesScreen))throw new AssertionError("NPC menu not opened");});
            ctx.runOnClient(c->{QuestGuiSmoke.button(c.gui.screen(),"Skins");if(!QuestGuiSmoke.get(c.gui.screen(),"section").equals("SKINS"))throw new AssertionError("Unified skins menu");c.gui.screen().onClose();});
            for(String category:new String[]{"npc_skin_normal","npc_skin_slim"}){
                ctx.runOnClient(c->{
                    fixture.npc.category=category;fixture.npc.type="self";fixture.npc.name="Test Actor";
                    var actor=MpsqCinematicActors.create(fixture.npc);
                    if(!actor.getName().getString().equals("Test Actor")||!actor.getDisplayName().getString().equals("Test Actor"))throw new AssertionError("NPC name");
                    var player=(net.minecraft.client.player.RemotePlayer)actor;
                    if(!player.getSkin().body().equals(c.player.getSkin().body()))throw new AssertionError("Self skin texture");
                    for(var part:net.minecraft.world.entity.player.PlayerModelPart.values())if(!player.isModelPartShown(part))throw new AssertionError("Missing skin layer");
                    c.gui.setScreen(MpsqScenesScreen.cinematic(screen(),fixture,change->{throw new AssertionError("Test must not save");}));
                });ctx.waitTicks(5);ctx.takeScreenshot("cinematic-"+category);
            }
            ctx.setScreen(()->null);
            ctx.runOnClient(c->{
                fixture.npc.category="npc_skin_normal";fixture.npc.name="NPC";fixture.npc.type="self";
                var actor=MpsqCinematicActors.create(fixture.npc);
                if(!actor.getName().equals(MpsqNametags.forPlayer(c.player.getGameProfile().name())))throw new AssertionError("Default player NPC name/rank");
                fixture.npc.category="mob";fixture.npc.type="minecraft:villager";
                if(MpsqCinematicActors.create(fixture.npc)==null)throw new AssertionError("Villager creation");
                fixture.npc.category="npc_model";fixture.npc.type=MpsqAccessoryRenderer.WUMPUS_ASSET_ID;fixture.npc.url=MpsqAccessoryRenderer.assetPreviewUrl(fixture.npc.type,"");
                c.gui.setScreen(MpsqScenesScreen.cinematic(screen(),fixture,change->{throw new AssertionError("Test must not save");}));
            });ctx.waitTicks(100);ctx.takeScreenshot("cinematic-wumpus-model");
            ctx.runOnClient(c->{
                fixture.npc.placed=true;fixture.npc.x=c.player.getX();fixture.npc.y=c.player.getY();fixture.npc.z=c.player.getZ()+4;fixture.npc.yaw=180;
                c.gui.setScreen(new MpsqCinematicPreview(null,fixture,0));
            });ctx.waitTicks(20);ctx.takeScreenshot("cinematic-wumpus-playback");
            ctx.runOnClient(c->ModConfig.mpsqEnabled=false);ctx.waitTicks(2);
            ctx.runOnClient(c->{if(MpsqCinematicPreview.active()||c.getCameraEntity()!=c.player)throw new AssertionError("Disable must end playback and restore player camera");ModConfig.mpsqEnabled=true;});
            ctx.runOnClient(c->c.gui.setScreen(new MpsqCinematicEventScreen(screen(),fixture,"SOUND",change->{throw new AssertionError("Test must not save");})));
            ctx.waitTicks(2);
            ctx.runOnClient(c->{var s=c.gui.screen();s.mouseClicked(new MouseButtonEvent(s.width/2,44,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false);});
            ctx.waitTicks(2);ctx.takeScreenshot("cinematic-sound-editor");
            ctx.runOnClient(c->{
                var event=new MpsqCinematicScene.Event();event.type="FADE";event.time=2;event.duration=4;
                var scene=new MpsqCinematicScene();scene.events.add(event);var runtime=new MpsqCinematicEvents(scene,0);
                if(runtime.fade(1)!=0||runtime.fade(2)!=0||runtime.fade(4)!=255||runtime.fade(6)!=0)throw new AssertionError("Fade timing");
                runtime.advance(2);runtime.advance(3);runtime.advance(3);
                try{var f=MpsqCinematicEvents.class.getDeclaredField("fired");f.setAccessible(true);if(((java.util.Set<?>)f.get(runtime)).size()!=1)throw new AssertionError("Event executed twice");}catch(ReflectiveOperationException e){throw new AssertionError(e);}
                runtime.stop();
            });
            ctx.runOnClient(c->{
                fixture.npc.actions.clear();fixture.npc.route=new MpsqCinematicScene.Waypoint[5];fixture.npc.category="npc_model";fixture.npc.type=MpsqAccessoryRenderer.WUMPUS_ASSET_ID;fixture.npc.linkedNpc="linked-smoke";fixture.npc.placed=true;
                var action=new MpsqCinematicScene.NpcAction();action.clip="wave";action.duration=5;action.look="CAMERA";fixture.npc.actions.add(action);
                var row=new com.google.gson.JsonObject();row.addProperty("id","linked-smoke");row.addProperty("asset_id",MpsqAccessoryRenderer.WUMPUS_ASSET_ID);row.addProperty("category","npc_model");row.addProperty("url",MpsqAccessoryRenderer.assetPreviewUrl(MpsqAccessoryRenderer.WUMPUS_ASSET_ID,""));row.addProperty("world_x",c.player.getX());row.addProperty("world_y",c.player.getY());row.addProperty("world_z",c.player.getZ()+4);row.addProperty("scale",1);row.addProperty("task_type","none");
                try{var f=MpsqAccessoryRenderer.class.getDeclaredField("npcs");f.setAccessible(true);var rows=new com.google.gson.JsonArray();rows.add(row);f.set(null,rows);var next=MpsqAccessoryRenderer.class.getDeclaredField("next");next.setAccessible(true);next.setLong(null,System.currentTimeMillis()+60000);}catch(Exception e){throw new AssertionError(e);}
                var dialogue=new MpsqCinematicScene.Event();dialogue.type="DIALOGUE";dialogue.duration=5;dialogue.value="Willkommen! Ich bin derselbe Wumpus wie außerhalb der Kamerafahrt.";dialogue.extra="Wumpus";fixture.events.add(dialogue);
                c.gui.setScreen(new MpsqCinematicPreview(null,fixture,0));
            });ctx.waitTicks(20);ctx.takeScreenshot("npc-linked-wumpus-wave-dialogue");
            ctx.runOnClient(c->{
                var pose=MpsqCinematicActors.linkedPose("linked-smoke");if(pose==null||!pose.clip().equals("wave"))throw new AssertionError("Linked wave override");
                try{var f=MpsqCinematicActors.class.getDeclaredField("active");f.setAccessible(true);if(f.get(null)!=null)throw new AssertionError("Linked actor must not spawn duplicate");}catch(Exception e){throw new AssertionError(e);}
                c.gui.screen().onClose();if(MpsqCinematicActors.linkedPose("linked-smoke")!=null)throw new AssertionError("Linked override not restored");
                fixture.events.clear();fixture.npc.linkedNpc="";fixture.npc.actions.clear();
                fixture.npc.category="npc_skin_normal";fixture.npc.type="self";fixture.npc.look="CAMERA";
                fixture.npc.route[0]=new MpsqCinematicScene.Waypoint(c.player.getX()-2,c.player.getY(),c.player.getZ()+4,1);
                fixture.npc.route[1]=new MpsqCinematicScene.Waypoint(c.player.getX()+2,c.player.getY(),c.player.getZ()+4,4);
                var action=new MpsqCinematicScene.NpcAction();action.clip="swing";action.duration=4;fixture.npc.actions.add(action);
                c.gui.setScreen(new MpsqCinematicPreview(null,fixture,0));
            });ctx.waitTicks(20);ctx.takeScreenshot("npc-walk-swing");
            ctx.runOnClient(c->{
                try{var f=MpsqCinematicActors.class.getDeclaredField("active");f.setAccessible(true);var actor=(net.minecraft.world.entity.LivingEntity)f.get(null);if(actor==null||!actor.walkAnimation.isMoving())throw new AssertionError("Walking animation missing");}catch(Exception e){throw new AssertionError(e);}
                c.gui.screen().onClose();fixture.npc.actions.clear();fixture.npc.route=new MpsqCinematicScene.Waypoint[5];
                try{var f=MpsqAccessoryRenderer.class.getDeclaredField("npcs");f.setAccessible(true);f.set(null,new com.google.gson.JsonArray());}catch(Exception e){throw new AssertionError(e);}
            });
            ctx.runOnClient(c->{
                var row=new com.google.gson.JsonObject();row.addProperty("id","yaw-regression");row.addProperty("asset_id",MpsqAccessoryRenderer.WUMPUS_ASSET_ID);row.addProperty("category","npc_model");row.addProperty("url",MpsqAccessoryRenderer.assetPreviewUrl(MpsqAccessoryRenderer.WUMPUS_ASSET_ID,""));row.addProperty("world_x",c.player.getX());row.addProperty("world_y",c.player.getY());row.addProperty("world_z",c.player.getZ()+4);row.addProperty("scale",1);row.addProperty("yaw",180);row.addProperty("task_type","none");
                try{var f=MpsqAccessoryRenderer.class.getDeclaredField("npcs");f.setAccessible(true);var rows=new com.google.gson.JsonArray();rows.add(row);f.set(null,rows);var next=MpsqAccessoryRenderer.class.getDeclaredField("next");next.setAccessible(true);next.setLong(null,System.currentTimeMillis()+60000);}catch(Exception e){throw new AssertionError(e);}
                c.gui.setScreen(null);c.player.setYRot(0);c.player.setXRot(0);
            });ctx.waitTicks(80);ctx.takeScreenshot("wumpus-facing-player-placement-yaw-180");
            ctx.runOnClient(c->{try{var f=MpsqAccessoryRenderer.class.getDeclaredField("npcRotations");f.setAccessible(true);var map=(java.util.Map<?,?>)f.get(null);var rotation=map.get("yaw-regression");float yaw=(float)QuestGuiSmoke.get(rotation,"bodyYaw");if(Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw-180))>15)throw new AssertionError("Wumpus turned away: "+yaw);var n=MpsqAccessoryRenderer.class.getDeclaredField("npcs");n.setAccessible(true);n.set(null,new com.google.gson.JsonArray());}catch(Exception e){throw new AssertionError(e);}});
            ScenesGuiSmoke.run(ctx);
            NpcAssetsSmoke.run(ctx); BirdVariantsSmoke.run(ctx);
            SeagullDropsSmoke.run(ctx);
            QuestGuiSmoke.run(ctx);
            for(String page:new String[]{"Team","Bearbeiten"}){
                ctx.setScreen(()->{
                    try{var constructor=ModConfigScreen.class.getDeclaredConstructor(String.class,net.minecraft.client.gui.screens.Screen.class);constructor.setAccessible(true);
                        var menu=constructor.newInstance(page,null);var requested=ModConfigScreen.class.getDeclaredField("roleRefreshStarted");requested.setAccessible(true);requested.set(menu,true);return menu;
                    }catch(Exception e){throw new AssertionError(e);}
                });ctx.waitTicks(2);ctx.takeScreenshot("menu-"+page);
                ctx.runOnClient(c->{
                    var labels=c.gui.screen().children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->((net.minecraft.client.gui.components.Button)w).getMessage().getString()).toList();
                    var expected=page.equals("Team")?java.util.List.of("Rückkehr-Auslosung","Texte","Todos","Logs","Replay","Zurück"):java.util.List.of("Bildschirme","Kameras","Kamerafahrten","Models","Zurück");
                    if(!labels.equals(expected))throw new AssertionError("Menu order: "+labels);
                });
            }
            ctx.setScreen(()->new MpsqReplayScreen(null));ctx.waitTicks(2);ctx.takeScreenshot("menu-replay");
            ctx.runOnClient(c->{
                var p=c.player.blockPosition();var fixtures=new java.util.ArrayList<LocalScreenStore.LocalScreenData>();
                for(var type:LocalScreenStore.ScreenInputType.values())fixtures.add(new LocalScreenStore.LocalScreenData(java.util.UUID.randomUUID(),p,p,type.name(),c.player.position(),type,"",null,null));
                LocalScreenStore.replaceAll(fixtures);
            });
            for(boolean cinema:new boolean[]{true,false}){
                ctx.setScreen(()->new BildschirmListScreen(null,cinema));ctx.waitTicks(2);
                ctx.runOnClient(c->{try{var f=BildschirmListScreen.class.getDeclaredField("bildschirme");f.setAccessible(true);if(((java.util.List<?>)f.get(c.gui.screen())).size()!=(cinema?1:2))throw new AssertionError("Screen type filter");}catch(ReflectiveOperationException e){throw new AssertionError(e);}});
            }
            ctx.runOnClient(c->LocalScreenStore.replaceAll(java.util.List.of()));
            ctx.setScreen(()->{
                var menu=new MpsqModelsScreen(null);
                try{
                    var requested=MpsqModelsScreen.class.getDeclaredField("requested");requested.setAccessible(true);requested.set(menu,true);
                    var tab=MpsqModelsScreen.class.getDeclaredField("tab");tab.setAccessible(true);tab.set(menu,1);
                    var assets=new com.google.gson.JsonArray();for(int i=0;i<30;i++){var entry=new com.google.gson.JsonObject();entry.addProperty("id","preview_"+i);entry.addProperty("category","npc_model");entry.addProperty("display_name","Vorschau "+i);entry.addProperty("url",MpsqAccessoryRenderer.assetPreviewUrl(MpsqAccessoryRenderer.WUMPUS_ASSET_ID,""));assets.add(entry);}
                    var field=MpsqModelsScreen.class.getDeclaredField("assets");field.setAccessible(true);field.set(menu,assets);
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}return menu;
            });ctx.waitTicks(5);ctx.runOnClient(c->c.gui.screen().mouseScrolled(200,100,0,-7));ctx.waitTicks(5);ctx.takeScreenshot("models-scrolled");
            ctx.setScreen(()->null);
            ctx.runOnClient(c->{
                fixture.points.clear();double x=c.player.getX(),y=c.player.getEyeY(),z=c.player.getZ();
                fixture.points.add(new MpsqCinematicScene.Point(0,x-3,y,z+5,0,0));
                fixture.points.add(new MpsqCinematicScene.Point(5,x+3,y+1,z+7,0,0));
                fixture.points.add(new MpsqCinematicScene.Point(10,x+4,y,z+3,0,0));
                c.player.setYRot(0);c.player.setXRot(0);MpsqCinematicMarkers.toggle(fixture);
                if(!MpsqCinematicMarkers.showing(fixture))throw new AssertionError("Markers not enabled");
            });ctx.waitTicks(5);ctx.takeScreenshot("cinematic-path-preview");
            ctx.runOnClient(c->{MpsqCinematicMarkers.toggle(fixture);if(MpsqCinematicMarkers.showing(fixture))throw new AssertionError("Markers not disabled");});
            ctx.setScreen(()->null);
        }
        System.out.println("CINEMATIC_GUI_SMOKE_PASS");
    }
}

