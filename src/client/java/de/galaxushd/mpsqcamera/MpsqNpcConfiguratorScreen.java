package de.galaxushd.mpsqcamera;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Pixel-styled client configurator for an already placed MPSQ NPC. */
public final class MpsqNpcConfiguratorScreen extends Screen {
    private static final String[] TABS={"Aussehen","Interaktion","Animation","Drehung","Aufgabe"};
    private static final String[] TASKS={"none","accessories","tutorial","quest"};
    private static final String[] TASK_LABELS={"Keine Aufgabe","Accessoires","Tutorial","Quests"};
    private static final String[] ANIMATIONS={"none","bob","turn","pulse","nod","tilt","look_around","shake","wave"};
    private static final String[] ANIMATION_LABELS={"Keine","Schweben","Drehen","Pulsieren","Nicken","Neigen","Umschauen","Schütteln","Winken"};

    private final Screen parent;
    private final String npcId,server,world;
    private int tab;
    private String displayName,glowColor,animation,dialogue,status="",taskType="none";
    private float scale;
    private float yaw,pitch;
    private boolean facePlayer;
    private TextFieldWidget nameField,dialogueField;
    private boolean pending,glowEnabled;
    private int scroll;

    public MpsqNpcConfiguratorScreen(Screen parent,JsonObject npc){
        super(Text.literal("NPC-Konfigurator"));this.parent=parent;this.npcId=npc.get("id").getAsString();
        this.server=MpsqActionSync.server();this.world=MpsqActionSync.world();
        this.displayName=str(npc,"display_name",str(npc,"name","NPC"));this.scale=npc.has("scale")?npc.get("scale").getAsFloat():1f;
        this.taskType=str(npc,"task_type","none");
        this.glowColor=defaultGlowColor(this.taskType);this.animation=str(npc,"animation","none");
        this.glowEnabled=!MpsqNpcVisitStore.isGlowDisabled(this.npcId)&&(!isRoleTask(this.taskType)||!MpsqNpcVisitStore.hasVisited(this.taskType));
        this.yaw=npc.has("yaw")?npc.get("yaw").getAsFloat():0f;this.pitch=npc.has("pitch")?npc.get("pitch").getAsFloat():0f;this.facePlayer=npc.has("face_player")&&npc.get("face_player").getAsBoolean();
        JsonObject interaction=npc.has("interaction_data")&&npc.get("interaction_data").isJsonObject()?npc.getAsJsonObject("interaction_data"):new JsonObject();
        List<String> pages=new ArrayList<>();if(interaction.has("pages")&&interaction.get("pages").isJsonArray())for(var page:interaction.getAsJsonArray("pages"))pages.add(page.getAsString());this.dialogue=String.join(" || ",pages);
    }

