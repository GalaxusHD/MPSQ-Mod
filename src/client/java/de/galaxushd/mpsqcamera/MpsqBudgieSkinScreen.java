package de.galaxushd.mpsqcamera;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/** Color selection and points purchase screen for the Budgie pet. */
final class MpsqBudgieSkinScreen extends Screen {
    private static final String[] VARIANTS={"green","blue_spangle","cobalt","gray","green_spangle","light_green","olive","sky_blue","white","yellow"};
    private static final Map<String,String> LABELS=new LinkedHashMap<>();
    static {
        LABELS.put("green","Grün · Standard"); LABELS.put("blue_spangle","Blue Spangle"); LABELS.put("cobalt","Cobalt");
        LABELS.put("gray","Grau"); LABELS.put("green_spangle","Green Spangle"); LABELS.put("light_green","Hellgrün");
        LABELS.put("olive","Olive"); LABELS.put("sky_blue","Himmelblau"); LABELS.put("white","Weiß"); LABELS.put("yellow","Gelb");
    }
    private final Screen parent;
    private final boolean[] owned=new boolean[VARIANTS.length];
    private final String[] accessoryIds=new String[VARIANTS.length];
    private final int[] prices=new int[VARIANTS.length];
    private boolean loading=true;
    private boolean pending;
    private String status="Lade freischaltbare Farbvarianten …";

    MpsqBudgieSkinScreen(Screen parent) { super(Text.literal("Wellensittich · Farbvarianten")); this.parent=parent; owned[0]=true; }

    @Override protected void init() {
        super.init();
        MpsqApiClient.get("/pets/budgie-skins").whenComplete((data,error)->{
            if(client==null)return;
            client.execute(()->{
                loading=false;
                if(error!=null){status="Skins konnten nicht geladen werden. Backend-Migration erforderlich.";return;}
                try {
                    JsonArray rows=data.getAsJsonArray();
                    for(JsonElement element:rows){JsonObject row=element.getAsJsonObject();String key=row.get("key").getAsString();
                        for(int i=1;i<VARIANTS.length;i++)if(VARIANTS[i].equals(key)){owned[i]=row.get("owned").getAsBoolean();accessoryIds[i]=row.get("id").getAsString();prices[i]=row.get("price_points").getAsInt();}}
                    status="Rechtsklick auf den Wellensittich öffnet diese Auswahl.";
                }catch(Exception ignored){status="Skin-Daten sind ungültig.";}
            });
        });
    }

    @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta){context.fillGradient(0,0,width,height,0xE61A1A1A,0xF2050505);}

    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        context.drawCenteredTextWithShadow(textRenderer,"WELLENSITTICH · FARBVARIANTEN",width/2,18,0xFFFFFFFF);
        int cols=5, cardW=Math.min(112,(width-32)/cols), cardH=118, gap=5;
        int totalW=cols*cardW+(cols-1)*gap,left=(width-totalW)/2,top=Math.max(48,(height-(2*cardH+gap+36))/2);
        for(int i=0;i<VARIANTS.length;i++){
            int col=i%cols,row=i/cols,x=left+col*(cardW+gap),y=top+row*(cardH+gap);
            boolean selected=VARIANTS[i].equals(MpsqPetSelectionStore.budgieVariant());
            context.fill(x,y,x+cardW,y+cardH,selected?0xFF642EC2:0xFF303238);
            context.drawTexture(RenderPipelines.GUI_TEXTURED,Identifier.of(MpsqCameraClient.MOD_ID,"textures/pets/budgies/"+VARIANTS[i]+".png"),x+(cardW-64)/2,y+5,0,0,64,64,32,32,32,32);
            String label=LABELS.get(VARIANTS[i]);context.drawCenteredTextWithShadow(textRenderer,label,x+cardW/2,y+73,0xFFFFFFFF);
            String action=i==0?"STANDARD":owned[i]?selected?"AKTIV":"AUSWÄHLEN":loading?"…":accessoryIds[i]==null?"NICHT VERFÜGBAR":prices[i]+" PUNKTE";
            context.drawCenteredTextWithShadow(textRenderer,action,x+cardW/2,y+94,owned[i]?0xFF8CFF8C:0xFFFFD65A);
            if(mouseX>=x&&mouseX<x+cardW&&mouseY>=y&&mouseY<y+cardH)context.fill(x,y,x+cardW,y+1,0xFFFF55AA);
        }
        context.drawCenteredTextWithShadow(textRenderer,status,width/2,top+2*cardH+gap+8,0xFFE0E0E0);
        context.drawCenteredTextWithShadow(textRenderer,"Zurück",width/2,height-22,0xFFFFFFFF);
    }

    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
        if(button!=0)return true;
        int cols=5,cardW=Math.min(112,(width-32)/cols),cardH=118,gap=5,totalW=cols*cardW+(cols-1)*gap,left=(width-totalW)/2,top=Math.max(48,(height-(2*cardH+gap+36))/2);
        if(mouseY>=height-34){client.setScreen(parent);return true;}
        for(int i=0;i<VARIANTS.length;i++){
            int x=left+(i%cols)*(cardW+gap),y=top+(i/cols)*(cardH+gap);
            if(mouseX<x||mouseX>=x+cardW||mouseY<y||mouseY>=y+cardH)continue;
            if(owned[i]){MpsqPetSelectionStore.selectBudgieVariant(VARIANTS[i]);status="Ausgewählt: "+LABELS.get(VARIANTS[i]);return true;}
            if(loading||pending||accessoryIds[i]==null){status="Skin-Katalog fehlt. Bitte BUDGIE_PET_SKINS.sql ausführen.";return true;}
            pending=true;status="Kauf wird verarbeitet …";JsonObject body=new JsonObject();body.addProperty("accessoryId",accessoryIds[i]);int selectedIndex=i;
            MpsqApiClient.post("/me/accessories/buy",body).whenComplete((data,error)->{if(client==null)return;client.execute(()->{pending=false;if(error!=null){status="Kauf fehlgeschlagen: MPSQ-Punkte oder Backend prüfen.";return;}owned[selectedIndex]=true;MpsqPetSelectionStore.selectBudgieVariant(VARIANTS[selectedIndex]);status="Freigeschaltet: "+LABELS.get(VARIANTS[selectedIndex]);});});
            return true;
        }
        return true;
    }
    @Override public void close(){if(client!=null)client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
