package de.galaxushd.mpsqcamera;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/** Records visual player motion locally at game-tick resolution; no world actions are replayed. */
public final class MpsqMotionCapture {
 public static final class Frame {
  public double time,x,y,z;public float yaw,head,pitch;public String clip="idle";public float age;
  public String pose="STANDING";public boolean sprinting,using,offhand,swing,swingOffhand;public int useRemaining;
  public com.google.gson.JsonArray equipment;
  transient com.google.gson.JsonArray resolvedEquipment;
  transient boolean equipmentResolved;
  public Frame(){}
  Frame(double t,net.minecraft.client.player.LocalPlayer p,String clip,float age){time=t;x=p.getX();y=p.getY();z=p.getZ();yaw=p.yBodyRot;head=net.minecraft.util.Mth.wrapDegrees(p.getYHeadRot()-yaw);pitch=p.getXRot();this.clip=clip;this.age=age;
   pose=p.getPose().name();sprinting=p.isSprinting();using=p.isUsingItem();offhand=using&&p.getUsedItemHand()==net.minecraft.world.InteractionHand.OFF_HAND;useRemaining=p.getUseItemRemainingTicks();swing=p.isSwinging();swingOffhand=swing&&p.getCurrentSwing()!=null&&p.getCurrentSwing().hand()==net.minecraft.world.InteractionHand.OFF_HAND;
   var all=new com.google.gson.JsonArray();var ops=p.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
   for(var slot:SLOTS){var item=p.getItemBySlot(slot);all.add(item.isEmpty()?com.google.gson.JsonNull.INSTANCE:net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops,item).result().orElse(com.google.gson.JsonNull.INSTANCE));}
   var previous=frames.isEmpty()?null:frames.getLast().resolvedEquipment;resolvedEquipment=all.equals(previous)?previous:all;if(previous!=resolvedEquipment)equipment=all;
  }
 }
 private static final net.minecraft.world.entity.EquipmentSlot[] SLOTS={net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.MAINHAND,net.minecraft.world.entity.EquipmentSlot.OFFHAND};
 private static final MpsqWeakIdentityCache<net.minecraft.world.entity.LivingEntity,com.google.gson.JsonArray> appliedEquipment=new MpsqWeakIdentityCache<>();
 private static final MpsqWeakIdentityCache<net.minecraft.world.entity.LivingEntity,Boolean> appliedSwing=new MpsqWeakIdentityCache<>();
 static void apply(net.minecraft.world.entity.LivingEntity entity,MpsqCinematicActors.Pose pose){
  var f=pose.recorded();if(f==null)return;
  try{entity.setPose(net.minecraft.world.entity.Pose.valueOf(f.pose));}catch(Exception ignored){}
  entity.setSprinting(f.sprinting);
  var items=f.resolvedEquipment;
  if(items!=null&&appliedEquipment.get(entity)!=items){var ops=entity.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);for(int i=0;i<Math.min(SLOTS.length,items.size());i++)entity.setItemSlot(SLOTS[i],items.get(i).isJsonNull()?net.minecraft.world.item.ItemStack.EMPTY:net.minecraft.world.item.ItemStack.CODEC.parse(ops,items.get(i)).result().orElse(net.minecraft.world.item.ItemStack.EMPTY));appliedEquipment.put(entity,items);}
  boolean was=appliedSwing.getOrDefault(entity,false);if(f.swing&&!was)entity.swing(f.swingOffhand?net.minecraft.world.InteractionHand.OFF_HAND:net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,true);appliedSwing.put(entity,f.swing);
  if(f.using){var hand=f.offhand?net.minecraft.world.InteractionHand.OFF_HAND:net.minecraft.world.InteractionHand.MAIN_HAND;if(!entity.isUsingItem()||entity.getUsedItemHand()!=hand){entity.stopUsingItem();entity.startUsingItem(hand);}if(entity instanceof de.galaxushd.mpsqcamera.mixin.client.MotionUseAccessor accessor)accessor.mpsq$remaining(f.useRemaining);}else if(entity.isUsingItem())entity.stopUsingItem();
 }
 private static KeyMapping stop,animationKey;private static String sceneClip="";private static int sceneStart;private static Screen back;private static Object world;private static Consumer<List<Frame>> done;
 private static final List<Frame> frames=new ArrayList<>();private static int ticks,countdown,lastTitle;private static String previousClip="";private static int clipStart;
 public static void initialize(){
  stop=net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mpsqcamera.stop_recording",InputConstants.Type.KEYBOARD,InputConstants.KEY_RIGHT,MpsqKeyCategories.MAIN));
  animationKey=net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mpsqcamera.record_animation",InputConstants.Type.KEYBOARD,InputConstants.KEY_R,MpsqKeyCategories.MAIN));
  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c->tick());
 }
 static void start(Screen parent,Consumer<List<Frame>> callback){
  if(!MpsqScenes.allowed())throw new IllegalStateException("Offizier-Rang innerhalb der Gesamtfläche erforderlich.");
  sceneClip="";back=parent;done=callback;world=Minecraft.getInstance().level;frames.clear();ticks=clipStart=0;countdown=60;lastTitle=-1;previousClip="";Minecraft.getInstance().gui.setScreen(null);
 }
 public static boolean cancelKey(int key,int action){if(done==null||key!=InputConstants.KEY_ESCAPE)return false;if(action==InputConstants.PRESS)finish(false);return true;}
 private static void tick(){
  if(stop==null)return;boolean choose=false;while(animationKey.consumeClick())choose=true;boolean pressed=false;while(stop.consumeClick())pressed=true;if(done==null)return;var c=Minecraft.getInstance();
  if(c.level!=world||c.player==null||!MpsqScenes.allowed()){done=null;back=null;frames.clear();return;}
  if(pressed){finish(countdown==0);return;}
  if(choose&&countdown==0&&c.gui.screen()==null){c.gui.setScreen(new MpsqRecordingAnimationScreen());return;}
  if(c.gui.screen() instanceof MpsqRecordingAnimationScreen)return;
  if(countdown>0){int seconds=(countdown+19)/20;if(seconds!=lastTitle){lastTitle=seconds;c.gui.hud.setTimes(0,22,0);c.gui.hud.setTitle(Component.literal(""+seconds));c.gui.hud.setSubtitle(Component.literal("Bewegungsaufnahme startet"));}countdown--;return;}
  if(frames.isEmpty()){c.gui.hud.setTitle(Component.literal("Aufnahme läuft"));c.gui.hud.setSubtitle(Component.literal("Beenden: "+stop.getTranslatedKeyMessage().getString()+" · Esc verwerfen"));c.gui.hud.setTimes(0,35,10);}
  var p=c.player;try{TeamVisibilitySettings.requirePlacement(p.getX(),p.getY(),p.getZ());}catch(Exception e){finish(false);de.galaxushd.mpsqcamera.compat.PlayerMessages.send(p,Component.literal("Aufnahme verworfen: Die Gesamtfläche wurde verlassen."),false);return;}
  String clip=p.isSwimming()?"swim":p.isCrouching()?"crouch":p.isSwinging()?"swing":"idle";
  var animation=MpsqScenesClips.get(sceneClip);
  if(animation!=null&&(ticks-sceneStart)/20d<animation.length())clip=sceneClip;else sceneClip="";
  if(!clip.equals(previousClip)){previousClip=clip;clipStart=ticks;}
  frames.add(new Frame(ticks/20.0,p,clip,(ticks-(sceneClip.isEmpty()?clipStart:sceneStart))/20f));ticks++;
  if(frames.size()>=12000)finish(true);
 }
 static void selectAnimation(String id){sceneClip=id;sceneStart=ticks;Minecraft.getInstance().gui.setScreen(null);}
 private static void finish(boolean save){var c=Minecraft.getInstance();var callback=done;var parent=back;var result=new ArrayList<>(frames);done=null;back=null;frames.clear();c.gui.setScreen(parent);if(save&&result.size()>1)callback.accept(result);}
 static void validate(List<Frame> frames){if(frames==null||frames.size()>12000)throw new IllegalArgumentException("Aufnahme maximal 10 Minuten.");double previous=-1;for(var f:frames){if(f==null||!Double.isFinite(f.time)||f.time<=previous||f.time>600||!Double.isFinite(f.x)||!Double.isFinite(f.y)||!Double.isFinite(f.z)||!Float.isFinite(f.yaw)||!Float.isFinite(f.head)||!Float.isFinite(f.pitch)||!Float.isFinite(f.age)||f.age<0||Math.abs(f.pitch)>90||(!Set.of("idle","crouch","swing","swim").contains(f.clip)&&MpsqScenesClips.get(f.clip)==null))throw new IllegalArgumentException("Ungültige Bewegungsaufnahme.");try{net.minecraft.world.entity.Pose.valueOf(f.pose);}catch(Exception e){throw new IllegalArgumentException("Ungültige Körperhaltung.");}if(f.useRemaining<0||f.useRemaining>72000||f.equipment!=null&&(f.equipment.size()!=6||f.equipment.toString().length()>65536))throw new IllegalArgumentException("Ungültige Aufnahme-Ausrüstung.");previous=f.time;}}
 static MpsqCinematicActors.Pose sample(List<Frame> frames,double time,boolean loop,String direction){
  if(frames==null||frames.isEmpty())return null;double length=frames.getLast().time;double t=Math.max(0,time);if(length>0){double cycle=direction.equals("BOTH")?2*length:length;t=loop?t%cycle:Math.min(t,cycle);if(direction.equals("REVERSE"))t=length-t;else if(t>length)t=2*length-t;}
  int lo=0,hi=frames.size()-1;while(lo<hi){int mid=(lo+hi+1)/2;if(frames.get(mid).time<=t)lo=mid;else hi=mid-1;}
  var a=frames.get(lo);var b=frames.get(Math.min(lo+1,frames.size()-1));double f=b.time>a.time?Math.max(0,Math.min(1,(t-a.time)/(b.time-a.time))):0;
  if(!a.equipmentResolved){com.google.gson.JsonArray previous=null;for(int i=0;i<=lo;i++){var frame=frames.get(i);if(frame.equipment!=null)previous=frame.equipment;frame.resolvedEquipment=previous;frame.equipmentResolved=true;}}
  return new MpsqCinematicActors.Pose(a.x+(b.x-a.x)*f,a.y+(b.y-a.y)*f,a.z+(b.z-a.z)*f,(float)(a.yaw+MpsqCinematicScene.angleDelta(a.yaw,b.yaw)*f),(float)(a.head+MpsqCinematicScene.angleDelta(a.head,b.head)*f),(float)(a.pitch+(b.pitch-a.pitch)*f),a.clip,a.age+(float)(t-a.time),a);
 }
}
