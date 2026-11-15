package de.galaxushd.mpsqcamera;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class MpsqActionSetupScreen extends Screen {
    private final BlockPos pos;
    private final String block, server, world;
    private final String blockName, properties;
    private final MpsqTriggerBlockPolicy.Kind blockKind;
    private EditBox value, duration;
    private EditBox heightInput;
    private Button linkScreenButton;
    private Button barColorButton;
    private Button systemButton;
    private Button playlistButton,timerButton;private int timerMode;
    private static final String[] BAR_COLORS={"purple","pink","red"};
    private static final String[] BAR_COLOR_LABELS={"Violett","Pink","Rot"};
    private static final String[] QUICK_ACTIONS={"TOGGLE_AUDIO","START_PLAYLIST","TOGGLE_COUNTDOWN","TOGGLE_BOSSBAR","SWITCH_SYSTEM","RLGL_GREEN"};
    private static final String[] BLOCK_ACTIONS={"TOGGLE_AUDIO","START_PLAYLIST","TOGGLE_COUNTDOWN","TOGGLE_BOSSBAR","SHOW_DIALOGUE","OPEN_LINK","START_CINEMATIC","SWITCH_SYSTEM","RLGL_GREEN","SKY_SQUID_START"};
    private final String[] actions;
    private List<LocalScreenStore.LocalScreenData> linkScreens=List.of();
    private List<PlaylistOption> playlists=List.of();
    private int action, linkScreenIndex, barColor, systemIndex;
    private int playlistIndex;
    private boolean linkScreensLoading,playlistsLoading;
    private String status="";
    private java.util.UUID virtualScreen;
    private java.util.function.BiPredicate<String,JsonObject> virtualSave;
    public void virtualButton(java.util.UUID screen,java.util.function.BiPredicate<String,JsonObject> save){virtualScreen=screen;virtualSave=save;}

    public MpsqActionSetupScreen(BlockPos pos,String block,String blockName,MpsqTriggerBlockPolicy.Kind blockKind,String properties) {
        super(Component.literal("MPSQ-Redstone einrichten"));
        this.pos=pos.immutable(); this.block=block; this.blockName=blockName; this.blockKind=blockKind; this.properties=properties;
        this.actions=block.isEmpty()?QUICK_ACTIONS:BLOCK_ACTIONS;
        server=MpsqActionSync.server(); world=MpsqActionSync.world();
    }
    public MpsqActionSetupScreen(BlockPos pos,String block) { this(pos,block,block,MpsqTriggerBlockPolicy.Kind.NONE,""); }
    public MpsqActionSetupScreen() { this(BlockPos.ZERO, "","",MpsqTriggerBlockPolicy.Kind.NONE,""); }

    @Override protected void init() {
        int x=width/2-130,y=35;
        linkScreens=virtualScreen!=null?LocalScreenStore.findById(virtualScreen).stream().toList():LocalScreenStore.getAllScreens().stream()
                .filter(s->s.inputType()==LocalScreenStore.ScreenInputType.MPSQ_REDSTONE).toList();
        addRenderableWidget(Button.builder(Component.literal(actionLabel(actions[action])),b->{
            action=(action+1)%actions.length; b.setMessage(Component.literal(actionLabel(actions[action]))); updateVisibility();
        }).bounds(x,y,260,20).build());
        value=addRenderableWidget(new EditBox(font,x,y+23,260,20,Component.literal("Text, Titel, Sound-ID oder URL")));
        value.setMaxLength(3072);
        systemButton=addRenderableWidget(Button.builder(Component.literal(systemLabel()),b->{
            minecraft.gui.setScreen(new MpsqGamePickerScreen(this, index -> systemIndex=index));
        }).bounds(x,y+23,260,20).build());
        playlistButton=addRenderableWidget(Button.builder(Component.literal(playlistLabel()),b->{
            if(!playlists.isEmpty()){playlistIndex=(playlistIndex+1)%playlists.size();b.setMessage(Component.literal(playlistLabel()));}
        }).bounds(x,y+49,260,20).build());
        timerButton=addRenderableWidget(Button.builder(Component.literal(timerLabel()),b->{timerMode=(timerMode+1)%4;timerButton.setMessage(Component.literal(timerLabel()));updateVisibility();}).bounds(x,y+49,260,20).build());
        barColorButton=addRenderableWidget(Button.builder(Component.literal(BAR_COLOR_LABELS[barColor]),b->{
            barColor=(barColor+1)%BAR_COLORS.length;b.setMessage(Component.literal(BAR_COLOR_LABELS[barColor]));
        }).bounds(x,y+75,260,20).build());
        duration=addRenderableWidget(new EditBox(font,x,y+101,260,20,Component.literal("Sekunden")));
        duration.setValue("30");
        heightInput=addRenderableWidget(new EditBox(font,x,y+49,260,20,Component.literal("Ausscheidungs-Höhe Y")));
        heightInput.setHint(Component.literal("Y-Grenze, z. B. -60"));
        linkScreenButton=addRenderableWidget(Button.builder(Component.literal(screenLabel()),b->{
            if(!linkScreens.isEmpty()){linkScreenIndex=(linkScreenIndex+1)%linkScreens.size();b.setMessage(Component.literal(screenLabel()));}
        }).bounds(x,y+101,260,20).build());
        addRenderableWidget(Button.builder(Component.literal(block.isEmpty()?"Auslösen":"Speichern"),b->save()).bounds(x,y+133,125,20).build());
        addRenderableWidget(Button.builder(Component.literal("Abbrechen"),b->onClose()).bounds(x+135,y+133,125,20).build());
        updateVisibility();
        if (MpsqActionSync.isMpsqServer()) {
            loadPlaylists();
            linkScreensLoading=true;
            linkScreenButton.setMessage(Component.literal(screenLabel()));
            updateVisibility();
            ScreenSyncManager.refresh().whenComplete((ignored,error)->minecraft.execute(()->{
                linkScreensLoading=false;
                if (error==null) {
                    linkScreens=LocalScreenStore.getAllScreens().stream()
                            .filter(s->s.inputType()==LocalScreenStore.ScreenInputType.MPSQ_REDSTONE).toList();
                    linkScreenIndex=linkScreens.isEmpty()?0:Math.floorMod(linkScreenIndex,linkScreens.size());
                } else {
                    status="MPSQ-Redstone-Bildschirme konnten nicht geladen werden.";
                }
                linkScreenButton.setMessage(Component.literal(screenLabel()));
                updateVisibility();
            }));
        }
    }

    private String timerLabel(){return switch(timerMode){case 1->"ModularTimer starten";case 2->"ModularTimer stoppen";case 3->"ModularTimer umschalten";default->"Countdown-Bossbar";};}
    private String screenLabel(){
        if(linkScreensLoading)return "MPSQ-Redstone-Bildschirme werden geladen …";
        if(linkScreens.isEmpty())return "Kein MPSQ-Redstone-Bildschirm geladen";
        return "Bildschirm: "+linkScreens.get(Math.floorMod(linkScreenIndex,linkScreens.size())).name();
    }
    private void updateVisibility(){
        String selected=actions[action];
        boolean audio="TOGGLE_AUDIO".equals(selected), countdown="TOGGLE_COUNTDOWN".equals(selected), link="OPEN_LINK".equals(selected);
        boolean bossbar="TOGGLE_BOSSBAR".equals(selected);
        boolean switchSystem="SWITCH_SYSTEM".equals(selected);
        boolean playlist="START_PLAYLIST".equals(selected);
        boolean rlglGreen="RLGL_GREEN".equals(selected);
        List<MpsqSystemController.SystemOption> systems=MpsqSystemController.availableSystems();
        if(!systems.isEmpty())systemIndex=Math.floorMod(systemIndex,systems.size());else systemIndex=0;
        systemButton.setMessage(Component.literal(systemLabel()));
        systemButton.visible=switchSystem;systemButton.active=switchSystem&&!systems.isEmpty();
        String systemId=systems.isEmpty()?"":systems.get(systemIndex).id();
        heightInput.visible=switchSystem&&(systemId.equals("glass_bridge")||systemId.equals("rope_jump")||systemId.equals("sky_squid"));heightInput.active=heightInput.visible;
        playlistButton.visible=playlist;playlistButton.active=playlist&&!playlistsLoading&&!playlists.isEmpty();
        value.visible=!switchSystem&&!playlist&&!selected.startsWith("RLGL_")&&!selected.equals("SKY_SQUID_START");value.active=value.visible;
        timerButton.visible=countdown;timerButton.active=countdown;duration.visible=(countdown&&timerMode==0)||rlglGreen;duration.active=duration.visible;
        if(rlglGreen&&duration.getValue().equals("30"))duration.setValue("5");
        barColorButton.visible=(countdown&&timerMode==0)||bossbar;barColorButton.active=barColorButton.visible;
        linkScreenButton.visible=link;linkScreenButton.active=link&&!linkScreensLoading&&!linkScreens.isEmpty();
        value.setHint(Component.literal(countdown&&timerMode>0?"Name des ModularTimers":selected.equals("START_CINEMATIC")?"Name der Kamerafahrt":link?"HTTPS-Link für den Bildschirm":audio?"minecraft:entity.cat.ambient oder MPSQ-Sound-ID":"Text oder Titel (Farben mit &c etc.)"));
    }

    private void loadPlaylists(){
        playlistsLoading=true;
        MpsqApiClient.get("/playlists").whenComplete((json,error)->minecraft.execute(()->{
            playlistsLoading=false;
            if(error==null&&json!=null&&json.isJsonArray()){
                java.util.ArrayList<PlaylistOption> loaded=new java.util.ArrayList<>();
                for(var element:json.getAsJsonArray()){
                    if(!element.isJsonObject())continue;JsonObject row=element.getAsJsonObject();
                    if(!row.has("name")||!row.has("tracks")||!row.get("tracks").isJsonArray())continue;
                    JsonArray tracks=row.getAsJsonArray("tracks");java.util.ArrayList<String> ids=new java.util.ArrayList<>();
                    for(var track:tracks){
                        if(track.isJsonPrimitive())ids.add(track.getAsString());
                        else if(track.isJsonObject()){JsonObject item=track.getAsJsonObject();String id=firstString(item,"id","assetId","asset_id","soundId","sound_id","url");if(!id.isBlank())ids.add(id);}
                    }
                    if(!ids.isEmpty())loaded.add(new PlaylistOption(row.get("name").getAsString(),ids));
                }
                playlists=List.copyOf(loaded);playlistIndex=playlists.isEmpty()?0:Math.floorMod(playlistIndex,playlists.size());
            }else status="Playlists konnten nicht geladen werden.";
            playlistButton.setMessage(Component.literal(playlistLabel()));updateVisibility();
        }));
    }
    private String playlistLabel(){if(playlistsLoading)return "Playlists werden geladen …";if(playlists.isEmpty())return "Keine Playlists verfügbar";return "Playlist: "+playlists.get(Math.floorMod(playlistIndex,playlists.size())).name();}
    private static String firstString(JsonObject object,String... keys){for(String key:keys)if(object.has(key)&&object.get(key).isJsonPrimitive())return object.get(key).getAsString();return "";}

    private String systemLabel(){
        List<MpsqSystemController.SystemOption> systems=MpsqSystemController.availableSystems();
        if(systems.isEmpty())return "Kein MPSQ-System registriert";
        MpsqSystemController.SystemOption selected=systems.get(Math.floorMod(systemIndex,systems.size()));
        return selected.label();
    }

    private void save() { try{TeamVisibilitySettings.requirePlacement(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);}catch(IllegalArgumentException e){status=e.getMessage();return;}
        JsonObject data=new JsonObject(),body=new JsonObject(),position=new JsonObject();
        switch(actions[action]) {
            case "START_CINEMATIC" -> {
                if(minecraft.getSingleplayerServer()==null){status="Kamerafahrten sind bisher lokal; Redstone-Verknüpfung in Einzelspielerwelten verfügbar.";return;}
                try{
                    var scene=MpsqCinematicTriggers.find(value.getValue());
                    if(scene.points.isEmpty())throw new IllegalArgumentException("Diese Kamerafahrt hat noch keine Punkte.");
                    if(!scene.dimension.equals(world))throw new IllegalArgumentException("Kamerafahrt liegt in einer anderen Dimension.");
                    if(!scene.redstoneEnabled)throw new IllegalArgumentException("MPSQ-Redstone zuerst im Auslöser-Menü dieser Szene einschalten.");
                    data.addProperty("sceneId",scene.id);
                }catch(Exception e){status=e.getMessage();return;}
            }
            case "TOGGLE_AUDIO" -> {
                String sound=value.getValue().trim();
                if(sound.isEmpty()||sound.length()>128){status="minecraft:Sound-ID oder MPSQ-Sound-ID eingeben.";return;}
                if(sound.contains(":")){
                    var id=net.minecraft.resources.Identifier.tryParse(sound);
                    if(id==null||!net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(id)){status="Diese Minecraft-Sound-ID ist nicht registriert. Nutze minecraft:pfad oder eine MPSQ-Datei-ID.";return;}
                }else if(!sound.matches("[a-zA-Z0-9_-]{1,64}")){
                    status="MPSQ-Sound-IDs dürfen nur Buchstaben, Zahlen, _ und - enthalten; Minecraft-IDs brauchen namespace:pfad.";return;
                }
                data.addProperty("sourceType","auto");data.addProperty("sound",sound);
            }
            case "SWITCH_SYSTEM" -> {
                List<MpsqSystemController.SystemOption> systems=MpsqSystemController.availableSystems();
                if(systems.isEmpty()){status="Es ist kein MPSQ-System registriert.";return;}
                MpsqSystemController.SystemOption selected=systems.get(Math.floorMod(systemIndex,systems.size()));
                if(!MpsqSystemController.implemented(selected.id())){status="Platzhalter – dieses Spiel ist noch nicht verfügbar.";return;}
                data.addProperty("systemId",selected.id());
                if(selected.id().equals("glass_bridge")||selected.id().equals("rope_jump")||selected.id().equals("sky_squid")){
                    try{double y=Double.parseDouble(heightInput.getValue().trim());if(!Double.isFinite(y)||y < -30000000||y > 30000000)throw new NumberFormatException();data.addProperty("height",y);}
                    catch(NumberFormatException error){status="Bitte eine gültige Y-Höhe eingeben.";return;}
                }
            }
            case "RLGL_GREEN" -> {
                try { int seconds=Integer.parseInt(duration.getValue()); if(seconds<1||seconds>120)throw new NumberFormatException(); data.addProperty("duration",seconds); }
                catch(NumberFormatException e){status="Grünphasen-Countdown: 1–120 Sekunden (Standard 5)";return;}
            }
            case "START_PLAYLIST" -> {
                if(playlistsLoading){status="Playlists werden noch geladen.";return;}
                if(playlists.isEmpty()){status="Keine abspielbare Playlist verfügbar.";return;}
                PlaylistOption selected=playlists.get(Math.floorMod(playlistIndex,playlists.size()));
                JsonArray tracks=new JsonArray();selected.tracks().forEach(tracks::add);
                data.addProperty("sourceType","mp3");data.addProperty("playlistName",selected.name());data.add("tracks",tracks);
            }
            case "TOGGLE_BOSSBAR" -> {data.addProperty("title",value.getValue());data.addProperty("color",BAR_COLORS[barColor]);}
            case "TOGGLE_COUNTDOWN" -> {
                if(timerMode>0){try{MpsqModularTimer.requireEdit();MpsqModularTimer.find(value.getValue().trim());}catch(Exception e){status=e.getMessage();return;}data.addProperty("timerName",value.getValue().trim());data.addProperty("timerAction",timerMode==1?"start":timerMode==2?"pause":"toggle");break;}
                try{int seconds=Integer.parseInt(duration.getValue());if(seconds<1||seconds>7200)throw new NumberFormatException();data.addProperty("duration",seconds);}
                catch(NumberFormatException e){status="Dauer: 1–7200 Sekunden";return;}
                data.addProperty("title",value.getValue());
                data.addProperty("color",BAR_COLORS[barColor]);
            }
            case "OPEN_LINK" -> {
                String url=value.getValue().trim();
                try{URI uri=URI.create(url);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||url.length()>2048)throw new IllegalArgumentException();}
                catch(IllegalArgumentException e){status="Bitte einen gültigen HTTPS-Link eingeben.";return;}
                if(linkScreensLoading){status="Kino-Bildschirme werden noch geladen.";return;}
                if(linkScreens.isEmpty()){status="Es wurde kein Kino-Bildschirm geladen.";return;}
                data.addProperty("url",url);data.addProperty("screenId",linkScreens.get(linkScreenIndex).id().toString());
            }
            case "SHOW_DIALOGUE" -> {JsonArray pages=new JsonArray();for(String line:value.getValue().split("\\|\\|",-1)){line=line.trim();if(line.isEmpty()||line.length()>240||pages.size()>=12){status="1–12 Textseiten mit höchstens 240 Zeichen, getrennt mit ||";return;}pages.add(line);}data.add("pages",pages);}
        }
        position.addProperty("x",pos.getX());position.addProperty("y",pos.getY());position.addProperty("z",pos.getZ());
        body.add("position",position);body.addProperty("serverId",server);body.addProperty("worldId",world);
        body.addProperty("blockId",block);body.addProperty("objectType",blockKind.name());body.addProperty("actionType",actions[action]);body.add("actionData",data);
        body.addProperty("minimumRank","SKY_SQUID_START".equals(actions[action])?"spieler":"OPEN_LINK".equals(actions[action])?"vip":actions[action].startsWith("RLGL_")?"arbeiter":"SWITCH_SYSTEM".equals(actions[action])?"soldat":"offizier");
        if(virtualSave!=null){status=virtualSave.test(actions[action],data)?"TV-Knopf gespeichert.":"TV-Knopf konnte nicht gespeichert werden.";return;}
        if(MpsqActionSync.server().isBlank()&&minecraft.getSingleplayerServer()!=null){
            boolean saved=MpsqLocalActionStore.set(pos,block,blockKind.name(),actions[action],data);
            status=saved?"Aktion in dieser Einzelspielerwelt gespeichert.":"Lokale Aktion konnte nicht gespeichert werden.";return;
        }
        if(!MpsqActionSync.isMpsqServer()){status="Online-Aktionen sind nur auf mixelpixel.net verfügbar.";return;}
        status="Wird gespeichert…";
        MpsqApiClient.post(block.isEmpty()?"/actions":"/triggers",body).whenComplete((r,e)->minecraft.execute(()->{
            status=e==null?(block.isEmpty()?"Aktion gesendet.":"Gespeichert. Rechtsklick löst die Aktion aus."):"Speichern fehlgeschlagen: "+e.getMessage();
            if(e==null)MpsqTriggerManager.refresh();
        }));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor c,int x,int y,float d){
        super.extractRenderState(c,x,y,d);
        c.centeredText(font,title,width/2,24,MpsqTheme.TEXT_TITEL);
        String selected=actions[action];
        String hint="START_CINEMATIC".equals(selected)?"Szenenname eingeben; MPSQ-Redstone in der Szene aktivieren":
                "OPEN_LINK".equals(selected)?"HTTPS-Link auf dem ausgewählten Kino-Bildschirm":
                "SWITCH_SYSTEM".equals(selected)?"Knopf schaltet das ausgewählte MPSQ-System ein oder aus":
                "RLGL_GREEN".equals(selected)?"Grünphase: Countdown läuft; danach beginnt automatisch die Rotphase":
                "SKY_SQUID_START".equals(selected)?"Startet den aktuellen Turm; Knopf muss innerhalb seiner Zone liegen":
                "SHOW_DIALOGUE".equals(selected)?"Textseiten mit || trennen":
                "TOGGLE_AUDIO".equals(selected)?"Minecraft-ID: minecraft:pfad · MPSQ-ID: ohne Namespace":
                "TOGGLE_COUNTDOWN".equals(selected)?"Countdown-Dauer in Sekunden":"&0–&f Farben: &chellrot, &egelb, &r zurücksetzen";
        if(height>210)c.text(font,hint,width/2-130,202,0xFFFFFFFF);
        c.centeredText(font,Component.literal(status),width/2,height-24,0xFFFFFFFF);
    }
    private static String actionLabel(String action){return switch(action){
        case "TOGGLE_AUDIO"->"Musik / Ton umschalten";case "START_PLAYLIST"->"Playlist abspielen";case "TOGGLE_COUNTDOWN"->"Countdown umschalten";
        case "TOGGLE_BOSSBAR"->"Bossbar umschalten";
        case "START_CINEMATIC"->"Kamerafahrt auslösen";
        case "SWITCH_SYSTEM"->"Aktion / System starten oder wechseln";
        case "RLGL_GREEN"->"Spielphase: Grün + Countdown";
        case "SHOW_DIALOGUE"->"Dialog (F zum Weitergehen)";case "OPEN_LINK"->"Link öffnen";default->action;};}
    @Override public boolean isPauseScreen(){return false;}
    private record PlaylistOption(String name,List<String> tracks){}
}
