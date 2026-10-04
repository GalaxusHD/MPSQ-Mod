package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.PlayerEntity;

/** Places a normal- or slim-skin NPC at the block being targeted. */
public final class MpsqNpcPlacementScreen extends Screen {
    private final BlockPos pos;private final Screen parent;private final String server,world;
    private final double worldX,worldY,worldZ;
    private TextFieldWidget asset;private String status="";private boolean pending,facePlayer;private float yaw,pitch;private String modelCategory="npc_skin_normal";
    public MpsqNpcPlacementScreen(Screen parent,BlockPos support){this(parent,support.up().toImmutable(),support.getX()+0.5,support.getY()+1.0,support.getZ()+0.5,0,0,false);}
    private MpsqNpcPlacementScreen(Screen parent,BlockPos pos,double worldX,double worldY,double worldZ,float yaw,float pitch,boolean facePlayer){super(Text.literal("MPSQ-NPC platzieren"));this.parent=parent;this.pos=pos.toImmutable();this.worldX=worldX;this.worldY=worldY;this.worldZ=worldZ;this.yaw=yaw;this.pitch=pitch;this.facePlayer=facePlayer;server=MpsqActionSync.server();world=MpsqActionSync.world();}
    public static MpsqNpcPlacementScreen atPlayer(Screen parent,PlayerEntity player){BlockPos block=player.getBlockPos();return new MpsqNpcPlacementScreen(parent,block,block.getX()+0.5,block.getY(),block.getZ()+0.5,player.getYaw(),player.getPitch(),false);}
    @Override protected void init(){asset=addDrawableChild(new TextFieldWidget(textRenderer,width/2-120,70,145,20,Text.literal("Skin-ID")));asset.setMaxLength(64);asset.setPlaceholder(Text.literal("Skin-ID"));if(MpsqAccessoryRenderer.WUMPUS_ASSET_ID.equals(modelCategory)){asset.setText(MpsqAccessoryRenderer.WUMPUS_ASSET_ID);asset.setEditable(false);}
        addDrawableChild(ButtonWidget.builder(Text.literal("Katalog"),b->client.setScreen(new MpsqModelsScreen(this))).dimensions(width/2+30,70,90,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("NPC platzieren"),b->save(false)).dimensions(width/2-120,102,240,20).build());
        addDrawableChild(ButtonWidget.builder(categoryText(),b->{modelCategory=switch(modelCategory){case "npc_skin_normal"->"npc_skin_slim";case "npc_skin_slim"->"npc_model";default->"npc_skin_normal";};if("npc_model".equals(modelCategory)){asset.setText(MpsqAccessoryRenderer.WUMPUS_ASSET_ID);asset.setEditable(false);}else{asset.setEditable(true);asset.setText("");asset.setPlaceholder(Text.literal("Skin-ID"));}b.setMessage(categoryText());}).dimensions(width/2-120,130,240,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Blickrichtung: "+(facePlayer?"folgt Spielern":"fest")),b->{facePlayer=!facePlayer;b.setMessage(Text.literal("Blickrichtung: "+(facePlayer?"folgt Spielern":"fest")));}).dimensions(width/2-120,158,240,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("NPCs an dieser Position entfernen"),b->save(true)).dimensions(width/2-120,186,240,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zurück"),b->close()).dimensions(width/2-60,height-28,120,20).build());}
    private void save(boolean remove){if(pending)return;String assetId="npc_model".equals(modelCategory)?MpsqAccessoryRenderer.WUMPUS_ASSET_ID:asset.getText().trim();if(!remove&&!assetId.matches("[a-z0-9_-]{1,64}")){status="Bitte zuerst eine gültige Skin-ID eintragen.";return;}JsonObject b=new JsonObject();b.addProperty("server",server);b.addProperty("world",world);b.addProperty("x",pos.getX());b.addProperty("y",pos.getY());b.addProperty("z",pos.getZ());b.addProperty("positionX",worldX);b.addProperty("positionY",worldY);b.addProperty("positionZ",worldZ);b.addProperty("yaw",yaw);b.addProperty("pitch",pitch);b.addProperty("facePlayer",facePlayer||"npc_model".equals(modelCategory));b.addProperty("assetId",assetId);b.addProperty("category",modelCategory);b.addProperty("remove",remove);
        if(server.isBlank()&&client.getServer()!=null){JsonObject local=new JsonObject();local.addProperty("asset_id",assetId);local.addProperty("name","npc_model".equals(modelCategory)?"Wumpus":assetId);local.addProperty("category",modelCategory);local.addProperty("world_x",worldX);local.addProperty("world_y",worldY);local.addProperty("world_z",worldZ);local.addProperty("yaw",yaw);local.addProperty("pitch",pitch);local.addProperty("face_player",facePlayer||"npc_model".equals(modelCategory));local.addProperty("scale",1);local.addProperty("glow_color","none");local.addProperty("animation","none");local.addProperty("task_type","none");local.add("interaction_data",new JsonObject());JsonObject saved=MpsqLocalNpcStore.set(world,pos,remove,local);status=saved!=null||remove?"NPC in dieser Einzelspielerwelt gespeichert.":"NPC konnte lokal nicht gespeichert werden.";if(saved!=null){MpsqAccessoryRenderer.refresh();client.setScreen(new MpsqNpcConfiguratorScreen(this,saved));}return;}
        if(server.isBlank()){status="Bitte einer Einzelspielerwelt oder mixelpixel.net beitreten.";return;}if(!MpsqActionSync.isMpsqServer()){status="Online-Speicher ist nur auf mixelpixel.net verfügbar.";return;}pending=true;status="Wird gespeichert…";MpsqApiClient.post("/npcs",b).whenComplete((d,e)->client.execute(()->{pending=false;status=e==null?"Gespeichert.":"NPC konnte nicht gespeichert werden: "+e.getMessage();if(e==null){MpsqAccessoryRenderer.refresh();if(!remove&&d.isJsonArray()&&!d.getAsJsonArray().isEmpty()){client.setScreen(new MpsqNpcConfiguratorScreen(this,d.getAsJsonArray().get(0).getAsJsonObject()));}}}));}
    @Override public void renderBackground(DrawContext c,int x,int y,float d){super.renderBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void render(DrawContext c,int x,int y,float d){super.render(c,x,y,d);c.drawCenteredTextWithShadow(textRenderer,title,width/2,24,MpsqTheme.TEXT_TITEL);c.drawCenteredTextWithShadow(textRenderer,String.format(java.util.Locale.ROOT,"Position: %.2f, %.2f, %.2f · Blick: %.0f° / %.0f°",worldX,worldY,worldZ,yaw,pitch),width/2,48,0xFFAAAAAA);c.drawCenteredTextWithShadow(textRenderer,"Der NPC startet mit deiner aktuellen Blickrichtung.",width/2,188,0xFFFFFFFF);c.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(status,width-24),width/2,height-44,0xFFFFFFFF);}
    @Override public void close(){client.setScreen(parent);}
    private Text categoryText(){return Text.literal("NPC Modell: "+switch(modelCategory){case "npc_skin_slim"->"Skin Slim";case "npc_model"->"Wumpus";default->"Skin Normal";});}
    @Override public boolean shouldPause(){return false;}
}
