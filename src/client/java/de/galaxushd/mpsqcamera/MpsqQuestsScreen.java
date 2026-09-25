package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Player quest board and Officer quest editor for a placed quest NPC. */
public final class MpsqQuestsScreen extends Screen {
    private static final int TOP=76, BOTTOM=50, ROW=46;
    private final Screen parent;
    private final String npcId;
    private JsonArray quests=new JsonArray();
    private String status="Quests werden geladen …";
    private int scroll;
    private boolean pending,editing,dragging;
    private JsonObject edited;
    private TextFieldWidget titleField,iconField,itemField,countField,pointsField,accessoryField;
    private long lastScan;

    public MpsqQuestsScreen(Screen parent,String npcId){super(Text.literal("Quests"));this.parent=parent;this.npcId=npcId;}
    @Override protected void init(){
        if(!editing){
            String path="/npcs/"+npcId+"/quests?server="+enc(MpsqActionSync.server())+"&world="+enc(MpsqActionSync.world());
            MpsqApiClient.get(path).whenComplete((d,e)->client.execute(()->{if(e==null&&d.isJsonArray()){quests=d.getAsJsonArray();status=quests.isEmpty()?"Noch keine Quests vorhanden.":"";}else status="Quests konnten nicht geladen werden.";clamp();}));
            if(staff())addDrawableChild(ButtonWidget.builder(Text.literal("+ Quest hinzufügen"),b->edit(null)).dimensions(width/2-74,46,148,22).build());
        }else initEditor();
    }
    private void initEditor(){int x=width/2-150,y=94,w=300;titleField=field(x,y,w,"Quest-Titel",edited==null?"":str(edited,"title",""));iconField=field(x,y+48,w,"GUI-Icon · minecraft:paper",edited==null?"minecraft:paper":str(edited,"icon_item","minecraft:paper"));itemField=field(x,y+96,w,"Sammel-Item · minecraft:stone",edited==null?"minecraft:stone":str(edited,"objective_item","minecraft:stone"));countField=field(x,y+144,w,"Anzahl",edited==null?"10":str(edited,"target_count","10"));pointsField=field(x,y+192,w,"Punkte-Belohnung (oder 0 für Accessoire)",edited==null?"100":str(edited,"reward_points","0"));accessoryField=field(x,y+240,w,"Accessoire-ID (nur bei 0 Punkten)",edited==null?"":str(edited,"reward_accessory_id",""));
        addDrawableChild(ButtonWidget.builder(Text.literal(pending?"Speichert …":"Quest speichern"),b->save()).dimensions(x,y+292,142,22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Abbrechen"),b->{editing=false;edited=null;clearAndInit();}).dimensions(x+158,y+292,142,22).build());
        if(edited!=null)addDrawableChild(ButtonWidget.builder(Text.literal("Quest löschen"),b->delete()).dimensions(x,y+322,300,22).build());
    }
    private TextFieldWidget field(int x,int y,int w,String hint,String initial){var f=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,22,Text.literal(hint)));f.setMaxLength(128);f.setPlaceholder(Text.literal(hint));f.setText(initial);return f;}
    private boolean staff(){return TeamStateStore.self().map(p->p.permissionRank().level()>=TeamRank.OFFICER.level()).orElse(false);}
    private static String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private static String str(JsonObject o,String key,String fallback){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;}
    private void clamp(){scroll=Math.max(0,Math.min(scroll,Math.max(0,quests.size()*ROW-(height-TOP-BOTTOM))));}
    @Override public void tick(){super.tick();if(editing||client.player==null||System.currentTimeMillis()-lastScan<1500)return;lastScan=System.currentTimeMillis();for(var e:quests){JsonObject q=e.getAsJsonObject();if(!q.has("accepted")||!q.get("accepted").getAsBoolean()||q.get("claimed").getAsBoolean())continue;String itemId=str(q,"objective_item","minecraft:air");try{var item=Registries.ITEM.get(Identifier.of(itemId));int count=0;var inventory=client.player.getInventory();for(int slot=0;slot<inventory.size();slot++){var stack=inventory.getStack(slot);if(stack.isOf(item))count+=stack.getCount();}count=Math.min(count,q.get("target_count").getAsInt());if(count>q.get("progress").getAsInt())sendProgress(q,count);}catch(IllegalArgumentException ignored){}}
    }
    private void sendProgress(JsonObject quest,int count){JsonObject body=new JsonObject();body.addProperty("progress",count);MpsqApiClient.post("/quests/"+quest.get("id").getAsString()+"/progress",body).whenComplete((d,e)->client.execute(()->{if(e==null){quest.addProperty("progress",count);status="Quest-Fortschritt gespeichert.";}}));}
    @Override public void renderBackground(DrawContext c,int x,int y,float delta){super.renderBackground(c,x,y,delta);MpsqTheme.drawBackground(c,width,height);}
    @Override public void render(DrawContext c,int mx,int my,float delta){super.render(c,mx,my,delta);int center=width/2;c.drawCenteredTextWithShadow(textRenderer,title,center,20,0xFFFF7182);
        if(editing){c.drawCenteredTextWithShadow(textRenderer,"QUEST STUDIO",center,68,0xFFFFFFFF);c.drawTextWithShadow(textRenderer,"Belohnung: Punkte oder genau ein Accessoire",center-150,390,0xFFBBBBBB);c.drawCenteredTextWithShadow(textRenderer,status,center,height-26,0xFFFFA0AA);}
        else {c.drawCenteredTextWithShadow(textRenderer,"Linksklick: annehmen / ablehnen / Belohnung · Rechtsklick: Fortschritt",center,55,0xFFBBBBBB);int bottom=height-BOTTOM;c.enableScissor(center-178,TOP,center+178,bottom);
            for(int i=0;i<quests.size();i++){int yy=TOP+i*ROW-scroll;if(yy+ROW<TOP||yy>bottom)continue;JsonObject q=quests.get(i).getAsJsonObject();c.fill(center-174,yy,center+174,yy+ROW-3,i%2==0?0x88404755:0x66404755);
                drawItem(c,str(q,"icon_item","minecraft:paper"),center-164,yy+7);c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(str(q,"title","Quest"),220),center-139,yy+6,0xFFFFFFFF);
                String item=str(q,"objective_item","minecraft:stone");int prog=q.has("progress")?q.get("progress").getAsInt():0,target=q.get("target_count").getAsInt();boolean accepted=q.has("accepted")&&q.get("accepted").getAsBoolean(),claimed=q.has("claimed")&&q.get("claimed").getAsBoolean();
                c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(item+"  "+prog+" / "+target+"   ·   "+rewardLabel(q),260),center-139,yy+22,0xFFCCCCCC);
                String action=claimed?"Abgeholt":!accepted?"Annehmen":prog>=target?"Belohnung abholen":"Ablehnen";c.drawTextWithShadow(textRenderer,action,center+55,yy+22,claimed?0xFF77DD99:0xFFFFA0AA);
                c.fill(center-139,yy+36,center+164,yy+40,0xFF20232C);float ratio=target<=0?0:Math.min(1f,(float)prog/target);c.fill(center-139,yy+36,center-139+(int)(303*ratio),yy+40,0xFFFF536A);
            }c.disableScissor();drawScrollbar(c,center+181,TOP,bottom);c.drawCenteredTextWithShadow(textRenderer,status,center,height-31,0xFFFFFFFF);}
        c.drawTextWithShadow(textRenderer,Text.literal("Zurück"),10,height-18,0xFFFFFFFF);
    }
    private String rewardLabel(JsonObject q){int points=q.has("reward_points")?q.get("reward_points").getAsInt():0;return points>0?points+" MPSQ-Punkte":"Accessoire";}
    private void drawItem(DrawContext c,String id,int x,int y){try{var item=Registries.ITEM.get(Identifier.of(id));if(item!=Items.AIR)c.drawItem(new ItemStack(item),x,y);}catch(IllegalArgumentException ignored){}}
    private int maxScroll(){return Math.max(0,quests.size()*ROW-(height-TOP-BOTTOM));}
    private void drawScrollbar(DrawContext c,int x,int top,int bottom){int visible=bottom-top,content=quests.size()*ROW;if(content<=visible)return;int thumb=Math.max(20,visible*visible/content),travel=visible-thumb,y=top+(maxScroll()==0?0:travel*scroll/maxScroll());c.fill(x,top,x+4,bottom,0x66000000);c.fill(x,y,x+4,y+thumb,dragging?0xFFFF7182:0xFFBBBBBB);}
    private void scrollTo(double y){int visible=height-TOP-BOTTOM,thumb=Math.max(20,visible*visible/Math.max(1,quests.size()*ROW)),travel=Math.max(1,visible-thumb);scroll=(int)Math.round(Math.max(0,Math.min(travel,y-TOP-thumb/2))*maxScroll()/travel);clamp();}
    private void edit(JsonObject q){edited=q==null?null:q.deepCopy();editing=true;status="";clearAndInit();}
    private void save(){if(pending)return;try{String title=titleField.getText().trim(),icon=iconField.getText().trim(),item=itemField.getText().trim();int count=Integer.parseInt(countField.getText().trim()),points=Integer.parseInt(pointsField.getText().trim());String accessory=accessoryField.getText().trim();if(title.isEmpty()||count<1||points<0||(points==0&&accessory.isEmpty())||(points>0&&!accessory.isEmpty()))throw new IllegalArgumentException();JsonObject body=new JsonObject();body.addProperty("server",MpsqActionSync.server());body.addProperty("world",MpsqActionSync.world());body.addProperty("title",title);body.addProperty("iconItem",icon);body.addProperty("objectiveItem",item);body.addProperty("targetCount",count);body.addProperty("rewardPoints",points);body.addProperty("rewardAccessoryId",accessory);if(edited!=null)body.addProperty("questId",edited.get("id").getAsString());pending=true;MpsqApiClient.post("/npcs/"+npcId+"/quests",body).whenComplete((d,e)->client.execute(()->{pending=false;if(e==null){editing=false;edited=null;status="Quest gespeichert.";clearAndInit();}else status="Speichern fehlgeschlagen: "+e.getMessage();}));}catch(RuntimeException ex){status="Bitte Titel, Item, Anzahl und genau eine gültige Belohnung eintragen.";}}
    private void delete(){if(pending||edited==null)return;JsonObject body=new JsonObject();body.addProperty("server",MpsqActionSync.server());body.addProperty("world",MpsqActionSync.world());body.addProperty("action","delete");body.addProperty("questId",edited.get("id").getAsString());pending=true;MpsqApiClient.post("/npcs/"+npcId+"/quests",body).whenComplete((d,e)->client.execute(()->{pending=false;if(e==null){editing=false;edited=null;status="Quest gelöscht.";clearAndInit();}else status="Löschen fehlgeschlagen.";}));}
    @Override public boolean mouseClicked(double x,double y,int button){if(editing)return super.mouseClicked(x,y,button);int c=width/2;if(y>=TOP&&y<height-BOTTOM&&x>=c-178&&x<=c+178){int i=(int)(y-TOP+scroll)/ROW;if(i>=0&&i<quests.size()){JsonObject q=quests.get(i).getAsJsonObject();if(button==1&&staff()){edit(q);return true;}if(button==1){float p=(float)(q.get("progress").getAsInt())/Math.max(1,q.get("target_count").getAsInt());String id=q.get("id").getAsString();MpsqBossbarManager.apply(new MpsqBossbarState("quest-"+id,str(q,"title","Quest"),"purple",p,true));status="Fortschritt wird oben angezeigt.";return true;}if(button==0){if(!q.get("accepted").getAsBoolean()){act(q,"accept");return true;}if(q.get("progress").getAsInt()>=q.get("target_count").getAsInt()&&!q.get("claimed").getAsBoolean()){act(q,"claim");return true;}if(!q.get("claimed").getAsBoolean()){act(q,"decline");return true;}}}}
        if(button==0&&x>=c+179&&x<=c+188&&y>=TOP&&y<height-BOTTOM&&maxScroll()>0){dragging=true;scrollTo(y);return true;}if(y>=height-26&&x<100){close();return true;}return super.mouseClicked(x,y,button);}
    private void act(JsonObject q,String action){if(pending)return;pending=true;MpsqApiClient.post("/quests/"+q.get("id").getAsString()+"/"+action,new JsonObject()).whenComplete((d,e)->client.execute(()->{pending=false;if(e==null){if(action.equals("accept"))q.addProperty("accepted",true);else if(action.equals("decline")){q.addProperty("accepted",false);q.addProperty("progress",0);}else q.addProperty("claimed",true);if(action.equals("claim")){int points=q.get("reward_points").getAsInt();status=points>0?"Questbelohnung: "+points+" MPSQ-Punkte.":"Quest-Accessoire freigeschaltet.";}else status=action.equals("decline")?"Quest abgelehnt.":"Quest angenommen.";}else status="Aktion fehlgeschlagen: "+e.getMessage();}));}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){if(!editing){scroll-=(int)Math.signum(v)*ROW*3;clamp();return true;}return super.mouseScrolled(x,y,h,v);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging&&button==0){scrollTo(y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0)dragging=false;return super.mouseReleased(x,y,button);}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
