package de.galaxushd.mpsqcamera;
import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class MpsqActionSetupScreen extends Screen {
    private final BlockPos pos;
    private final String block, server, world;
    private TextFieldWidget value, duration;
    private ButtonWidget actionButton;
    private ButtonWidget soundTypeButton;
    private ButtonWidget platformButton;
    private ButtonWidget screenButton;
    private static final String[] SOUND_TYPES={"minecraft","mp3","mp4"};
    private static final String[] SOUND_TYPE_LABELS={"Minecraft-ID","MP3-Datei-ID","MP4-Datei-ID"};
    private int soundType;
    private static final String[] QUICK_ACTIONS={"PLAY_AUDIO","START_PLAYLIST","STOP_AUDIO","START_COUNTDOWN","SHOW_BOSSBAR","HIDE_BOSSBAR"};
    private static final String[] BLOCK_ACTIONS={"PLAY_AUDIO","START_PLAYLIST","STOP_AUDIO","START_COUNTDOWN","SHOW_BOSSBAR","HIDE_BOSSBAR","SHOW_DIALOGUE","OPEN_LINK"};
    private static final String[] LINK_PLATFORMS={"youtube","twitch"};
    private static final String[] LINK_LABELS={"YouTube · MPSQ-Kanal","Twitch · MixelPixel"};
    private int linkPlatform;
    private int screenIndex;
    private final String[] actions;
    private int action;
    private String status="";
    public MpsqActionSetupScreen(BlockPos pos,String block) {
        super(Text.literal("MPSQ-Knopf einrichten"));
        this.pos=pos.toImmutable(); this.block=block;
        this.actions=block.isEmpty()?QUICK_ACTIONS:BLOCK_ACTIONS;
        server=MpsqActionSync.server(); world=MpsqActionSync.world();
    }
    public MpsqActionSetupScreen() { this(BlockPos.ORIGIN, ""); }
    @Override protected void init() {
        int x=width/2-130,y=60;
        actionButton=addDrawableChild(ButtonWidget.builder(Text.literal(actionLabel(actions[action])),b->{
            action=(action+1)%actions.length; b.setMessage(Text.literal(actionLabel(actions[action])));updateSoundTypeVisibility();
        }).dimensions(x,y,260,20).build());
        soundTypeButton=addDrawableChild(ButtonWidget.builder(Text.literal(SOUND_TYPE_LABELS[soundType]),b->{soundType=(soundType+1)%SOUND_TYPES.length;b.setMessage(Text.literal(SOUND_TYPE_LABELS[soundType]));}).dimensions(x,y+23,260,20).build());
        platformButton=addDrawableChild(ButtonWidget.builder(Text.literal(LINK_LABELS[linkPlatform]),b->{linkPlatform=(linkPlatform+1)%LINK_PLATFORMS.length;b.setMessage(Text.literal(LINK_LABELS[linkPlatform]));}).dimensions(x,y+49,260,20).build());
        screenButton=addDrawableChild(ButtonWidget.builder(Text.literal(screenLabel()),b->{var screens=linkScreens();if(screens.isEmpty()){status="Bildschirme werden gesucht…";ScreenSyncManager.refresh().whenComplete((ignored,error)->client.execute(()->{status=error==null?"Kein MPSQ-Kinobildschirm verfügbar.":"Bildschirme konnten nicht geladen werden.";b.setMessage(Text.literal(screenLabel()));}));return;}screenIndex=(screenIndex+1)%screens.size();b.setMessage(Text.literal(screenLabel()));}).dimensions(x,y+73,260,20).build());
        value=addDrawableChild(new TextFieldWidget(textRenderer,x,y+49,260,20,Text.literal("Text oder Sound-ID")));
        value.setMaxLength(3072);
        duration=addDrawableChild(new TextFieldWidget(textRenderer,x,y+88,260,20,Text.literal("Sekunden")));
        duration.setText("30");
        addDrawableChild(ButtonWidget.builder(Text.literal(block.isEmpty()?"Auslösen":"Speichern"),b->save()).dimensions(x,y+120,125,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Abbrechen"),b->close()).dimensions(x+135,y+120,125,20).build());
        updateSoundTypeVisibility();
    }
    private java.util.List<LocalScreenStore.LocalScreenData> linkScreens(){return LocalScreenStore.getAllScreens().stream().filter(screen->screen.inputType()==LocalScreenStore.ScreenInputType.LINK).toList();}
    private String screenLabel(){var screens=linkScreens();if(screens.isEmpty())return "Zielbildschirm: keiner gefunden";screenIndex=Math.floorMod(screenIndex,screens.size());var screen=screens.get(screenIndex);return "Bildschirm: "+screen.name();}
    private void updateSoundTypeVisibility(){boolean audio=actions[action].equals("PLAY_AUDIO")||actions[action].equals("START_PLAYLIST");boolean countdown=actions[action].equals("START_COUNTDOWN");boolean link=actions[action].equals("OPEN_LINK");if(soundTypeButton!=null){soundTypeButton.visible=audio;soundTypeButton.active=audio;}if(duration!=null){duration.visible=countdown;duration.active=countdown;}if(value!=null){value.visible=!link;value.active=!link;}if(platformButton!=null){platformButton.visible=link;platformButton.active=link;}if(screenButton!=null){screenButton.visible=link;screenButton.active=link;screenButton.setMessage(Text.literal(screenLabel()));}}
    private void save() {
        JsonObject data=new JsonObject(),body=new JsonObject(),position=new JsonObject();
        switch(actions[action]) {
            case "PLAY_AUDIO" -> { if(soundType==0&&net.minecraft.util.Identifier.tryParse(value.getText())==null){status="Gültige Minecraft-Sound-ID erforderlich";return;}if(soundType>0&&!value.getText().matches("[a-zA-Z0-9_-]{1,64}")){status="Bitte die Datei-ID aus dem Sound-Upload angeben.";return;}data.addProperty("sourceType",SOUND_TYPES[soundType]);data.addProperty("sound",value.getText().trim()); }
            case "START_PLAYLIST" -> {
                JsonArray tracks=new JsonArray();
                for(String id:value.getText().split(",")){id=id.trim();if(soundType==0?net.minecraft.util.Identifier.tryParse(id)==null:!id.matches("[a-zA-Z0-9_-]{1,64}")){status=soundType==0?"Sound-IDs durch Kommas trennen":"Datei-IDs durch Kommas trennen";return;}tracks.add(id);}
                data.addProperty("sourceType",SOUND_TYPES[soundType]);data.add("tracks",tracks);
            }
            case "START_COUNTDOWN" -> {
                try { int seconds=Integer.parseInt(duration.getText()); if(seconds<1||seconds>7200)throw new NumberFormatException(); data.addProperty("duration",seconds); }
                catch(NumberFormatException e){status="Dauer: 1–7200 Sekunden";return;}
                data.addProperty("title",value.getText());
            }
            case "OPEN_LINK" -> {var screens=linkScreens();if(screens.isEmpty()){status="Erstelle zuerst einen MPSQ-Kinobildschirm.";return;}screenIndex=Math.floorMod(screenIndex,screens.size());data.addProperty("platform",LINK_PLATFORMS[linkPlatform]);data.addProperty("screenId",screens.get(screenIndex).id().toString());}
            case "SHOW_BOSSBAR" -> data.addProperty("title",value.getText());
            case "SHOW_DIALOGUE" -> {JsonArray pages=new JsonArray();for(String line:value.getText().split("\\|\\|",-1)){line=line.trim();if(line.isEmpty()||line.length()>240||pages.size()>=12){status="1–12 Textseiten mit höchstens 240 Zeichen, getrennt mit ||";return;}pages.add(line);}data.add("pages",pages);}
        }
        position.addProperty("x",pos.getX());position.addProperty("y",pos.getY());position.addProperty("z",pos.getZ());
        body.add("position",position);body.addProperty("serverId",server);body.addProperty("worldId",world);
        body.addProperty("blockId",block);body.addProperty("actionType",actions[action]);body.add("actionData",data);
        body.addProperty("minimumRank","OPEN_LINK".equals(actions[action])?"vip":"offizier");
        status="Wird gespeichert…";
        if(MpsqActionSync.server().isBlank() && client.getServer()!=null) {
            boolean saved=MpsqLocalActionStore.set(pos,block,actions[action],data);
            status=saved?"Aktion in dieser Einzelspielerwelt gespeichert.":"Lokale Aktion konnte nicht gespeichert werden.";
            return;
        }
        MpsqApiClient.post(block.isEmpty()?"/actions":"/triggers",body).whenComplete((r,e)->client.execute(()->{
            status=e==null?(block.isEmpty()?"Aktion gesendet.":"Gespeichert. Rechtsklick löst die Aktion aus."):"Speichern fehlgeschlagen: "+e.getMessage();
            if(e==null)MpsqTriggerManager.refresh();
        }));
    }
    @Override public void render(DrawContext c,int x,int y,float d){
        super.render(c,x,y,d);
        c.drawCenteredTextWithShadow(textRenderer,title,width/2,24,MpsqTheme.TEXT_TITEL);
        String hint=actions[action].equals("OPEN_LINK")?"Der Knopf startet den Kanal auf dem ausgewählten MPSQ-Bildschirm.":actions[action].equals("SHOW_DIALOGUE")?"Textseiten mit || trennen":(actions[action].equals("PLAY_AUDIO")||actions[action].equals("START_PLAYLIST"))?(soundType==0?"Minecraft-Sound-ID, z. B. minecraft:music.menu":"Sound-Datei-ID aus dem MPSQ-Upload") : "Text oder Titel";
        if(actions[action].equals("START_COUNTDOWN"))c.drawTextWithShadow(textRenderer,"Countdown-Dauer in Sekunden",width/2-130,132,0xFFFFFFFF);
        else c.drawTextWithShadow(textRenderer,hint,width/2-130,actions[action].equals("OPEN_LINK")?158:132,0xFFFFFFFF);
        c.drawCenteredTextWithShadow(textRenderer,Text.literal(status),width/2,height-24,0xFFFFFFFF);
    }
    private static String actionLabel(String action) {
        return switch(action) {
            case "PLAY_AUDIO" -> "Musik / Ton";
            case "START_PLAYLIST" -> "Playlist starten";
            case "STOP_AUDIO" -> "Musik stoppen";
            case "START_COUNTDOWN" -> "Countdown starten";
            case "SHOW_BOSSBAR" -> "Bossbar anzeigen";
            case "HIDE_BOSSBAR" -> "Bossbar ausblenden";
            case "SHOW_DIALOGUE" -> "Dialog (F zum Weitergehen)";
            case "OPEN_LINK" -> "Link öffnen";
            default -> action;
        };
    }
    @Override public boolean shouldPause(){return false;}
}
