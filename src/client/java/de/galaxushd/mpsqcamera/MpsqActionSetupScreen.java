package de.galaxushd.mpsqcamera;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.net.URI;
import java.util.List;

public final class MpsqActionSetupScreen extends Screen {
    private final BlockPos pos;
    private final String block, server, world;
    private final String blockName, properties;
    private final MpsqTriggerBlockPolicy.Kind blockKind;
    private TextFieldWidget value, duration;
    private ButtonWidget linkScreenButton;
    private ButtonWidget barColorButton;
    private static final String[] BAR_COLORS={"purple","pink","red"};
    private static final String[] BAR_COLOR_LABELS={"Violett (Standard)","Pink (#ec2f53)","Rot (#cf2020)"};
    private static final String[] QUICK_ACTIONS={"TOGGLE_AUDIO","TOGGLE_COUNTDOWN","TOGGLE_BOSSBAR","SWITCH_SYSTEM"};
    private static final String[] BLOCK_ACTIONS={"TOGGLE_AUDIO","TOGGLE_COUNTDOWN","TOGGLE_BOSSBAR","SHOW_DIALOGUE","OPEN_LINK","SWITCH_SYSTEM"};
    private final String[] actions;
    private List<LocalScreenStore.LocalScreenData> linkScreens=List.of();
    private int action, linkScreenIndex, barColor;
    private boolean linkScreensLoading;
    private String status="";

    public MpsqActionSetupScreen(BlockPos pos,String block,String blockName,MpsqTriggerBlockPolicy.Kind blockKind,String properties) {
        super(Text.literal("MPSQ-Redstone einrichten"));
        this.pos=pos.toImmutable(); this.block=block; this.blockName=blockName; this.blockKind=blockKind; this.properties=properties;
        this.actions=block.isEmpty()?QUICK_ACTIONS:BLOCK_ACTIONS;
        server=MpsqActionSync.server(); world=MpsqActionSync.world();
    }
    public MpsqActionSetupScreen(BlockPos pos,String block) { this(pos,block,block,MpsqTriggerBlockPolicy.Kind.NONE,""); }
    public MpsqActionSetupScreen() { this(BlockPos.ORIGIN, "","",MpsqTriggerBlockPolicy.Kind.NONE,""); }