    @Override protected void init(){
        clearChildren();int left=panelLeft(),content=left+122,top=panelTop(),footer=panelBottom()-28;
        if(tab==0){nameField=addDrawableChild(new TextFieldWidget(textRenderer,content,top+52-scroll,panelRight()-12-content,22,Text.literal("Name")));nameField.setMaxLength(64);nameField.setText(displayName);
            addDrawableChild(ButtonWidget.builder(Text.literal("−"),b->{captureFields();scale=Math.max(.25f,scale-.25f);clearAndInit();}).dimensions(content,top+103-scroll,34,22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("+"),b->{captureFields();scale=Math.min(3f,scale+.25f);clearAndInit();}).dimensions(content+142,top+103-scroll,34,22).build());
            boolean visited=isRoleTask(taskType)&&MpsqNpcVisitStore.hasVisited(taskType);String glowLabel=visited?"Aus · bereits genutzt":glowEnabled?"An":"Aus";
            addDrawableChild(ButtonWidget.builder(Text.literal("Glow: "+glowLabel),b->{if(visited){glowEnabled=false;status="Nach der ersten Nutzung bleibt dieser Aufgaben-Glow aus.";}else{glowEnabled=!glowEnabled;status="Glow-Einstellung geändert – bitte speichern.";}clearAndInit();}).dimensions(content,top+157-scroll,184,24).build());
        }else if(tab==1){dialogueField=addDrawableChild(new TextFieldWidget(textRenderer,content,top+65-scroll,panelRight()-12-content,22,Text.literal("Dialog")));dialogueField.setMaxLength(3072);dialogueField.setText(dialogue);}
        else if(tab==3){
            addDrawableChild(ButtonWidget.builder(Text.literal("Yaw −15°"),b->{yaw=wrapYaw(yaw-15);clearAndInit();}).dimensions(content,top+76-scroll,88,22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Yaw +15°"),b->{yaw=wrapYaw(yaw+15);clearAndInit();}).dimensions(content+96,top+76-scroll,88,22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Pitch −15°"),b->{pitch=Math.max(-90,pitch-15);clearAndInit();}).dimensions(content,top+119-scroll,88,22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Pitch +15°"),b->{pitch=Math.min(90,pitch+15);clearAndInit();}).dimensions(content+96,top+119-scroll,88,22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Blick zu Spielern: "+(facePlayer?"An":"Aus")),b->{facePlayer=!facePlayer;clearAndInit();}).dimensions(content,top+163-scroll,184,22).build());
        }else if(tab==4){
            int current=Math.max(0,java.util.Arrays.asList(TASKS).indexOf(taskType));
            addDrawableChild(ButtonWidget.builder(Text.literal("Aufgabe: "+taskLabel(taskType)),b->{taskType=TASKS[(current+1)%TASKS.length];glowColor=defaultGlowColor(taskType);glowEnabled=!MpsqNpcVisitStore.isGlowDisabled(npcId)&&(!isRoleTask(taskType)||!MpsqNpcVisitStore.hasVisited(taskType));clearAndInit();}).dimensions(content,top+68-scroll,210,26).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal(pending?"Speichert …":"Änderungen speichern"),b->save()).dimensions(content,footer,panelRight()-12-content,22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"),b->close()).dimensions(panelLeft()+12,footer,96,22).build());
    }

    @Override public void renderBackground(DrawContext c,int x,int y,float delta){
        super.renderBackground(c,x,y,delta);MpsqTheme.drawBackground(c,width,height);
        int left=panelLeft(),right=panelRight(),top=panelTop(),bottom=panelBottom();
        c.fill(left+4,top+5,right+4,bottom+5,0x88000000);c.fill(left,top,right,bottom,0xF0191D28);c.fill(left,top,right,top+3,0xFFFF536A);c.fill(left,top,left+2,bottom,0xFF9C203C);c.fill(right-2,top,right,bottom,0xFF9C203C);c.fill(left,bottom-2,right,bottom,0xFF9C203C);
    }
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        int left=panelLeft(),right=panelRight(),top=panelTop(),bottom=panelBottom();
        c.enableScissor(left+2,top+2,right-2,bottom-2);super.render(c,mouseX,mouseY,delta);c.disableScissor();
        c.drawTextWithShadow(textRenderer,Text.literal("MPSQ  /  NPC STUDIO"),left+14,top+8,0xFFFF7182);c.drawTextWithShadow(textRenderer,Text.literal("NPC-Konfigurator · "+TABS[tab]),left+14,top+24,0xFFFFFFFF);
        for(int i=0;i<TABS.length;i++)drawTab(c,left+10,top+48+i*26,i,i==tab);
        int content=left+122,viewportBottom=bottom-50;c.enableScissor(content-4,top+43,right-10,viewportBottom);
        c.fill(content-9,top+43,right-12,top+45,0xFF9C203C);
        if(tab==0){c.drawTextWithShadow(textRenderer,Text.literal("Anzeigename"),content,top+43-scroll,0xFFE8EAF0);c.drawTextWithShadow(textRenderer,Text.literal("Größe"),content,top+91-scroll,0xFFE8EAF0);c.drawCenteredTextWithShadow(textRenderer,Text.literal(String.format(java.util.Locale.ROOT,"%.2f×",scale)),content+88,top+109-scroll,0xFFFFFFFF);c.drawTextWithShadow(textRenderer,Text.literal("Glowfarbe: "+colorName(glowColor)+" · fest nach Rolle"),content,top+137-scroll,0xFFE8EAF0);}
        else if(tab==1){c.drawTextWithShadow(textRenderer,Text.literal("Dialog beim Rechtsklick"),content,top+47-scroll,0xFFE8EAF0);c.drawTextWithShadow(textRenderer,Text.literal("Seiten mit  ||  trennen · max. 12 × 240 Zeichen"),content,top+96-scroll,0xFFBBBBBB);c.drawTextWithShadow(textRenderer,Text.literal("Der Text erscheint im MPSQ-Dialogfenster."),content,top+115-scroll,0xFF999999);}
        else if(tab==2){c.drawTextWithShadow(textRenderer,Text.literal("Modellbewegung auswählen"),content,top+49-scroll,0xFFE8EAF0);for(int i=0;i<ANIMATION_LABELS.length;i++){int bx=content,by=top+73+i*34-scroll;c.fill(bx,by,bx+190,by+27,animation.equals(ANIMATIONS[i])?0xFF9C203C:0xFF292E3A);drawPixelIcon(c,bx+7,by+7,i+3,0xFFFF687A);c.drawTextWithShadow(textRenderer,Text.literal(ANIMATION_LABELS[i]),bx+29,by+8,0xFFFFFFFF);if(animation.equals(ANIMATIONS[i]))c.drawTextWithShadow(textRenderer,Text.literal("✓"),bx+171,by+8,0xFFFFA0AA);}}
        else if(tab==3){c.drawTextWithShadow(textRenderer,Text.literal("Kopfrotation · Schritte von 15°"),content,top+49-scroll,0xFFE8EAF0);c.drawTextWithShadow(textRenderer,Text.literal("Yaw: "+Math.round(yaw)+"°"),content,top+104-scroll,0xFFFFFFFF);c.drawTextWithShadow(textRenderer,Text.literal("Pitch: "+Math.round(pitch)+"°"),content,top+147-scroll,0xFFFFFFFF);c.drawTextWithShadow(textRenderer,Text.literal("Folgt Spielern nur bis 30 Blöcke Entfernung."),content,top+196-scroll,0xFFBBBBBB);}
        else {c.drawTextWithShadow(textRenderer,Text.literal("NPC-Funktion auswählen"),content,top+48-scroll,0xFFE8EAF0);c.drawTextWithShadow(textRenderer,Text.literal("Accessoires: Shop · Tutorial: Dialog · Quests: Aufgaben"),content,top+108-scroll,0xFFBBBBBB);c.drawTextWithShadow(textRenderer,Text.literal("Der passende Bild-Tag wird über dem NPC angezeigt."),content,top+128-scroll,0xFF999999);}
        c.disableScissor();c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(status,right-left-30),left+15,bottom-39,status.startsWith("Gespeichert")?0xFF77DD99:0xFFFFA0AA);
    }

    private void drawTab(DrawContext c,int x,int y,int index,boolean selected){c.fill(x,y,x+106,y+25,selected?0xFF9C203C:0xFF242A35);drawPixelIcon(c,x+6,y+6,index,selected?0xFFFF9BA7:0xFFB9C0CD);c.drawTextWithShadow(textRenderer,Text.literal(switch(index){case 0->"Optik";case 1->"Text";case 2->"Pose";case 3->"Winkel";default->"Aufgabe";}),x+23,y+9,0xFFFFFFFF);}
    private void drawPixelIcon(DrawContext c,int x,int y,int icon,int color){String[] pattern=switch(icon%6){case 0->new String[]{"01110","11111","10101","11111","01010"};case 1->new String[]{"00100","01110","11111","01110","00100"};case 2->new String[]{"10001","01010","00100","01010","10001"};case 3->new String[]{"00100","01110","11111","00100","00100"};case 4->new String[]{"01010","11111","11111","01110","00100"};default->new String[]{"10001","01010","00100","01010","10001"};};for(int row=0;row<pattern.length;row++)for(int col=0;col<pattern[row].length();col++)if(pattern[row].charAt(col)=='1')c.fill(x+col*2,y+row*2,x+col*2+2,y+row*2+2,color);}
    private int panelWidth(){return Math.max(280,Math.min(520,width-24));}private int panelLeft(){return Math.max(8,(width-panelWidth())/2);}private int panelRight(){return panelLeft()+panelWidth();}private int panelTop(){return 24;}private int panelBottom(){return height-8;}
    private String colorName(String value){return switch(value.toLowerCase(java.util.Locale.ROOT)){case "#c3971f"->"Gelb";case "#8027b0"->"Violett";case "#2149c4"->"Blau";default->"Pink";};}
    private static String defaultGlowColor(String task){return switch(task){case "tutorial"->"#c3971f";case "accessories"->"#8027b0";case "quest"->"#2149c4";default->"#ec2f53";};}
    private String taskLabel(String value){for(int i=0;i<TASKS.length;i++)if(TASKS[i].equals(value))return TASK_LABELS[i];return TASK_LABELS[0];}
    private static boolean isRoleTask(String value){return "tutorial".equals(value)||"accessories".equals(value)||"quest".equals(value);}
    private void captureFields(){if(nameField!=null)displayName=nameField.getText().trim();if(dialogueField!=null)dialogue=dialogueField.getText();}
    private void save(){if(pending)return;captureFields();List<String> pages=new ArrayList<>();if(!dialogue.isBlank())for(String p:dialogue.split("\\|\\|",-1)){String page=p.trim();if(page.isEmpty()||page.length()>240||pages.size()>=12){status="Jede Dialogseite braucht 1–240 Zeichen (max. 12 Seiten).";return;}pages.add(page);}if(displayName.isBlank()){status="Bitte einen NPC-Namen eingeben.";return;}
        glowColor=defaultGlowColor(taskType);JsonArray pageArray=new JsonArray();pages.forEach(pageArray::add);JsonObject interaction=new JsonObject();interaction.add("pages",pageArray);JsonObject body=new JsonObject();body.addProperty("name",displayName);body.addProperty("scale",scale);body.addProperty("glowColor",glowColor);body.addProperty("animation",animation);body.addProperty("yaw",yaw);body.addProperty("pitch",pitch);body.addProperty("facePlayer",facePlayer);body.addProperty("taskType",taskType);body.add("interactionData",interaction);body.addProperty("server",server);body.addProperty("world",world);
        String savedSettings="Gespeichert · "+animationLabel(animation)+" · "+colorName(glowColor);
        if(server.isBlank()&&client.getServer()!=null){pending=true;status="Wird lokal gespeichert …";clearAndInit();boolean saved=MpsqLocalNpcStore.update(MpsqActionSync.world(),npcId,body);pending=false;status=saved?savedSettings:"Lokaler NPC wurde nicht gefunden.";if(saved&&(!isRoleTask(taskType)||!MpsqNpcVisitStore.hasVisited(taskType)))MpsqNpcVisitStore.setGlowEnabled(npcId,glowEnabled);MpsqAccessoryRenderer.refresh();clearAndInit();return;}
        pending=true;status="Wird gespeichert …";clearAndInit();MpsqApiClient.patch("/npcs/"+npcId,body).whenComplete((data,error)->client.execute(()->{pending=false;status=error==null?savedSettings:"Speichern fehlgeschlagen: "+error.getMessage();if(error==null&&(!isRoleTask(taskType)||!MpsqNpcVisitStore.hasVisited(taskType)))MpsqNpcVisitStore.setGlowEnabled(npcId,glowEnabled);MpsqAccessoryRenderer.refresh();clearAndInit();}));}
    private String animationLabel(String value){for(int i=0;i<ANIMATIONS.length;i++)if(ANIMATIONS[i].equals(value))return ANIMATION_LABELS[i];return ANIMATION_LABELS[0];}
    private static String str(JsonObject o,String key,String fallback){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;}
    private static float wrapYaw(float value){return ((value%360)+360)%360;}

    @Override public boolean mouseClicked(double x,double y,int button){if(button==0){int left=panelLeft(),top=panelTop();if(x>=left+8&&x<=left+114&&y>=top+48&&y<top+48+TABS.length*26){captureFields();tab=Math.max(0,Math.min(4,(int)(y-(top+48))/26));scroll=0;clearAndInit();return true;}if(tab==2&&x>=left+122&&x<=left+320&&y>=top+73-scroll&&y<top+73-scroll+ANIMATIONS.length*34){int index=(int)(y-(top+73-scroll))/34;if(index>=0&&index<ANIMATIONS.length){animation=ANIMATIONS[index];status="Pose gewählt – Änderungen noch speichern.";return true;}}if(tab==4&&x>=left+122&&y>=top+68-scroll&&y<top+94-scroll){int current=Math.max(0,java.util.Arrays.asList(TASKS).indexOf(taskType));taskType=TASKS[(current+1)%TASKS.length];glowColor=defaultGlowColor(taskType);glowEnabled=!MpsqNpcVisitStore.isGlowDisabled(npcId)&&(!isRoleTask(taskType)||!MpsqNpcVisitStore.hasVisited(taskType));clearAndInit();return true;}}return super.mouseClicked(x,y,button);}
    private int contentHeight(){return switch(tab){case 0->244;case 1->130;case 2->73+ANIMATIONS.length*34;case 3->220;default->184;};}
    private int maxScroll(){return Math.max(0,contentHeight()-(panelBottom()-50-(panelTop()+43)));}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(x<panelLeft()+118||x>panelRight()-8)return false;captureFields();scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)Math.signum(vertical)*24));clearAndInit();return true;}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
