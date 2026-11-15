package de.galaxushd.mpsqcamera;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import com.google.gson.Gson;

/** Per-actor timeline; each clip has its own time, duration and optional look target. */
final class MpsqCinematicNpcActionsScreen extends Screen {
    private final MpsqCinematicNpcScreen parent;
    private String clip;private int page;
    MpsqCinematicNpcActionsScreen(MpsqCinematicNpcScreen parent,String clip){super(Component.literal("NPC-Aktionen"));this.parent=parent;this.clip=clip;}
    @Override protected void init(){
        var clips=MpsqCinematicActors.clips(parent.scene.npc);if(clip.isBlank()&&!clips.isEmpty())clip=clips.getFirst();
        addRenderableWidget(Button.builder(Component.literal("Animation: "+clip),b->{int next=(clips.indexOf(clip)+1)%Math.max(1,clips.size());if(!clips.isEmpty())clip=clips.get(next);rebuildWidgets();}).bounds(width/2-150,30,205,20).build());
        addRenderableWidget(Button.builder(Component.literal("Hinzufügen"),b->{var a=new MpsqCinematicScene.NpcAction();a.clip=clip;minecraft.gui.setScreen(new Edit(this,a,true));}).bounds(width/2+60,30,90,20).build()).active=!clip.isBlank();
        int rows=Math.max(1,(height-95)/24);var list=parent.scene.npc.actions;page=Math.max(0,Math.min(page,Math.max(0,(list.size()-1)/rows)));
        for(int i=page*rows;i<Math.min(list.size(),(page+1)*rows);i++){var a=list.get(i);int y=56+(i-page*rows)*24;
            addRenderableWidget(Button.builder(Component.literal(a.time+" s · "+a.clip+" · "+a.duration+" s"),b->minecraft.gui.setScreen(new Edit(this,a,false))).bounds(width/2-150,y,260,20).build());
            addRenderableWidget(Button.builder(Component.literal("X"),b->minecraft.gui.setScreen(new ConfirmScreen(ok->{if(ok)parent.edit(()->list.remove(a));minecraft.gui.setScreen(this);},Component.literal("Aktion löschen?"),Component.literal(a.clip)))).bounds(width/2+115,y,35,20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Zurück"),b->onClose()).bounds(width/2-150,height-28,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("<"),b->{page--;rebuildWidgets();}).bounds(width/2-45,height-28,90,20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b->{page++;rebuildWidgets();}).bounds(width/2+50,height-28,100,20).build());
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    private static final class Edit extends Screen {
        private final MpsqCinematicNpcActionsScreen parent;private final MpsqCinematicScene.NpcAction source,draft;private final boolean fresh;private final int sourceIndex;
        private EditBox start,duration;private String error="";
        Edit(MpsqCinematicNpcActionsScreen parent,MpsqCinematicScene.NpcAction source,boolean fresh){super(Component.literal(source.clip));this.parent=parent;this.source=source;this.fresh=fresh;sourceIndex=parent.parent.scene.npc.actions.indexOf(source);draft=new Gson().fromJson(new Gson().toJson(source),MpsqCinematicScene.NpcAction.class);}
        @Override protected void init(){
            start=addRenderableWidget(new EditBox(font,width/2,44,130,20,Component.literal("Start")));start.setValue(""+draft.time);
            duration=addRenderableWidget(new EditBox(font,width/2,72,130,20,Component.literal("Dauer")));duration.setValue(""+draft.duration);
            String[] modes={"DEFAULT","FIXED","PLAYER","CAMERA","PLAYER_HEAD","CAMERA_HEAD"};
            addRenderableWidget(Button.builder(Component.literal("Blick während Aktion: "+draft.look),b->{draft.look=modes[(java.util.Arrays.asList(modes).indexOf(draft.look)+1)%modes.length];b.setMessage(Component.literal("Blick während Aktion: "+draft.look));}).bounds(width/2-150,105,300,20).build());
            addRenderableWidget(Button.builder(Component.literal("Speichern"),b->{try{
                draft.time=Double.parseDouble(start.getValue().replace(',','.'));draft.duration=Double.parseDouble(duration.getValue().replace(',','.'));
                var check=new Gson().fromJson(new Gson().toJson(parent.parent.scene.npc),MpsqCinematicScene.Npc.class);
                int index=sourceIndex;if(fresh)check.actions.add(draft);else check.actions.set(index,draft);check.validate();
                parent.parent.edit(()->{if(fresh)parent.parent.scene.npc.actions.add(draft);else parent.parent.scene.npc.actions.set(index,draft);});onClose();
            }catch(Exception e){error=e.getMessage();}}).bounds(width/2-130,height-28,125,20).build());
            addRenderableWidget(Button.builder(Component.literal("Abbrechen"),b->onClose()).bounds(width/2+5,height-28,125,20).build());
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,20,0xFFFFFFFF);g.text(font,"Start (Sekunden)",width/2-140,50,0xFFFFFFFF);g.text(font,"Dauer (Sekunden)",width/2-140,78,0xFFFFFFFF);g.centeredText(font,error,width/2,height-42,0xFFFF7777);}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
    }
}
