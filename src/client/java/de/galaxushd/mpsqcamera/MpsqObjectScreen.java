package de.galaxushd.mpsqcamera;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Places an uploaded static model above the selected supporting block. */
public final class MpsqObjectScreen extends Screen {
    private final BlockPos pos;
    private final String server,world;
    private final boolean editing;
    private final String initialModel,initialName,initialSoundId;
    private TextFieldWidget model;
    private TextFieldWidget displayName;
    private TextFieldWidget soundId;
    private float size=1.0f;
    private int rotation;
    private String status="";
    private boolean pending;
    public MpsqObjectScreen(BlockPos support){super(Text.literal("MPSQ-Möbel platzieren"));pos=support.up().toImmutable();server=MpsqActionSync.server();world=MpsqActionSync.world();editing=false;initialModel="";initialName="";initialSoundId="";}
    public MpsqObjectScreen(JsonObject existing){super(Text.literal("MPSQ-Möbel-Konfigurator"));pos=BlockPos.ofFloored(existing.get("x").getAsDouble(),existing.get("y").getAsDouble(),existing.get("z").getAsDouble());server=MpsqActionSync.server();world=MpsqActionSync.world();editing=true;initialModel=str(existing,"model_id",str(existing,"modelId",""));initialName=str(existing,"display_name",str(existing,"displayName",""));initialSoundId=str(existing,"sound_id",str(existing,"soundId",""));size=readFloat(existing,"scale",1.0f);rotation=existing.has("rotation")?existing.get("rotation").getAsInt():0;}
    @Override protected void init(){
        model=addDrawableChild(new TextFieldWidget(textRenderer,width/2-120,70,145,20,Text.literal("Modell-ID")));model.setMaxLength(64);model.setPlaceholder(Text.literal("Modell-ID"));model.setText(initialModel);
        addDrawableChild(ButtonWidget.builder(Text.literal("Katalog"),b->client.setScreen(new MpsqModelsScreen(this))).dimensions(width/2+30,70,90,20).build());
        displayName=addDrawableChild(new TextFieldWidget(textRenderer,width/2-120,98,240,20,Text.literal("Anzeigename")));displayName.setMaxLength(64);displayName.setPlaceholder(Text.literal("Anzeigename (optional)"));displayName.setText(initialName);
        soundId=addDrawableChild(new TextFieldWidget(textRenderer,width/2-120,154,240,20,Text.literal("Sound-ID")));soundId.setMaxLength(64);soundId.setPlaceholder(Text.literal("MP3-Datei-ID (optional)"));soundId.setText(initialSoundId);
        addDrawableChild(ButtonWidget.builder(Text.literal(String.format(java.util.Locale.ROOT,"Größe: %.1fx",size)),b->{size=size>=3.0f?0.5f:size+0.5f;b.setMessage(Text.literal(String.format(java.util.Locale.ROOT,"Größe: %.1fx",size)));}).dimensions(width/2-120,126,116,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Drehung: "+rotation+"°"),b->{rotation=(rotation+90)%360;b.setMessage(Text.literal("Drehung: "+rotation+"°"));}).dimensions(width/2+4,126,116,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(editing?"Änderungen speichern":"Platzieren / Ersetzen"),b->save(false)).dimensions(width/2-120,182,240,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Möbel hier entfernen"),b->save(true)).dimensions(width/2-120,208,240,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"),b->close()).dimensions(width/2-60,height-28,120,20).build());
    }
    private void save(boolean remove){
        if(pending)return;
        if(!remove&&!model.getText().trim().matches("[a-z0-9_-]{1,64}")){status="Bitte eine gültige Möbel-ID eingeben.";return;}
        BlockPos target=pos;int facing=rotation;
        if(!remove&&model.getText().trim().equalsIgnoreCase("minecraft-cat-lying")&&client.player!=null){target=client.player.getBlockPos();facing=Math.floorMod(Math.round(client.player.getYaw()+180),360)/90*90;}
        final BlockPos saveTarget=target;
        if(server.isBlank() && client.getServer()!=null){
            pending=true;status="Wird lokal gespeichert…";
            boolean saved=MpsqLocalObjectStore.set(world,target.getX(),target.getY(),target.getZ(),model.getText().trim(),facing,displayName.getText().trim(),size,soundId.getText().trim(),remove);
            pending=false;
            status=saved?(remove?"Möbel entfernt.":"In dieser Einzelspielerwelt gespeichert."):(remove?"An dieser Position wurde kein Möbel gefunden.":"Speichern in der Einzelspielerwelt fehlgeschlagen.");
            if(saved){if(remove)MpsqAccessoryRenderer.removeFurnitureAt(target.getX(),target.getY(),target.getZ());else MpsqAccessoryRenderer.refresh();}
            return;
        }
        if(!MpsqActionSync.isMpsqServer()){status="Online-Speicher ist nur auf mixelpixel.net verfügbar.";return;}
        JsonObject body=new JsonObject();body.addProperty("server",server);body.addProperty("world",world);body.addProperty("x",target.getX());body.addProperty("y",target.getY());body.addProperty("z",target.getZ());body.addProperty("rotation",facing);body.addProperty("modelId",model.getText().trim());body.addProperty("displayName",displayName.getText().trim());body.addProperty("scale",size);body.addProperty("soundId",soundId.getText().trim());body.addProperty("remove",remove);
        pending=true;status=remove?"Möbel wird entfernt…":"Wird gespeichert…";MpsqApiClient.post("/objects",body).whenComplete((data,error)->client.execute(()->{
            pending=false;
            if(error!=null){status="Fehler: "+error.getMessage();return;}
            boolean ok=data!=null&&data.isJsonObject()&&data.getAsJsonObject().has("ok")&&data.getAsJsonObject().get("ok").getAsBoolean();
            if(!ok){status=remove?"An dieser Position wurde kein Möbel gefunden.":"Speichern fehlgeschlagen.";return;}
            status=remove?"Möbel entfernt.":"Gespeichert.";
            if(remove)MpsqAccessoryRenderer.removeFurnitureAt(saveTarget.getX(),saveTarget.getY(),saveTarget.getZ());else MpsqAccessoryRenderer.refresh();
        }));
    }
    @Override public void renderBackground(DrawContext c,int x,int y,float d){super.renderBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void render(DrawContext c,int x,int y,float d){super.render(c,x,y,d);c.drawCenteredTextWithShadow(textRenderer,title,width/2,24,0xFFFFFFFF);c.drawCenteredTextWithShadow(textRenderer,"Position: "+pos.toShortString(),width/2,50,0xFFAAAAAA);c.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(status,width-24),width/2,height-46,0xFFFFFFFF);}
    @Override public boolean shouldPause(){return false;}
    private static String str(JsonObject o,String key,String fallback){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;}
    private static float readFloat(JsonObject o,String key,float fallback){try{float value=o.has(key)?o.get(key).getAsFloat():fallback;return Float.isFinite(value)?Math.max(0.25f,Math.min(3.0f,value)):fallback;}catch(RuntimeException ignored){return fallback;}}
}