    @Override protected void init() {
        int x=width/2-130,y=35;
        linkScreens=LocalScreenStore.getAllScreens().stream()
                .filter(s->s.inputType()==LocalScreenStore.ScreenInputType.MPSQ_REDSTONE).toList();
        addDrawableChild(ButtonWidget.builder(Text.literal(actionLabel(actions[action])),b->{
            action=(action+1)%actions.length; b.setMessage(Text.literal(actionLabel(actions[action]))); updateVisibility();
        }).dimensions(x,y,260,20).build());
        value=addDrawableChild(new TextFieldWidget(textRenderer,x,y+23,260,20,Text.literal("Text, Titel, Sound-ID oder URL")));
        value.setMaxLength(3072);
        barColorButton=addDrawableChild(ButtonWidget.builder(Text.literal(BAR_COLOR_LABELS[barColor]),b->{
            barColor=(barColor+1)%BAR_COLORS.length;b.setMessage(Text.literal(BAR_COLOR_LABELS[barColor]));
        }).dimensions(x,y+49,260,20).build());
        duration=addDrawableChild(new TextFieldWidget(textRenderer,x,y+75,260,20,Text.literal("Sekunden")));
        duration.setText("30");
        linkScreenButton=addDrawableChild(ButtonWidget.builder(Text.literal(screenLabel()),b->{
            if(!linkScreens.isEmpty()){linkScreenIndex=(linkScreenIndex+1)%linkScreens.size();b.setMessage(Text.literal(screenLabel()));}
        }).dimensions(x,y+75,260,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(block.isEmpty()?"Auslösen":"Speichern"),b->save()).dimensions(x,y+107,125,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Abbrechen"),b->close()).dimensions(x+135,y+107,125,20).build());
        updateVisibility();
        if (MpsqActionSync.isMpsqServer()) {
            linkScreensLoading=true;
            linkScreenButton.setMessage(Text.literal(screenLabel()));
            updateVisibility();
            ScreenSyncManager.refresh().whenComplete((ignored,error)->client.execute(()->{
                linkScreensLoading=false;
                if (error==null) {
                    linkScreens=LocalScreenStore.getAllScreens().stream()
                            .filter(s->s.inputType()==LocalScreenStore.ScreenInputType.MPSQ_REDSTONE).toList();
                    linkScreenIndex=linkScreens.isEmpty()?0:Math.floorMod(linkScreenIndex,linkScreens.size());
                } else {
                    status="MPSQ-Redstone-Bildschirme konnten nicht geladen werden.";
                }
                linkScreenButton.setMessage(Text.literal(screenLabel()));
                updateVisibility();
            }));
        }
    }

    private String screenLabel(){
        if(linkScreensLoading)return "MPSQ-Redstone-Bildschirme werden geladen …";
        if(linkScreens.isEmpty())return "Kein Kino-Bildschirm geladen";
        return "Bildschirm: "+linkScreens.get(Math.floorMod(linkScreenIndex,linkScreens.size())).name();
    }
    private void updateVisibility(){
        String selected=actions[action];
        boolean audio="TOGGLE_AUDIO".equals(selected), countdown="TOGGLE_COUNTDOWN".equals(selected), link="OPEN_LINK".equals(selected);
        boolean bossbar="TOGGLE_BOSSBAR".equals(selected);
        duration.visible=countdown;duration.active=countdown;
        barColorButton.visible=countdown||bossbar;barColorButton.active=countdown||bossbar;
        linkScreenButton.visible=link;linkScreenButton.active=link&&!linkScreensLoading&&!linkScreens.isEmpty();
        value.setPlaceholder(Text.literal(link?"HTTPS-Link für den Bildschirm":audio?"minecraft:entity.cat.ambient oder MPSQ-Sound-ID":"SWITCH_SYSTEM".equals(selected)?"System-ID, z. B. bewegungssensor":"Text oder Titel (Farben mit &c etc.)"));
    }

    private void save() {
        JsonObject data=new JsonObject(),body=new JsonObject(),position=new JsonObject();
        switch(actions[action]) {
            case "TOGGLE_AUDIO" -> {
                String sound=value.getText().trim();
                if(sound.isEmpty()||sound.length()>128){status="minecraft:Sound-ID oder MPSQ-Sound-ID eingeben.";return;}
                if(sound.contains(":")){
                    var id=net.minecraft.util.Identifier.tryParse(sound);
                    if(id==null||!net.minecraft.registry.Registries.SOUND_EVENT.containsId(id)){status="Diese Minecraft-Sound-ID ist nicht registriert. Nutze minecraft:pfad oder eine MPSQ-Datei-ID.";return;}
                }else if(!sound.matches("[a-zA-Z0-9_-]{1,64}")){
                    status="MPSQ-Sound-IDs dürfen nur Buchstaben, Zahlen, _ und - enthalten; Minecraft-IDs brauchen namespace:pfad.";return;
                }
                data.addProperty("sourceType","auto");data.addProperty("sound",sound);
            }
            case "SWITCH_SYSTEM" -> {
                String systemId=value.getText().trim().toLowerCase(java.util.Locale.ROOT);
                if(!systemId.matches("[a-z0-9_-]{1,64}")){status="System-ID: 1–64 Zeichen, a–z, 0–9, _ oder -";return;}
                data.addProperty("systemId",systemId);
            }
            case "TOGGLE_BOSSBAR" -> {data.addProperty("title",value.getText());data.addProperty("color",BAR_COLORS[barColor]);}
            case "TOGGLE_COUNTDOWN" -> {
                try{int seconds=Integer.parseInt(duration.getText());if(seconds<1||seconds>7200)throw new NumberFormatException();data.addProperty("duration",seconds);}
                catch(NumberFormatException e){status="Dauer: 1–7200 Sekunden";return;}
                data.addProperty("title",value.getText());
                data.addProperty("color",BAR_COLORS[barColor]);
            }
            case "OPEN_LINK" -> {
                String url=value.getText().trim();
                try{URI uri=URI.create(url);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||url.length()>2048)throw new IllegalArgumentException();}
                catch(IllegalArgumentException e){status="Bitte einen gültigen HTTPS-Link eingeben.";return;}
                if(linkScreensLoading){status="Kino-Bildschirme werden noch geladen.";return;}
                if(linkScreens.isEmpty()){status="Es wurde kein Kino-Bildschirm geladen.";return;}
                data.addProperty("url",url);data.addProperty("screenId",linkScreens.get(linkScreenIndex).id().toString());
            }
            case "SHOW_DIALOGUE" -> {JsonArray pages=new JsonArray();for(String line:value.getText().split("\\|\\|",-1)){line=line.trim();if(line.isEmpty()||line.length()>240||pages.size()>=12){status="1–12 Textseiten mit höchstens 240 Zeichen, getrennt mit ||";return;}pages.add(line);}data.add("pages",pages);}
        }
        position.addProperty("x",pos.getX());position.addProperty("y",pos.getY());position.addProperty("z",pos.getZ());
        body.add("position",position);body.addProperty("serverId",server);body.addProperty("worldId",world);
        body.addProperty("blockId",block);body.addProperty("objectType",blockKind.name());body.addProperty("actionType",actions[action]);body.add("actionData",data);
        body.addProperty("minimumRank","OPEN_LINK".equals(actions[action])?"vip":"offizier");
        if(MpsqActionSync.server().isBlank()&&client.getServer()!=null){
            boolean saved=MpsqLocalActionStore.set(pos,block,blockKind.name(),actions[action],data);
            status=saved?"Aktion in dieser Einzelspielerwelt gespeichert.":"Lokale Aktion konnte nicht gespeichert werden.";return;
        }
        if(!MpsqActionSync.isMpsqServer()){status="Online-Aktionen sind nur auf mixelpixel.net verfügbar.";return;}
        status="Wird gespeichert…";
        MpsqApiClient.post(block.isEmpty()?"/actions":"/triggers",body).whenComplete((r,e)->client.execute(()->{
            status=e==null?(block.isEmpty()?"Aktion gesendet.":"Gespeichert. Rechtsklick löst die Aktion aus."):"Speichern fehlgeschlagen: "+e.getMessage();
            if(e==null)MpsqTriggerManager.refresh();
        }));
    }

    @Override public void render(DrawContext c,int x,int y,float d){
        super.render(c,x,y,d);
        c.drawCenteredTextWithShadow(textRenderer,title,width/2,24,MpsqTheme.TEXT_TITEL);
        String selected=actions[action];
        String hint="OPEN_LINK".equals(selected)?"HTTPS-Link auf dem ausgewählten Kino-Bildschirm":
                "SWITCH_SYSTEM".equals(selected)?"Gleiche ID stoppt; andere ID ersetzt das aktive System":
                "SHOW_DIALOGUE".equals(selected)?"Textseiten mit || trennen":
                "TOGGLE_AUDIO".equals(selected)?"Minecraft-ID: minecraft:pfad · MPSQ-ID: ohne Namespace":
                "TOGGLE_COUNTDOWN".equals(selected)?"Countdown-Dauer in Sekunden":"&0–&f Farben: &chellrot, &egelb, &r zurücksetzen";
        c.drawTextWithShadow(textRenderer,hint,width/2-130,176,0xFFFFFFFF);
        c.drawCenteredTextWithShadow(textRenderer,Text.literal(status),width/2,height-24,0xFFFFFFFF);
    }
    private static String actionLabel(String action){return switch(action){
        case "TOGGLE_AUDIO"->"Musik / Ton umschalten";case "TOGGLE_COUNTDOWN"->"Countdown umschalten";
        case "TOGGLE_BOSSBAR"->"Bossbar umschalten";
        case "SWITCH_SYSTEM"->"Aktion / System starten oder wechseln";
        case "SHOW_DIALOGUE"->"Dialog (F zum Weitergehen)";case "OPEN_LINK"->"Link öffnen";default->action;};}
    @Override public boolean shouldPause(){return false;}
}
