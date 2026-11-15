package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Places a normal- or slim-skin NPC at the block being targeted. */
public final class MpsqNpcPlacementScreen extends Screen {
    private final BlockPos pos;private final Screen parent;private final String server,world;
    private final double worldX,worldY,worldZ;
    private EditBox asset;private String status="";private boolean pending,facePlayer;private float yaw,pitch;private String modelCategory="npc_skin_normal";
    public MpsqNpcPlacementScreen(Screen parent,BlockPos support){this(parent,support.above().immutable(),support.getX()+0.5,support.getY()+1.0,support.getZ()+0.5,0,0,false);}
    private MpsqNpcPlacementScreen(Screen parent,BlockPos pos,double worldX,double worldY,double worldZ,float yaw,float pitch,boolean facePlayer){super(Component.literal("MPSQ-NPC platzieren"));this.parent=parent;this.pos=pos.immutable();this.worldX=worldX;this.worldY=worldY;this.worldZ=worldZ;this.yaw=MpsqCinematicScene.snapRotation(yaw);this.pitch=MpsqCinematicScene.snapRotation(pitch);this.facePlayer=facePlayer;server=MpsqActionSync.server();world=MpsqActionSync.world();}
    public static MpsqNpcPlacementScreen atPlayer(Screen parent,Player player){BlockPos block=player.blockPosition();return new MpsqNpcPlacementScreen(parent,block,block.getX()+0.5,block.getY(),block.getZ()+0.5,player.getYRot(),player.getXRot(),false);}
    @Override protected void init(){asset=addRenderableWidget(new EditBox(font,width/2-120,70,145,20,Component.literal("Skin-ID")));asset.setMaxLength(64);asset.setHint(Component.literal("Skin-ID"));if(MpsqAccessoryRenderer.WUMPUS_ASSET_ID.equals(modelCategory)){asset.setValue(MpsqAccessoryRenderer.WUMPUS_ASSET_ID);asset.setEditable(false);}
        addRenderableWidget(Button.builder(Component.literal("Katalog"),b->minecraft.gui.setScreen(new MpsqModelsScreen(this))).bounds(width/2+30,70,90,20).build());
        addRenderableWidget(Button.builder(Component.literal("NPC platzieren"),b->save(false)).bounds(width/2-120,102,240,20).build());
        addRenderableWidget(Button.builder(categoryText(),b->{modelCategory=switch(modelCategory){case "npc_skin_normal","npc_skin_slim"->"npc_model";default->"npc_skin_normal";};if("npc_model".equals(modelCategory)){asset.setValue(MpsqAccessoryRenderer.WUMPUS_ASSET_ID);asset.setEditable(false);}else{asset.setEditable(true);asset.setValue("");asset.setHint(Component.literal("Skin-ID"));}b.setMessage(categoryText());}).bounds(width/2-120,130,240,20).build());
        addRenderableWidget(Button.builder(Component.literal("Blickrichtung: "+(facePlayer?"folgt Spielern":"fest")),b->{facePlayer=!facePlayer;b.setMessage(Component.literal("Blickrichtung: "+(facePlayer?"folgt Spielern":"fest")));}).bounds(width/2-120,158,240,20).build());
        addRenderableWidget(Button.builder(Component.literal("NPCs an dieser Position entfernen"),b->save(true)).bounds(width/2-120,186,240,20).build());
        addRenderableWidget(Button.builder(Component.literal("Zurück"),b->onClose()).bounds(width/2-60,height-28,120,20).build());}
    private void save(boolean remove){if(pending)return;if(!remove)try{TeamVisibilitySettings.requirePlacement(worldX,worldY,worldZ);}catch(IllegalArgumentException e){status=e.getMessage();return;}String assetId="npc_model".equals(modelCategory)?MpsqAccessoryRenderer.WUMPUS_ASSET_ID:asset.getValue().trim();if(!remove&&!assetId.matches("[a-z0-9_-]{1,64}")){status="Bitte zuerst eine gültige Skin-ID eintragen.";return;}JsonObject b=new JsonObject();b.addProperty("server",server);b.addProperty("world",world);b.addProperty("x",pos.getX());b.addProperty("y",pos.getY());b.addProperty("z",pos.getZ());b.addProperty("positionX",worldX);b.addProperty("positionY",worldY);b.addProperty("positionZ",worldZ);b.addProperty("yaw",yaw);b.addProperty("pitch",pitch);b.addProperty("facePlayer",facePlayer||"npc_model".equals(modelCategory));b.addProperty("assetId",assetId);b.addProperty("category",modelCategory);b.addProperty("remove",remove);
        if(server.isBlank()&&minecraft.getSingleplayerServer()!=null){JsonObject local=new JsonObject();local.addProperty("asset_id",assetId);local.addProperty("name","npc_model".equals(modelCategory)?"Wumpus":assetId);local.addProperty("category",modelCategory);local.addProperty("world_x",worldX);local.addProperty("world_y",worldY);local.addProperty("world_z",worldZ);local.addProperty("yaw",yaw);local.addProperty("pitch",pitch);local.addProperty("face_player",facePlayer||"npc_model".equals(modelCategory));local.addProperty("scale",1);local.addProperty("glow_color","none");local.addProperty("animation","none");local.addProperty("task_type","none");local.add("interaction_data",new JsonObject());JsonObject saved=MpsqLocalNpcStore.set(world,pos,remove,local);status=saved!=null||remove?"NPC in dieser Einzelspielerwelt gespeichert.":"NPC konnte lokal nicht gespeichert werden.";if(saved!=null){MpsqAccessoryRenderer.refresh();MpsqScenes.open(saved);}return;}
        if(server.isBlank()){status="Bitte einer Einzelspielerwelt oder mixelpixel.net beitreten.";return;}if(!MpsqActionSync.isMpsqServer()){status="Online-Speicher ist nur auf mixelpixel.net verfügbar.";return;}pending=true;status="Wird gespeichert…";MpsqApiClient.post("/npcs",b).whenComplete((d,e)->minecraft.execute(()->{pending=false;status=e==null?"Gespeichert.":"NPC konnte nicht gespeichert werden: "+e.getMessage();if(e==null){MpsqAccessoryRenderer.refresh();if(!remove&&d.isJsonArray()&&!d.getAsJsonArray().isEmpty()){MpsqScenes.open(d.getAsJsonArray().get(0).getAsJsonObject());}}}));}
    @Override public void extractBackground(GuiGraphicsExtractor c,int x,int y,float d){super.extractBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void extractRenderState(GuiGraphicsExtractor c,int x,int y,float d){super.extractRenderState(c,x,y,d);c.centeredText(font,title,width/2,24,MpsqTheme.TEXT_TITEL);c.centeredText(font,String.format(java.util.Locale.ROOT,"Position: %.2f, %.2f, %.2f · Blick: %.0f° / %.0f°",worldX,worldY,worldZ,yaw,pitch),width/2,48,0xFFAAAAAA);c.centeredText(font,"Der NPC startet mit deiner aktuellen Blickrichtung.",width/2,212,0xFFFFFFFF);c.centeredText(font,font.plainSubstrByWidth(status,width-24),width/2,height-44,0xFFFFFFFF);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    private Component categoryText(){return Component.literal("NPC Modell: "+switch(modelCategory){case "npc_skin_slim"->"Skin Slim";case "npc_model"->"Wumpus";default->"Spieler-Skin (automatisch)";});}
    @Override public boolean isPauseScreen(){return false;}
}
