package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Places a static uploaded NPC model at the block being targeted. */
public final class MpsqNpcPlacementScreen extends Screen {
    private final BlockPos pos;private final Screen parent;private final String server,world;
    private TextFieldWidget asset;private String status="";private boolean pending;
    public MpsqNpcPlacementScreen(Screen parent,BlockPos support){super(Text.literal("MPSQ-NPC platzieren"));this.parent=parent;this.pos=support.up().toImmutable();server=MpsqActionSync.server();world=MpsqActionSync.world();}
    @Override protected void init(){asset=addDrawableChild(new TextFieldWidget(textRenderer,width/2-120,70,240,20,Text.literal("NPC-Modell-ID")));asset.setMaxLength(64);asset.setPlaceholder(Text.literal("ID aus dem Admin-Upload"));
        addDrawableChild(ButtonWidget.builder(Text.literal("NPC platzieren"),b->save(false)).dimensions(width/2-120,102,240,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("NPCs an diesem Block entfernen"),b->save(true)).dimensions(width/2-120,130,240,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zurück"),b->close()).dimensions(width/2-60,height-28,120,20).build());}
    private void save(boolean remove){if(pending)return;JsonObject b=new JsonObject();b.addProperty("server",server);b.addProperty("world",world);b.addProperty("x",pos.getX());b.addProperty("y",pos.getY());b.addProperty("z",pos.getZ());b.addProperty("assetId",asset.getText().trim());b.addProperty("remove",remove);pending=true;status="Wird gespeichert…";MpsqApiClient.post("/npcs",b).whenComplete((d,e)->client.execute(()->{pending=false;status=e==null?"Gespeichert.":"NPC konnte nicht gespeichert werden: "+e.getMessage();if(e==null){MpsqAccessoryRenderer.refresh();if(!remove&&d.isJsonArray()&&!d.getAsJsonArray().isEmpty()){client.setScreen(new MpsqNpcConfiguratorScreen(this,d.getAsJsonArray().get(0).getAsJsonObject()));}}}));}
    @Override public void renderBackground(DrawContext c,int x,int y,float d){super.renderBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void render(DrawContext c,int x,int y,float d){super.render(c,x,y,d);c.drawCenteredTextWithShadow(textRenderer,title,width/2,24,MpsqTheme.TEXT_TITEL);c.drawCenteredTextWithShadow(textRenderer,"Position: "+pos.toShortString(),width/2,48,0xFFAAAAAA);c.drawTextWithShadow(textRenderer,"NPC danach konfigurieren: Name, Größe, Farbe, Dialog und Animation",width/2-155,94,0xFFFFFFFF);c.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(status,width-24),width/2,height-44,0xFFFFFFFF);}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
