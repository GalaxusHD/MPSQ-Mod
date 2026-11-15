package de.galaxushd.mpsqcamera;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Ephemeral reactions never overwrite the configured idle animation. */
final class MpsqNpcReactions {
    static final String IDLE1="scenes/scene_townsfolk_1/idle",IDLE2="scenes/scene_townsfolk_2/idle";
    static final String SEE="scenes/scene_townsfolk_2/see",INTERACT="scenes/scene_townsfolk_2/interact",POSE="scenes/scene_townsfolk_5_pose_7/idle";
    private static final Map<MpsqScenes.Profile,MpsqNpcReactionClock> states=new WeakHashMap<>();
    private static String menuNpc="";
    private static Object world;
    static void opened(String id){menuNpc=id.startsWith("cinematic:")?id:"npc:"+id;world=Minecraft.getInstance().level;}
    static void tick(){var c=Minecraft.getInstance();if(c.level!=world){menuNpc="";states.clear();world=c.level;}if(c.gui.screen()==null&&!MpsqDialogueManager.active())menuNpc="";}
    static MpsqNpcReactionClock.Choice choose(MpsqScenes.Profile p,Vec3 pos,String base,double time){
        if(!Set.of("accessories","quest","tutorial").contains(p.task)||!Set.of(IDLE1,IDLE2).contains(base))return null;
        var state=states.computeIfAbsent(p,k->new MpsqNpcReactionClock());
        boolean menu=!p.runtimeId.isBlank()&&p.runtimeId.equals(menuNpc);
        boolean looking=false; // Gaze animations disabled; Wumpus owns its independent wave behavior.
        return state.advance(base,looking,menu,time,MpsqScenesClips.get(SEE).length(),MpsqScenesClips.get(base.equals(IDLE1)?INTERACT:POSE).length());
    }
}
