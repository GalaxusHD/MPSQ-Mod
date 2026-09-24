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
    private static final String[] QUICK_ACTIONS={"PLAY_AUDIO","START_PLAYLIST","STOP_AUDIO","START_COUNTDOWN","SHOW_BOSSBAR","HIDE_BOSSBAR","SEND_ANNOUNCEMENT"};
    private static final String[] BLOCK_ACTIONS={"PLAY_AUDIO","START_PLAYLIST","STOP_AUDIO","START_COUNTDOWN","SHOW_BOSSBAR","HIDE_BOSSBAR","SEND_ANNOUNCEMENT","OPEN_REDEEM","OPEN_LINK"};
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
            action=(action+1)%actions.length; b.setMessage(Text.literal(actionLabel(actions[action])));
        }).dimensions(x,y,260,20).build());
        value=addDrawableChild(new TextFieldWidget(textRenderer,x,y+46,260,20,Text.literal("Text oder Sound-ID")));
        value.setMaxLength(512);
        duration=addDrawableChild(new TextFieldWidget(textRenderer,x,y+88,260,20,Text.literal("Sekunden")));
        duration.setText("30");
        addDrawableChild(ButtonWidget.builder(Text.literal(block.isEmpty()?"Auslösen":"Speichern"),b->save()).dimensions(x,y+120,125,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Abbrechen"),b->close()).dimensions(x+135,y+120,125,20).build());
    }
    private void save() {
        JsonObject data=new JsonObject(),body=new JsonObject(),position=new JsonObject();
        switch(actions[action]) {
            case "PLAY_AUDIO" -> { if(net.minecraft.util.Identifier.tryParse(value.getText())==null){status="Gültige Sound-ID erforderlich";return;} data.addProperty("sound",value.getText()); }
            case "START_PLAYLIST" -> {
                JsonArray tracks=new JsonArray();
                for(String id:value.getText().split(",")){id=id.trim();if(net.minecraft.util.Identifier.tryParse(id)==null){status="Sound-IDs durch Kommas trennen";return;}tracks.add(id);}
                data.add("tracks",tracks);
            }
            case "START_COUNTDOWN" -> {
                try { int seconds=Integer.parseInt(duration.getText()); if(seconds<1||seconds>7200)throw new NumberFormatException(); data.addProperty("duration",seconds); }
                catch(NumberFormatException e){status="Dauer: 1–7200 Sekunden";return;}
                data.addProperty("title",value.getText());
            }
            case "OPEN_LINK" -> {if(!value.getText().startsWith("https://")){status="HTTPS-Link erforderlich";return;}data.addProperty("url",value.getText());}
            case "SHOW_BOSSBAR" -> data.addProperty("title",value.getText());
            case "SEND_ANNOUNCEMENT" -> data.addProperty("text",value.getText());
        }
        position.addProperty("x",pos.getX());position.addProperty("y",pos.getY());position.addProperty("z",pos.getZ());
        body.add("position",position);body.addProperty("serverId",server);body.addProperty("worldId",world);
        body.addProperty("blockId",block);body.addProperty("actionType",actions[action]);body.add("actionData",data);
        body.addProperty("minimumRank",("OPEN_REDEEM".equals(actions[action])||"OPEN_LINK".equals(actions[action]))?"vip":"offizier");
        status="Wird gespeichert…";
        if(MpsqActionSync.server().isBlank() || MpsqActionSync.world().isBlank()) {
            status="Bitte zuerst einer Welt auf dem MPSQ-Server beitreten.";
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
        c.drawTextWithShadow(textRenderer,"Text oder Sound-ID (z. B. minecraft:music.menu)",width/2-130,92,0xFFFFFFFF);
        c.drawTextWithShadow(textRenderer,"Countdown-Dauer in Sekunden",width/2-130,134,0xFFFFFFFF);
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
            case "SEND_ANNOUNCEMENT" -> "Ansage senden";
            case "OPEN_REDEEM" -> "Redeem öffnen";
            case "OPEN_LINK" -> "Link öffnen";
            default -> action;
        };
    }
    @Override public boolean shouldPause(){return false;}
}
