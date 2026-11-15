package de.galaxushd.mpsqcamera;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** One startup sequence per process; license acceptance is local and versioned separately. */
public final class MpsqStartupScreen extends Screen {
    private static final String LICENSE_VERSION="1.0";
    private static final Path ACCEPTANCE=FabricLoader.getInstance().getConfigDir().resolve("mpsq-license-accepted.txt");
    private static boolean started;
    private final Screen destination;
    private boolean license,checked,needsLicense;
    private String error="";
    private int stage,scroll;
    private long stageStart;
    private Button confirm;
    private final List<String> lines=new ArrayList<>();

    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!started&&client.level==null&&client.gui.overlay()==null&&client.gui.screen() instanceof TitleScreen){
                started=true;
                client.gui.setScreen(new MpsqStartupScreen(client.gui.screen()));
            }
        });
    }
    private MpsqStartupScreen(Screen destination){
        super(Component.literal("Willkommen bei MPSQ"));
        this.destination=destination;
        try{needsLicense=!Files.readString(ACCEPTANCE).trim().equals(LICENSE_VERSION);}
        catch(Exception ignored){needsLicense=true;}
        stageStart=System.nanoTime();
    }
    @Override protected void init(){
        if(!license)return;
        int panel=Math.min(370,width-32);
        lines.clear();
        for(String paragraph:LizenzScreen.licenseLines()){
            String current="";
            for(String word:paragraph.split(" ")){
                String candidate=current.isEmpty()?word:current+" "+word;
                if(!current.isEmpty()&&font.width(candidate)>panel-24){lines.add(current);current=word;}
                else current=candidate;
            }
            lines.add(current);
        }
        scroll=Math.min(scroll,maxScroll());
        addRenderableWidget(Button.builder(Component.literal(checked?"[✓] Ich akzeptiere die Nutzungsbedingungen":"[ ] Ich akzeptiere die Nutzungsbedingungen"),button->{
            checked=!checked;button.setMessage(Component.literal(checked?"[✓] Ich akzeptiere die Nutzungsbedingungen":"[ ] Ich akzeptiere die Nutzungsbedingungen"));confirm.active=checked;
        }).bounds((width-panel)/2,height-66,panel,20).build());
        confirm=addRenderableWidget(Button.builder(Component.literal("Bestätigen"),button->accept()).bounds(width/2-75,height-40,150,20).build());
        confirm.active=checked;
    }
    private void accept(){
        if(!checked)return;
        try{
            Files.createDirectories(ACCEPTANCE.getParent());
            Path temporary=ACCEPTANCE.resolveSibling(ACCEPTANCE.getFileName()+".tmp");
            Files.writeString(temporary,LICENSE_VERSION);
            try{Files.move(temporary,ACCEPTANCE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,ACCEPTANCE,StandardCopyOption.REPLACE_EXISTING);}
            license=false;needsLicense=false;minecraft.gui.setScreen(destination);
        }catch(Exception ex){error="Zustimmung konnte nicht gespeichert werden.";MpsqCameraClient.LOGGER.warn(error,ex);}
    }
    private int maxScroll(){return Math.max(0,lines.size()*11-Math.max(11,height-128));}
    @Override public void tick(){
        if(license)return;
        if(minecraft.gui.overlay()!=null){stageStart=System.nanoTime();return;}
        if((System.nanoTime()-stageStart)/1e9>=4.5){
            if(++stage>=2){
                if(needsLicense){license=true;clearWidgets();init();}
                else minecraft.gui.setScreen(destination);
                return;
            }
            stageStart=System.nanoTime();
        }
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){g.fill(0,0,width,height,0xFF000000);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){
        g.fill(0,0,width,height,0xFF000000);
        if(license){
            int panel=Math.min(370,width-32),left=(width-panel)/2,top=40,bottom=height-88;
            g.fill(left,18,left+panel,34,0xFF181818);
            g.centeredText(font,"WILLKOMMEN BEI MPSQ",width/2,22,0xFFE0E0E0);
            g.enableScissor(left,top,left+panel,bottom);
            for(int i=0;i<lines.size();i++)g.centeredText(font,lines.get(i),width/2,top+i*11-scroll,0xFFBBBBBB);
            g.disableScissor();
            if(maxScroll()>0)g.centeredText(font,"Mit dem Mausrad weiterlesen",width/2,height-82,0xFF777777);
            if(!error.isEmpty())g.centeredText(font,error,width/2,height-12,0xFFFF7777);
        }else{
            int sourceW=stage==0?1672:860,sourceH=stage==0?941:860;
            float fit=stage==0?Math.min((float)width/sourceW,(float)height/sourceH):Math.min(width*.55f/sourceW,height*.70f/sourceH);
            int w=Math.max(1,Math.round(sourceW*fit)),h=Math.max(1,Math.round(sourceH*fit));
            Identifier texture=Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID,"textures/intro/"+(stage==0?"galaxus":"mpsq")+".png");
            g.blit(RenderPipelines.GUI_TEXTURED,texture,(width-w)/2,(height-h)/2,0,0,w,h,sourceW,sourceH,sourceW,sourceH);
            double seconds=(System.nanoTime()-stageStart)/1e9;
            double opacity=Math.max(0,Math.min(1,Math.min(seconds/.9,(4.5-seconds)/.9)));
            int alpha=(int)Math.round((1-opacity*(stage==0?1.0:0.55))*255);
            if(alpha>0)g.fill(0,0,width,height,alpha<<24);
        }
        super.extractRenderState(g,x,y,delta);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(license){scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)(vertical*22)));return true;}
        return false;
    }
    @Override public boolean shouldCloseOnEsc(){return false;}
    @Override public void onClose(){ }
    @Override public boolean isPauseScreen(){return false;}
}
