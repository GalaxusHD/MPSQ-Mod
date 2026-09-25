package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.PlayerEntity;

/** Places a static uploaded NPC model at the block being targeted. */
public final class MpsqNpcPlacementScreen extends Screen {
    private final BlockPos pos;private final Screen parent;private final String server,world;
    private final double worldX,worldY,worldZ;
    private TextFieldWidget asset;private String status="";private boolean pending,facePlayer;private float yaw,pitch;
    public MpsqNpcPlacementScreen(Screen parent,BlockPos support){this(parent,support.up().toImmutable(),support.getX()+0.5,support.getY()+1.0,support.getZ()+0.5,0,0,false);}
    private MpsqNpcPlacementScreen(Screen parent,BlockPos pos,double worldX,double worldY,double worldZ,float yaw,float pitch,boolean facePlayer){super(Text.literal("MPSQ-NPC platzieren"));this.parent=parent;this.pos=pos.toImmutable();this.worldX=worldX;this.worldY=worldY;this.worldZ=worldZ;this.yaw=yaw;this.pitch=pitch;this.facePlayer=facePlayer;server=MpsqActionSync.server();world=MpsqActionSync.world();}
    public static MpsqNpcPlacementScreen atPlayer(Screen parent,PlayerEntity player){return new MpsqNpcPlacementScreen(parent,player.getBlockPos(),player.getX(),player.getY(),player.getZ(),player.getYaw(),player.getPitch(),false);}
    @Override protected void init(){asset=addDrawableChild(new TextFieldWidget(textRenderer,width/2-120,70,240,20,Text.literal("NPC-Modell-ID")));asset.setMaxLength(64);asset.setPlaceholder(Text.literal("ID aus dem Admin-Upload"));
        addDrawableChild(ButtonWidget.builder(Text.literal("NPC platzieren"),b->save(false)).dimensions(width/2-120,102,240,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Blickrichtung: "+(facePlayer?"folgt Spielern":"fest")),b->{facePlayer=!facePlayer;b.setMessage(Text.literal("Blickrichtung: "+(facePlayer?"folgt Spielern":"fest")));}).dimensions(width/2-120,130,240,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("NPCs an dieser Position entfernen"),b->save(true)).dimensions(width/2-120,158,240,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zurück"),b->close()).dimensions(width/2-60,height-28,120,20).build());}
    private void save(boolean remove){if(pending)return;if(!remove&&!asset.getText().trim().matches("[a-z0-9_-]{1,64}")){status="Bitte zuerst eine gültige NPC-Modell-ID aus dem Upload eintragen.";return;}JsonObject b=new JsonObject();b.addProperty("server",server);b.addProperty("world",world);b.addProperty("x",pos.getX());b.addProperty("y",pos.getY());b.addProperty("z",pos.getZ());b.addProperty("positionX",worldX);b.addProperty("positionY",worldY);b.addProperty("positionZ",worldZ);b.addProperty("yaw",yaw);b.addProperty("pitch",pitch);b.addProperty("facePlayer",facePlayer);b.addProperty("assetId",asset.getText().trim());b.addProperty("remove",remove);
        if(server.isBlank()&&client.getServer()!=null){JsonObject local=new JsonObject();local.addProperty("asset_id",asset.getText().trim());local.addProperty("name",asset.getText().trim());local.addProperty("category","npc_model");local.addProperty("world_x",worldX);local.addProperty("world_y",worldY);local.addProperty("world_z",worldZ);local.addProperty("yaw",yaw);local.addProperty("pitch",pitch);local.addProperty("face_player",facePlayer);local.addProperty("scale",1);local.addProperty("glow_color","none");local.addProperty("animation","none");local.addProperty("task_type","none");local.add("interaction_data",new JsonObject());JsonObject saved=MpsqLocalNpcStore.set(world,pos,remove,local);status=saved!=null||remove?"NPC in dieser Einzelspielerwelt gespeichert.":"NPC konnte lokal nicht gespeichert werden.";if(saved!=null)MpsqAccessoryRenderer.refresh();return;}
        if(server.isBlank()){status="Bitte erst einer Welt beitreten.";return;}pending=true;status="Wird gespeichert…";MpsqApiClient.post("/npcs",b).whenComplete((d,e)->client.execute(()->{pending=false;status=e==null?"Gespeichert.":"NPC konnte nicht gespeichert werden: "+e.getMessage();if(e==null){MpsqAccessoryRenderer.refresh();if(!remove&&d.isJsonArray()&&!d.getAsJsonArray().isEmpty()){client.setScreen(new MpsqNpcConfiguratorScreen(this,d.getAsJsonArray().get(0).getAsJsonObject()));}}}));}
    @Override public void renderBackground(DrawContext c,int x,int y,float d){super.renderBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void render(DrawContext c,int x,int y,float d){super.render(c,x,y,d);c.drawCenteredTextWithShadow(textRenderer,title,width/2,24,MpsqTheme.TEXT_TITEL);c.drawCenteredTextWithShadow(textRenderer,String.format(java.util.Locale.ROOT,"Position: %.2f, %.2f, %.2f · Blick: %.0f° / %.0f°",worldX,worldY,worldZ,yaw,pitch),width/2,48,0xFFAAAAAA);c.drawCenteredTextWithShadow(textRenderer,"Der NPC startet mit deiner aktuellen Blickrichtung.",width/2,188,0xFFFFFFFF);c.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(status,width-24),width/2,height-44,0xFFFFFFFF);}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
