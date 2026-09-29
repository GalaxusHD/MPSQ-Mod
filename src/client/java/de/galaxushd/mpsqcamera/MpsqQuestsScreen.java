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
    private static final int TOP=108, BOTTOM=48, CARD_HEIGHT=108, GAP=8, COLUMNS=3;
    private final Screen parent;
    private final String npcId;
    private JsonArray quests=new JsonArray();
    private String status="Quests werden geladen …";
    private int scroll;
    private boolean pending,editing,dragging;
    private boolean accessoryCatalogLoaded,accessoryCatalogLoading;
    private JsonObject edited;
    private JsonArray accessoryCatalog=new JsonArray();
    private int accessoryCursor=-1;
    private TextFieldWidget titleField,descriptionField,iconField,itemField,countField,pointsField,accessoryField;
    private ButtonWidget saveButton;
    private long lastScan;
    private boolean localWorld(){return MpsqActionSync.server().isBlank()&&MpsqLocalWorldStore.available();}
    private boolean canEdit(){return staff()||localWorld();}

    public MpsqQuestsScreen(Screen parent,String npcId){super(Text.literal("Quests"));this.parent=parent;this.npcId=npcId;}

    @Override protected void init(){
        if(!editing){
            if(localWorld()){loadLocalQuests();return;}
            String path="/npcs/"+npcId+"/quests?server="+enc(MpsqActionSync.server())+"&world="+enc(MpsqActionSync.world());
            MpsqApiClient.get(path).whenComplete((data,error)->client.execute(()->{
                if(error==null&&data.isJsonArray()){quests=data.getAsJsonArray();status=quests.isEmpty()?"Noch keine Quests vorhanden.":"";}
                else status="Quests konnten nicht geladen werden.";
                clamp();
            }));
            if(canEdit())addDrawableChild(ButtonWidget.builder(Text.literal("+ Quest"),b->edit(null)).dimensions(width/2-52,45,104,22).build());
        }else {initEditor();loadAccessoryCatalog();}
    }

    private void loadLocalQuests(){quests=new JsonArray();for(JsonElement e:MpsqLocalWorldStore.array("quests")){if(!e.isJsonObject())continue;JsonObject q=e.getAsJsonObject();if(npcId.equals(str(q,"npc_id","")))quests.add(q.deepCopy());}status=quests.isEmpty()?"Noch keine Quests vorhanden.":"Questdaten dieser privaten Welt geladen.";}

    private void initEditor(){
        int x=width/2-150,y=88,w=240;
        titleField=field(x,y,w,"Quest-Titel",edited==null?"":str(edited,"title",""),80);
        descriptionField=field(x,y+36,300,"Aufgabe / Beschreibung",edited==null?"":str(edited,"description",""),240);
        iconField=field(x,y+72,w,"GUI-Icon · minecraft:paper",edited==null?"minecraft:paper":str(edited,"icon_item","minecraft:paper"),128);
        addDrawableChild(ButtonWidget.builder(Text.literal("Hand"),b->useMainHand(iconField)).dimensions(x+248,y+72,52,22).build());
        itemField=field(x,y+108,w,"Sammelziel · minecraft:stone",edited==null?"minecraft:stone":str(edited,"objective_item","minecraft:stone"),128);
        addDrawableChild(ButtonWidget.builder(Text.literal("Hand"),b->useMainHand(itemField)).dimensions(x+248,y+108,52,22).build());
        countField=field(x,y+144,300,"Benötigte Anzahl",edited==null?"10":str(edited,"target_count","10"),8);
        pointsField=field(x,y+180,300,"Punktebelohnung (0 = Accessoire)",edited==null?"100":str(edited,"reward_points","0"),10);
        accessoryField=field(x,y+216,w,"Accessoire-ID · nur bei 0 Punkten",edited==null?"":str(edited,"reward_accessory_id",""),64);
        addDrawableChild(ButtonWidget.builder(Text.literal("Katalog"),b->chooseAccessory()).dimensions(x+248,y+216,52,22).build());
        saveButton=addDrawableChild(ButtonWidget.builder(Text.literal(pending?"Speichert …":"Quest speichern"),b->save()).dimensions(x,y+254,142,22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Abbrechen"),b->{editing=false;edited=null;clearAndInit();}).dimensions(x+158,y+254,142,22).build());
        if(edited!=null)addDrawableChild(ButtonWidget.builder(Text.literal("Quest löschen"),b->delete()).dimensions(x,y+282,300,22).build());
    }

    private TextFieldWidget field(int x,int y,int w,String hint,String initial,int max){
        var field=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,22,Text.literal(hint)));
        field.setMaxLength(max);field.setPlaceholder(Text.literal(hint));field.setText(initial);return field;
    }

    private void useMainHand(TextFieldWidget field){
        if(client.player==null)return;
        ItemStack stack=client.player.getMainHandStack();
        if(stack.isEmpty()){status="Halte zuerst das gewünschte Item in der Haupthand.";return;}
        field.setText(Registries.ITEM.getId(stack.getItem()).toString());status="Item übernommen: "+field.getText();
    }

    private void loadAccessoryCatalog(){
        if(accessoryCatalogLoaded||accessoryCatalogLoading)return;accessoryCatalogLoading=true;
        MpsqApiClient.get("/accessory-catalog").whenComplete((data,error)->client.execute(()->{
            accessoryCatalogLoading=false;accessoryCatalogLoaded=true;
            if(error==null&&data.isJsonArray())accessoryCatalog=data.getAsJsonArray();
        }));
    }

    private void chooseAccessory(){
        if(accessoryCatalog.size()==0){status=accessoryCatalogLoading?"Accessoire-Katalog lädt …":"Kein Shop-Accessoire verfügbar.";return;}
        for(int step=1;step<=accessoryCatalog.size();step++){
            int index=(accessoryCursor+step)%accessoryCatalog.size();JsonObject row=accessoryCatalog.get(index).getAsJsonObject();
            if(!row.has("accessory_id")||row.get("accessory_id").isJsonNull())continue;
            accessoryCursor=index;accessoryField.setText(row.get("accessory_id").getAsString());pointsField.setText("0");
            status="Belohnung gewählt: "+str(row,"display_name","Accessoire");return;
        }
        status="Im Shop-Katalog gibt es noch keine eingerichteten Accessoire-Belohnungen.";
    }

    private boolean staff(){return TeamStateStore.self().map(p->p.permissionRank().level()>=TeamRank.OFFICER.level()).orElse(false);}
    private static String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private static String str(JsonObject o,String key,String fallback){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;}
    private int boardWidth(){return Math.min(Math.max(0,width-24),900);}
    private int boardLeft(){return (width-boardWidth())/2;}
    private int cardWidth(){return (boardWidth()-16-GAP*(COLUMNS-1))/COLUMNS;}
    private int contentHeight(){int rows=(quests.size()+COLUMNS-1)/COLUMNS;return rows==0?0:rows*(CARD_HEIGHT+GAP)-GAP;}
    private int maxScroll(){return Math.max(0,contentHeight()-(height-TOP-BOTTOM));}
    private void clamp(){scroll=Math.max(0,Math.min(scroll,maxScroll()));}

    @Override public void tick(){
        super.tick();if(editing||client.player==null||System.currentTimeMillis()-lastScan<1500)return;lastScan=System.currentTimeMillis();
        for(var element:quests){JsonObject q=element.getAsJsonObject();if(!q.has("accepted")||!q.get("accepted").getAsBoolean()||q.get("claimed").getAsBoolean())continue;
            String itemId=str(q,"objective_item","minecraft:air");try{var item=Registries.ITEM.get(Identifier.of(itemId));int count=0;var inventory=client.player.getInventory();for(int slot=0;slot<inventory.size();slot++){var stack=inventory.getStack(slot);if(stack.isOf(item))count+=stack.getCount();}count=Math.min(count,q.get("target_count").getAsInt());if(count>q.get("progress").getAsInt())sendProgress(q,count);}catch(IllegalArgumentException ignored){}
        }
    }

    private void sendProgress(JsonObject quest,int count){
        if(localWorld()){quest.addProperty("progress",count);persistLocalQuest(quest);status="Quest-Fortschritt lokal gespeichert.";return;}
        JsonObject body=new JsonObject();body.addProperty("progress",count);
        MpsqApiClient.post("/quests/"+quest.get("id").getAsString()+"/progress",body)
            .whenComplete((data,error)->client.execute(()->{if(error==null){quest.addProperty("progress",count);status="Quest-Fortschritt gespeichert.";}else status="Fortschritt konnte nicht gespeichert werden: "+error.getMessage();}));
    }

    @Override public void renderBackground(DrawContext context,int x,int y,float delta){super.renderBackground(context,x,y,delta);MpsqTheme.drawBackground(context,width,height);}

    @Override public void render(DrawContext c,int mx,int my,float delta){
        super.render(c,mx,my,delta);int center=width/2;c.drawCenteredTextWithShadow(textRenderer,title,center,20,0xFFFF7182);
        if(editing){
            c.drawCenteredTextWithShadow(textRenderer,"QUEST STUDIO",center,62,0xFFFFFFFF);
            c.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(status,Math.max(100,width-24)),center,height-24,0xFFFFA0AA);
        }else{
            String help=canEdit()?"+ erstellt eine Quest · Linksklick: annehmen/ablehnen/Belohnung · Rechtsklick: bearbeiten":"Linksklick: annehmen/ablehnen/Belohnung · Rechtsklick: Fortschritt";
            c.drawCenteredTextWithShadow(textRenderer,Text.literal(help),center,76,0xFFBBBBBB);
            int left=boardLeft(),boardW=boardWidth(),cw=cardWidth(),bottom=height-BOTTOM;
            c.enableScissor(left,TOP,left+boardW,bottom);
            for(int i=0;i<quests.size();i++){
                int col=i%COLUMNS,row=i/COLUMNS,x=left+8+col*(cw+GAP),y=TOP+row*(CARD_HEIGHT+GAP)-scroll;
                if(y+CARD_HEIGHT<TOP||y>bottom)continue;
                JsonObject q=quests.get(i).getAsJsonObject();boolean accepted=q.has("accepted")&&q.get("accepted").getAsBoolean();boolean claimed=q.has("claimed")&&q.get("claimed").getAsBoolean();
                int progress=q.has("progress")?q.get("progress").getAsInt():0,target=q.has("target_count")?q.get("target_count").getAsInt():1;
                c.fill(x,y,x+cw,y+CARD_HEIGHT,i%2==0?0xD9282D39:0xD9222733);c.fill(x,y,x+cw,y+2,0xFFFF536A);
                drawItem(c,str(q,"icon_item","minecraft:paper"),x+8,y+8);
                c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(str(q,"title","Quest"),Math.max(30,cw-104)),x+31,y+8,0xFFFFFFFF);
                c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(rewardLabel(q),90),x+cw-96,y+8,0xFFFFD36A);
                String description=str(q,"description","");if(description.isBlank())description="Sammle "+target+" Items.";
                c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(description,Math.max(20,cw-16)),x+8,y+29,0xFFCCCCCC);
                drawItem(c,str(q,"objective_item","minecraft:stone"),x+8,y+46);
                c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(str(q,"objective_item","minecraft:stone")+"  "+progress+" / "+target,Math.max(20,cw-44)),x+31,y+50,0xFFE5E5E5);
                int barX=x+8,barY=y+72,barW=cw-16;c.fill(barX,barY,barX+barW,barY+4,0xFF171A21);float ratio=target<=0?0:Math.min(1f,(float)progress/target);c.fill(barX,barY,barX+(int)(barW*ratio),barY+4,0xFFFF536A);
                String action=claimed?"Abgeholt":!accepted?"Annehmen":progress>=target?"Belohnung abholen":"Ablehnen";
                c.drawTextWithShadow(textRenderer,accepted?"Angenommen":"Verfügbar",x+8,y+84,accepted?0xFF77DD99:0xFFBBBBBB);
                c.drawTextWithShadow(textRenderer,action,x+cw-textRenderer.getWidth(action)-8,y+84,claimed?0xFF77DD99:0xFFFFA0AA);
            }
            c.disableScissor();drawScrollbar(c,left+boardW+4,TOP,bottom);
            c.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(status,Math.max(100,width-24)),center,height-24,0xFFFFFFFF);
        }
        c.drawTextWithShadow(textRenderer,Text.literal("Zurück"),10,height-18,0xFFFFFFFF);
    }

    private String rewardLabel(JsonObject q){int points=q.has("reward_points")?q.get("reward_points").getAsInt():0;return points>0?points+" Punkte":"Accessoire";}
    private void drawItem(DrawContext c,String id,int x,int y){try{var item=Registries.ITEM.get(Identifier.of(id));if(item!=Items.AIR)c.drawItem(new ItemStack(item),x,y);}catch(IllegalArgumentException ignored){}}
    private void drawScrollbar(DrawContext c,int x,int top,int bottom){int visible=bottom-top,content=contentHeight();if(content<=visible)return;int thumb=Math.max(20,visible*visible/content),travel=visible-thumb,y=top+(maxScroll()==0?0:travel*scroll/maxScroll());c.fill(x,top,x+4,bottom,0x66000000);c.fill(x,y,x+4,y+thumb,dragging?0xFFFF7182:0xFFBBBBBB);}
    private void scrollTo(double y){int visible=height-TOP-BOTTOM,thumb=Math.max(20,visible*visible/Math.max(1,contentHeight())),travel=Math.max(1,visible-thumb);scroll=(int)Math.round(Math.max(0,Math.min(travel,y-TOP-thumb/2))*maxScroll()/travel);clamp();}
    private void edit(JsonObject q){edited=q==null?null:q.deepCopy();editing=true;status="";clearAndInit();}

    private void save(){
        if(pending)return;
        try{
            String title=titleField.getText().trim(),description=descriptionField.getText().trim(),icon=iconField.getText().trim(),item=itemField.getText().trim();
            int count=Integer.parseInt(countField.getText().trim()),points=Integer.parseInt(pointsField.getText().trim());String accessory=accessoryField.getText().trim();
            if(title.isEmpty()||description.isEmpty()||description.length()>240||!validItemId(icon)||!validItemId(item)||count<1||points<0||(points==0&&accessory.isEmpty())||(points>0&&!accessory.isEmpty()))throw new IllegalArgumentException();
            JsonObject body=new JsonObject();body.addProperty("server",MpsqActionSync.server());body.addProperty("world",MpsqActionSync.world());body.addProperty("title",title);body.addProperty("description",description);body.addProperty("iconItem",icon);body.addProperty("objectiveItem",item);body.addProperty("targetCount",count);body.addProperty("rewardPoints",points);body.addProperty("rewardAccessoryId",accessory);if(edited!=null)body.addProperty("questId",edited.get("id").getAsString());
            pending=true;status="Quest wird gespeichert …";saveButton.setMessage(Text.literal("Speichert …"));
            if(localWorld()){
                JsonObject q=new JsonObject();q.addProperty("id",edited==null?java.util.UUID.randomUUID().toString():edited.get("id").getAsString());q.addProperty("npc_id",npcId);q.addProperty("title",title);q.addProperty("description",description);q.addProperty("icon_item",icon);q.addProperty("objective_item",item);q.addProperty("target_count",count);q.addProperty("reward_points",points);q.addProperty("reward_accessory_id",accessory);q.addProperty("accepted",edited!=null&&edited.has("accepted")&&edited.get("accepted").getAsBoolean());q.addProperty("progress",edited!=null&&edited.has("progress")?edited.get("progress").getAsInt():0);q.addProperty("claimed",edited!=null&&edited.has("claimed")&&edited.get("claimed").getAsBoolean());replaceLocalQuest(q);pending=false;editing=false;edited=null;status="Quest lokal in dieser Welt gespeichert.";clearAndInit();return;
            }
            MpsqApiClient.post("/npcs/"+npcId+"/quests",body).whenComplete((data,error)->client.execute(()->{pending=false;if(error==null){editing=false;edited=null;status="Quest gespeichert.";clearAndInit();}else{status="Speichern fehlgeschlagen: "+error.getMessage();if(saveButton!=null)saveButton.setMessage(Text.literal("Quest speichern"));}}));
        }catch(RuntimeException ex){status="Titel, Beschreibung, Item-IDs, Anzahl und eine Belohnung prüfen.";}
    }

    private boolean validItemId(String id){return id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");}
    private void delete(){if(pending||edited==null)return;if(localWorld()){JsonArray all=MpsqLocalWorldStore.array("quests"),next=new JsonArray();for(JsonElement e:all)if(!edited.get("id").getAsString().equals(str(e.getAsJsonObject(),"id","")))next.add(e);MpsqLocalWorldStore.setArray("quests",next);editing=false;edited=null;status="Quest aus dieser Welt gelöscht.";clearAndInit();return;}JsonObject body=new JsonObject();body.addProperty("server",MpsqActionSync.server());body.addProperty("world",MpsqActionSync.world());body.addProperty("action","delete");body.addProperty("questId",edited.get("id").getAsString());pending=true;MpsqApiClient.post("/npcs/"+npcId+"/quests",body).whenComplete((d,e)->client.execute(()->{pending=false;if(e==null){editing=false;edited=null;status="Quest gelöscht.";clearAndInit();}else{status="Löschen fehlgeschlagen: "+e.getMessage();clearAndInit();}}));}

    @Override public boolean mouseClicked(double x,double y,int button){
        if(editing)return super.mouseClicked(x,y,button);
        int left=boardLeft(),boardW=boardWidth(),cw=cardWidth();
        if(y>=TOP&&y<height-BOTTOM&&x>=left+8&&x<left+boardW-8){
            int localY=(int)(y-TOP+scroll),col=(int)(x-(left+8))/(cw+GAP),row=localY/(CARD_HEIGHT+GAP),index=row*COLUMNS+col;
            if(localY%(CARD_HEIGHT+GAP)<CARD_HEIGHT&&col>=0&&col<COLUMNS&&index>=0&&index<quests.size()&&x<left+8+col*(cw+GAP)+cw){JsonObject q=quests.get(index).getAsJsonObject();
                if(button==1&&canEdit()){edit(q);return true;}
                if(button==1){float p=(float)q.get("progress").getAsInt()/Math.max(1,q.get("target_count").getAsInt());String id=q.get("id").getAsString();MpsqBossbarManager.apply(new MpsqBossbarState("quest-"+id,str(q,"title","Quest"),"purple",p,true));status="Fortschritt wird oben angezeigt.";return true;}
                if(button==0){if(!q.get("accepted").getAsBoolean()){act(q,"accept");return true;}if(q.get("progress").getAsInt()>=q.get("target_count").getAsInt()&&!q.get("claimed").getAsBoolean()){act(q,"claim");return true;}if(!q.get("claimed").getAsBoolean()){act(q,"decline");return true;}}
            }
        }
        if(button==0&&x>=left+boardW&&x<=left+boardW+12&&y>=TOP&&y<height-BOTTOM&&maxScroll()>0){dragging=true;scrollTo(y);return true;}
        if(y>=height-26&&x<100){close();return true;}
        return super.mouseClicked(x,y,button);
    }

    private void replaceLocalQuest(JsonObject q){JsonArray all=MpsqLocalWorldStore.array("quests"),next=new JsonArray();for(JsonElement e:all)if(!q.get("id").getAsString().equals(str(e.getAsJsonObject(),"id","")))next.add(e);next.add(q);MpsqLocalWorldStore.setArray("quests",next);loadLocalQuests();}
    private void persistLocalQuest(JsonObject q){replaceLocalQuest(q);}
    private void act(JsonObject q,String action){if(pending)return;if(localWorld()){if(action.equals("accept"))q.addProperty("accepted",true);else if(action.equals("decline")){q.addProperty("accepted",false);q.addProperty("progress",0);}else{int reward=q.has("reward_points")?q.get("reward_points").getAsInt():0;boolean ok=reward>0?MpsqLocalWorldStore.addPoints(reward):MpsqLocalWorldStore.ownAccessory(str(q,"reward_accessory_id",""));if(!ok){status="Belohnung konnte lokal nicht gespeichert werden.";return;}q.addProperty("claimed",true);}replaceLocalQuest(q);if(action.equals("claim")){int reward=q.get("reward_points").getAsInt();status=reward>0?"Questbelohnung: "+reward+" lokale Punkte.":"Quest-Accessoire lokal freigeschaltet.";}else status=action.equals("decline")?"Quest abgelehnt.":"Quest angenommen.";MpsqAccessoryRenderer.refresh();return;}pending=true;MpsqApiClient.post("/quests/"+q.get("id").getAsString()+"/"+action,new JsonObject()).whenComplete((d,e)->client.execute(()->{pending=false;if(e==null){if(action.equals("accept"))q.addProperty("accepted",true);else if(action.equals("decline")){q.addProperty("accepted",false);q.addProperty("progress",0);}else q.addProperty("claimed",true);if(action.equals("claim")){int points=q.get("reward_points").getAsInt();status=points>0?"Questbelohnung: "+points+" MPSQ-Punkte.":"Quest-Accessoire freigeschaltet.";}else status=action.equals("decline")?"Quest abgelehnt.":"Quest angenommen.";}else status="Aktion fehlgeschlagen: "+e.getMessage();}));}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(!editing){scroll-=(int)Math.signum(vertical)*CARD_HEIGHT*2;clamp();return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging&&button==0){scrollTo(y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0)dragging=false;return super.mouseReleased(x,y,button);}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
