package de.galaxushd.mpsqcamera;

import java.util.*;
import java.util.function.Consumer;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;

/** A scene-local actor editor. Placing records a position; it never spawns a server mob. */
final class MpsqCinematicNpcScreen extends Screen {
    private static final Identifier ART=Identifier.fromNamespaceAndPath("mpsqcamera","textures/gui/cinematic_npcs.png");
    private final Screen parent;
    final MpsqCinematicScene scene;
    private final Consumer<Runnable> save;
    private final List<Slot> slots=new ArrayList<>();
    private int left,top;
    private LivingEntity preview;
    private String error="";
    private record Slot(int x,int y,String title,ItemStack item,Consumer<Integer> action){}
    MpsqCinematicNpcScreen(Screen parent,MpsqCinematicScene scene,Consumer<Runnable> save){
        super(Component.literal("NPCs"));this.parent=parent;this.scene=scene;this.save=save;
        if(scene.npc==null)scene.npc=new MpsqCinematicScene.Npc();
    }
    void edit(Runnable action){save.accept(action);preview=null;init();}
    private void slot(int x,int y,String label,ItemStack item,Consumer<Integer> action){slots.add(new Slot(x,y,label,item,action));}
    @Override protected void init(){
        left=(width-121)/2;top=(height-185)/2;slots.clear();
        var n=scene.npc;n.validate();
        slot(26,18,"Name: "+n.name,ItemStack.EMPTY,b->minecraft.gui.setScreen(new MpsqCinematicScreen.TextInput(this,"NPC-Name",n.name,v->edit(()->n.name=v.strip()))));
        int[][] positions={{26,36},{26,54},{26,72},{26,90},{8,54},{44,54}};
        String[] labels={"Helm","Brustplatte","Hose","Schuhe","Haupthand","Nebenhand"};
        for(int i=0;i<6;i++){
            final int index=i;
            ItemStack item=stack(n.equipment[i]);
            slot(positions[i][0],positions[i][1],labels[i]+" · Klick: Item-ID · Rechtsklick: leeren",item,b->{
                if(b==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT){edit(()->n.equipment[index]="minecraft:air");return;}
                minecraft.gui.setScreen(new MpsqCinematicScreen.TextInput(this,labels[index]+" (Item-ID)",n.equipment[index],v->{
                    Identifier id=Identifier.tryParse(v.strip());
                    if(id==null||!BuiltInRegistries.ITEM.containsKey(id))throw new IllegalArgumentException("Unbekannte Item-ID.");
                    edit(()->n.equipment[index]=id.toString());
                }));
            });
        }
        slot(8,108,"Aktionen · Zeitplan bearbeiten",new ItemStack(Items.BLAZE_POWDER),b->minecraft.gui.setScreen(new MpsqCinematicNpcActionsScreen(this,"")));
        var clips=MpsqCinematicActors.clips(n);
        for(int index=0;index<Math.min(5,clips.size());index++){String clip=clips.get(index);slot(26+18*index,108,clip+" · Startzeit und Dauer",new ItemStack(index==0?Items.CLOCK:Items.BLAZE_POWDER),b->minecraft.gui.setScreen(new MpsqCinematicNpcActionsScreen(this,clip)));}
        slot(8,126,"Bewegung · Startzeit und Streckendauer",new ItemStack(Items.LEATHER_BOOTS),b->minecraft.gui.setScreen(new MotionSettings(this)));
        for(int index=0;index<5;index++){
            int point=index;boolean enabled=index==0||n.route[0]!=null&&(index==4||n.route[index-1]!=null);
            String label=pointLabel(index);boolean exists=n.route[index]!=null;
            slot(26+18*index,126,label+(exists?" · Klick: löschen":enabled?" · Klick: Position setzen":" · Zuerst vorherige Position setzen"),new ItemStack(!enabled?Items.BARRIER:exists?Items.EMERALD:Items.ENDER_PEARL),b->{
                if(!enabled)return;
                if(n.route[point]!=null){minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(ok->{if(ok)edit(()->{if(point==0)Arrays.fill(n.route,null);else if(point<4)for(int j=point;j<4;j++)n.route[j]=null;else n.route[4]=null;});minecraft.gui.setScreen(this);},Component.literal(label+" löschen?"),Component.literal(point<4?"Nachfolgende Zwischenpositionen werden ebenfalls entfernt.":"Die Endposition wird entfernt.")));}
                else {capture=this;captureIndex=point;captureWorld=minecraft.level;minecraft.gui.setScreen(null);de.galaxushd.mpsqcamera.compat.PlayerMessages.send(minecraft.player,Component.literal("Position wählen, dann /mpsq-cinematic npc-position. Abbrechen: /mpsq-cinematic abbrechen"),false);}
            });
        }
        slot(116,162,"Bewegung aufnehmen",new ItemStack(Items.RECOVERY_COMPASS),b->MpsqMotionCapture.start(this,frames->edit(()->{n.recording=frames;n.recordingId=java.util.UUID.randomUUID().toString();n.movementStart=0;n.placed=true;})));
        slot(8,144,"Rotation · Blickziel und Kopfrotation",new ItemStack(Items.COMPASS),b->minecraft.gui.setScreen(new RotationSettings(this)));
        slot(26,144,"Körper −15°",new ItemStack(Items.ARROW),b->edit(()->n.yaw=MpsqCinematicScene.snapRotation(n.yaw)-15));
        slot(44,144,"Körper +15°",new ItemStack(Items.SPECTRAL_ARROW),b->edit(()->n.yaw=MpsqCinematicScene.snapRotation(n.yaw)+15));
        slot(62,144,"Blickverfolgung: "+n.look,new ItemStack(Items.SPYGLASS),b->minecraft.gui.setScreen(new RotationSettings(this)));
        slot(80,144,"Kopf −15°",new ItemStack(Items.PLAYER_HEAD),b->edit(()->n.headYaw=Math.max(-75,MpsqCinematicScene.snapRotation(n.headYaw)-15)));
        slot(98,144,"Kopf +15°",new ItemStack(Items.SKELETON_SKULL),b->edit(()->n.headYaw=Math.min(75,MpsqCinematicScene.snapRotation(n.headYaw)+15)));
        slot(62,162,"Vorhandenen MPSQ-Wumpus verknüpfen",new ItemStack(Items.LEAD),b->minecraft.gui.setScreen(new LinkedWumpus(this)));
        slot(44,162,"Typ auswählen: Mobs, Skins, Slim-Skins, Custom Models",new ItemStack(Items.VILLAGER_SPAWN_EGG),b->minecraft.gui.setScreen(new Types(this)));
        slot(8,162,"Zurück",ItemStack.EMPTY,b->onClose());
        slot(80,162,n.placed?"Platzieren: Position aktualisieren":"Platzieren: aktuelle Spielerposition",ItemStack.EMPTY,b->{
            if(minecraft.player==null||!scene.dimension.equals(minecraft.level.dimension().identifier().toString()))throw new IllegalArgumentException("Bitte in die Welt der Kamerafahrt wechseln.");
            TeamVisibilitySettings.requirePlacement(minecraft.player.getX(),minecraft.player.getY(),minecraft.player.getZ());edit(()->{n.x=minecraft.player.getX();n.y=minecraft.player.getY();n.z=minecraft.player.getZ();n.yaw=MpsqCinematicScene.snapRotation(minecraft.player.getYRot());n.placed=true;});
        });
        slot(98,162,"Platzierung löschen",ItemStack.EMPTY,b->edit(()->n.placed=false));
    }
    private static MpsqCinematicNpcScreen capture;
    private static net.minecraft.client.multiplayer.ClientLevel captureWorld;
    private static int captureIndex;
    private static boolean captureReady;
    static void capturePosition(){if(capture!=null)captureReady=true;else {var c=net.minecraft.client.Minecraft.getInstance();if(c.player!=null)de.galaxushd.mpsqcamera.compat.PlayerMessages.send(c.player,Component.literal("Zuerst einen NPC-Wegpunkt im Menü auswählen."),false);}}
    static void cancelCapture(){var target=capture;capture=null;captureReady=false;if(target!=null)net.minecraft.client.Minecraft.getInstance().execute(()->net.minecraft.client.Minecraft.getInstance().gui.setScreen(target));}
    static void captureTick(){
        if(capture==null)return;var c=net.minecraft.client.Minecraft.getInstance();
        if(c.level!=captureWorld||!TeamVisibilitySettings.inArea()){capture=null;captureReady=false;return;}
        if(!captureReady)return;captureReady=false;var target=capture;int index=captureIndex;
        try{if(c.player==null)throw new IllegalArgumentException("Kein Spieler.");TeamVisibilitySettings.requirePlacement(c.player.getX(),c.player.getY(),c.player.getZ());target.edit(()->target.scene.npc.route[index]=new MpsqCinematicScene.Waypoint(c.player.getX(),c.player.getY(),c.player.getZ(),3));}
        catch(Exception e){target.error=e.getMessage();}capture=null;c.gui.setScreen(target);
    }
    static String pointLabel(int i){return i==0?"Startposition":i==4?"Endposition":"Position "+i;}
    private static double seconds(String value){double v=Double.parseDouble(value.replace(',','.'));if(!Double.isFinite(v)||v<0||v>86400)throw new IllegalArgumentException("Ungültige Zeit.");return v;}
    private static final class MotionSettings extends Screen {
        private final MpsqCinematicNpcScreen parent;
        MotionSettings(MpsqCinematicNpcScreen parent){super(Component.literal("NPC-Bewegung"));this.parent=parent;}
        @Override protected void init(){var n=parent.scene.npc;int y=35;
            addRenderableWidget(Button.builder(Component.literal("Startzeit: "+n.movementStart+" s"),b->minecraft.gui.setScreen(new MpsqCinematicScreen.TextInput(this,"Startzeit in Sekunden",""+n.movementStart,v->{double value=seconds(v);parent.edit(()->n.movementStart=value);}))).bounds(width/2-120,y,240,20).build());y+=24;
            for(int i=1;i<5;i++)if(n.route[i]!=null){int index=i;addRenderableWidget(Button.builder(Component.literal("Bis "+pointLabel(i)+": "+n.route[i].duration+" s"),b->minecraft.gui.setScreen(new MpsqCinematicScreen.TextInput(this,"Streckendauer in Sekunden",""+n.route[index].duration,v->{double value=seconds(v);if(value<0.1||value>3600)throw new IllegalArgumentException("Die Streckendauer muss zwischen 0,1 und 3600 Sekunden liegen.");parent.edit(()->n.route[index].duration=value);}))).bounds(width/2-120,y,240,20).build());y+=24;}
            addRenderableWidget(Button.builder(Component.literal("Zurück"),b->onClose()).bounds(width/2-60,height-28,120,20).build());}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
    }
    private static final class RotationSettings extends Screen {
        private final MpsqCinematicNpcScreen parent;
        RotationSettings(MpsqCinematicNpcScreen parent){super(Component.literal("NPC-Rotation"));this.parent=parent;}
        @Override protected void init(){int y=30;var n=parent.scene.npc;
            String[] modes={"FIXED","PLAYER","CAMERA","PLAYER_HEAD","CAMERA_HEAD"};String[] labels={"Feste Rotation","Spieler verfolgen (Körper)","Kamera verfolgen (Körper)","Spieler verfolgen (Kopf)","Kamera verfolgen (Kopf)"};
            for(int i=0;i<modes.length;i++){String mode=modes[i];addRenderableWidget(Button.builder(Component.literal(labels[i]+(n.look.equals(mode)?" ✓":"")),b->{parent.edit(()->n.look=mode);rebuildWidgets();}).bounds(width/2-120,y,240,20).build());y+=22;}
            addRenderableWidget(Button.builder(Component.literal("Kopf hoch −15°"),b->{parent.edit(()->n.pitch=Math.max(-90,n.pitch-15));rebuildWidgets();}).bounds(width/2-120,y,118,20).build());
            addRenderableWidget(Button.builder(Component.literal("Kopf runter +15°"),b->{parent.edit(()->n.pitch=Math.min(90,n.pitch+15));rebuildWidgets();}).bounds(width/2+2,y,118,20).build());
            y+=24;
            addRenderableWidget(Button.builder(Component.literal("Spieler ansehen"),b->{face(false);rebuildWidgets();}).bounds(width/2-120,y,118,20).build());
            addRenderableWidget(Button.builder(Component.literal("Kamera ansehen"),b->{face(true);rebuildWidgets();}).bounds(width/2+2,y,118,20).build());
            addRenderableWidget(Button.builder(Component.literal("Zurück"),b->onClose()).bounds(width/2-60,height-28,120,20).build());}
        private void face(boolean camera){var n=parent.scene.npc;var target=camera?minecraft.getCameraEntity():minecraft.player;if(target==null)return;double x=target.getX()-n.x,z=target.getZ()-n.z;parent.edit(()->{n.yaw=MpsqCinematicScene.snapRotation((float)Math.toDegrees(Math.atan2(-x,z)));n.headYaw=0;n.look="FIXED";});}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
    }
    static ItemStack stack(String id){if((id.startsWith("mpsqcamera:nm_hat_")||id.startsWith("mpsqcamera:nm_head_"))){var stack=new ItemStack(Items.PAPER);stack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL,Identifier.parse(id));return stack;}var key=Identifier.tryParse(id);return key==null?ItemStack.EMPTY:new ItemStack(BuiltInRegistries.ITEM.getValue(key));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){
        super.extractRenderState(g,mx,my,d);
        g.blit(RenderPipelines.GUI_TEXTURED,ART,left,top,0,0,121,185,242,370,242,370);
        var n=scene.npc;
        if(n.category.equals("npc_model")){
            MpsqAccessoryRenderer.drawGuiPreview(g,MpsqAccessoryRenderer.assetPreviewUrl(n.type,n.url),left+88,top+70,2.4f);
        }else{
            if(preview==null){preview=MpsqCinematicActors.create(n);if(preview!=null){preview.setId(-1900000003);preview.addTag("mpsq_cinematic_preview");preview.setCustomNameVisible(false);}}
            if(preview!=null)InventoryScreen.extractEntityInInventoryFollowsMouse(g,left+62,top+36,left+114,top+106,
                Math.max(8,Math.min(29,(int)(58/Math.max(1.0,preview.getBbHeight())))),0,left+88,top+71,preview);
        }
        for(var s:slots){
            if(!s.item.isEmpty())g.item(s.item,left+s.x,top+s.y);
            if(mx>=left+s.x&&mx<left+s.x+16&&my>=top+s.y&&my<top+s.y+16){
                g.fill(left+s.x,top+s.y,left+s.x+16,top+s.y+16,0x55FFFFFF);
                g.setComponentTooltipForNextFrame(font,List.of(Component.literal(s.title)),mx,my);
            }
        }
        g.centeredText(font,n.placed?"NPC platziert · sichtbar während der Kamerafahrt":"Noch nicht platziert",width/2,top+189,0xFFFFFFFF);
        if(!error.isEmpty())g.centeredText(font,error,width/2,top+201,0xFFFF7777);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        if(!MpsqParticipation.allowed())return true;
        for(var s:slots)if(e.x()>=left+s.x&&e.x()<left+s.x+16&&e.y()>=top+s.y&&e.y()<top+s.y+16){
            try{s.action.accept(e.button());}catch(Exception ex){error=ex.getMessage();}return true;
        }
        return super.mouseClicked(e,twice);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}

    private static final class LinkedWumpus extends Screen {
        private final MpsqCinematicNpcScreen parent;private int page;
        LinkedWumpus(MpsqCinematicNpcScreen parent){super(Component.literal("Vorhandenen Wumpus verknüpfen"));this.parent=parent;}
        @Override protected void init(){
            var list=new ArrayList<JsonObject>();for(var e:MpsqAccessoryRenderer.npcsSnapshot()){var o=e.getAsJsonObject();if(o.has("id")&&o.has("asset_id")&&MpsqAccessoryRenderer.WUMPUS_ASSET_ID.equals(o.get("asset_id").getAsString()))list.add(o);}
            int rows=Math.max(1,(height-90)/24);page=Math.max(0,Math.min(page,Math.max(0,(list.size()-1)/rows)));
            for(int i=page*rows;i<Math.min(list.size(),(page+1)*rows);i++){var o=list.get(i);double x=coordinate(o,"x"),y=coordinate(o,"y"),z=coordinate(o,"z");
                addRenderableWidget(Button.builder(Component.literal("Wumpus · "+Math.round(x)+", "+Math.round(y)+", "+Math.round(z)),b->{
                    TeamVisibilitySettings.requirePlacement(x,y,z);parent.edit(()->{var n=parent.scene.npc;n.linkedNpc=o.get("id").getAsString();n.category="npc_model";n.type=MpsqAccessoryRenderer.WUMPUS_ASSET_ID;n.url=MpsqAccessoryRenderer.assetPreviewUrl(n.type,"");n.x=x;n.y=y;n.z=z;n.yaw=o.has("yaw")?MpsqCinematicScene.snapRotation(o.get("yaw").getAsFloat()):0;n.placed=true;Arrays.fill(n.route,null);n.actions.clear();});onClose();
                }).bounds(width/2-150,35+(i-page*rows)*24,300,20).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Verknüpfung lösen"),b->{parent.edit(()->parent.scene.npc.linkedNpc="");onClose();}).bounds(width/2-150,height-52,300,20).build());
            addRenderableWidget(Button.builder(Component.literal("Zurück"),b->onClose()).bounds(width/2-150,height-28,100,20).build());
            addRenderableWidget(Button.builder(Component.literal("<"),b->{page--;rebuildWidgets();}).bounds(width/2-45,height-28,90,20).build());
            addRenderableWidget(Button.builder(Component.literal(">"),b->{page++;rebuildWidgets();}).bounds(width/2+50,height-28,100,20).build());
        }
        private static double coordinate(JsonObject o,String axis){return o.has("world_"+axis)?o.get("world_"+axis).getAsDouble():o.get(axis).getAsDouble()+(axis.equals("y")?0:.5);}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
    }
    private static final class Types extends Screen {
        private final MpsqCinematicNpcScreen parent;
        private JsonArray assets=new JsonArray();
        private String category="mob",status="";
        private int page;
        private boolean requested;
        private record Choice(String label,String id,String url){}
        Types(MpsqCinematicNpcScreen parent){super(Component.literal("NPC-Typ auswählen"));this.parent=parent;}
        @Override protected void init(){
            if(!requested){
                requested=true;
                assets=MpsqLocalWorldStore.array("model_catalog");
                MpsqApiClient.get("/models/catalog").whenComplete((data,err)->minecraft.execute(()->{
                    if(err==null&&data.isJsonArray())assets=data.getAsJsonArray();
                    else status="Nur lokal verfügbare Modelle; Katalog nicht erreichbar.";
                    rebuild();
                }));
            }
            rebuild();
        }
        private void rebuild(){
            clearWidgets();
            String[] cats={"mob","npc_skin_normal","npc_model"};
            String[] names={"Mobs","Spieler-Skins","Custom Models"};
            for(int i=0;i<3;i++){final String cat=cats[i];addRenderableWidget(Button.builder(Component.literal(names[i]),b->{category=cat;page=0;rebuild();}).bounds(width/2-164+i*110,36,108,20).build());}
            List<Choice> choices=new ArrayList<>();
            if(category.equals("mob")){
                for(var type:BuiltInRegistries.ENTITY_TYPE)if(type.getCategory()!=MobCategory.MISC||Set.of("minecraft:villager","minecraft:wandering_trader","minecraft:iron_golem","minecraft:snow_golem").contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString())){
                    String id=BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();choices.add(new Choice(type.getDescription().getString(),id,""));
                }
            }else{
                for(var a:assets)if(a.isJsonObject()){
                    var o=a.getAsJsonObject();if(!(category.startsWith("npc_skin")?str(o,"category","").startsWith("npc_skin"):category.equals(str(o,"category",""))))continue;
                    String id=str(o,"id",str(o,"asset_id",""));
                    String url=MpsqAccessoryRenderer.assetPreviewUrl(id,str(o,"url",""));
                    if(!id.isBlank()&&choices.stream().noneMatch(v->v.id.equals(id)))choices.add(new Choice(str(o,"display_name",str(o,"name","NPC")),id,url));
                }
                if(category.equals("npc_model")&&choices.stream().noneMatch(v->v.id.equals(MpsqAccessoryRenderer.WUMPUS_ASSET_ID)))choices.add(new Choice("Wumpus",MpsqAccessoryRenderer.WUMPUS_ASSET_ID,MpsqAccessoryRenderer.assetPreviewUrl(MpsqAccessoryRenderer.WUMPUS_ASSET_ID,"")));
                if(category.startsWith("npc_skin"))choices.add(new Choice("Eigener Spieler-Skin","self",""));
            }
            choices.sort(Comparator.comparing(Choice::label));
            int perPage=Math.max(1,(height-125)/22);
            page=Math.max(0,Math.min(page,Math.max(0,(choices.size()-1)/perPage)));
            for(int i=page*perPage;i<Math.min(choices.size(),(page+1)*perPage);i++){
                var c=choices.get(i);
                addRenderableWidget(Button.builder(Component.literal(c.label),b->{
                    parent.edit(()->{var n=parent.scene.npc;n.category=category;for(var asset:assets)if(asset.isJsonObject()&&c.id.equals(str(asset.getAsJsonObject(),"id","")))n.category=str(asset.getAsJsonObject(),"category",category);n.type=c.id;n.url=c.url;n.linkedNpc="";n.actions.clear();});
                    onClose();
                }).bounds(width/2-150,64+(i-page*perPage)*22,300,20).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Zurück"),b->onClose()).bounds(width/2-150,height-30,94,20).build());
            addRenderableWidget(Button.builder(Component.literal("<"),b->{page--;rebuild();}).bounds(width/2-45,height-30,90,20).build());
            addRenderableWidget(Button.builder(Component.literal(">"),b->{page++;rebuild();}).bounds(width/2+56,height-30,94,20).build());
        }
        private static String str(JsonObject o,String k,String fallback){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():fallback;}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,18,0xFFFFFFFF);g.centeredText(font,status,width/2,height-44,0xFFFFCC88);}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
    }
}

