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
    private TextFieldWidget model;
    private int rotation;
    private String status="";
    private boolean pending;
    public MpsqObjectScreen(BlockPos support){super(Text.literal("MPSQ-Objekt platzieren"));pos=support.up().toImmutable();server=MpsqActionSync.server();world=MpsqActionSync.world();}
    @Override protected void init(){
        model=addDrawableChild(new TextFieldWidget(textRenderer,width/2-120,70,145,20,Text.literal("Modell-ID")));model.setMaxLength(64);model.setPlaceholder(Text.literal("Modell-ID"));
        addDrawableChild(ButtonWidget.builder(Text.literal("Katalog"),b->client.setScreen(new MpsqModelsScreen(this))).dimensions(width/2+30,70,90,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Drehung: "+rotation+"°"),b->{rotation=(rotation+90)%360;b.setMessage(Text.literal("Drehung: "+rotation+"°"));}).dimensions(width/2-120,98,240,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Platzieren / Ersetzen"),b->save(false)).dimensions(width/2-120,126,240,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Objekt hier entfernen"),b->save(true)).dimensions(width/2-120,154,240,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"),b->close()).dimensions(width/2-60,height-28,120,20).build());
    }
    private void save(boolean remove){
        if(pending)return;
        if(!remove&&!model.getText().trim().matches("[a-z0-9_-]{1,64}")){status="Bitte eine gültige Möbel-ID eingeben.";return;}
        BlockPos target=pos;int facing=rotation;
        if(!remove&&model.getText().trim().equalsIgnoreCase("minecraft-cat-lying")&&client.player!=null){target=client.player.getBlockPos();facing=Math.floorMod(Math.round(client.player.getYaw()+180),360)/90*90;}
        if(server.isBlank() && client.getServer()!=null){
            pending=true;status="Wird lokal gespeichert…";
            boolean saved=MpsqLocalObjectStore.set(world,target.getX(),target.getY(),target.getZ(),model.getText().trim(),facing,remove);
            pending=false;
            status=saved?"In dieser Einzelspielerwelt gespeichert.":"Speichern in der Einzelspielerwelt fehlgeschlagen.";
            if(saved)MpsqAccessoryRenderer.refresh();
            return;
        }
        if(!MpsqActionSync.isMpsqServer()){status="Online-Speicher ist nur auf mixelpixel.net verfügbar.";return;}
        JsonObject body=new JsonObject();body.addProperty("server",server);body.addProperty("world",world);body.addProperty("x",target.getX());body.addProperty("y",target.getY());body.addProperty("z",target.getZ());body.addProperty("rotation",facing);body.addProperty("modelId",model.getText());body.addProperty("remove",remove);
        pending=true;status="Wird gespeichert…";MpsqApiClient.post("/objects",body).whenComplete((data,error)->client.execute(()->{pending=false;status=error==null?"Gespeichert.":"Fehler: "+error.getMessage();if(error==null)MpsqAccessoryRenderer.refresh();}));
    }
    @Override public void renderBackground(DrawContext c,int x,int y,float d){super.renderBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void render(DrawContext c,int x,int y,float d){super.render(c,x,y,d);c.drawCenteredTextWithShadow(textRenderer,title,width/2,24,0xFFFFFFFF);c.drawCenteredTextWithShadow(textRenderer,"Position: "+pos.toShortString(),width/2,50,0xFFAAAAAA);c.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(status,width-24),width/2,height-46,0xFFFFFFFF);}
    @Override public boolean shouldPause(){return false;}
}
