package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;

public final class MpsqFinishZoneTool {
    private static Object world;
    private static BlockPos first;
    private static int selected;
    private static boolean editor;
    private static net.minecraft.world.item.Item toolItem(){
        var id=net.minecraft.resources.Identifier.tryParse(ModConfig.zoneToolItemId);
        if(id==null||!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id))return Items.GLOW_INK_SAC;
        var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
        return item==Items.AIR?Items.GLOW_INK_SAC:item;
    }
    public static void selectMaster(){selected=MpsqZoneCatalog.MASTER;first=null;showSelection();}
    public static void selectCamera(){selected=MpsqZoneCatalog.CAMERA;first=null;showSelection();}
    public static boolean editorEnabled(){return editor&&staff()&&TeamVisibilitySettings.visible();}
    public static void toggleEditor(){editor=!editor;first=null;}
    public static int selectedColor(){return MpsqZoneCatalog.frameColor(selected);}
    public static BlockPos firstCorner(){return first;}
    public static boolean staff(){TeamRank r=TeamStateStore.self().map(TeamProfile::permissionRank).orElse(TeamRank.PLAYER);return r==TeamRank.WORKER||r==TeamRank.SOLDIER||r==TeamRank.OFFICER||r==TeamRank.SENIOR_OFFICER||r==TeamRank.FRONTMAN;}
    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(c->{if(world==c.level)return;world=c.level;first=null;MpsqSystemController.stopActive();MpsqGamesRuntime.resetSession();MpsqZoneCatalog.load();});
        UseItemCallback.EVENT.register((p,w,h)->{
            if(!w.isClientSide()||h!=InteractionHand.MAIN_HAND||!TeamVisibilitySettings.visible()||!staff()||!p.getItemInHand(h).is(toolItem()))return InteractionResult.PASS;
            var c=Minecraft.getInstance();if(c.hitResult!=null&&c.hitResult.getType()!=HitResult.Type.MISS)return InteractionResult.PASS;
            if(p.isShiftKeyDown()){
                if(deleteTarget())return InteractionResult.SUCCESS;
                c.gui.setScreen(new MpsqGamePickerScreen(null,index->{
                    String game=MpsqSystemController.availableSystems().get(index).id();
                    for(int i=0;i<MpsqZoneCatalog.KINDS.size();i++)if(MpsqZoneCatalog.KINDS.get(i).game().equals(game)){selected=i;first=null;showSelection();break;}
                },true));
                return InteractionResult.SUCCESS;
            }
            if(!editorEnabled())return InteractionResult.PASS;
            String game=MpsqZoneCatalog.KINDS.get(selected).game();
            selected=MpsqZoneCatalog.nextKind(selected);
            first=null;
            showSelection();
            return InteractionResult.SUCCESS;
        });
        UseBlockCallback.EVENT.register((p,w,h,hit)->{
            if(!w.isClientSide()||h!=InteractionHand.MAIN_HAND||!TeamVisibilitySettings.visible()||!staff()||!p.getItemInHand(h).is(toolItem()))return InteractionResult.PASS;
            if(!editorEnabled())return InteractionResult.PASS;
            if(!MpsqLocalWorldStore.available()){de.galaxushd.mpsqcamera.compat.PlayerMessages.send(p, Component.literal("Zonen-Editor derzeit nur in Einzelspielerwelten verfügbar."),true);return InteractionResult.SUCCESS;}
            if(!MpsqSystemController.activeSystemId().isEmpty()){de.galaxushd.mpsqcamera.compat.PlayerMessages.send(p, Component.literal("Vor dem Bearbeiten das Spiel ausschalten."),true);return InteractionResult.SUCCESS;}
            if(p.isShiftKeyDown()){deleteTarget();return InteractionResult.SUCCESS;}
            if(selected!=MpsqZoneCatalog.MASTER&&(!MpsqZoneCatalog.inMaster(p)||!MpsqZoneCatalog.inMaster(hit.getBlockPos()))){
                de.galaxushd.mpsqcamera.compat.PlayerMessages.send(p,Component.literal("Der Zonen-Editor funktioniert nur innerhalb der Gesamtfläche. Gesamtfläche im Auswahlmenü anlegen."),true);return InteractionResult.SUCCESS;
            }
            if(MpsqZoneCatalog.KINDS.get(selected).game().equals("six_leg")){
                first=null;
                if(hit.getDirection()!=net.minecraft.core.Direction.UP){de.galaxushd.mpsqcamera.compat.PlayerMessages.send(p, Component.literal("Bitte die Oberseite des Bodenblocks anklicken."),true);return InteractionResult.SUCCESS;}
                BlockPos checkpoint=hit.getBlockPos().above();
                de.galaxushd.mpsqcamera.compat.PlayerMessages.send(p, Component.literal(MpsqZoneCatalog.add(selected,checkpoint,checkpoint)),true);
                return InteractionResult.SUCCESS;
            }
            if(first==null){first=hit.getBlockPos().immutable();de.galaxushd.mpsqcamera.compat.PlayerMessages.send(p, Component.literal(MpsqZoneCatalog.KINDS.get(selected).title()+": Ecke 1. Gegenüberliegende Ecke einschließlich Höhe auswählen."),true);}
            else if(selected==MpsqZoneCatalog.CAMERA){
                BlockPos a=first,b=hit.getBlockPos().immutable();first=null;
                Minecraft.getInstance().gui.setScreen(new MpsqCinematicScreen.TextInput(null,"Name der Kamerazone","",name->{
                    if(Minecraft.getInstance().level!=w||!editorEnabled())throw new IllegalStateException("Zonen-Editor nicht mehr verfügbar.");
                    String result=MpsqZoneCatalog.add(MpsqZoneCatalog.CAMERA,a,b,name);
                    if(!result.startsWith("Gespeichert:"))throw new IllegalArgumentException(result);
                    de.galaxushd.mpsqcamera.compat.PlayerMessages.send(p,Component.literal(result),false);
                }));
            }
            else{String result=MpsqZoneCatalog.add(selected,first,hit.getBlockPos());first=null;de.galaxushd.mpsqcamera.compat.PlayerMessages.send(p, Component.literal(result),true);}
            return InteractionResult.SUCCESS;
        });
    }
    private static boolean deleteTarget(){
        if(!editorEnabled()||!MpsqLocalWorldStore.available()||!MpsqSystemController.activeSystemId().isEmpty())return false;
        var c=Minecraft.getInstance();if(c.player==null)return false;
        var start=c.player.getEyePosition();var end=start.add(c.player.getViewVector(1f).scale(c.player.blockInteractionRange()));
        MpsqZoneCatalog.Entry target=null;double nearest=Double.POSITIVE_INFINITY;
        for(var entry:MpsqZoneCatalog.all()){
            if(entry.kind()!=MpsqZoneCatalog.MASTER&&!MpsqZoneCatalog.inMaster(c.player))continue;
            var b=entry.box();double[] lo={b.minX(),b.minY(),b.minZ()},hi={b.maxX(),b.maxY(),b.maxZ()};
            // Only the twelve visible edges are clickable, not the empty volume.
            for(int axis=0;axis<3;axis++)for(int a=0;a<2;a++)for(int d=0;d<2;d++){
                int j=(axis+1)%3,k=(axis+2)%3;double[] min=lo.clone(),max=hi.clone();
                min[j]=max[j]=a==0?lo[j]:hi[j];min[k]=max[k]=d==0?lo[k]:hi[k];
                var hit=new net.minecraft.world.phys.AABB(min[0]-.08,min[1]-.08,min[2]-.08,max[0]+.08,max[1]+.08,max[2]+.08).clip(start,end);
                if(hit.isPresent()){double distance=start.distanceToSqr(hit.get());if(distance<nearest){nearest=distance;target=entry;}}
            }
        }
        if(target==null)return false;
        first=null;de.galaxushd.mpsqcamera.compat.PlayerMessages.send(c.player, Component.literal(MpsqZoneCatalog.remove(target)),true);return true;
    }
    private static void showSelection(){
        var c=Minecraft.getInstance();var kind=MpsqZoneCatalog.KINDS.get(selected);
        c.gui.hud.setTimes(5,35,10);
        c.gui.hud.setTitle(Component.literal(MpsqZoneCatalog.label(kind.game())));
        c.gui.hud.setSubtitle(Component.literal(kind.name()));
    }
}
