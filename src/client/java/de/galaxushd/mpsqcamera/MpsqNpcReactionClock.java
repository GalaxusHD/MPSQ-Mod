package de.galaxushd.mpsqcamera;

/** Rising-edge gaze reaction and menu override, using animation seconds. */
final class MpsqNpcReactionClock {
    private boolean looking;
    private double reaction=-1,menuStart=-1;
    record Choice(String clip,float age){}
    Choice advance(String base,boolean gaze,boolean menu,double time,double reactionLength,double menuLength){
        boolean first=base.equals(MpsqNpcReactions.IDLE1);
        if(menu){if(menuStart<0)menuStart=time;reaction=-1;looking=gaze;
            return new Choice(first?MpsqNpcReactions.INTERACT:MpsqNpcReactions.POSE,(float)((time-menuStart)%menuLength));}
        menuStart=-1;
        if(first&&gaze&&!looking&&reaction<0)reaction=time;
        looking=gaze;
        if(reaction>=0){double age=time-reaction;if(age<reactionLength)return new Choice(MpsqNpcReactions.SEE,(float)age);reaction=-1;}
        return null;
    }
}
