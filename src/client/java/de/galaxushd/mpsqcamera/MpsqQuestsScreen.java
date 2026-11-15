package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Player quest board and Officer quest editor for a placed quest NPC. */
public final class MpsqQuestsScreen extends Screen {
    private static final int TOP=108, BOTTOM=48, CARD_HEIGHT=108, GAP=8, COLUMNS=3;
    private static final int GUI_SLOT_SIZE=36, ACTION_MAX_WIDTH=GUI_SLOT_SIZE*2, ACTION_GAP=2;
    private static final int ACTION_TEXTURE_WIDTH=1472, ACTION_TEXTURE_HEIGHT=288;
    private static final Identifier ACCEPT_ACTION=Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID,"textures/gui/quest_accept.png");
    private static final Identifier DECLINE_ACTION=Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID,"textures/gui/quest_decline.png");
    private final Screen parent;
    private final String npcId;
    private JsonArray quests=new JsonArray();
    private String status="Quests werden geladen …";
    private int scroll;
    private String selectedQuestId="";
    private boolean pending,editing,dragging;
    private boolean accessoryCatalogLoaded,accessoryCatalogLoading;
    private JsonObject edited;
    private JsonArray accessoryCatalog=new JsonArray();
    private int accessoryCursor=-1;
    private EditBox titleField,descriptionField,iconField,itemField,countField,pointsField,accessoryField;
    private Button saveButton;
    private long lastScan;
    private final String initialQuestId;
    private boolean directEditor;
    private String category = "standard";
    private final net.minecraft.client.multiplayer.ClientLevel originWorld = net.minecraft.client.Minecraft.getInstance().level;
    static MpsqQuestsScreen editor(Screen parent, String npcId, JsonObject quest, String category) {
        if (!MpsqQuestMenuScreen.canEdit()) throw new IllegalStateException("Offizier-Rang erforderlich.");
        var screen = new MpsqQuestsScreen(parent, npcId);
        screen.directEditor = true; screen.editing = true; screen.status = ""; screen.edited = quest == null ? null : quest.deepCopy(); screen.category = category;
        return screen;
    }
    private boolean localWorld(){return MpsqActionSync.server().isBlank()&&MpsqLocalWorldStore.available();}
    private boolean canEdit(){return staff()&&(directEditor||!(parent instanceof MpsqQuestMenuScreen));}

    public MpsqQuestsScreen(Screen parent,String npcId){this(parent,npcId,"");}
    public MpsqQuestsScreen(Screen parent,String npcId,String initialQuestId){super(Component.literal("Quests"));this.parent=parent;this.npcId=npcId;this.initialQuestId=initialQuestId==null?"":initialQuestId;}

    @Override protected void init(){
        if(!editing){
            if(initialQuestId.isBlank()){minecraft.gui.setScreen(new MpsqQuestMenuScreen(parent,npcId));return;}
            if(localWorld()){loadLocalQuests();return;}
            String path="/npcs/"+npcId+"/quests?server="+enc(MpsqActionSync.server())+"&world="+enc(MpsqActionSync.world());
            MpsqApiClient.get(path).whenComplete((data,error)->minecraft.execute(()->{
                if(error==null&&data.isJsonArray()){quests=data.getAsJsonArray();status=quests.isEmpty()?"Noch keine Quests vorhanden.":"";selectedQuestId=initialQuestId;MpsqQuestProgress.observe(quests);}
                else status="Quests konnten nicht geladen werden.";
                MpsqQuestProgress.observe(quests);clamp();
            }));
            if(canEdit())addRenderableWidget(Button.builder(Component.literal("+"),b->edit(null)).bounds(width/2-11,45,22,22).build());
        }else {initEditor();loadAccessoryCatalog();}
    }

    private void loadLocalQuests(){quests=new JsonArray();for(JsonElement e:MpsqLocalWorldStore.array("quests")){if(!e.isJsonObject())continue;JsonObject q=e.getAsJsonObject();if(npcId.equals(str(q,"npc_id","")))quests.add(q.deepCopy());}status=quests.isEmpty()?"Noch keine Quests vorhanden.":"Questdaten dieser privaten Welt geladen.";selectedQuestId=initialQuestId;}

    private void initEditor(){
        int x=width/2-150,y=30,w=240,step=Math.max(22,Math.min(36,(height-70)/9));
        titleField=field(x,y,w,"Quest-Titel",edited==null?"":str(edited,"title",""),80);
        descriptionField=field(x,y+step,300,"Aufgabe / Beschreibung",edited==null?"":str(edited,"description",""),240);
        iconField=field(x,y+2*step,w,"GUI-Icon · minecraft:paper",edited==null?"":str(edited,"icon_item","minecraft:paper"),128);
        addRenderableWidget(Button.builder(Component.literal("Hand"),b->useMainHand(iconField)).bounds(x+248,y+2*step,52,22).build());
        itemField=field(x,y+3*step,w,"Ziel · minecraft:stone oder kill:minecraft:pig",edited==null?"":str(edited,"objective_item","minecraft:stone"),128);
        addRenderableWidget(Button.builder(Component.literal("Hand"),b->useMainHand(itemField)).bounds(x+248,y+3*step,52,22).build());
        countField=field(x,y+4*step,300,"Benötigte Anzahl",edited==null?"":str(edited,"target_count","10"),8);
        pointsField=field(x,y+5*step,300,"Punktebelohnung (0 = Accessoire)",edited==null?"":str(edited,"reward_points","0"),10);
        accessoryField=field(x,y+6*step,w,"Accessoire-ID · nur bei 0 Punkten",edited==null?"":str(edited,"reward_accessory_id",""),64);
        addRenderableWidget(Button.builder(Component.literal("Katalog"),b->chooseAccessory()).bounds(x+248,y+6*step,52,22).build());
        saveButton=addRenderableWidget(Button.builder(Component.literal(pending?"Speichert …":"Quest speichern"),b->save()).bounds(x,y+7*step,142,22).build());
        addRenderableWidget(Button.builder(Component.literal("Abbrechen"),b->{if(directEditor){onClose();return;}editing=false;edited=null;rebuildWidgets();}).bounds(x+158,y+7*step,142,22).build());
        if(edited!=null)addRenderableWidget(Button.builder(Component.literal("Quest löschen"),b->minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(ok->{minecraft.gui.setScreen(this);if(ok)delete();},Component.literal("Quest löschen?"),Component.literal("Diese Quest wird dauerhaft gelöscht.")))).bounds(x,y+8*step,300,22).build());
    }

    private EditBox field(int x,int y,int w,String hint,String initial,int max){
        var field=addRenderableWidget(new EditBox(font,x,y,w,22,Component.literal(hint)));
        field.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(hint)));field.setMaxLength(max);field.setHint(Component.literal(hint));field.setValue(initial);return field;
    }

    private void useMainHand(EditBox field){
        if(minecraft.player==null)return;
        ItemStack stack=minecraft.player.getMainHandItem();
        if(stack.isEmpty()){status="Halte zuerst das gewünschte Item in der Haupthand.";return;}
        field.setValue(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());status="Item übernommen: "+field.getValue();
    }

    private void loadAccessoryCatalog(){
        if(accessoryCatalogLoaded||accessoryCatalogLoading)return;accessoryCatalogLoading=true;
        MpsqApiClient.get("/accessory-catalog").whenComplete((data,error)->minecraft.execute(()->{
            accessoryCatalogLoading=false;accessoryCatalogLoaded=true;
            if(error==null&&data.isJsonArray())accessoryCatalog=data.getAsJsonArray();
        }));
    }

    private void chooseAccessory(){
        if(accessoryCatalog.size()==0){status=accessoryCatalogLoading?"Accessoire-Katalog lädt …":"Kein Shop-Accessoire verfügbar.";return;}
        for(int step=1;step<=accessoryCatalog.size();step++){
            int index=(accessoryCursor+step)%accessoryCatalog.size();JsonObject row=accessoryCatalog.get(index).getAsJsonObject();
            if(!row.has("accessory_id")||row.get("accessory_id").isJsonNull())continue;
            accessoryCursor=index;accessoryField.setValue(row.get("accessory_id").getAsString());pointsField.setValue("0");
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

    @Override public void tick(){super.tick();if(!editing)for(var e:quests)if(e.isJsonObject())MpsqQuestProgress.updateView(e.getAsJsonObject());}

    @Override public void extractBackground(GuiGraphicsExtractor context,int x,int y,float delta){super.extractBackground(context,x,y,delta);if(editing)context.fill(0,0,width,height,0x55000000);else context.fill(0,0,width,height,0x77000000);}

    @Override public void extractRenderState(GuiGraphicsExtractor c,int mx,int my,float delta){
        if(!editing){renderDetail(c);return;}
        super.extractRenderState(c,mx,my,delta);
        c.centeredText(font,title,width/2,14,0xFFFFFFFF);
        c.centeredText(font,font.plainSubstrByWidth(status,Math.max(100,width-24)),width/2,height-16,0xFFFFA0AA);
    }
    private JsonObject detailQuest(){for(var e:quests)if(e.isJsonObject()&&initialQuestId.equals(str(e.getAsJsonObject(),"id","")))return e.getAsJsonObject();return null;}
    private float detailScale;private int detailLeft,detailTop,detailHeight,detailHeaderHeight;
    private java.util.List<net.minecraft.util.FormattedCharSequence> detailTitle=java.util.List.of(),detailObjective=java.util.List.of();
    private java.util.List<net.minecraft.util.FormattedCharSequence> detailLines=java.util.List.of();
    private void detailLayout(JsonObject q){
        detailLines=font.split(MpsqRichText.parse(str(q,"description","")),328);
        detailTitle=font.split(MpsqRichText.parse(str(q,"title","Quest")),296);
        detailObjective=font.split(MpsqRichText.parse(MpsqQuestProgress.objective(q)),328);
        detailHeaderHeight=Math.max(52,24+detailTitle.size()*font.lineHeight+font.lineHeight);
        detailHeight=detailHeaderHeight+130+(detailLines.size()+detailObjective.size())*font.lineHeight;
        detailScale=Math.min(1f,Math.min((width-20f)/360,(height-20f)/detailHeight));
        detailLeft=(width-Math.round(360*detailScale))/2;detailTop=(height-Math.round(detailHeight*detailScale))/2;
    }
    private static boolean flag(JsonObject q,String key){return q.has(key)&&q.get(key).getAsBoolean();}
    private static int number(JsonObject q,String key,int fallback){return q.has(key)?q.get(key).getAsInt():fallback;}
    private void renderDetail(GuiGraphicsExtractor c){
        var q=detailQuest();if(q==null){c.centeredText(font,status,width/2,height/2,0xFFFFFFFF);return;}detailLayout(q);
        c.pose().pushMatrix();c.pose().translate(detailLeft,detailTop);c.pose().scale(detailScale);
        c.fill(0,0,360,detailHeight,0xF0282D39);c.fill(0,0,360,2,0xFF77DD99);
        MpsqGuiIcon.draw(c,str(q,"icon_item","minecraft:paper"),12,12,28);
        int titleY=12;for(var line:detailTitle){c.text(font,line,48,titleY,0xFFFFFFFF);titleY+=font.lineHeight;}
        c.text(font,rewardLabel(q),48,titleY+5,0xFFFFD36A);
        int y=detailHeaderHeight;for(var line:detailLines){c.text(font,line,16,y,0xFFDDDDDD);y+=font.lineHeight;}
        int target=number(q,"target_count",1),progress=number(q,"progress",0);boolean active=flag(q,"accepted")&&!flag(q,"claimed");
        y+=10;for(var line:detailObjective){c.text(font,line,16,y,0xFFFFFFFF);y+=font.lineHeight;}
        if(active){int by=y+27;c.fill(16,by,344,by+5,0xFF15181F);c.fill(16,by,16+Math.round(328*Math.min(1f,(float)progress/Math.max(1,target))),by+5,0xFF77DD99);c.centeredText(font,progress+" / "+target,180,by+9,0xFFFFFFFF);}
        int by=detailHeight-42;boolean accepted=flag(q,"accepted"),claimed=flag(q,"claimed");
        c.fill(60,by,300,by+26,claimed?0xFF444444:accepted?0xFF982E3A:0xFF246B3E);
        c.centeredText(font,claimed?"Abgeschlossen":accepted?"Ablehnen · angenommen":"Annehmen · nicht angenommen",180,by+9,0xFFFFFFFF);
        if(active&&progress>=target){c.fill(60,by-25,300,by-3,0xFF80601D);c.centeredText(font,"Belohnung abholen",180,by-18,0xFFFFFFFF);}
        c.text(font,"Zurück",8,detailHeight-12,0xFFFFFFFF);c.pose().popMatrix();
        if(!status.isBlank())c.centeredText(font,font.plainSubstrByWidth(status,Math.max(100,width-20)),width/2,Math.max(1,detailTop-12),0xFFFFD36A);
    }
    private boolean clickDetail(net.minecraft.client.input.MouseButtonEvent event){
        if(event.button()!=com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT)return true;
        var q=detailQuest();if(q==null)return true;detailLayout(q);double x=(event.x()-detailLeft)/detailScale,y=(event.y()-detailTop)/detailScale;int by=detailHeight-42;
        if(y>=detailHeight-16&&x<60){onClose();return true;}
        if(x>=60&&x<=300&&!flag(q,"claimed")){
            if(y>=by&&y<by+26)act(q,flag(q,"accepted")?"decline":"accept");
            else if(y>=by-25&&y<by-3&&flag(q,"accepted")&&number(q,"progress",0)>=number(q,"target_count",1))act(q,"claim");
        }return true;
    }

    private String rewardLabel(JsonObject q){int points=q.has("reward_points")?q.get("reward_points").getAsInt():0;return points>0?points+" Punkte":"Accessoire";}
    private static String questKey(JsonObject q,int index){return str(q,"id","quest-slot-"+index);}
    private void edit(JsonObject q){if(!canEdit())return;category=q==null?"standard":str(q,"category","standard");edited=q==null?null:q.deepCopy();editing=true;status="";rebuildWidgets();}

    private void save(){
        if(pending||!canEdit()||minecraft.level!=originWorld)return;
        try{
            String title=titleField.getValue().trim(),description=descriptionField.getValue().trim(),icon=iconField.getValue().trim(),item=itemField.getValue().trim();
            int count=Integer.parseInt(countField.getValue().trim()),points=Integer.parseInt(pointsField.getValue().trim());String accessory=accessoryField.getValue().trim();
            if(title.isEmpty()||description.isEmpty()||description.length()>240||!validItemId(icon)||!validItemId(item.startsWith("kill:")?item.substring(5):item)||count<1||points<0||(points==0&&accessory.isEmpty())||(points>0&&!accessory.isEmpty()))throw new IllegalArgumentException();
            JsonObject body=new JsonObject();body.addProperty("category",category);body.addProperty("server",MpsqActionSync.server());body.addProperty("world",MpsqActionSync.world());body.addProperty("title",title);body.addProperty("description",description);body.addProperty("iconItem",icon);body.addProperty("objectiveItem",item);body.addProperty("targetCount",count);body.addProperty("rewardPoints",points);body.addProperty("rewardAccessoryId",accessory);if(edited!=null)body.addProperty("questId",edited.get("id").getAsString());
            pending=true;status="Quest wird gespeichert …";saveButton.setMessage(Component.literal("Speichert …"));
            if(localWorld()){
                JsonObject q=new JsonObject();q.addProperty("id",edited==null?java.util.UUID.randomUUID().toString():edited.get("id").getAsString());q.addProperty("npc_id",npcId);q.addProperty("category",category);q.addProperty("title",title);q.addProperty("description",description);q.addProperty("icon_item",icon);q.addProperty("objective_item",item);q.addProperty("target_count",count);q.addProperty("reward_points",points);q.addProperty("reward_accessory_id",accessory);q.addProperty("accepted",edited!=null&&edited.has("accepted")&&edited.get("accepted").getAsBoolean());q.addProperty("progress",edited!=null&&edited.has("progress")?edited.get("progress").getAsInt():0);q.addProperty("claimed",edited!=null&&edited.has("claimed")&&edited.get("claimed").getAsBoolean());replaceLocalQuest(q);pending=false;editing=false;edited=null;status="Quest lokal in dieser Welt gespeichert.";if(directEditor)onClose();else rebuildWidgets();return;
            }
            MpsqApiClient.post("/npcs/"+npcId+"/quests",body).whenComplete((data,error)->minecraft.execute(()->{pending=false;if(minecraft.level!=originWorld)return;if(error==null){editing=false;edited=null;status="Quest gespeichert.";if(directEditor)onClose();else rebuildWidgets();}else{status="Speichern fehlgeschlagen: "+error.getMessage();if(saveButton!=null)saveButton.setMessage(Component.literal("Quest speichern"));}}));
        }catch(RuntimeException ex){pending=false;if(saveButton!=null)saveButton.setMessage(Component.literal("Quest speichern"));status="Titel, Beschreibung, Item-IDs, Anzahl und eine Belohnung prüfen.";}
    }

    private boolean validItemId(String id){return id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");}
    private void delete(){if(pending||edited==null||!canEdit()||minecraft.level!=originWorld)return;if(localWorld()){JsonArray all=MpsqLocalWorldStore.array("quests"),next=new JsonArray();for(JsonElement e:all)if(!edited.get("id").getAsString().equals(str(e.getAsJsonObject(),"id","")))next.add(e);if(!MpsqLocalWorldStore.setArray("quests",next)){status="Löschen fehlgeschlagen.";return;}editing=false;edited=null;status="Quest aus dieser Welt gelöscht.";if(directEditor)onClose();else rebuildWidgets();return;}JsonObject body=new JsonObject();body.addProperty("server",MpsqActionSync.server());body.addProperty("world",MpsqActionSync.world());body.addProperty("action","delete");body.addProperty("questId",edited.get("id").getAsString());pending=true;MpsqApiClient.post("/npcs/"+npcId+"/quests",body).whenComplete((d,e)->minecraft.execute(()->{pending=false;if(e==null){editing=false;edited=null;status="Quest gelöscht.";if(directEditor)onClose();else rebuildWidgets();}else{status="Löschen fehlgeschlagen: "+e.getMessage();rebuildWidgets();}}));}

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled){return editing?super.mouseClicked(event,doubled):clickDetail(event);}

    private void replaceLocalQuest(JsonObject q){JsonArray all=MpsqLocalWorldStore.array("quests"),next=new JsonArray();for(JsonElement e:all)if(q.get("id").getAsString().equals(str(e.getAsJsonObject(),"id","")))next.add(q);else next.add(e);boolean found=false;for(JsonElement e:all)if(q.get("id").getAsString().equals(str(e.getAsJsonObject(),"id","")))found=true;if(!found)next.add(q);if(!MpsqLocalWorldStore.setArray("quests",next))throw new IllegalStateException("Quest konnte nicht gespeichert werden.");loadLocalQuests();}
    private void persistLocalQuest(JsonObject q){replaceLocalQuest(q);}
    private void act(JsonObject q,String action){if(pending)return;if(localWorld()){if(action.equals("accept"))q.addProperty("accepted",true);else if(action.equals("decline")){q.addProperty("accepted",false);q.addProperty("progress",0);}else{int reward=q.has("reward_points")?q.get("reward_points").getAsInt():0;boolean ok=reward>0?MpsqLocalWorldStore.addPoints(reward):MpsqLocalWorldStore.ownAccessory(str(q,"reward_accessory_id",""));if(!ok){status="Belohnung konnte lokal nicht gespeichert werden.";return;}q.addProperty("claimed",true);}replaceLocalQuest(q);if(action.equals("claim")){int reward=q.get("reward_points").getAsInt();status=reward>0?"Questbelohnung: "+reward+" lokale Punkte.":"Quest-Accessoire lokal freigeschaltet.";}else status=action.equals("decline")?"Quest abgelehnt.":"Quest angenommen.";MpsqQuestProgress.observe(quests);MpsqAccessoryRenderer.refresh();return;}pending=true;MpsqApiClient.post("/quests/"+q.get("id").getAsString()+"/"+action,new JsonObject()).whenComplete((d,e)->minecraft.execute(()->{pending=false;if(e==null){if(action.equals("accept"))q.addProperty("accepted",true);else if(action.equals("decline")){q.addProperty("accepted",false);q.addProperty("progress",0);}else q.addProperty("claimed",true);if(action.equals("claim")){int points=q.get("reward_points").getAsInt();status=points>0?"Questbelohnung: "+points+" MPSQ-Punkte.":"Quest-Accessoire freigeschaltet.";}else status=action.equals("decline")?"Quest abgelehnt.":"Quest angenommen.";}else status="Aktion fehlgeschlagen: "+e.getMessage();MpsqQuestProgress.observe(quests);}));}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(!editing){scroll-=(int)Math.signum(vertical)*CARD_HEIGHT*2;clamp();return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        double x = event.x(), y = event.y();
        int button = event.button();
return super.mouseDragged(event, dx,dy);}
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double x = event.x(), y = event.y();
        int button = event.button();
if(button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT)dragging=false;return super.mouseReleased(event);}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
 int keyCode=event.key(), scanCode=event.keycode(), modifiers=event.modifiers();
return super.keyPressed(event);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
