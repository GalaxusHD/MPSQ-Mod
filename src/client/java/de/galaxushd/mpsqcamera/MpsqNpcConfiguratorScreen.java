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
    private static final String[] COLORS={"none","white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"};
    private static final String[] COLOR_LABELS={"Kein Leuchten","Weiß","Orange","Magenta","Hellblau","Gelb","Hellgrün","Rosa","Grau","Hellgrau","Türkis","Lila","Blau","Braun","Grün","Rot","Schwarz"};
    private static final String[] ANIMATIONS={"none","bob","turn","pulse"};
    private static final String[] ANIMATION_LABELS={"Keine","Schweben","Drehen","Pulsieren"};
    private static final int[] COLOR_VALUES={0xFF777777,0xFFFFFFFF,0xFFFFAA33,0xFFFF55FF,0xFF55AAFF,0xFFFFFF55,0xFF55FF55,0xFFFF88BB,0xFF666666,0xFFBBBBBB,0xFF55FFFF,0xFFAA55FF,0xFF5555FF,0xFF8B5A2B,0xFF55AA33,0xFFFF5555,0xFF333333};

    private final Screen parent;
    private final String npcId,server,world;
    private int tab;
    private String displayName,glowColor,animation,dialogue,status="",taskType="none";
    private float scale;
    private float yaw,pitch;
    private boolean facePlayer;
    private TextFieldWidget nameField,dialogueField;
    private boolean pending;

    public MpsqNpcConfiguratorScreen(Screen parent,JsonObject npc){
        super(Text.literal("NPC-Konfigurator"));this.parent=parent;this.npcId=npc.get("id").getAsString();
        this.server=MpsqActionSync.server();this.world=MpsqActionSync.world();
        this.displayName=str(npc,"display_name",str(npc,"name","NPC"));this.scale=npc.has("scale")?npc.get("scale").getAsFloat():1f;
        this.glowColor=str(npc,"glow_color","none");this.animation=str(npc,"animation","none");
        this.taskType=str(npc,"task_type","none");
        this.yaw=npc.has("yaw")?npc.get("yaw").getAsFloat():0f;this.pitch=npc.has("pitch")?npc.get("pitch").getAsFloat():0f;this.facePlayer=npc.has("face_player")&&npc.get("face_player").getAsBoolean();
        JsonObject interaction=npc.has("interaction_data")&&npc.get("interaction_data").isJsonObject()?npc.getAsJsonObject("interaction_data"):new JsonObject();
        List<String> pages=new ArrayList<>();if(interaction.has("pages")&&interaction.get("pages").isJsonArray())for(var page:interaction.getAsJsonArray("pages"))pages.add(page.getAsString());this.dialogue=String.join(" || ",pages);
    }

    @Override protected void init(){
        clearChildren();int left=panelLeft(),content=left+82,top=43;
        if(tab==0){nameField=addDrawableChild(new TextFieldWidget(textRenderer,content,top+52,panelRight()-12-content,22,Text.literal("Name")));nameField.setMaxLength(64);nameField.setText(displayName);
            addDrawableChild(ButtonWidget.builder(Text.literal("−"),b->{captureFields();scale=Math.max(.25f,scale-.25f);clearAndInit();}).dimensions(content,top+103,34,22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("+"),b->{captureFields();scale=Math.min(3f,scale+.25f);clearAndInit();}).dimensions(content+142,top+103,34,22).build());
        }else if(tab==1){dialogueField=addDrawableChild(new TextFieldWidget(textRenderer,content,top+65,panelRight()-12-content,22,Text.literal("Dialog")));dialogueField.setMaxLength(3072);dialogueField.setText(dialogue);}
        else if(tab==3){
            addDrawableChild(ButtonWidget.builder(Text.literal("Yaw −15°"),b->{yaw=wrapYaw(yaw-15);clearAndInit();}).dimensions(content,top+76,88,22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Yaw +15°"),b->{yaw=wrapYaw(yaw+15);clearAndInit();}).dimensions(content+96,top+76,88,22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Pitch −15°"),b->{pitch=Math.max(-90,pitch-15);clearAndInit();}).dimensions(content,top+119,88,22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Pitch +15°"),b->{pitch=Math.min(90,pitch+15);clearAndInit();}).dimensions(content+96,top+119,88,22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Blick zu Spielern: "+(facePlayer?"An":"Aus")),b->{facePlayer=!facePlayer;clearAndInit();}).dimensions(content,top+163,184,22).build());
        }else if(tab==4){
            int current=Math.max(0,java.util.Arrays.asList(TASKS).indexOf(taskType));
            addDrawableChild(ButtonWidget.builder(Text.literal("Aufgabe: "+taskLabel(taskType)),b->{taskType=TASKS[(current+1)%TASKS.length];clearAndInit();}).dimensions(content,top+68,210,26).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal(pending?"Speichert …":"Änderungen speichern"),b->save()).dimensions(content,height-48,panelRight()-12-content,22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"),b->close()).dimensions(panelLeft()+12,height-48,58,22).build());
    }

    @Override public void renderBackground(DrawContext c,int x,int y,float delta){
        super.renderBackground(c,x,y,delta);MpsqTheme.drawBackground(c,width,height);
        int left=panelLeft(),right=width-left,top=43,bottom=height-34;
        c.fill(left+4,top+5,right+4,bottom+5,0x88000000);c.fill(left,top,right,bottom,0xF0191D28);c.fill(left,top,right,top+3,0xFFFF536A);c.fill(left,top,left+2,bottom,0xFF9C203C);c.fill(right-2,top,right,bottom,0xFF9C203C);c.fill(left,bottom-2,right,bottom,0xFF9C203C);
    }
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        super.render(c,mouseX,mouseY,delta);int left=panelLeft(),right=width-left,top=43,bottom=height-34;
        c.drawTextWithShadow(textRenderer,Text.literal("MPSQ  /  NPC STUDIO"),left+15,top+13,0xFFFF7182);c.drawTextWithShadow(textRenderer,Text.literal("KONFIGURATOR"),left+15,top+27,0xFFFFFFFF);
        drawNpcPreview(c,left+14,top+68);
        for(int i=0;i<TABS.length;i++)drawTab(c,left+12,top+139+i*37,i,i==tab);
        int content=left+82;c.fill(content-9,top+42,right-12,top+44,0xFF9C203C);c.drawTextWithShadow(textRenderer,Text.literal(TABS[tab].toUpperCase()),content,top+27,0xFFFF7182);
        if(tab==0){c.drawTextWithShadow(textRenderer,Text.literal("Anzeigename"),content,top+43,0xFFE8EAF0);c.drawTextWithShadow(textRenderer,Text.literal("Größe"),content,top+91,0xFFE8EAF0);c.drawCenteredTextWithShadow(textRenderer,Text.literal(String.format(java.util.Locale.ROOT,"%.2f×",scale)),content+88,top+109,0xFFFFFFFF);c.drawTextWithShadow(textRenderer,Text.literal("Leuchtfarbe · Minecraft-Farben"),content,top+137,0xFFE8EAF0);drawPalette(c,content,top+158);c.drawTextWithShadow(textRenderer,Text.literal("Ausgewählt: "+colorName(glowColor)),content,top+225,0xFFBBBBBB);}
        else if(tab==1){c.drawTextWithShadow(textRenderer,Text.literal("Dialog beim Rechtsklick"),content,top+47,0xFFE8EAF0);c.drawTextWithShadow(textRenderer,Text.literal("Seiten mit  ||  trennen · max. 12 × 240 Zeichen"),content,top+96,0xFFBBBBBB);c.drawTextWithShadow(textRenderer,Text.literal("Der Text erscheint im MPSQ-Dialogfenster."),content,top+115,0xFF999999);}
        else if(tab==2){c.drawTextWithShadow(textRenderer,Text.literal("Laufanimation auswählen"),content,top+49,0xFFE8EAF0);for(int i=0;i<ANIMATION_LABELS.length;i++){int bx=content,by=top+73+i*34;c.fill(bx,by,bx+190,by+27,animation.equals(ANIMATIONS[i])?0xFF9C203C:0xFF292E3A);drawPixelIcon(c,bx+7,by+7,i+3,0xFFFF687A);c.drawTextWithShadow(textRenderer,Text.literal(ANIMATION_LABELS[i]),bx+29,by+8,0xFFFFFFFF);if(animation.equals(ANIMATIONS[i]))c.drawTextWithShadow(textRenderer,Text.literal("✓"),bx+171,by+8,0xFFFFA0AA);}}
        else if(tab==3){c.drawTextWithShadow(textRenderer,Text.literal("Kopfrotation · Schritte von 15°"),content,top+49,0xFFE8EAF0);c.drawTextWithShadow(textRenderer,Text.literal("Yaw: "+Math.round(yaw)+"°"),content,top+104,0xFFFFFFFF);c.drawTextWithShadow(textRenderer,Text.literal("Pitch: "+Math.round(pitch)+"°"),content,top+147,0xFFFFFFFF);c.drawTextWithShadow(textRenderer,Text.literal("Folgt Spielern nur bis 30 Blöcke Entfernung."),content,top+196,0xFFBBBBBB);}
        else {c.drawTextWithShadow(textRenderer,Text.literal("NPC-Funktion auswählen"),content,top+48,0xFFE8EAF0);c.drawTextWithShadow(textRenderer,Text.literal("Accessoires: Shop · Tutorial: Dialog · Quests: Aufgaben"),content,top+108,0xFFBBBBBB);c.drawTextWithShadow(textRenderer,Text.literal("Der passende Bild-Tag wird über dem NPC angezeigt."),content,top+128,0xFF999999);}
        c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(status,right-left-30),left+15,bottom-19,status.startsWith("Gespeichert")?0xFF77DD99:0xFFFFA0AA);
    }

    private void drawNpcPreview(DrawContext c,int x,int y){int bg=colorValue(glowColor);c.fill(x,y,x+48,y+52,0xFF252A35);c.fill(x+4,y+4,x+44,y+48,0xFF323846);int accent=glowColor.equals("none")?0xFFFF536A:bg;c.fill(x+17,y+9,x+31,y+23,accent);c.fill(x+12,y+24,x+36,y+39,accent);c.fill(x+8,y+26,x+12,y+36,0xFF171A21);c.fill(x+36,y+26,x+40,y+36,0xFF171A21);c.fill(x+15,y+39,x+21,y+47,accent);c.fill(x+27,y+39,x+33,y+47,accent);c.drawCenteredTextWithShadow(textRenderer,Text.literal("NPC"),x+24,y+54,0xFFBBBBBB);}
    private void drawTab(DrawContext c,int x,int y,int index,boolean selected){c.fill(x,y,x+60,y+31,selected?0xFF9C203C:0xFF242A35);drawPixelIcon(c,x+6,y+7,index,selected?0xFFFF9BA7:0xFFB9C0CD);c.drawTextWithShadow(textRenderer,Text.literal(switch(index){case 0->"Optik";case 1->"Text";case 2->"Pose";case 3->"Winkel";default->"Aufgabe";}),x+25,y+11,0xFFFFFFFF);}
    private void drawPixelIcon(DrawContext c,int x,int y,int icon,int color){String[] pattern=switch(icon%6){case 0->new String[]{"01110","11111","10101","11111","01010"};case 1->new String[]{"00100","01110","11111","01110","00100"};case 2->new String[]{"10001","01010","00100","01010","10001"};case 3->new String[]{"00100","01110","11111","00100","00100"};case 4->new String[]{"01010","11111","11111","01110","00100"};default->new String[]{"10001","01010","00100","01010","10001"};};for(int row=0;row<pattern.length;row++)for(int col=0;col<pattern[row].length();col++)if(pattern[row].charAt(col)=='1')c.fill(x+col*2,y+row*2,x+col*2+2,y+row*2+2,color);}
    private void drawPalette(DrawContext c,int x,int y){for(int i=0;i<COLORS.length;i++){int cx=x+(i%6)*23,cy=y+(i/6)*21;int color=COLOR_VALUES[i];c.fill(cx-2,cy-2,cx+18,cy+16,glowColor.equals(COLORS[i])?0xFFFFFFFF:0xFF11151D);c.fill(cx,cy,cx+14,cy+12,color);if(i==0){c.fill(cx+5,cy+5,cx+9,cy+7,0xFF333844);}}}
    private int panelLeft(){return Math.max(12,(width-390)/2);}private int panelRight(){return width-panelLeft();}private int colorValue(String value){for(int i=0;i<COLORS.length;i++)if(COLORS[i].equals(value))return COLOR_VALUES[i];return COLOR_VALUES[0];}
    private String colorName(String value){for(int i=0;i<COLORS.length;i++)if(COLORS[i].equals(value))return COLOR_LABELS[i];return COLOR_LABELS[0];}
    private String taskLabel(String value){for(int i=0;i<TASKS.length;i++)if(TASKS[i].equals(value))return TASK_LABELS[i];return TASK_LABELS[0];}
    private void captureFields(){if(nameField!=null)displayName=nameField.getText().trim();if(dialogueField!=null)dialogue=dialogueField.getText();}
    private void save(){if(pending)return;captureFields();List<String> pages=new ArrayList<>();for(String p:dialogue.split("\\|\\|",-1)){String page=p.trim();if(page.isEmpty()||page.length()>240||pages.size()>=12){status="Dialog: 1–12 Seiten, jeweils max. 240 Zeichen.";return;}pages.add(page);}if(displayName.isBlank()){status="Bitte einen NPC-Namen eingeben.";return;}
        JsonArray pageArray=new JsonArray();pages.forEach(pageArray::add);JsonObject interaction=new JsonObject();interaction.add("pages",pageArray);JsonObject body=new JsonObject();body.addProperty("name",displayName);body.addProperty("scale",scale);body.addProperty("glowColor",glowColor);body.addProperty("animation",animation);body.addProperty("yaw",yaw);body.addProperty("pitch",pitch);body.addProperty("facePlayer",facePlayer);body.addProperty("taskType",taskType);body.add("interactionData",interaction);body.addProperty("server",server);body.addProperty("world",world);pending=true;status="Wird gespeichert …";clearAndInit();
        MpsqApiClient.patch("/npcs/"+npcId,body).whenComplete((data,error)->client.execute(()->{pending=false;status=error==null?"Gespeichert – NPC aktualisiert.":"Speichern fehlgeschlagen: "+error.getMessage();MpsqAccessoryRenderer.refresh();clearAndInit();}));}
    private static String str(JsonObject o,String key,String fallback){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;}
    private static float wrapYaw(float value){return ((value%360)+360)%360;}

    @Override public boolean mouseClicked(double x,double y,int button){if(button==0){int left=panelLeft(),top=43;if(x>=left+10&&x<=left+74&&y>=top+134&&y<top+324){captureFields();tab=Math.max(0,Math.min(4,(int)(y-(top+139))/37));clearAndInit();return true;}if(tab==0&&x>=left+82&&y>=top+156&&y<top+220){int col=(int)(x-(left+82))/23,row=(int)(y-(top+158))/21;int index=row*6+col;if(col>=0&&col<6&&row>=0&&row<3&&index<COLORS.length){captureFields();glowColor=COLORS[index];return true;}}if(tab==2&&x>=left+82&&x<=left+280&&y>=top+73&&y<top+209){int index=(int)(y-(top+73))/34;if(index>=0&&index<ANIMATIONS.length){animation=ANIMATIONS[index];return true;}}if(tab==4&&x>=panelLeft()+82&&y>=top+68&&y<top+94){int current=Math.max(0,java.util.Arrays.asList(TASKS).indexOf(taskType));taskType=TASKS[(current+1)%TASKS.length];clearAndInit();return true;}}return super.mouseClicked(x,y,button);}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
