package de.galaxushd.mpsqcamera;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicitly entered tool mode, never consumes input outside the active world/editor. */
public final class MpsqScenesTool {
    private static MpsqSharedPaths.Route path;
    private static MpsqScenes.Target target;
    private static MpsqScenes.Profile draft,placer;
    private static Screen back;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static int axis,step;
    private static final double[] STEPS={.01,.1,1};
    private static boolean naming;
    static void beginPath(Screen parent){clear();back=parent;world=Minecraft.getInstance().level;path=new MpsqSharedPaths.Route();path.dimension=world.dimension().identifier().toString();Minecraft.getInstance().gui.setScreen(null);hint();}
    static void recordedPath(Screen parent,java.util.List<MpsqMotionCapture.Frame> frames){beginPath(parent);path.recording=frames;var a=frames.getFirst();var b=frames.getLast();path.points.add(new MpsqCinematicScene.Waypoint(a.x,a.y,a.z,1));path.points.add(new MpsqCinematicScene.Waypoint(b.x,b.y,b.z,Math.max(.1,b.time)));naming=true;say("Aufnahme benennen: /mpsq-scenes pfadname Mein Pfad");}
    static void beginMove(MpsqScenes.Target t,Screen parent){clear();back=parent;world=Minecraft.getInstance().level;target=t;draft=MpsqScenes.copy(t.profile());step=1;Minecraft.getInstance().gui.setScreen(null);hint();}
    static void beginPlace(MpsqScenes.Profile template,Screen parent){clear();placer=template;back=parent;world=Minecraft.getInstance().level;Minecraft.getInstance().gui.setScreen(null);hint();}
    private static boolean active(){var c=Minecraft.getInstance();return c.level==world&&c.player!=null&&MpsqScenes.allowed()&&(path!=null||target!=null||placer!=null);}
    static void tick(){if((path!=null||target!=null||placer!=null)&&!active())clear();}
    private static void clear(){path=null;target=null;draft=null;placer=null;world=null;back=null;naming=false;axis=0;}
    private static void say(String s){var c=Minecraft.getInstance();if(c.player!=null)de.galaxushd.mpsqcamera.compat.PlayerMessages.send(c.player,Component.literal(s),true);}
    static void initialize(){net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("mpsqcamera","scenes_tool"),(g,t)->renderHint(g));}
    private static void hint(){} // Persistent HUD below replaces the single-line action bar.
    private static void renderHint(net.minecraft.client.gui.GuiGraphicsExtractor g){
        var c=Minecraft.getInstance();if(!active()||c.gui.screen()!=null)return;
        String first,second;
        if(placer!=null){first="NPC platzieren: Rechtsklick auf einen Block";second="Einmal verwendbar · Esc abbrechen";}
        else if(path!=null){first=naming?"Pfad benennen: /mpsq-scenes pfadname Mein Pfad":"Pfad: Linksklick Punkt · Q zurück · Punkte "+path.points.size()+"/21";second=naming?"Esc abbrechen":"Rechtsklick fertig · Esc abbrechen";}
        else{first="Verschieben: "+new String[]{"X","Y","Z","Größe"}[axis]+" · Schritt "+String.format(java.util.Locale.GERMANY,"%.2f",STEPS[step])+" · Scrollen wählen";second="Shift+Scrollen ändern · Q Schritt · Linksklick speichern · Esc abbrechen";}
        int widest=Math.max(c.font.width(first),c.font.width(second));float scale=Math.min(1f,(g.guiWidth()-16f)/Math.max(1,widest));
        g.pose().pushMatrix();g.pose().translate(g.guiWidth()/2f,g.guiHeight()-77f);g.pose().scale(scale);
        g.fill(-widest/2-4,-3,widest/2+4,22,0x80102638);
        g.centeredText(c.font,first,0,0,0xFFFFFFFF);g.centeredText(c.font,second,0,11,0xFFFFFFFF);g.pose().popMatrix();
    }

    public static boolean mouse(int button,int action){var c=Minecraft.getInstance();if(!active()||c.gui.screen()!=null||naming||(button!=InputConstants.MOUSE_BUTTON_LEFT&&button!=InputConstants.MOUSE_BUTTON_RIGHT))return false;if(action!=InputConstants.PRESS)return true;
        try{if(placer!=null){if(button==InputConstants.MOUSE_BUTTON_RIGHT){if(!(c.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)||hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)throw new IllegalArgumentException("Einen Block innerhalb der Reichweite ansehen.");var pos=hit.getBlockPos().relative(hit.getDirection());TeamVisibilitySettings.requirePlacement(pos.getX()+.5,pos.getY(),pos.getZ()+.5);var created=MpsqScenes.create();var p=MpsqScenes.copy(placer);p.runtimeId=created.key();p.npc.x=pos.getX()+.5;p.npc.y=pos.getY();p.npc.z=pos.getZ()+.5;p.npc.yaw=c.player.getYRot();p.npc.placed=true;MpsqScenes.save(created,p);var parent=back;clear();MpsqScenesHub.showLater(parent);}}else if(path!=null){if(button==InputConstants.MOUSE_BUTTON_LEFT){if(path.points.size()>=21)throw new IllegalArgumentException("Maximal 21 Wegpunkte.");var p=c.player.position();TeamVisibilitySettings.requirePlacement(p.x,p.y,p.z);double duration=1;if(!path.points.isEmpty()){var old=path.points.getLast();duration=Math.max(.1,Math.sqrt(Math.pow(p.x-old.x,2)+Math.pow(p.y-old.y,2)+Math.pow(p.z-old.z,2))/1.4);}path.points.add(new MpsqCinematicScene.Waypoint(p.x,p.y,p.z,duration));hint();}else{if(path.points.size()<2)throw new IllegalArgumentException("Mindestens zwei Wegpunkte setzen.");naming=true;say("Pfad benennen: /mpsq-scenes pfadname Mein Pfad (Esc zum Abbruch)");}}
        else if(button==InputConstants.MOUSE_BUTTON_LEFT){MpsqScenes.save(target,draft);var parent=back;clear();MpsqScenesHub.showLater(parent);}}
        catch(Exception e){say(e.getMessage());}return true;
    }
    static void finishPath(String name){try{if(!active()||path==null||!naming)throw new IllegalStateException("Kein abgeschlossener Pfad wartet auf einen Namen.");path.name=name.strip();MpsqSharedPaths.save(path);var parent=back;clear();MpsqScenesHub.showLater(parent);}catch(Exception e){say(e.getMessage());}}
    public static boolean key(int key,int action){var c=Minecraft.getInstance();if(!active()||c.gui.screen()!=null)return false;if(key!=InputConstants.KEY_Q&&key!=InputConstants.KEY_ESCAPE)return false;if(action==InputConstants.PRESS){if(key==InputConstants.KEY_ESCAPE){var parent=back;clear();MpsqScenesHub.showLater(parent);}else if(path!=null){if(!path.points.isEmpty())path.points.removeLast();naming=false;hint();}else if(target!=null){step=(step+1)%STEPS.length;hint();}}return true;}
    public static boolean scroll(double amount){var c=Minecraft.getInstance();if(!active()||target==null||c.gui.screen()!=null)return false;
        if(!c.player.isShiftKeyDown())axis=Math.floorMod(axis+(amount>0?1:-1),4);
        else{var old=MpsqScenes.copy(draft);double delta=Math.signum(amount)*STEPS[step];try{switch(axis){case 0->draft.npc.x+=delta;case 1->draft.npc.y+=delta;case 2->draft.npc.z+=delta;default->draft.scale=(float)Math.max(.25,Math.min(3,draft.scale+delta));}draft.validate();TeamVisibilitySettings.requirePlacement(draft.npc.x,draft.npc.y,draft.npc.z);}catch(Exception e){draft=old;say(e.getMessage());return true;}}
        hint();return true;
    }
    static MpsqScenes.Profile preview(String id){return active()&&target!=null&&target.key().equals("npc:"+id)?draft:null;}
}
