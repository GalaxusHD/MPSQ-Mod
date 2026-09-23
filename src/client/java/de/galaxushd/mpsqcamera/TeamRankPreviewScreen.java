package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.List;

/** Persistent rank explanation; selecting an icon never changes a player's rank. */
public final class TeamRankPreviewScreen extends Screen {
    private final Screen parent;
    private TeamRank selected;
    private static final List<TeamRank> RANKS=List.of(TeamRank.FRONTMAN,TeamRank.SENIOR_OFFICER,TeamRank.OFFICER,TeamRank.SOLDIER,TeamRank.WORKER,TeamRank.UNDERCOVER_001,TeamRank.STREAMER,TeamRank.PLAYER,TeamRank.VIP);
    public TeamRankPreviewScreen(Screen parent){super(Text.literal("Ränge"));this.parent=parent;}
    private int rowHeight(){return Math.max(15,Math.min(28,(height-94)/9));}
    private int iconHeight(){return Math.min(12,rowHeight()-5);}
    private int panelX(){return Math.max(130,Math.min(width/3,205));}
    @Override protected void init(){addDrawableChild(ButtonWidget.builder(Text.literal("Zurück"),b->close()).dimensions(12,height-28,100,20).build());addDrawableChild(ButtonWidget.builder(Text.literal("Mitglieder / Ränge"),b->client.setScreen(new TeamMembersScreen(this))).dimensions(width-152,height-28,140,20).build());}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0&&x>=12&&x<panelX()-10){int i=(int)((y-58)/rowHeight());if(y>=58&&i<RANKS.size()){selected=RANKS.get(i);return true;}}
        return super.mouseClicked(x,y,button);
    }
    @Override public void renderBackground(DrawContext c,int x,int y,float d){super.renderBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void render(DrawContext c,int x,int y,float d){
        super.render(c,x,y,d);
        c.drawCenteredTextWithShadow(textRenderer,title,width/2,24,MpsqTheme.TEXT_TITEL);c.fill(12,45,width-12,47,MpsqTheme.TEXT_GEDAEMPT);
        int row=58;
        for(TeamRank rank:RANKS){if(rank==selected)c.fill(10,row-2,panelX()-10,row+rowHeight()-2,0x88557A9B);rank.draw(c,14,row,iconHeight());row+=rowHeight();}
        if(selected==null)return;
        int left=panelX(), right=width-12, top=54;
        c.fill(left,top,right,height-38,0xB010141C);
        selected.draw(c,left+(right-left-selected.widthForHeight(iconHeight()))/2,top+12,iconHeight());
        c.enableScissor(left+8,top+32,right-8,height-42);
        int lineY=top+36;
        for(var line:textRenderer.wrapLines(Text.literal(description(selected)),Math.max(20,right-left-20))){c.drawTextWithShadow(textRenderer,line,left+10,lineY,0xFFFFFFFF);lineY+=12;}
        lineY+=10;
        for(String permission:permissions(selected)){
            for(var line:textRenderer.wrapLines(Text.literal("• "+permission),Math.max(20,right-left-20))){c.drawTextWithShadow(textRenderer,line,left+10,lineY,0xFFAACCDD);lineY+=12;}
            lineY+=4;
        }
        c.disableScissor();
    }
    private static String description(TeamRank rank){return switch(rank){
        case SENIOR_OFFICER -> "Verwaltet das Team und behält seine Rechte auch mit einem vorübergehenden Rang.";
        case FRONTMAN -> "Leitet das Event und nutzt die Verwaltungswerkzeuge des Teams.";
        case OFFICER -> "Organisiert das Team und verwaltet den Ablauf des Events.";
        case SOLDIER -> "Unterstützt die Durchführung und Überwachung der Spiele.";
        case WORKER -> "Unterstützt das Team bei Aufgaben während des Events.";
        case UNDERCOVER_001 -> "Vorübergehende Rolle für die Teilnahme am Event.";
        case STREAMER -> "Begleitet das Event mit Zugriff auf freigegebene Kameras.";
        case PLAYER -> "Nimmt als Spieler am Event teil.";
        case VIP -> "Vorübergehende Zuschauerrolle ohne Mitarbeiterrechte.";
    };}
    private static List<String> permissions(TeamRank r){
        if(r==TeamRank.SENIOR_OFFICER)return List.of("Dauerhafte Teamränge verwalten","Vorübergehende Ränge auswählen","Texte, Todos und Eventaktionen verwalten","Rangänderungen in Logs ansehen","Kameras ansehen");
        if(r==TeamRank.FRONTMAN||r==TeamRank.OFFICER)return List.of("Untere Teamränge verwalten","Texte und Eventaktionen verwalten","Rangänderungen in Logs ansehen","Kameras ansehen","Eigenen 001-Rang wählen");
        if(r==TeamRank.WORKER||r==TeamRank.SOLDIER)return List.of("Kameras ansehen","Verborgene Spielernamen sehen","Eigenen 001-Rang wählen");
        if(r==TeamRank.STREAMER)return List.of("Freigegebene Kameras ansehen","Keine Rangverwaltung");
        return List.of("Rangübersicht ansehen","Keine Rangverwaltung","Keine internen Texte oder Logs");
    }
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
