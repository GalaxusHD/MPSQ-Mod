package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

/** MPSQ-native gold/navy NPC editor; original purchased Scenes artwork, native client behavior. */
final class MpsqScenesScreen extends Screen {
    private final MpsqScenes.Target target;
    private final Screen parent;private final Consumer<MpsqScenes.Profile> persist;
    private int equipmentSlot;
    private String selectedClip="idle";private int actionIndex=-1;
    private final net.minecraft.client.multiplayer.ClientLevel world=net.minecraft.client.Minecraft.getInstance().level;
    private MpsqScenes.Profile profile;
    private String section="",status="";private int left,top,page;private float uiScale;
    private JsonArray skins=MpsqBuiltinNpcCatalog.catalog();
    private boolean requested;
    private final List<Cell> cells=new ArrayList<>();
    private final Map<Integer,String> mobIcons=new HashMap<>();
    private final Map<Integer,ItemStack> equipmentIcons=new HashMap<>();
    private final Map<Integer,String> skinUrls=new HashMap<>();
    private final Map<Integer,Preview> previews=new HashMap<>();
    private record Preview(String name,int width,int height){}
    private record Cell(int x,int y,Item item,String name,Consumer<Integer> click){}
    MpsqScenesScreen(MpsqScenes.Target target){this(target,null,null);}
    MpsqScenesScreen(MpsqScenes.Target target,Screen parent,Consumer<MpsqScenes.Profile> persist){super(Component.literal("NPC MENU"));this.target=target;this.parent=parent;this.persist=persist;profile=MpsqScenes.copy(target.profile());}
    static MpsqScenesScreen cinematic(Screen parent,MpsqCinematicScene scene,Consumer<Runnable> save){return new MpsqScenesScreen(MpsqScenes.fromScene(scene),parent,p->save.accept(()->{scene.npc.restoreFrom(p.npc);scene.npc.scenesClip=p.clip;scene.npc.scenesScale=p.scale;scene.npc.visibility=p.visibility;scene.npc.scenesTask=p.task;scene.npc.scenesMotionEnabled=p.enabled;scene.npc.scenesMotionLoop=p.loop;scene.npc.scenesDirection=p.direction;scene.npc.placed=!p.deleted;}));}
    private void change(Runnable change){
        ensure();var before=MpsqScenes.copy(profile);var edited=actionIndex>=0&&actionIndex<profile.npc.actions.size()?profile.npc.actions.get(actionIndex):null;
        try{change.run();profile.npc.scenesTask=profile.task;profile.validate();if(edited!=null)actionIndex=profile.npc.actions.indexOf(edited);if(persist==null)MpsqScenes.save(target,profile);else persist.accept(profile);status="Gespeichert";}catch(Exception e){profile.restoreFrom(before);throw e;}
        rebuildWidgets();
    }
    private void ensure(){if(minecraft.level!=world||!MpsqScenes.allowed())throw new IllegalStateException("Welt oder Bearbeitungsrechte haben sich geändert.");}
    private void action(Runnable r){try{ensure();r.run();}catch(Exception e){status=e.getMessage();}}
    private void tab(String value){section=value;page=0;rebuildWidgets();}
    private int px(float x){return left+Math.round(x*uiScale);}
    private int py(float y){return top+Math.round(y*uiScale);}
    private int size(float n){return Math.round(n*uiScale);}
    private static net.minecraft.resources.Identifier art(String name){return net.minecraft.resources.Identifier.fromNamespaceAndPath("mpsqcamera","textures/gui/scenes/"+name+".png");}
    private void icon(int x,int y,String image,String label,Runnable run){addRenderableWidget(new MpsqScenesWidget(px(x),py(y),size(18),size(18),label,image,()->action(run)));}
    private void button(int col,int row,String label,Runnable run){var widget=new MpsqScenesWidget(px(10+col*81),py(30+row*(section.equals("SETTINGS")?14:18)),size(80),size(16),label,null,()->action(run));if(section.equals("MOVE")||section.equals("COSMETIC"))widget.transparent();addRenderableWidget(widget);}
    private void cell(int index,Item icon,String label,Consumer<Integer> click){cells.add(new Cell(px(section.equals("PATH")?31+((index-27)%7)*18:11+(index%9)*18),py(section.equals("PATH")?66+((index-27)/7)*18:30+(index/9)*18),icon,label,click));}
    private void input(String label,String value,Consumer<String> save){minecraft.gui.setScreen(new Input(this,label,value,v->{action(()->save.accept(v));if(!status.equals("Gespeichert"))throw new IllegalArgumentException(status);}));}
    private static double number(String s,double min,double max){double n=Double.parseDouble(s.replace(',','.'));if(!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException("Erlaubter Bereich: "+min+" bis "+max);return n;}
    @Override protected void init(){
        uiScale=Math.min(2f,Math.min((width-16)/180f,(height-32)/144f));left=(width-size(180))/2;top=Math.max(4,(height-size(144)-16)/2);cells.clear();mobIcons.clear();equipmentIcons.clear();skinUrls.clear();previews.clear();
        if(!requested){requested=true;MpsqApiClient.get("/models/catalog").whenComplete((data,error)->minecraft.execute(()->{if(minecraft.level==world&&error==null&&data.isJsonArray()){skins=data.getAsJsonArray();if(minecraft.gui.screen()==this)rebuildWidgets();}}));}
        switch(section){case "LINK"->links();case "SKINS"->skins();case "PATH"->path();case "ACTIONS"->actions();case "SETTINGS"->settings();case "MOVE"->move();case "COSMETIC"->cosmetic();case "EQUIPMENT"->equipment();case "ARMOR"->armor();case "TIMELINE"->timeline();case "ACTION_EDIT"->actionEdit();default->main();}
        icon(section.equals("PATH")?6:120,122,"goback",section.isBlank()?"Schließen":"Zurück",this::onClose);
        if(section.equals("ACTIONS")||section.equals("SKINS")||section.equals("TIMELINE")||section.equals("LINK")){
            icon(138,122,"goback","Vorherige Seite",()->{page=Math.max(0,page-1);rebuildWidgets();});
            icon(156,122,"next","Nächste Seite",()->{page++;rebuildWidgets();});
        }else if(section.equals("PATH")){
            icon(80,122,"direction_mixed","Richtung wechseln",()->change(()->profile.direction=switch(profile.direction){case "FORWARD"->"REVERSE";case "REVERSE"->"BOTH";default->"FORWARD";}));
            icon(138,122,"add","Nächsten Wegpunkt setzen",()->{for(int i=0;i<profile.npc.route.length;i++)if(profile.npc.route[i]==null){MpsqScenes.capture(this,i);return;}status="Alle 21 Wegpunkte sind gesetzt.";});
        }else icon(156,122,"home","Hauptmenü",()->tab(""));
    }
    private void main(){
        icon(66,102,"home","Scenes-Übersicht",()->minecraft.gui.setScreen(new MpsqScenesHub(this)));
        icon(12,30,"skin1","Skins",()->tab("SKINS"));icon(102,30,"armor","Ausrüstung",()->tab("EQUIPMENT"));
        icon(12,48,"paths","Pfade",()->tab("PATH"));icon(102,48,"destination","Aktionen",()->tab("ACTIONS"));
        icon(12,66,"settings","Einstellungen",()->tab("SETTINGS"));icon(102,66,"copyyaw","Zu mir drehen",()->change(()->{
            var n=profile.npc;n.yaw=MpsqCinematicScene.snapRotation((float)Math.toDegrees(Math.atan2(-(minecraft.player.getX()-n.x),minecraft.player.getZ()-n.z)));n.look="FIXED";
        }));
        icon(12,84,"move","Verschieben / Größe",()->tab("MOVE"));icon(102,84,"delete","NPC löschen",()->confirm("NPC entfernen?","Die Platzierung wird entfernt.",()->{change(()->profile.deleted=true);minecraft.gui.setScreen(null);}));
        icon(12,102,"reset","Animation stoppen",()->change(()->{profile.clip="idle";profile.npc.actions.clear();}));
        icon(102,102,"paths","Aktionszeiten",()->timeline("idle"));
    }
    private void timeline(String clip){selectedClip=clip;tab("TIMELINE");}
    private void equipment(){icon(66,102,"hat","Animiertes Accessoire",()->tab("COSMETIC"));String[] labels={"Helm","Brustplatte","Hose","Schuhe","Haupthand","Nebenhand"};String[] icons={"hat","armor","left_pant","right_pant","left_arm","right_arm"};for(int i=0;i<6;i++){int slot=i;icon(12+(i%3)*54,30+(i/3)*36,icons[i],labels[i]+": "+profile.npc.equipment[i],()->{if(slot<4){equipmentSlot=slot;tab("ARMOR");return;}input(labels[slot]+" · Item-ID (minecraft:air = leeren)",profile.npc.equipment[slot],v->{var id=net.minecraft.resources.Identifier.tryParse(v.strip());if(id==null||!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id))throw new IllegalArgumentException("Unbekanntes Item");change(()->profile.npc.equipment[slot]=id.toString());});});}}
    private void cosmetic(){
        var n=profile.npc;
        button(0,0,n.cosmeticModel.isBlank()?"Accessoire auswählen":n.cosmeticModel,()->change(()->{if(!n.category.startsWith("npc_skin"))throw new IllegalArgumentException("Für Spieler-NPCs mit Scenes-Animation.");var choices=List.of("","nm_hat_budgie","nm_hat_hedgehog","nm_hat_wumpus","nm_hat_owl_long_eared","nm_hat_seagull");n.cosmeticModel=choices.get((choices.indexOf(n.cosmeticModel)+1)%choices.size());if(!n.cosmeticModel.isBlank()&&MpsqScenesClips.get(profile.clip)==null)profile.clip="scenes/scene_townsfolk_1/idle";}));
        button(1,0,"Körperteil: "+n.cosmeticBone,()->change(()->{var bones=List.of("h_head","body","left_arm","right_arm","left_leg","right_leg");n.cosmeticBone=bones.get((bones.indexOf(n.cosmeticBone)+1)%bones.size());}));
        button(0,1,"X: "+n.cosmeticX,()->input("Accessoire X (Modell-Pixel)",""+n.cosmeticX,v->change(()->n.cosmeticX=number(v,-32,32))));button(1,1,"Y: "+n.cosmeticY,()->input("Accessoire Y (Modell-Pixel)",""+n.cosmeticY,v->change(()->n.cosmeticY=number(v,-32,32))));
        button(0,2,"Z: "+n.cosmeticZ,()->input("Accessoire Z (Modell-Pixel)",""+n.cosmeticZ,v->change(()->n.cosmeticZ=number(v,-32,32))));button(1,2,"Größe: "+n.cosmeticScale,()->input("Accessoire Größe", ""+n.cosmeticScale,v->change(()->n.cosmeticScale=(float)number(v,.1,3))));
        button(0,3,"Drehen X: "+n.cosmeticRX,()->input("Drehen X (Grad)",""+n.cosmeticRX,v->change(()->n.cosmeticRX=(float)number(v,-360,360))));button(1,3,"Drehen Y: "+n.cosmeticRY,()->input("Drehen Y (Grad)",""+n.cosmeticRY,v->change(()->n.cosmeticRY=(float)number(v,-360,360))));
        button(0,4,"Drehen Z: "+n.cosmeticRZ,()->input("Drehen Z (Grad)",""+n.cosmeticRZ,v->change(()->n.cosmeticRZ=(float)number(v,-360,360))));button(1,4,"Ablegen",()->change(()->n.cosmeticModel=""));
    }
    private void armor(){
        String part=new String[]{"helmet","chestplate","leggings","boots"}[equipmentSlot];
        var choices=new ArrayList<String>();choices.add("minecraft:air");
        for(String material:List.of("leather","chainmail","iron","golden","diamond","netherite"))choices.add("minecraft:"+material+"_"+part);
        if(equipmentSlot==0){choices.add("minecraft:turtle_helmet");for(String hat:List.of("budgie","hedgehog","wumpus","owl_long_eared","seagull"))choices.add("mpsqcamera:nm_hat_"+hat);}
        for(int i=0;i<choices.size();i++){String id=choices.get(i);var stack=MpsqCinematicNpcScreen.stack(id);cell(i,stack.getItem(),id.equals("minecraft:air")?"Ablegen":id,b->change(()->profile.npc.equipment[equipmentSlot]=id));equipmentIcons.put(i,stack);}
    }
    private void timeline(){
        icon(12,102,"add","Aktion hinzufügen: "+selectedClip,()->{actionIndex=-1;tab("ACTION_EDIT");});
        int count=profile.npc.actions.size();page=Math.max(0,Math.min(page,Math.max(0,(count-1)/27)));
        for(int i=page*27;i<Math.min(count,(page+1)*27);i++){int index=i;var a=profile.npc.actions.get(i);cell(i-page*27,Items.AIR,a.time+" s · "+a.clip+" · "+a.duration+" s",b->{actionIndex=index;selectedClip=a.clip;tab("ACTION_EDIT");});}
    }
    private double draftStart=0,draftDuration=2;private String draftLook="DEFAULT";
    private void actionEdit(){
        if(actionIndex>=0){var a=profile.npc.actions.get(actionIndex);draftStart=a.time;draftDuration=a.duration;draftLook=a.look;}
        button(0,0,"Start: "+draftStart+" s",()->input("Startzeit in Sekunden",""+draftStart,v->{double n=number(v,0,86400);if(actionIndex>=0)change(()->profile.npc.actions.get(actionIndex).time=n);else{draftStart=n;status="Gespeichert";}}));
        if(!selectedClip.equals(MpsqSeagullDrops.ACTION))button(1,0,"Dauer: "+draftDuration+" s",()->input("Dauer in Sekunden",""+draftDuration,v->{double n=number(v,.1,3600);if(actionIndex>=0)change(()->profile.npc.actions.get(actionIndex).duration=n);else{draftDuration=n;status="Gespeichert";}}));
        if(!selectedClip.equals(MpsqSeagullDrops.ACTION))button(0,2,"Blick: "+draftLook,()->{var modes=List.of("DEFAULT","FIXED","PLAYER","CAMERA","PLAYER_HEAD","CAMERA_HEAD");String look=modes.get((modes.indexOf(draftLook)+1)%modes.size());if(actionIndex>=0)change(()->profile.npc.actions.get(actionIndex).look=look);else{draftLook=look;rebuildWidgets();}});
        icon(12,102,"spawn_on",actionIndex<0?"Aktion speichern":"Fertig",()->{if(actionIndex<0)change(()->{var a=new MpsqCinematicScene.NpcAction();a.clip=selectedClip;a.time=draftStart;a.duration=draftDuration;a.look=draftLook;profile.npc.actions.add(a);});tab("TIMELINE");});
        if(actionIndex>=0)icon(66,102,"delete","Aktion löschen",()->confirm("Aktion löschen?",selectedClip,()->{change(()->profile.npc.actions.remove(actionIndex));tab("TIMELINE");}));
    }
    private void skins(){
        record Choice(String title,String type,String url,String category){}
        var list=new ArrayList<Choice>();list.add(new Choice("Eigener Spielerskin","self","self","npc_skin_normal"));
        var seen=new HashSet<String>();seen.add("self");var orderedSkins=MpsqBuiltinNpcCatalog.catalog();for(var entry:skins)orderedSkins.add(entry);
        for(String group:List.of("npc_model","npc_skin")){
            if(group.equals("npc_model")){list.add(new Choice("Wumpus",MpsqAccessoryRenderer.WUMPUS_ASSET_ID,MpsqAccessoryRenderer.assetPreviewUrl(MpsqAccessoryRenderer.WUMPUS_ASSET_ID,""),"npc_model"));list.add(new Choice("Möwe","nm_seagull","builtin://mpsq/nm_seagull","npc_model"));seen.add(MpsqAccessoryRenderer.WUMPUS_ASSET_ID);seen.add("nm_seagull");}
            for(var e:orderedSkins)if(e.isJsonObject()){var o=e.getAsJsonObject();String category=MpsqScenes.str(o,"category","");String id=MpsqScenes.str(o,"id","");if(category.startsWith(group)&&!id.equals("mpsq_tutorial")&&seen.add(id))list.add(new Choice(MpsqScenes.str(o,"display_name",id),id,MpsqScenes.str(o,"url",""),category));}
        }
        var mobs=new ArrayList<net.minecraft.world.entity.EntityType<?>>();for(var type:net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE)if(type.getCategory()!=net.minecraft.world.entity.MobCategory.MISC)mobs.add(type);
        mobs.sort(Comparator.comparing(t->net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(t).toString()));
        for(var type:mobs){String id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();list.add(new Choice(type.getDescription().getString(),id,"","mob"));}
        page=Math.max(0,Math.min(page,(list.size()-1)/36));
        for(int i=page*36;i<Math.min(list.size(),(page+1)*36);i++){var choice=list.get(i);int index=i-page*36;
            if(choice.category.startsWith("npc_skin")){skinUrls.put(index,choice.url);if(!choice.type.equals("self"))MpsqAccessoryRenderer.prepareCinematicSkin(choice.url,false);}
            if(choice.type.equals(MpsqAccessoryRenderer.WUMPUS_ASSET_ID)||choice.type.equals("nm_seagull"))equipmentIcons.put(index,MpsqCinematicNpcScreen.stack("mpsqcamera:nm_head_"+(choice.type.equals("nm_seagull")?"seagull":"wumpus")));
            if(choice.category.equals("mob"))mobIcons.put(index,choice.type);
            cell(index,Items.AIR,choice.title,b->change(()->{profile.npc.type=choice.type;profile.npc.url=choice.url;profile.npc.category=choice.category;profile.clip="idle";profile.npc.actions.clear();}));
        }
    }
    private void actions(){
        var ids=MpsqCinematicActors.clips(profile.npc);page=Math.max(0,Math.min(page,Math.max(0,(ids.size()-1)/36)));
        for(int i=page*36;i<Math.min(ids.size(),(page+1)*36);i++){String id=ids.get(i);var clip=MpsqScenesClips.get(id);int index=i-page*36;if(clip!=null)previews.put(index,preview(clip));String label=clip!=null?clip.label():switch(id){case "seagull_drop"->"Abwerfen";case "idle"->"Idle";case "lie"->"Liegen";case "swim"->"Schwimmen";case "jump"->"Springen (jump)";case "walk"->profile.npc.type.equals("nm_seagull")?"Fliegen":"Gehen";default->id;};
            cell(index,Items.AIR,label+(id.equals(MpsqSeagullDrops.ACTION)?"\nLinksklick: einmal abwerfen · Rechtsklick: Zeitplan":"\nLinksklick: dauerhaft · Rechtsklick: Zeitplan"),b->{if(b==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT)timeline(id);else if(id.equals(MpsqSeagullDrops.ACTION))MpsqSeagullDrops.drop(profile.npc,MpsqScenes.pose(profile),profile.scale);else change(()->profile.clip=id);});}
    }
    private static Preview preview(MpsqScenesClips.Clip clip){return switch(clip.id()){
        case "scenes/scene_townsfolk_1/idle" -> new Preview("clips/townsfolk1",64,64);
        case "scenes/scene_townsfolk_1/see" -> new Preview("clips/townsfolk1",64,64);
        case "scenes/scene_townsfolk_1/interact" -> new Preview("clips/townsfolk1",64,64);
        case "scenes/scene_townsfolk_2/idle" -> new Preview("clips/townsfolk2",64,64);
        case "scenes/scene_townsfolk_2/see" -> new Preview("clips/townsfolk2",64,64);
        case "scenes/scene_townsfolk_2/interact" -> new Preview("clips/townsfolk2",64,64);
        case "scenes/scene_townsfolk_3/idle" -> new Preview("clips/townsfolk3",64,64);
        case "scenes/scene_townsfolk_3/interact" -> new Preview("clips/townsfolk3",64,64);
        case "scenes/scene_townsfolk_4/idle" -> new Preview("clips/townsfolk4",64,64);
        case "scenes/scene_townsfolk_4/wakeup" -> new Preview("clips/townsfolk4",64,64);
        case "scenes/scene_townsfolk_5_pose_1/idle" -> new Preview("clips/townsfolk5_1",64,64);
        case "scenes/scene_townsfolk_5_pose_2/idle" -> new Preview("clips/townsfolk5_2",64,64);
        case "scenes/scene_townsfolk_5_pose_3/idle" -> new Preview("clips/townsfolk5_3",64,64);
        case "scenes/scene_townsfolk_5_pose_4/idle" -> new Preview("clips/townsfolk5_4",64,64);
        case "scenes/scene_townsfolk_5_pose_5/idle" -> new Preview("clips/townsfolk5_5",64,64);
        case "scenes/scene_townsfolk_5_pose_6/idle" -> new Preview("clips/townsfolk5_6",64,64);
        case "scenes/scene_townsfolk_5_pose_7/idle" -> new Preview("clips/townsfolk5_7",64,64);
        case "scenes/scene_townsfolk_vol5/dance1" -> new Preview("clips/townsfolk_vol5_dance_1",480,480);
        case "scenes/scene_townsfolk_vol5/dance2" -> new Preview("clips/townsfolk_vol5_dance_2",524,524);
        case "scenes/scene_townsfolk_vol5/dance3" -> new Preview("clips/townsfolk_vol5_dance_3",480,480);
        case "scenes/scene_townsfolk_vol5/dance4" -> new Preview("clips/townsfolk_vol5_dance_4",464,464);
        case "scenes/scene_townsfolk_vol5/dance5" -> new Preview("clips/townsfolk_vol5_dance_5",465,465);
        case "scenes/scene_townsfolk_vol5/dance6" -> new Preview("clips/townsfolk_vol5_dance_6",474,474);
        case "scenes/scene_townsfolk_vol5/dance7" -> new Preview("clips/townsfolk_vol5_dance_7",460,460);
        case "scenes/scene_townsfolk_vol5/dance8" -> new Preview("clips/townsfolk_vol5_dance_8",457,457);
        case "scenes/scene_townsfolk_vol5/dance9" -> new Preview("clips/townsfolk_vol5_dance_9",481,481);
        case "scenes/scene_townsfolk_vol5/dance10" -> new Preview("clips/townsfolk_vol5_dance_10",475,475);
        case "scenes/scene_townsfolk_vol5/dance11" -> new Preview("clips/townsfolk_vol5_dance_11",501,501);
        case "scenes/scene_townsfolk_vol5/cry1" -> new Preview("clips/townsfolk_vol5_cry_1",342,342);
        case "scenes/scene_townsfolk_vol5/cry2" -> new Preview("clips/townsfolk_vol5_cry_2",355,355);
        case "scenes/scene_townsfolk_vol5/fence_sit_1" -> new Preview("clips/townsfolk_vol5_fence_sit_1",521,521);
        case "scenes/scene_townsfolk_vol5/fence_sit_2" -> new Preview("clips/townsfolk_vol5_fence_sit_2",519,519);
        case "scenes/scene_townsfolk_vol5/sleep1" -> new Preview("clips/townsfolk_vol5_sleep_1",430,430);
        case "scenes/scene_townsfolk_vol5/sleep2" -> new Preview("clips/townsfolk_vol5_sleep_2",415,415);
        case "scenes/scene_townsfolk_vol5/sleep3" -> new Preview("clips/townsfolk_vol5_sleep_3",451,451);
        case "scenes/scene_townsfolk_vol5/eat_sandwich_stand" -> new Preview("clips/townsfolk_vol5_eat_sandwich",552,552);
        case "scenes/scene_townsfolk_vol5/eat_soup_stand" -> new Preview("clips/townsfolk_vol5_eat_soup",638,638);
        case "scenes/scene_townsfolk_vol5/eat_marshmallow_sit" -> new Preview("clips/townsfolk_vol5_eat_marshmallow_sit",563,563);
        default -> null;
    };}
    private void path(){
        if(!profile.npc.recording.isEmpty())icon(120,48,"reset","Aufnahme entfernen (normalen Pfad verwenden)",()->change(()->{profile.npc.recording.clear();profile.npc.recordingId=java.util.UUID.randomUUID().toString();}));
        icon(84,48,"add","Bewegung aufnehmen (3 Sekunden Countdown)",()->MpsqMotionCapture.start(this,frames->change(()->{profile.npc.recording=frames;profile.npc.recordingId=java.util.UUID.randomUUID().toString();profile.npc.movementStart=0;profile.sharedPath="";profile.enabled=true;})));
        icon(48,48,"reset","Start/Ende verbinden: "+(profile.npc.closedRoute?"Ja":"Nein"),()->change(()->profile.npc.closedRoute=!profile.npc.closedRoute));
        icon(138,30,"paths","Gemeinsamen Pfad zuweisen",()->{var paths=MpsqSharedPaths.all();String names=String.join(", ",paths.stream().map(p->p.name).toList());input("Pfadname (leer = eigener Pfad): "+names,profile.sharedPath.isBlank()?"":java.util.Optional.ofNullable(MpsqSharedPaths.get(profile.sharedPath)).map(p->p.name).orElse(""),value->change(()->{profile.sharedPath=value.isBlank()?"":paths.stream().filter(p->p.name.equalsIgnoreCase(value.strip())).findFirst().orElseThrow(()->new IllegalArgumentException("Unbekannter Pfad")).id;}));});
        icon(30,30,profile.enabled?"spawn_on":"spawn_off","Pfad: "+(profile.enabled?"An":"Aus"),()->change(()->profile.enabled=!profile.enabled));
        icon(66,30,"reset","Wiederholen: "+(profile.loop?"Ja":"Nein"),()->change(()->profile.loop=!profile.loop));
        icon(102,30,"settings","Startzeit: "+profile.npc.movementStart+" s",()->input("Startzeit (Sekunden)",""+profile.npc.movementStart,s->change(()->profile.npc.movementStart=number(s,0,86400))));
        for(int i=0;i<profile.npc.route.length;i++){
            int index=i;var p=profile.npc.route[i];boolean enabled=i==0||profile.npc.route[i-1]!=null;
            String title=(i==0?"Start":i==20?"Ende":"Punkt "+i)+(p==null?" +":" ✓");
            cell(27+i,p==null?enabled?Items.ENDER_PEARL:Items.BARRIER:Items.EMERALD,title+"\nLinksklick: setzen / Dauer\nRechtsklick: löschen",b->{if(!enabled)return;
                if(b==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT&&p!=null)confirm("Wegpunkt löschen?","Nachfolgende Punkte werden ebenfalls entfernt.",()->change(()->{for(int j=index;j<profile.npc.route.length;j++)profile.npc.route[j]=null;}));
                else if(p==null)MpsqScenes.capture(this,index);
                else if(index>0)input("Streckendauer (Sekunden)",""+p.duration,s->change(()->profile.npc.route[index].duration=number(s,.1,3600)));
                else MpsqScenes.capture(this,index);
            });
        }
    }
    void capturePosition(int index){ensure();var p=minecraft.player;TeamVisibilitySettings.requirePlacement(p.getX(),p.getY(),p.getZ());change(()->{if(index<0){profile.npc.x=p.getX();profile.npc.y=p.getY();profile.npc.z=p.getZ();}else profile.npc.route[index]=new MpsqCinematicScene.Waypoint(p.getX(),p.getY(),p.getZ(),3);});}
    private void links(){
        var rows=MpsqAccessoryRenderer.npcsSnapshot();page=Math.max(0,Math.min(page,Math.max(0,(rows.size()-1)/36)));
        for(int i=page*36;i<Math.min(rows.size(),(page+1)*36);i++){var row=rows.get(i).getAsJsonObject();cell(i-page*36,Items.AIR,MpsqScenes.str(row,"display_name",MpsqScenes.str(row,"name","NPC")),b->{var original=MpsqScenes.fromNpc(row).profile();change(()->{profile.npc.restoreFrom(MpsqScenes.copy(original).npc);profile.npc.linkedNpc=MpsqScenes.str(row,"id","");profile.npc.placed=true;profile.scale=original.scale;});tab("");});}
    }
    private void settings(){
        if(target.sceneId()!=null)button(0,1,"Vorhandenen NPC wählen",()->tab("LINK"));
        if(target.sceneId()!=null)button(1,1,"Verknüpfung lösen",()->change(()->profile.npc.linkedNpc=""));
        button(1,2,"Editiermodus: "+(MpsqScenes.showPaths?"An":"Aus"),()->{MpsqScenes.showPaths=!MpsqScenes.showPaths;rebuildWidgets();});
        button(0,0,"Name",()->input("NPC-Name",profile.npc.name,s->change(()->{if(s.length()>64)throw new IllegalArgumentException("Name: 0–64 Zeichen");profile.npc.name=s;})));
        button(1,0,"Blick: "+profile.npc.look,()->change(()->{var modes=List.of("FIXED","PLAYER","PLAYER_HEAD","CAMERA","CAMERA_HEAD");profile.npc.look=modes.get((modes.indexOf(profile.npc.look)+1)%modes.size());}));
        if(target.sceneId()==null){
            button(0,1,"Dialog beim Anklicken",()->input("Dialogtext",profile.dialogue,s->change(()->profile.dialogue=s)));
            button(1,1,"Sound beim Anklicken",()->input("Minecraft-/MPSQ-Sound-ID",profile.sound,s->change(()->profile.sound=s)));
            button(0,2,"Spielerbefehl bei Klick",()->input("Befehl als anklickender Spieler",profile.command,s->change(()->profile.command=s.strip())));
        }
        button(0,5,"Glow: "+(MpsqNpcGlow.enabled(target.key(),profile.npc)?"An":"Aus"),()->{boolean enable=!MpsqNpcGlow.enabled(target.key(),profile.npc);change(()->profile.npc.glow=enable);if(enable)MpsqNpcGlow.rearm(target.key());rebuildWidgets();});
        button(1,5,"Rangtag: "+(MpsqNpcGlow.tag(profile.npc).isBlank()?"Keiner":MpsqNpcGlow.tag(profile.npc)),()->{if(!MpsqNpcGlow.lockedTag(profile.npc).isBlank()){status="Der Tag ist durch die Aufgabe festgelegt.";return;}var tags=new ArrayList<String>();tags.add("");for(var rank:TeamRank.values())tags.add(rank.id());tags.addAll(List.of("discord","quests","wiki","accessories"));change(()->profile.npc.rankTag=tags.get((tags.indexOf(profile.npc.rankTag)+1)%tags.size()));});
        button(0,4,"Sichtbarkeit: "+(profile.visibility.equals("ALL")?"Alle":"Kamerafahrt"),()->change(()->profile.visibility=profile.visibility.equals("ALL")?"CINEMATIC":"ALL"));
        button(1,4,"Aufgabe: "+profile.task,()->change(()->{var tasks=List.of("none","tutorial","quest","accessories");profile.task=tasks.get((tasks.indexOf(profile.task)+1)%tasks.size());}));
        button(0,3,"Kopf −15°",()->change(()->profile.npc.headYaw=Math.max(-75,profile.npc.headYaw-15)));
        button(1,3,"Kopf +15°",()->change(()->profile.npc.headYaw=Math.min(75,profile.npc.headYaw+15)));
    }
    private void move(){
        icon(66,122,"move","Mausrad-Werkzeug",()->MpsqScenesTool.beginMove(new MpsqScenes.Target(target.key(),target.sceneId(),target.row(),profile),new MpsqScenesHub(parent)));
        button(0,0,"Position aufnehmen",()->MpsqScenes.capture(this,-1));
        button(1,0,"Größe: "+profile.scale,()->input("Größe (0,25 bis 3)",""+profile.scale,s->change(()->profile.scale=(float)number(s,.25,3))));
        button(0,1,"Drehen −15°",()->change(()->profile.npc.yaw=MpsqCinematicScene.snapRotation(profile.npc.yaw)-15));
        button(1,1,"Drehen +15°",()->change(()->profile.npc.yaw=MpsqCinematicScene.snapRotation(profile.npc.yaw)+15));
        for(int i=0;i<3;i++){int axis=i;button(0,2+i,"XYZ".charAt(i)+" −0,1",()->offset(axis,-.1));button(1,2+i,"XYZ".charAt(i)+" +0,1",()->offset(axis,.1));}
    }
    private void offset(int axis,double amount){change(()->{if(axis==0)profile.npc.x+=amount;else if(axis==1)profile.npc.y+=amount;else profile.npc.z+=amount;});}
    private void confirm(String title,String description,Runnable yes){minecraft.gui.setScreen(new Screen(Component.literal(title)){
        @Override protected void init(){addRenderableWidget(new MpsqScenesWidget(width/2-120,height/2+20,115,20,"Löschen",null,()->{minecraft.gui.setScreen(MpsqScenesScreen.this);action(yes);}));addRenderableWidget(new MpsqScenesWidget(width/2+5,height/2+20,115,20,"Abbrechen",null,this::onClose));}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,height/2-25,0xFFFFFFFF);g.centeredText(font,description,width/2,height/2-5,0xFFFFFFFF);}
        @Override public void onClose(){minecraft.gui.setScreen(MpsqScenesScreen.this);}
        @Override public boolean isPauseScreen(){return false;}
    });}
    @Override public void tick(){if(minecraft.level!=world||!MpsqScenes.allowed())minecraft.gui.setScreen(null);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){
        String frame=switch(section){case "SKINS"->"scenes_skins";case "PATH"->"scenes_path_main";case "ACTIONS","TIMELINE","ACTION_EDIT"->"scenes_menu";case ""->"scenes_menu";default->"scenes_edit";};
        // Crop the unused player-inventory decoration, retaining the complete menu frame.
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,art(frame),left,top,32,14,size(180),size(144),180,144,256,256,(section.equals("MOVE")||section.equals("COSMETIC"))?0x40FFFFFF:0xFFFFFFFF);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){
        super.extractRenderState(g,x,y,d);
        for(int i=0;i<cells.size();i++){
            var c=cells.get(i);if(x>=c.x&&x<c.x+size(18)&&y>=c.y&&y<c.y+size(18))g.fill(c.x,c.y,c.x+size(18),c.y+size(18),0x40FFFFFF);
            String url=skinUrls.get(i);net.minecraft.resources.Identifier texture=null;
            if("self".equals(url)&&minecraft.player!=null)texture=minecraft.player.getSkin().body().texturePath();
            else if(url!=null){var skin=MpsqNpcSkinRenderer.get(url,false);if(skin!=null)texture=skin.texture();}
            var preview=previews.get(i);
            if(mobIcons.containsKey(i)){if(MpsqMobIcons.drawSpawnEgg(g,mobIcons.get(i),c.x,c.y,size(18)))continue;var key=net.minecraft.resources.Identifier.tryParse(mobIcons.get(i)+"_spawn_egg");var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(key);drawItem(g,new ItemStack(item),c.x,c.y);}
            else if(equipmentIcons.containsKey(i)){drawItem(g,equipmentIcons.get(i),c.x,c.y);}
            else if(preview!=null){
                float fit=(float)size(18)/Math.max(preview.width,preview.height);int w=Math.round(preview.width*fit),h=Math.round(preview.height*fit);
                MpsqCenteredIcon.draw(g,art(preview.name),c.x+size(1),c.y+size(1),size(16));
            }
            else if(texture==null)g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,art(section.equals("SKINS")?"skin1":section.equals("PATH")?(c.item==Items.BARRIER?"spawn_off":c.item==Items.EMERALD?"spawn_on":"add"):"paths"),c.x+size(1),c.y+size(1),0,0,size(16),size(16),16,16,16,16);
            else{
                g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,texture,c.x+size(1),c.y+size(1),8,8,size(16),size(16),8,8,64,64);
                g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,texture,c.x+size(1),c.y+size(1),40,8,size(16),size(16),8,8,64,64);
            }
        }
        g.text(font,font.plainSubstrByWidth(status,size(180)),left,py(146),0xFFFFFFAA);
        for(var c:cells)if(x>=c.x&&x<c.x+size(18)&&y>=c.y&&y<c.y+size(18))g.setTooltipForNextFrame(font,Arrays.stream(c.name.split("\n")).map(Component::literal).map(Component::getVisualOrderText).toList(),x,y);
    }
    private void drawItem(GuiGraphicsExtractor g,ItemStack stack,int x,int y){g.pose().pushMatrix();g.pose().translate(x+uiScale,y+uiScale);g.pose().scale(uiScale);g.item(stack,0,0);g.pose().popMatrix();}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubled){if(e.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT||e.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT)for(var c:cells)if(e.x()>=c.x&&e.x()<c.x+size(18)&&e.y()>=c.y&&e.y()<c.y+size(18)){action(()->c.click.accept(e.button()));return true;}return super.mouseClicked(e,doubled);}
    @Override public void onClose(){if(section.isBlank())minecraft.gui.setScreen(parent);else tab("");}
    @Override public boolean isPauseScreen(){return false;}
    private static final class Input extends Screen {
        private final MpsqScenesScreen parent;private final String initial;private final Consumer<String> save;private EditBox field;private String error="";
        Input(MpsqScenesScreen parent,String title,String initial,Consumer<String> save){super(Component.literal(title));this.parent=parent;this.initial=initial;this.save=save;}
        @Override protected void init(){field=addRenderableWidget(new EditBox(font,width/2-120,height/2-10,240,20,title));field.setMaxLength(256);field.setValue(initial);setInitialFocus(field);
            addRenderableWidget(new MpsqScenesWidget(width/2-120,height/2+20,115,20,"Speichern",null,()->{try{save.accept(field.getValue());onClose();}catch(Exception e){error=e.getMessage();}}));
            addRenderableWidget(new MpsqScenesWidget(width/2+5,height/2+20,115,20,"Abbrechen",null,this::onClose));}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,height/2-35,0xFFFFFFFF);g.centeredText(font,error,width/2,height/2+48,0xFFFF7777);}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
    }
}



