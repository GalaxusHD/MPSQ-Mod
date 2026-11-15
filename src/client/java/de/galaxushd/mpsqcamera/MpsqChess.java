package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import de.galaxushd.mpsqcamera.compat.*;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import java.util.*;

/** Local hot-seat chess. No world blocks are changed and no remote synchronization is claimed. */
public final class MpsqChess {
    static final MpsqChessGame game=new MpsqChessGame();
    static final class Board {
        double x,y,z;int fx,fz,rx,rz;BlockPos button;
        Vec3 point(double f,double r){return new Vec3(x+fx*f+rx*r,y,z+fz*f+rz*r);}
    }
    static Board board;
    private static Object level;
    private static BlockPos first;
    private static boolean selecting,selectButton;
    private static int selected=-1;
    private static List<MpsqChessRules.Move> moves=List.of();
    private static final MpsqModularTimer.Timer[] clocks=new MpsqModularTimer.Timer[2];
    private static String status="";
    private static int ticks;
    private static boolean ready(){var c=Minecraft.getInstance();return c.level!=null&&c.player!=null&&MpsqLocalWorldStore.available()&&!MpsqReplayPlayback.active();}
    static String status(){return status;}
    static boolean canEdit(){return ready()&&board!=null&&TeamStateStore.self().map(p->p.permissionRank().level()>=TeamRank.OFFICER.level()).orElse(false);}
    private static void requireEditor(Board expected){if(!canEdit()||board!=expected)throw new IllegalStateException("Schachbrett bearbeiten ist nur ab Offizier möglich.");}
    static void rotateBoard(Board expected){
        requireEditor(expected);var center=board.point(4,4);double dx=board.x-center.x,dz=board.z-center.z;
        board.x=center.x-dz;board.z=center.z+dx;
        int fx=board.fx,rx=board.rx;board.fx=-board.fz;board.fz=fx;board.rx=-board.rz;board.rz=rx;
        for(var timer:clocks)if(timer!=null){double tx=timer.x-center.x,tz=timer.z-center.z;timer.x=center.x-tz;timer.z=center.z+tx;timer.yaw+=90;}
        save();MpsqModularTimer.save();say("Schachbrett um 90° gedreht.");
    }
    static void deleteBoard(Board expected){
        requireEditor(expected);JsonArray all=MpsqLocalWorldStore.array("chess_boards");String world=MpsqActionSync.world();
        for(int i=all.size()-1;i>=0;i--)if(all.get(i).getAsJsonObject().get("world").getAsString().equals(world))all.remove(i);
        if(!MpsqLocalWorldStore.setArray("chess_boards",all))throw new IllegalStateException("Schachbrett konnte nicht gelöscht werden.");
        reset();for(var timer:clocks)if(timer!=null)MpsqModularTimer.change(timer,"remove",0);
        Arrays.fill(clocks,null);board=null;selecting=selectButton=false;first=null;say("Schachbrett gelöscht. Beide Plätze sind frei.");
    }
    public static void open(){if(!ready()){say("Schach ist in dieser Version ein lokaler Test für zwei Seiten an einem PC.");return;}if(board==null)selectBoard();else Minecraft.getInstance().gui.setScreen(new MpsqChessScreen());}
    static void selectBoard(){if(!ready())return;reset();selecting=true;selectButton=false;first=null;Minecraft.getInstance().gui.setScreen(null);say("Schachbrett: zuerst Bodenfeld a1, dann h8 rechts anklicken (8×8). Esc bricht ab.");}
    static void reset(){game.reset();selected=-1;moves=List.of();for(var t:clocks)if(t!=null)t.clock.reset();if(ready())MpsqModularTimer.save();}
    static void join(boolean white){
        ensureClocks(false);if(clocks[0].clock.initial<=0||clocks[1].clock.initial<=0)throw new IllegalArgumentException("Beide Schachuhren brauchen eine Startzeit größer als null.");
        game.join(white);status=(white?"Weiß":"Schwarz")+" beigetreten.";
        if(game.active()){for(var t:clocks){t.clock.up=false;t.clock.reset();}clocks[0].clock.start();status="Weiß ist am Zug.";}
        say(status);
    }
    static void resign(){if(game.active()){boolean loser=game.position().whiteToMove();game.finish(MpsqChessGame.Result.RESIGNED,!loser);ended();}else reset();}
    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(c.level!=level){game.reset();board=null;selected=-1;moves=List.of();selecting=selectButton=false;first=null;Arrays.fill(clocks,null);level=c.level;status="";if(ready())load();}
            if(!ready())return;
            if((selecting||selectButton)&&c.gui.screen()!=null){selecting=selectButton=false;first=null;}
            if(game.active()){
                int side=game.position().whiteToMove()?0:1;
                if(clocks[side]==null){game.finish(MpsqChessGame.Result.TIME,null);ended();}
                else if(clocks[side].clock.value<=0){game.finish(MpsqChessGame.Result.TIME,game.position().bareKing(side!=0)||game.position().insufficientMaterial()?null:side!=0);ended();}
                else {clocks[side].clock.up=false;clocks[side].clock.start();clocks[1-side].clock.running=false;}
                if(game.active()&&++ticks%40==0&&game.position().inCheck())notice(Component.literal("Schach").withStyle(ChatFormatting.YELLOW,ChatFormatting.BOLD));
            }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(MpsqChess::render);
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("mpsqcamera","chess_selection"),(g,t)->{
            var c=Minecraft.getInstance();if((!selecting&&!selectButton)||c.gui.screen()!=null)return;
            String a=selectButton?"Rechtsklick auf den Menü-Button":first==null?"Schachbrett: Rechtsklick auf Bodenfeld a1":"Schachbrett: Rechtsklick auf Bodenfeld h8";
            String b=selectButton?"Esc: abbrechen":"8×8 Bodenblöcke · von der weißen Seite auswählen · Esc: abbrechen";
            int w=Math.max(c.font.width(a),c.font.width(b));float scale=Math.min(1f,(g.guiWidth()-16f)/Math.max(1,w));
            g.pose().pushMatrix();g.pose().translate(g.guiWidth()/2f,g.guiHeight()-77f);g.pose().scale(scale);g.fill(-w/2-4,-3,w/2+4,22,0x80102638);g.centeredText(c.font,a,0,0,0xFFFFFFFF);g.centeredText(c.font,b,0,11,0xFFFFFFFF);g.pose().popMatrix();
        });
        ClientCommandRegistrationCallback.EVENT.register((d,c)->d.register(ClientCommands.literal("mpsq-schach").executes(ctx->{Minecraft.getInstance().execute(MpsqChess::open);return 1;})
            .then(ClientCommands.literal("brett").executes(ctx->{Minecraft.getInstance().execute(MpsqChess::selectBoard);return 1;}))
            .then(ClientCommands.literal("button").executes(ctx->{if(ready()&&board!=null){selectButton=true;Minecraft.getInstance().gui.setScreen(null);say("Den gewünschten Minecraft-Button rechts anklicken. Esc bricht ab.");}return 1;}))
            .then(ClientCommands.literal("reset").executes(ctx->{reset();say("Schach zurückgesetzt. Beide Plätze sind frei.");return 1;}))));
    }
    private static void say(String s){status=s;notice(Component.literal(s));}
    private static void notice(Component text){var c=Minecraft.getInstance();if(c.player!=null)PlayerMessages.send(c.player,text,true);}
    private static BlockHitResult blockHit(){var c=Minecraft.getInstance();Vec3 eye=c.player.getEyePosition();return c.level.clip(new ClipContext(eye,eye.add(c.player.getViewVector(1).scale(12)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,c.player));}
    private static int target(){
        if(board==null||!ready())return -1;var c=Minecraft.getInstance();Vec3 eye=c.player.getEyePosition(),end=eye.add(c.player.getViewVector(1).scale(12));
        BlockHitResult hit=blockHit();double blocked=hit.getType()==HitResult.Type.MISS?144:eye.distanceToSqr(hit.getLocation())+.03;
        int result=-1;double nearest=blocked;
        for(int i=0;i<64;i++)if(game.position().piece(i)!=0){var p=board.point(i%8+.5,i/8+.5);var contact=new AABB(p.x-.28,p.y,p.z-.28,p.x+.28,p.y+pieceHeight(game.position().piece(i)),p.z+.28).clip(eye,end);if(contact.isPresent()&&eye.distanceToSqr(contact.get())<nearest){nearest=eye.distanceToSqr(contact.get());result=i;}}
        if(result>=0)return result;
        double dy=end.y-eye.y;if(Math.abs(dy)<1e-8)return -1;double t=(board.y-eye.y)/dy;if(t<0||t>1)return -1;
        Vec3 p=eye.lerp(end,t);if(eye.distanceToSqr(p)>blocked)return -1;double dx=p.x-board.x,dz=p.z-board.z;
        int f=(int)Math.floor(dx*board.fx+dz*board.fz),r=(int)Math.floor(dx*board.rx+dz*board.rz);return f>=0&&f<8&&r>=0&&r<8?r*8+f:-1;
    }
    private static double pieceHeight(char p){return switch(Character.toLowerCase(p)){case 'k'->1.59;case 'q'->1.40;case 'b'->1.28;case 'n'->1.19;case 'r'->1.03;default->.90;};}
    private static MpsqModularTimer.Timer clockTarget(){
        if(!ready())return null;var c=Minecraft.getInstance();Vec3 eye=c.player.getEyePosition(),end=eye.add(c.player.getViewVector(1).scale(12));double blocked=eye.distanceToSqr(blockHit().getLocation())+.1;
        for(var t:clocks)if(t!=null){
            var rotation=new org.joml.Quaternionf().rotationY((float)Math.toRadians(-t.yaw)).rotateX((float)Math.toRadians(t.pitch)).conjugate();
            Vec3[] local=new Vec3[2];int n=0;for(Vec3 v:new Vec3[]{eye,end}){var q=rotation.transform(new org.joml.Vector3f((float)(v.x-t.x),(float)(v.y-t.y),(float)(v.z-t.z))).mul(16/t.scale);local[n++]=new Vec3(q.x,q.y,q.z);}
            var p=new AABB(-138,-2,-10,138,82,10).clip(local[0],local[1]);if(p.isPresent()){double fraction=local[0].distanceTo(p.get())/local[0].distanceTo(local[1]);if(144*fraction*fraction<=blocked)return t;}
        }return null;
    }
    private static boolean trophyTarget(){
        if(board==null||!ready())return false;var c=Minecraft.getInstance();var eye=c.player.getEyePosition();var end=eye.add(c.player.getViewVector(1).scale(12));double blocked=eye.distanceToSqr(blockHit().getLocation())+.03;int w=0,b=0;
        for(char piece:game.trophies()){boolean white=Character.isUpperCase(piece);int n=white?w++:b++;var p=board.point(white?-.5:8.5,.3+n*.49);var hit=new AABB(p.x-.18,p.y,p.z-.18,p.x+.18,p.y+pieceHeight(piece)*.6,p.z+.18).clip(eye,end);if(hit.isPresent()&&eye.distanceToSqr(hit.get())<=blocked)return true;}return false;
    }
    public static boolean blocks(){return ready()&&(selecting||selectButton||target()>=0||clockTarget()!=null||trophyTarget());}
    public static boolean key(int key,int action){if(Minecraft.getInstance().gui.screen()!=null)return false;if((selecting||selectButton)&&key==InputConstants.KEY_ESCAPE){if(action==InputConstants.PRESS){selecting=selectButton=false;first=null;say("Auswahl abgebrochen.");}return true;}return false;}
    public static boolean mouse(int button,int action){
        var c=Minecraft.getInstance();if(!ready()||!TeamVisibilitySettings.visible()||c.gui.screen()!=null||(button!=InputConstants.MOUSE_BUTTON_LEFT&&button!=InputConstants.MOUSE_BUTTON_RIGHT))return false;
        boolean right=button==InputConstants.MOUSE_BUTTON_RIGHT;
        if(selecting||selectButton){if(action==InputConstants.PRESS&&right)try{
            var hit=blockHit();if(hit.getType()!=HitResult.Type.BLOCK)throw new IllegalArgumentException("Einen Bodenblock auswählen.");BlockPos pos=hit.getBlockPos();
            if(selectButton){if(!(c.level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.ButtonBlock))throw new IllegalArgumentException("Bitte einen Minecraft-Button auswählen.");board.button=pos;save();selectButton=false;say("Schachmenü mit diesem Button verbunden.");}
            else if(first==null){first=pos;say("a1 gesetzt. Jetzt das gegenüberliegende Bodenfeld h8 anklicken.");}
            else {if(pos.getY()!=first.getY()||Math.abs(pos.getX()-first.getX())!=7||Math.abs(pos.getZ()-first.getZ())!=7)throw new IllegalArgumentException("Die Bodenfläche muss genau 8×8 Blöcke auf gleicher Höhe sein.");
                Board b=new Board();int sx=Integer.signum(pos.getX()-first.getX()),sz=Integer.signum(pos.getZ()-first.getZ());double viewX=c.player.getX()-(first.getX()+pos.getX()+1)/2.0,viewZ=c.player.getZ()-(first.getZ()+pos.getZ()+1)/2.0;
                if(Math.abs(viewX)>Math.abs(viewZ)){b.rx=sx;b.fz=sz;}else{b.fx=sx;b.rz=sz;}
                b.x=first.getX()+(sx<0?1:0);b.z=first.getZ()+(sz<0?1:0);b.y=first.getY()+1;board=b;selecting=false;first=null;ensureClocks(true);save();say("8×8-Schachbrett erstellt. Rechtsklick öffnet das Beitrittsmenü.");}
        }catch(Exception e){say(e.getMessage());}return true;}
        if(right&&c.player.isShiftKeyDown()){var timer=clockTarget();if(timer!=null){if(action==InputConstants.PRESS)c.gui.setScreen(new MpsqTimerScreen(null,timer));return true;}}
        if(clockTarget()!=null||trophyTarget())return true;
        if(board!=null&&board.button!=null&&right&&blockHit().getBlockPos().equals(board.button)){if(action==InputConstants.PRESS)open();return true;}
        int square=target();if(square<0)return false;if(action!=InputConstants.PRESS)return true;
        if(right&&canEdit()&&(c.player.getMainHandItem().is(net.minecraft.world.item.Items.GLOW_INK_SAC)||c.player.getOffhandItem().is(net.minecraft.world.item.Items.GLOW_INK_SAC))){c.gui.setScreen(new MpsqChessEditScreen(board));return true;}
        if(!game.active()||right&&c.player.isShiftKeyDown()){open();return true;}
        if(!right){char p=game.position().piece(square);if(p!=0&&Character.isUpperCase(p)==game.position().whiteToMove()){selected=square;moves=game.position().legalMoves().stream().filter(m->m.from()==square).toList();}else {selected=-1;moves=List.of();say("Eine eigene Figur der Seite am Zug auswählen.");}}
        else if(selected>=0){var choices=moves.stream().filter(m->m.to()==square).toList();if(choices.isEmpty())say("Dieses Feld ist kein erlaubtes Zugziel.");else if(choices.size()>1)c.gui.setScreen(new MpsqChessPromotionScreen(choices));else move(choices.getFirst());}
        else say("Zuerst eine eigene Figur mit Linksklick auswählen.");return true;
    }
    static void move(MpsqChessRules.Move m){
        if(!game.active())return;game.move(m);selected=-1;moves=List.of();
        if(!game.active()){ended();return;}int side=game.position().whiteToMove()?0:1;clocks[1-side].clock.running=false;clocks[side].clock.start();MpsqModularTimer.save();
        if(game.position().inCheck())notice(Component.literal("Schach").withStyle(ChatFormatting.YELLOW,ChatFormatting.BOLD));else say((side==0?"Weiß":"Schwarz")+" ist am Zug.");
    }
    private static void ended(){
        selected=-1;moves=List.of();for(var t:clocks)if(t!=null){t.clock.reset();t.visual();}MpsqModularTimer.save();
        Boolean winner=game.winner();status=winner==null?"Unentschieden":(winner?"Weiß":"Schwarz")+" gewinnt";
        if(game.result()==MpsqChessGame.Result.CHECKMATE){
            // One display controls both local seats: show both outcomes together.
            notice(Component.literal(winner?"Weiß: ":"Schwarz: ").append(Component.literal("Schachmatt").withStyle(ChatFormatting.GREEN,ChatFormatting.BOLD)).append(" · ").append(winner?"Schwarz: ":"Weiß: ").append(Component.literal("Schachmatt").withStyle(ChatFormatting.RED,ChatFormatting.BOLD)));
            fireworks(winner);
        }else if(winner==null)notice(Component.literal("Unentschieden").withStyle(ChatFormatting.GOLD,ChatFormatting.BOLD));
        else notice(Component.literal(status+(game.result()==MpsqChessGame.Result.TIME?" – Zeit abgelaufen":" – Aufgabe")).withStyle(ChatFormatting.GREEN,ChatFormatting.BOLD));
    }
    private static void fireworks(boolean white){
        var c=Minecraft.getInstance();var p=board.point(4,4);int color=white?0xFFFFFF:0x000000;
        c.level.createFireworks(p.x,p.y+.25,p.z,0,.15,0,List.of(new net.minecraft.world.item.component.FireworkExplosion(net.minecraft.world.item.component.FireworkExplosion.Shape.LARGE_BALL,it.unimi.dsi.fastutil.ints.IntList.of(color),it.unimi.dsi.fastutil.ints.IntList.of(color),true,false)),false);
    }
    private static void ensureClocks(boolean place){
        for(int i=0;i<2;i++){String name=i==0?"Schach_Weiss":"Schach_Schwarz";boolean created=false;try{clocks[i]=MpsqModularTimer.find(name);}catch(IllegalArgumentException e){clocks[i]=MpsqModularTimer.create(name,300000);created=true;}
            var t=clocks[i];t.system="";t.end="none";t.clock.up=false;if(place||created){var p=board.point(i==0?2.5:5.5,-.7);t.x=p.x;t.y=p.y+.3;t.scale=.14f;t.z=p.z;t.yaw=(float)Math.toDegrees(Math.atan2(-board.rx,board.rz));t.idle="none";t.animation="none";t.color=i==0?0xFFFFFFFF:0xFF888888;t.clock.reset();}}
        MpsqModularTimer.save();
    }
    static boolean ownsClock(MpsqModularTimer.Timer t){return t!=null&&(t==clocks[0]||t==clocks[1]);}
    private static void save(){JsonArray all=MpsqLocalWorldStore.array("chess_boards");String world=MpsqActionSync.world();for(int i=all.size()-1;i>=0;i--)if(all.get(i).getAsJsonObject().get("world").getAsString().equals(world))all.remove(i);JsonObject o=new Gson().toJsonTree(board).getAsJsonObject();o.remove("button");if(board.button!=null){o.addProperty("bx",board.button.getX());o.addProperty("by",board.button.getY());o.addProperty("bz",board.button.getZ());}o.addProperty("world",world);all.add(o);if(!MpsqLocalWorldStore.setArray("chess_boards",all))throw new IllegalStateException("Schachbrett konnte nicht gespeichert werden.");}
    private static void load(){try{for(var e:MpsqLocalWorldStore.array("chess_boards")){var o=e.getAsJsonObject();if(!o.get("world").getAsString().equals(MpsqActionSync.world()))continue;board=new Gson().fromJson(o,Board.class);if(!Double.isFinite(board.x+board.y+board.z)||board.fx*board.fx+board.fz*board.fz!=1||board.rx*board.rx+board.rz*board.rz!=1||board.fx*board.rx+board.fz*board.rz!=0)throw new IllegalArgumentException("Ungültiges Schachbrett");if(o.has("bx"))board.button=new BlockPos(o.get("bx").getAsInt(),o.get("by").getAsInt(),o.get("bz").getAsInt());ensureClocks(false);}}catch(Exception e){board=null;MpsqCameraClient.LOGGER.warn("Schachbrett nicht geladen",e);}}
    private static String model(char p){return "builtin://mpsq/nm_chess_"+(Character.isUpperCase(p)?"white_":"black_")+switch(Character.toLowerCase(p)){case 'k'->"king";case 'q'->"queen";case 'r'->"rook";case 'b'->"bishop";case 'n'->"knight";default->"pawn";};}
    private static void render(WorldRenderContext ctx){
        if(!ready()||board==null||!TeamVisibilitySettings.visible())return;if(ctx.camera().getPos().distanceToSqr(board.point(4,4))>150*150)return;
        for(int i=0;i<64;i++){char piece=game.position().piece(i);if(piece==0)continue;var p=board.point(i%8+.5,i/8+.5);float yaw=(float)Math.toDegrees(Math.atan2(-board.rx,board.rz))+(Character.isUpperCase(piece)?0:180);MpsqAccessoryRenderer.drawCinematicModel(ctx,model(piece),p.x,p.y,p.z,yaw,"idle",0,0,0,1,i==selected?0xFFA79100:0);}
        int w=0,b=0;for(char piece:game.trophies()){boolean white=Character.isUpperCase(piece);int n=white?w++:b++;var p=board.point(white?-.5:8.5,.3+n*.49);MpsqAccessoryRenderer.drawCinematicModel(ctx,model(piece),p.x,p.y,p.z,0,"idle",0,0,0,.6f);}
        if(selected>=0){for(var m:moves)ring(ctx,m.to(),game.position().castling(m)?0xFF5C006C:game.position().piece(game.position().capturedSquare(m))!=0?0xFFFF0000:0xFFD97C00);for(int s:game.position().friendlyBlockers(selected))ring(ctx,s,0xFF5F0000);}
        if(!game.active()){quad(ctx,.025,.025,7.975,.055,0xFFA79100);quad(ctx,.025,7.945,7.975,7.975,0xFFA79100);quad(ctx,.025,.055,.055,7.945,0xFFA79100);quad(ctx,7.945,.055,7.975,7.945,0xFFA79100);}
    }
    private static void ring(WorldRenderContext ctx,int square,int color){
        double f=square%8,r=square/8;quad(ctx,f+.09,r+.09,f+.91,r+.16,color);quad(ctx,f+.09,r+.84,f+.91,r+.91,color);quad(ctx,f+.09,r+.16,f+.16,r+.84,color);quad(ctx,f+.84,r+.16,f+.91,r+.84,color);
    }
    private static void quad(WorldRenderContext ctx,double f1,double r1,double f2,double r2,int color){var stack=ctx.matrixStack();stack.pushPose();var cam=ctx.camera().getPos();stack.translate(-cam.x,-cam.y,-cam.z);var out=ctx.consumers().getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads());
        double[][] points={{f1,r1},{f1,r2},{f2,r2},{f2,r1}};for(var v:points){var p=board.point(v[0],v[1]);out.addVertex(stack.last(),(float)p.x,(float)(p.y+.008),(float)p.z).setColor(color);}for(int i=3;i>=0;i--){var p=board.point(points[i][0],points[i][1]);out.addVertex(stack.last(),(float)p.x,(float)(p.y+.008),(float)p.z).setColor(color);}stack.popPose();}
}
