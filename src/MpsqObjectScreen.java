package de.galaxushd.mpsqcamera;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Places an uploaded static model above the selected supporting block. */
public final class MpsqObjectScreen extends Screen {
    private final BlockPos pos;
    private JsonObject placement;
    private final String server,world;
    private final boolean editing;
    private final String initialModel,initialName,initialSoundId;
    private EditBox model;
    private EditBox displayName;
    private EditBox soundId;
    private float size=1.0f;
    private int rotation;
    private String status="";
    private boolean pending;
    private String skinName="";
    public MpsqObjectScreen(BlockPos support){super(Component.literal("MPSQ-Möbel platzieren"));pos=support.above().immutable();server=MpsqActionSync.server();world=MpsqActionSync.world();editing=false;initialModel="";initialName="";initialSoundId="";}
    public MpsqObjectScreen(JsonObject existing){super(Component.literal("MPSQ-Möbel-Konfigurator"));placement=existing.deepCopy();pos=BlockPos.containing(MpsqFurniturePositions.anchor(existing,"x"),MpsqFurniturePositions.anchor(existing,"y"),MpsqFurniturePositions.anchor(existing,"z"));server=MpsqActionSync.server();world=MpsqActionSync.world();editing=true;skinName=str(existing,"skin_name","");initialModel=str(existing,"model_id",str(existing,"modelId",""));initialName=str(existing,"display_name",str(existing,"displayName",""));initialSoundId=str(existing,"sound_id",str(existing,"soundId",""));size=readFloat(existing,"scale",1.0f);rotation=existing.has("rotation")?existing.get("rotation").getAsInt():0;}
    @Override protected void init(){
        String keepModel=model==null?initialModel:model.getValue();
        String keepName=displayName==null?initialName:displayName.getValue();
        String keepSound=soundId==null?initialSoundId:soundId.getValue();
        model=addRenderableWidget(new EditBox(font,width/2-120,70,145,20,Component.literal("Modell-ID")));model.setMaxLength(64);model.setHint(Component.literal("Modell-ID"));model.setValue(keepModel);
        addRenderableWidget(Button.builder(Component.literal("Katalog"),b->minecraft.gui.setScreen(new MpsqModelsScreen(this))).bounds(width/2+30,70,90,20).build());
        displayName=addRenderableWidget(new EditBox(font,width/2-120,98,240,20,Component.literal("Anzeigename")));displayName.setMaxLength(64);displayName.setHint(Component.literal("Anzeigename (optional)"));displayName.setValue(keepName);
        soundId=addRenderableWidget(new EditBox(font,width/2-120,154,240,20,Component.literal("Sound-ID")));soundId.setMaxLength(128);soundId.setHint(Component.literal("Minecraft-Sound oder MPSQ-MP3/MP4-ID (optional)"));soundId.setValue(keepSound);
        addRenderableWidget(Button.builder(Component.literal(String.format(java.util.Locale.ROOT,"Größe: %.2fx",size)),b->{size=MpsqTvFurniture.MODEL.equals(model.getValue().trim())?(Math.round(size*3)%9+1)/3f:(size>=3.0f?0.5f:size+0.5f);b.setMessage(Component.literal(String.format(java.util.Locale.ROOT,"Größe: %.2fx",size)));}).bounds(width/2-120,126,116,20).build());
        addRenderableWidget(Button.builder(Component.literal("Drehung: "+rotation+"°"),b->{rotation=(Math.round(rotation/15f)*15+15)%360;b.setMessage(Component.literal("Drehung: "+rotation+"°"));}).bounds(width/2+4,126,116,20).build());
        Button figure=addRenderableWidget(Button.builder(Component.literal("Top-Figur: Spieler-Skin …"),b->minecraft.gui.setScreen(new MpsqFigureSkinScreen(this,skinName,value->skinName=value))).bounds(width/2-120,182,240,20).build());
        figure.active=MpsqBuiltinFurnitureCatalog.byId(model.getValue().trim())!=null&&!MpsqTvFurniture.MODEL.equals(model.getValue().trim());
        model.setResponder(value->figure.active=MpsqBuiltinFurnitureCatalog.byId(value.trim())!=null&&!MpsqTvFurniture.MODEL.equals(value.trim()));
        addRenderableWidget(Button.builder(Component.literal(editing?"Änderungen speichern":"Platzieren / Ersetzen"),b->save(false)).bounds(width/2-120,208,156,20).build());
        addRenderableWidget(Button.builder(Component.literal("Entfernen"),b->save(true)).bounds(width/2+40,208,80,20).build());
        if(editing)addRenderableWidget(Button.builder(Component.literal("Position: X / Y / Z"),b->{
            JsonObject raw=placement.deepCopy();raw.remove("anchor_x");raw.remove("anchor_y");raw.remove("anchor_z");raw.addProperty("x",pos.getX());raw.addProperty("y",pos.getY());raw.addProperty("z",pos.getZ());
            JsonObject current=MpsqFurniturePositions.apply(raw);
            minecraft.gui.setScreen(new MpsqPositionScreen(this,"Möbel positionieren",new net.minecraft.world.phys.Vec3(current.get("x").getAsDouble()+.5,current.get("y").getAsDouble(),current.get("z").getAsDouble()+.5),point->{
                MpsqFurniturePositions.save(raw,point);return java.util.concurrent.CompletableFuture.completedFuture(null);
            }));
        }).bounds(width/2-120,46,240,20).build());
        addRenderableWidget(Button.builder(Component.literal("Schließen"),b->onClose()).bounds(width/2-60,height-28,120,20).build());
    }
    private void save(boolean remove){
        if(pending)return;
        if(MpsqTvFurniture.MODEL.equals(model.getValue().trim()))size=Math.max(1,Math.min(9,Math.round(size*3)))/3f;
        if(!remove&&!model.getValue().trim().matches("[a-z0-9_-]{1,64}")){status="Bitte eine gültige Möbel-ID eingeben.";return;}
        String soundValue=soundId.getValue().trim();
        if(!remove&&(soundValue.length()>128||(!soundValue.isEmpty()&&!soundValue.matches("[a-zA-Z0-9_.:/-]+")))){status="Ungültige Minecraft- oder MPSQ-Sound-ID.";return;}
        BlockPos target=pos;int facing=rotation;
        if(!remove&&model.getValue().trim().equalsIgnoreCase("minecraft-cat-lying")&&minecraft.player!=null){target=minecraft.player.blockPosition();facing=Math.floorMod(Math.round(minecraft.player.getYRot()+180),360)/15*15;}
        if(!remove)try{TeamVisibilitySettings.requirePlacement(target.getX()+.5,target.getY(),target.getZ()+.5);}catch(IllegalArgumentException e){status=e.getMessage();return;}
        final BlockPos saveTarget=target;
        if(MpsqLocalWorldStore.available()){
            pending=true;status="Wird lokal gespeichert…";
            boolean saved=MpsqLocalObjectStore.set(world,target.getX(),target.getY(),target.getZ(),model.getValue().trim(),facing,displayName.getValue().trim(),size,soundValue,MpsqBuiltinFurnitureCatalog.byId(model.getValue().trim())!=null&&!MpsqTvFurniture.MODEL.equals(model.getValue().trim())?skinName:"",remove);
            pending=false;
            status=saved?(remove?"Möbel entfernt.":"In dieser Einzelspielerwelt gespeichert."):(remove?"An dieser Position wurde kein Möbel gefunden.":"Speichern in der Einzelspielerwelt fehlgeschlagen.");
            if(saved){MpsqTvFurniture.saved(target,!remove&&MpsqTvFurniture.MODEL.equals(model.getValue().trim()));if(remove){clearFinePosition();MpsqAccessoryRenderer.removeFurnitureAt(target.getX(),target.getY(),target.getZ());}else MpsqAccessoryRenderer.refresh();}
            return;
        }
        if(!MpsqActionSync.isMpsqServer()){status="Online-Speicher ist nur auf mixelpixel.net verfügbar.";return;}
        JsonObject body=new JsonObject();body.addProperty("server",server);body.addProperty("world",world);body.addProperty("x",target.getX());body.addProperty("y",target.getY());body.addProperty("z",target.getZ());body.addProperty("rotation",facing);body.addProperty("modelId",model.getValue().trim());body.addProperty("displayName",displayName.getValue().trim());body.addProperty("scale",size);body.addProperty("soundId",soundValue);body.addProperty("skinName",skinName);body.addProperty("remove",remove);
        pending=true;status=remove?"Möbel wird entfernt…":"Wird gespeichert…";MpsqApiClient.post("/objects",body).whenComplete((data,error)->minecraft.execute(()->{
            pending=false;
            if(error!=null){status="Fehler: "+error.getMessage();return;}
            boolean ok=data!=null&&data.isJsonObject()&&data.getAsJsonObject().has("ok")&&data.getAsJsonObject().get("ok").getAsBoolean();
            if(!ok){status=remove?"An dieser Position wurde kein Möbel gefunden.":"Speichern fehlgeschlagen.";return;}
            status=remove?"Möbel entfernt.":"Gespeichert.";
            if(remove){clearFinePosition();MpsqAccessoryRenderer.removeFurnitureAt(saveTarget.getX(),saveTarget.getY(),saveTarget.getZ());}else MpsqAccessoryRenderer.refresh();
        }));
    }
    private void clearFinePosition(){if(placement!=null)try{MpsqFurniturePositions.remove(placement);}catch(Exception e){status+=" Feinposition konnte nicht zurückgesetzt werden: "+e.getMessage();}}
    @Override public void extractBackground(GuiGraphicsExtractor c,int x,int y,float d){super.extractBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void extractRenderState(GuiGraphicsExtractor c,int x,int y,float d){super.extractRenderState(c,x,y,d);c.centeredText(font,title,width/2,24,0xFFFFFFFF);if(!editing)c.centeredText(font,"Position: "+pos.toShortString(),width/2,50,0xFFAAAAAA);c.centeredText(font,font.plainSubstrByWidth(status,width-24),width/2,height-46,0xFFFFFFFF);}
    @Override public boolean isPauseScreen(){return false;}
    private static String str(JsonObject o,String key,String fallback){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;}
    private static float readFloat(JsonObject o,String key,float fallback){try{float value=o.has(key)?o.get(key).getAsFloat():fallback;return Float.isFinite(value)?Math.max(0.25f,Math.min(3.0f,value)):fallback;}catch(RuntimeException ignored){return fallback;}}
}



