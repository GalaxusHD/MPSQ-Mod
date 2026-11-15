package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Client-observed rounds. Server adjudication is a separate integration. */
public final class MpsqGamesRuntime {
    private static final String[] IDS={"dalgona","tug_of_war","marbles","glass_bridge","finale","six_leg","room_game","hide_and_seek","rope_jump","sky_squid"};
    private static final int RED=0xFF5555,WHITE=0xFFFFFF,GREEN=0x55FF55;
    private static final int[] COLORS={0x000000,0x0000AA,0x00AA00,0x00AAAA,0xAA0000,0xAA00AA,0xFFAA00,0xAAAAAA,0x555555,0x5555FF,0x55FF55,0x55FFFF,0xFF5555,0xFF55FF,0xFFFF55,0xFFFFFF,
        0xFF7F50,0xFF1493,0x8A2BE2,0x4B0082,0x00BFFF,0x4682B4,0x40E0D0,0x008080,0x808000,0x9ACD32,
        0xFFD700,0xD2691E,0x8B4513,0x800000,0xFA8072,0xDDA0DD,0xB0C4DE,0x98FF98,0xF5DEB3,0x708090};
    private static String mode="";
    private static double height;
    private static final Map<UUID,Integer> glows=new HashMap<>(),hits=new HashMap<>(),lastZone=new HashMap<>();
    private static final Map<UUID,UUID> partners=new HashMap<>();
    private static final Map<UUID,Long> hitTimes=new HashMap<>();
    private static final Set<UUID> eliminated=new HashSet<>(),finished=new HashSet<>(),registered=new LinkedHashSet<>(),excluded=new HashSet<>();
    private static final List<Group> groups=new ArrayList<>();
    private static final Map<UUID,Integer> sixColors=new HashMap<>(),sixProgress=new HashMap<>();
    private static UUID waiting;
    private static final MpsqSkyRound skyRound = new MpsqSkyRound();
    private static final Set<String> excludedNames = new HashSet<>();
    private static final Set<String> removedFromRound = new HashSet<>();
    private static final Set<net.minecraft.core.BlockPos> skyPowered = new HashSet<>();
    private static class Group {List<UUID> members;int checkpoint=1;long end;int lane;Group(List<UUID> ids,int lane){this.lane=lane;members=new ArrayList<>(ids);end=System.currentTimeMillis()+300000;}}
    public static void initialize(){
        de.galaxushd.mpsqcamera.compat.WorldRenderEvents.AFTER_ENTITIES.register(ctx->{
            if(!mode.equals("room_game")||!MpsqFinishZoneTool.staff()||ctx.matrixStack()==null||ctx.consumers()==null)return;
            var c=net.minecraft.client.Minecraft.getInstance();if(c.level==null)return;
            for(var entry:MpsqZoneCatalog.forGame("room_game")){
                if(!MpsqZoneCatalog.inMaster(entry.box()))continue;
                var b=entry.box();int count=0;
                for(Entity entity:c.level.entitiesForRendering())if(eligible(entity)&&MpsqZoneCatalog.inMaster(entity)&&b.containsFeet(entity))count++;
                Component text=net.minecraft.network.chat.Component.literal(count+" Personen");var m=ctx.matrixStack();var camera=ctx.camera().getPos();
                m.pushPose();m.translate((b.minX()+b.maxX())/2-camera.x,(b.minY()+b.maxY())/2-camera.y,(b.minZ()+b.maxZ())/2-camera.z);
                // Vanilla name tags provide the correct facing and through-wall pass.
                // submitNameTag adds half a block; compensate to center the text on XYZ.
                ctx.consumers().collector().submitNameTag(m,new net.minecraft.world.phys.Vec3(0,-.5+c.font.lineHeight*.0125,0),0,text,true,0xF000F0,ctx.consumers().camera());m.popPose();
            }
        });
        for(String id:IDS)MpsqSystemController.register(id,MpsqZoneCatalog.label(id),new MpsqSystemController.Listener(){public void onActivated(){start(id);}public void onDeactivated(){stop();}});
        ClientTickEvents.END_CLIENT_TICK.register(MpsqGamesRuntime::tick);
        AttackEntityCallback.EVENT.register((p,w,h,e,hit)->hit(p,e,w.isClientSide()));
        UseEntityCallback.EVENT.register((p,w,h,e,hit)->hit(p,e,w.isClientSide()));
    }
    public static void height(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Y muss endlich sein");height=value;}
    public static String configurationError(String id){
        if(!id.isEmpty()&&!MpsqZoneCatalog.hasMaster())return "Bitte zuerst mit dem Leuchttintenbeutel eine Gesamtfläche anlegen.";
        if(Arrays.stream(IDS).noneMatch(id::equals))return "";
        if(id.equals("glass_bridge")||id.equals("rope_jump")||id.equals("hide_and_seek"))return "";
        var zones=MpsqZoneCatalog.forGame(id);
        if(id.equals("six_leg")){for(int[] team:MpsqZoneCatalog.SIX_TEAMS)for(int k:team){final int kind=k;if(zones.stream().filter(z->z.kind()==kind).count()!=6)return "Sechsbein benötigt für A und B je sechs Checkpoints pro Farbe (Start + fünf Stationen).";}}
        else if(id.equals("sky_squid")&&zones.size()!=3)return "Sky Squid Game benötigt drei Turmzonen.";
        else if(id.equals("dalgona")&&zones.size()!=4)return "Dalgona benötigt vier Teamzonen.";
        else if(id.equals("tug_of_war")&&(zones.stream().noneMatch(z->z.kind()==5)||zones.stream().noneMatch(z->z.kind()==6)||zones.stream().noneMatch(z->z.kind()==7)))return "Tauziehen benötigt Rot, Blau und mindestens eine Glow-entfernen-Zone.";
        else if(zones.isEmpty())return "Bitte zuerst die Zonen für dieses Spiel anlegen.";
        return "";
    }
    private static void start(String id){
        stop(); mode=id;
        if(id.equals("sky_squid")){
            Set<UUID> roster=new LinkedHashSet<>();var level=Minecraft.getInstance().level;
            if(level!=null)for(Entity e:level.entitiesForRendering())if(eligible(e)&&MpsqZoneCatalog.inMaster(e))roster.add(e.getUUID());
            skyRound.begin(roster);roster.forEach(uuid->glows.put(uuid,WHITE));
        }
        message(MpsqZoneCatalog.label(id)+": aktiviert");
        MpsqBossbarManager.apply(new MpsqBossbarState("game_active", "&fSpiel " + MpsqZoneCatalog.label(id) + " ist aktiv", "red", 1f, true));
        // RLGL keeps its phase bar as the active indicator; other games get a white HUD title.
        if(!id.equals("red_light_green_light")){
            var c=Minecraft.getInstance();
            if(c.gui!=null){c.gui.hud.setTitle(Component.literal(MpsqZoneCatalog.label(id)));c.gui.hud.setSubtitle(Component.literal("Spiel ist aktiv"));c.gui.hud.setTimes(10,70,20);}
        }
    }
    public static void stop(){removedFromRound.clear();MpsqBossbarManager.remove("six_team_0");MpsqBossbarManager.remove("six_team_1");MpsqKickAnimationManager.clearClones();mode="";sixColors.clear();sixProgress.clear();glows.clear();hits.clear();hitTimes.clear();lastZone.clear();partners.clear();eliminated.clear();finished.clear();registered.clear();groups.clear();waiting=null;skyRound.reset();skyPowered.clear();MpsqBossbarManager.remove("game_round");MpsqBossbarManager.remove("game_active");}
    public static void resetSession(){stop();excluded.clear();excludedNames.clear();}
    public static boolean activeParticipant(Player p){return p.isAlive() && participant(p) && !eliminated.contains(p.getUUID()) && !MpsqParticipation.inactive(p.getGameProfile().name());}
    public static boolean participant(Player p){
        if(MpsqNpcGlow.isNpc(p))return false;
        if(p.isSpectator()||removedFromRound.contains(p.getGameProfile().name().toLowerCase(Locale.ROOT))||excluded.contains(p.getUUID())||excludedNames.contains(p.getGameProfile().name().toLowerCase(Locale.ROOT)))return false;
        if(p instanceof AbstractClientPlayer a&&MpsqKickAnimationManager.isClone(a))return false;
        TeamRank rank=TeamStateStore.byMinecraftName(p.getGameProfile().name()).map(TeamProfile::displayedRank).orElse(TeamRank.PLAYER);
        if(MpsqParticipation.inactive(p.getGameProfile().name())&&!eliminated.contains(p.getUUID()))return false;
        return rank==TeamRank.PLAYER||rank==TeamRank.STREAMER||rank==TeamRank.UNDERCOVER_001||rank==TeamRank.VIP&&MpsqParticipation.returned(p.getGameProfile().name());
    }
    public static boolean outline(Entity e){
        if(mode.isEmpty()||!TeamVisibilitySettings.inArea()||!eligible(e)||!MpsqZoneCatalog.inMaster(e)||!glows.containsKey(e.getUUID()))return false;
        // Visibility is a permission, not the temporary role shown on the nameplate.
        TeamRank viewer=TeamStateStore.self().map(TeamProfile::permissionRank).orElse(TeamRank.PLAYER);
        if(!canViewGlow(viewer))return false;
        var c=Minecraft.getInstance();
        return !mode.equals("hide_and_seek")||eliminated.contains(e.getUUID())||c.player!=null&&c.player.hasLineOfSight(e);
    }
    private static boolean eligible(Entity e){
        if(MpsqNpcGlow.isNpc(e))return false;
        if(e==null||e.level()!=Minecraft.getInstance().level||excluded.contains(e.getUUID())||!e.isAlive())return false;
        if(e instanceof Player p)return participant(p)||teamGamePlayer(p);
        return e instanceof net.minecraft.world.entity.Mob;
    }
    private static boolean teamGamePlayer(Player p){
        if(MpsqNpcGlow.isNpc(p)||removedFromRound.contains(p.getGameProfile().name().toLowerCase(Locale.ROOT)))return false;
        return (mode.equals("dalgona")||mode.equals("tug_of_war"))&&!p.isSpectator()
                &&!(p instanceof AbstractClientPlayer a&&MpsqKickAnimationManager.isClone(a));
    }
    private static boolean canViewGlow(TeamRank rank){
        if(mode.equals("tug_of_war"))return rank.level()>=TeamRank.SOLDIER.level();
        return rank==TeamRank.VIP||rank==TeamRank.UNDERCOVER_001||rank==TeamRank.STREAMER||rank.level()>=TeamRank.SOLDIER.level();
    }
    public static int color(Entity e){return glows.getOrDefault(e.getUUID(),WHITE);}
    public static void restore(String name){
        excludedNames.remove(name.toLowerCase(Locale.ROOT));MpsqMovementSensorSystem.restore(name);
        var c=Minecraft.getInstance();if(c.level!=null)for(var p:c.level.players())if(p.getGameProfile().name().equalsIgnoreCase(name)){excluded.remove(p.getUUID());eliminated.remove(p.getUUID());glows.remove(p.getUUID());}
    }
    /** Remove only from this round; do not add the player to the event return lottery. */
    public static void removeFromRound(String name){
        removedFromRound.add(name.toLowerCase(Locale.ROOT));MpsqMovementSensorSystem.kick(name);
        var c=Minecraft.getInstance();if(c.level==null)return;
        for(var p:c.level.players())if(p.getGameProfile().name().equalsIgnoreCase(name)){
            UUID id=p.getUUID();glows.remove(id);registered.remove(id);sixColors.remove(id);sixProgress.remove(id);
            for(var group:groups)group.members.remove(id);
            if(mode.equals("sky_squid"))skyRound.kick(id);
            UUID partner=partners.remove(id);if(partner!=null){partners.remove(partner);finished.add(partner);glows.remove(partner);}
            if(id.equals(waiting))waiting=null;
        }
    }
    public static void kick(String name){
        MpsqParticipation.exclude(name);
        excludedNames.add(name.toLowerCase(Locale.ROOT));
        MpsqMovementSensorSystem.kick(name);
        var c=Minecraft.getInstance();if(c.level==null)return;
        for(var p:c.level.players())if(p.getGameProfile().name().equalsIgnoreCase(name)){
            UUID id=p.getUUID();excluded.add(id);glows.remove(id);registered.remove(id);
            if(mode.equals("sky_squid"))skyRound.kick(id);
            UUID partner=partners.remove(id);if(partner!=null){partners.remove(partner);finished.add(partner);glows.remove(partner);}
            if(id.equals(waiting))waiting=null;
        }
    }
    private static void eliminate(UUID id){if(!finished.contains(id)){if(eliminated.add(id)){var c=Minecraft.getInstance();if(c!=null&&c.level!=null)for(var p:c.level.players())if(p.getUUID().equals(id))MpsqParticipation.exclude(p.getGameProfile().name());}glows.put(id,RED);}}
    private static void tick(Minecraft c){
        if(mode.isEmpty())return;if(c.level==null||!TeamVisibilitySettings.visible()){stop();return;}
        List<Entity> players=new ArrayList<>();
        for(Entity e:c.level.entitiesForRendering())if(eligible(e))players.add(e);
        glows.keySet().removeIf(id->c.level.players().stream().anyMatch(p->p.getUUID().equals(id)&&!(participant(p)||teamGamePlayer(p))));
        if(mode.equals("marbles")){for(UUID id:new ArrayList<>(partners.keySet())){var entity=c.level.getEntity(id);if(entity!=null&&!entity.isAlive()||eliminated.contains(id)||excluded.contains(id)){UUID partner=partners.remove(id);if(partner!=null){partners.remove(partner);finished.add(partner);glows.remove(partner);}if(id.equals(waiting))waiting=null;}}}
        var zones=MpsqZoneCatalog.forGame(mode);
        for(var p:players){UUID id=p.getUUID();if(!MpsqZoneCatalog.inMaster(p)||eliminated.contains(id)||finished.contains(id))continue;
            int z=-1;for(int i=0;i<zones.size();i++)if(zones.get(i).box().contains(p)){z=i;break;}
            int previous=lastZone.getOrDefault(id,-1);lastZone.put(id,z);
            switch(mode){
                case "dalgona","tug_of_war" -> {if(z>=0&&z!=previous){int color=MpsqZoneCatalog.KINDS.get(zones.get(z).kind()).color();if(color<0)glows.remove(id);else glows.put(id,color);}}
                case "glass_bridge","rope_jump" -> {if(p.getY()<height)eliminate(id);}
                case "finale" -> {if(z>=0)registered.add(id);else if(registered.contains(id))eliminate(id);}
                case "marbles" -> {if(z>=0&&!partners.containsKey(id)){
                    if(waiting==null)waiting=id;
                    else if(!waiting.equals(id)&&pairMarbles(waiting,id))waiting=null;
                }}
                default -> {}
            }
        }
        if(mode.equals("six_leg"))sixLeg(players.stream().filter(MpsqZoneCatalog::inMaster).toList(),zones);
        if(mode.equals("sky_squid"))sky(players,zones);
    }
    /** Active partners reserve their color until the pair is resolved or the round resets. */
    private static boolean pairMarbles(UUID first,UUID second){
        if(first.equals(second)||partners.containsKey(first)||partners.containsKey(second))return false;
        Set<Integer> occupied=new HashSet<>();
        for(UUID member:partners.keySet()){Integer color=glows.get(member);if(color!=null)occupied.add(color);}
        int start=Math.floorMod(Objects.hash(first,second),COLORS.length);
        for(int offset=0;offset<COLORS.length;offset++){
            int color=COLORS[(start+offset)%COLORS.length];
            if(occupied.contains(color))continue;
            partners.put(first,second);partners.put(second,first);glows.put(first,color);glows.put(second,color);return true;
        }
        // No duplicates when full: participants remain queued and retry while in the zone.
        return false;
    }
    private static void sixLeg(List<? extends Entity> players,List<MpsqZoneCatalog.Entry> zones){
        for(var player:players){
            UUID id=player.getUUID();if(eliminated.contains(id)||finished.contains(id))continue;
            int progress=sixProgress.getOrDefault(id,1);
            if(progress==1&&!registered.contains(id))for(int[] team:MpsqZoneCatalog.SIX_TEAMS)for(int kind:team){
                final int k=kind;var start=zones.stream().filter(z->z.kind()==k).findFirst();
                if(start.isPresent()&&start.get().box().containsFeet(player)){sixColors.put(id,kind);glows.put(id,MpsqZoneCatalog.KINDS.get(kind).color());break;}
            }
            Integer kind=sixColors.get(id);if(kind==null||!registered.contains(id))continue;
            var route=zones.stream().filter(z->z.kind()==kind).toList();
            int target=progress==6?0:progress;
            if(route.size()>target&&route.get(target).box().containsFeet(player)){
                sixProgress.put(id,++progress);
                if(progress==7){finished.add(id);glows.remove(id);}
            }
        }
        for(int lane=0;lane<MpsqZoneCatalog.SIX_TEAMS.length;lane++){
        int[] team=MpsqZoneCatalog.SIX_TEAMS[lane];List<UUID> candidates=new ArrayList<>();
        for(int kind:team){
            final int k=kind;var same=zones.stream().filter(z->z.kind()==k).toList();
            if(same.isEmpty())continue;
            var p=players.stream().filter(x->!registered.contains(x.getUUID())&&!eliminated.contains(x.getUUID())&&!finished.contains(x.getUUID())&&same.get(0).box().containsFeet(x)).findFirst();
            if(p.isPresent())candidates.add(p.get().getUUID());
        }
        if(candidates.size()==5){Group group=new Group(List.copyOf(candidates),lane);groups.add(group);for(int i=0;i<5;i++){registered.add(candidates.get(i));glows.put(candidates.get(i),MpsqZoneCatalog.KINDS.get(team[i]).color());}}
        }
        long now=System.currentTimeMillis();
        for(var group:groups){if(group.checkpoint>=7)continue;
            if(now>=group.end){group.members.forEach(MpsqGamesRuntime::eliminate);group.checkpoint=7;continue;}
            if(group.members.stream().allMatch(finished::contains))group.checkpoint=7;
        }
        for(int lane=0;lane<2;lane++){final int selected=lane;long remaining=groups.stream().filter(g->g.lane==selected&&g.checkpoint<7).mapToLong(g->g.end-now).min().orElse(0);String barId="six_team_"+lane;if(remaining>0)MpsqBossbarManager.apply(new MpsqBossbarState(barId,"Sechsbein Team "+(lane==0?"A":"B")+": "+((remaining+999)/1000)+" s","purple",remaining/300000f,true));else MpsqBossbarManager.remove(barId);}
    }
    // Observe other players pressing configured tower buttons as well.
    public static void skyButtonUpdate(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if(!mode.equals("sky_squid"))return;
        boolean powered=state.getBlock() instanceof net.minecraft.world.level.block.ButtonBlock
                &&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED);
        if(!powered){skyPowered.remove(pos);return;}
        if(skyPowered.add(pos.immutable())&&MpsqTriggerManager.isSkyTowerButton(pos))startSkyTower(pos);
    }
    public static void startSkyTower(net.minecraft.core.BlockPos pos){
        if(!mode.equals("sky_squid")||!TeamVisibilitySettings.visible()){message("Bitte zuerst Sky Squid Game aktivieren.");return;}
        var c=Minecraft.getInstance();if(c.level==null)return;
        var zones=MpsqZoneCatalog.forGame("sky_squid");
        int tower=skyRound.tower();if(zones.size()!=3||tower>=3||skyRound.running())return;
        var zone=zones.get(tower).box();
        if(!MpsqZoneCatalog.inMaster(pos)||!zone.containsFeet(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)){
            message("Dieser Knopf muss im aktuellen Turm "+(tower+1)+" liegen.");return;
        }
        Set<UUID> inside=new HashSet<>();
        for(Entity e:c.level.entitiesForRendering())if(eligible(e)&&zone.containsFeet(e)&&e.getY()>=height)inside.add(e.getUUID());
        if(skyRound.press(tower,inside,System.currentTimeMillis())){
            for(UUID id:skyRound.survivors())glows.put(id,WHITE);
            message("Sky Squid Game: Turm "+(tower+1)+" gestartet – 15 Minuten, "+skyRound.survivors().size()+" Teilnehmer.");
        }else{
            Set<UUID> missing=skyRound.survivors();missing.removeAll(inside);
            message(skyRound.survivors().isEmpty()?"Sky Squid Game: Keine verbleibenden Teilnehmer.":"Sky Squid Game: In Turm "+(tower+1)+" fehlen noch "+missing.size()+" Teilnehmer.");
        }
    }
    private static void sky(List<? extends Entity> players,List<MpsqZoneCatalog.Entry> zones){
        if(zones.size()!=3)return;
        long now=System.currentTimeMillis();int previousTower=skyRound.tower();
        // Collect all losses together, so simultaneous falls cannot skip a tower.
        Set<UUID> losses=new HashSet<>();
        for(Entity p:players)if(p.getY()<height)losses.add(p.getUUID());
        if(skyRound.running()){
            var zone=zones.get(skyRound.tower()).box();
            for(Entity p:players)if(!zone.containsFeet(p))losses.add(p.getUUID());
            skyRound.tick(losses,now);
        }
        skyRound.fall(losses);
        if(skyRound.tower()!=previousTower){
            if(skyRound.survivors().isEmpty())message("Sky Squid Game: Runde beendet – keine verbleibenden Teilnehmer.");
            else if(skyRound.tower()==3)message("Sky Squid Game: Finale bestanden.");
            else message("Sky Squid Game: Turm bestanden. Weiter zu Turm "+(skyRound.tower()+1)+" und dort den Knopf drücken.");
        }
        for(UUID id:skyRound.eliminated())if(!excluded.contains(id))eliminate(id);
        for(UUID id:skyRound.survivors())if(!excluded.contains(id)){
            boolean arrived=skyRound.tower()<3&&players.stream().anyMatch(p->p.getUUID().equals(id)&&zones.get(skyRound.tower()).box().containsFeet(p));
            glows.put(id,skyRound.running()||skyRound.tower()==0||arrived?WHITE:GREEN);
        }
        if(skyRound.running())bar("Sky Squid Game · Turm "+(skyRound.tower()+1)+": "+((skyRound.remaining(now)+999)/1000)+" s",skyRound.remaining(now)/(float)MpsqSkyRound.DURATION);
        else MpsqBossbarManager.remove("game_round");
    }
    private static InteractionResult hit(Player attacker,Entity target,boolean client){
        if(!client||!mode.equals("hide_and_seek")||!TeamVisibilitySettings.visible()||!MpsqFinishZoneTool.staff()||!eligible(target)||!MpsqZoneCatalog.inMaster(target)||!MpsqZoneCatalog.inMaster(attacker))return InteractionResult.PASS;
        double reach=attacker.entityInteractionRange();var eye=attacker.getEyePosition();var box=target.getBoundingBox();
        var nearest=new net.minecraft.world.phys.Vec3(Math.max(box.minX,Math.min(box.maxX,eye.x)),Math.max(box.minY,Math.min(box.maxY,eye.y)),Math.max(box.minZ,Math.min(box.maxZ,eye.z)));
        if(eye.distanceToSqr(nearest)>reach*reach||!attacker.hasLineOfSight(target))return InteractionResult.FAIL;
        UUID id=target.getUUID();long now=System.currentTimeMillis();if(eliminated.contains(id)||now-hitTimes.getOrDefault(id,0L)<1000)return InteractionResult.SUCCESS;
        hitTimes.put(id,now);int n=hits.merge(id,1,Integer::sum);if(n>=3)eliminate(id);else glows.put(id,n==1?0xFFFF55:0xFFAA00);return InteractionResult.SUCCESS;
    }
    private static void bar(String title,float fraction){MpsqBossbarManager.apply(new MpsqBossbarState("game_round",title,"purple",Math.max(0,Math.min(1,fraction)),true));}
    private static void message(String text){var c=Minecraft.getInstance();if(c.player!=null)de.galaxushd.mpsqcamera.compat.PlayerMessages.send(c.player, Component.literal(text),false);}
}

