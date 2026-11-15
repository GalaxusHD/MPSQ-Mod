package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Persistent configuration, independent of round state. */
public final class MpsqZoneCatalog {
    public static final int MASTER = 17;
    public static final int CAMERA = 18;
    public record Kind(String game, String name, int color, int limit) {
        public String title() { return label(game) + ": " + name; }
    }
    public record Entry(int kind, MpsqGameZone box, String name) {
        public Entry(int kind,MpsqGameZone box){this(kind,box,"");}
    }
    public static final List<Kind> KINDS = List.of(
        new Kind("red_light_green_light", "Safe Zone", 0xFFAA00, 1),
        new Kind("dalgona", "rotes Team", 0xFF5555, 1), new Kind("dalgona", "blaues Team", 0x5555FF, 1),
        new Kind("dalgona", "grünes Team", 0x00AA00, 1), new Kind("dalgona", "gelbes Team", 0xFFFF55, 1),
        new Kind("tug_of_war", "rotes Team", 0xFF5555, 1), new Kind("tug_of_war", "blaues Team", 0x5555FF, 1),
        new Kind("tug_of_war", "Glow entfernen", -1, 2), new Kind("marbles", "Team Maker Zone", 0xFFFFFF, 1),
        new Kind("finale", "Safe Zone", 0xFFFFFF, 1),
        new Kind("six_leg", "Checkpoint Rot A", 0xFF5555, 6), new Kind("six_leg", "Checkpoint Grün A", 0x55FF55, 6),
        new Kind("six_leg", "Checkpoint Blau A", 0x5555FF, 6), new Kind("six_leg", "Checkpoint Gelb A", 0xFFFF55, 6),
        new Kind("six_leg", "Checkpoint Violett A", 0xAA00AA, 6),
        new Kind("room_game", "Raum Zone", 0xFFFFFF, 64), new Kind("sky_squid", "Turm Zone", 0xFFFFFF, 3),
        new Kind("master_area", "Gesamtfläche", 0x55FFFF, 1),
        new Kind("camera", "Kamerafahrt auslösen", 0xAA55FF, 128),
        new Kind("six_leg","Checkpoint Rot B",0xFF5555,6),new Kind("six_leg","Checkpoint Gelb B",0xFFFF55,6),new Kind("six_leg","Checkpoint Grün B",0x55FF55,6),new Kind("six_leg","Checkpoint Blau B",0x5555FF,6),new Kind("six_leg","Checkpoint Violett B",0xAA00AA,6));
    public static final int[][] SIX_TEAMS={{10,13,11,12,14},{19,20,21,22,23}};
    public static int nextKind(int current){if(KINDS.get(current).game().equals("six_leg")){int[] order={10,13,11,12,14,19,20,21,22,23};for(int i=0;i<order.length;i++)if(order[i]==current)return order[(i+1)%order.length];}String game=KINDS.get(current).game();int next=current;do{next=(next+1)%KINDS.size();}while(!KINDS.get(next).game().equals(game));return next;}
    private static final List<Entry> zones = new ArrayList<>();
    public static String label(String id) { return switch(id) {
        case "master_area" -> "MPSQ";
        case "camera" -> "Kamerafahrten";
        case "red_light_green_light" -> "Red Light Green Light"; case "dalgona" -> "Dalgona";
        case "tug_of_war" -> "Tauziehen"; case "marbles" -> "Murmelspiel"; case "finale" -> "Squid Game Finale";
        case "six_leg" -> "Sechsbein"; case "room_game" -> "Rundherum"; case "sky_squid" -> "Sky Squid Game";
        case "hide_and_seek" -> "Hide and Seek"; case "glass_bridge" -> "Glass Bridge"; case "rope_jump" -> "Seilspringen";
        default -> id; }; }
    public static List<Entry> all() { return List.copyOf(zones); }
    public static boolean hasMaster(){return zones.stream().anyMatch(e->e.kind()==MASTER);}
    public static boolean inMaster(net.minecraft.world.entity.Entity entity){return entity!=null&&zones.stream().anyMatch(e->e.kind()==MASTER&&e.box().containsFeet(entity));}
    public static boolean inMaster(double x,double y,double z){return zones.stream().anyMatch(e->e.kind()==MASTER&&e.box().containsFeet(x,y,z));}
    public static boolean inMaster(BlockPos pos){return zones.stream().anyMatch(e->e.kind()==MASTER&&e.box().containsFeet(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5));}
    public static boolean inMaster(MpsqGameZone box){return zones.stream().anyMatch(e->e.kind()==MASTER&&box.minX()>=e.box().minX()&&box.maxX()<=e.box().maxX()&&box.minY()>=e.box().minY()&&box.maxY()<=e.box().maxY()&&box.minZ()>=e.box().minZ()&&box.maxZ()<=e.box().maxZ());}
    public static List<Entry> forGame(String id) { return zones.stream().filter(e -> KINDS.get(e.kind).game.equals(id)).toList(); }
    private static String key() { return "game_zones_" + Minecraft.getInstance().level.dimension().identifier(); }
    public static void load() {
        zones.clear(); MpsqMovementSensorSystem.clearSafeZone();
        if (Minecraft.getInstance().level == null) return;
        try { for (JsonElement element : MpsqLocalWorldStore.array(key())) {
            JsonArray a=element.getAsJsonArray(); int kind=a.get(0).getAsInt();
            if(kind<0||kind>=KINDS.size()) continue;
            zones.add(new Entry(kind,new MpsqGameZone(new BlockPos(a.get(1).getAsInt(),a.get(2).getAsInt(),a.get(3).getAsInt()),new BlockPos(a.get(4).getAsInt(),a.get(5).getAsInt(),a.get(6).getAsInt())),a.size()>7?a.get(7).getAsString():""));
        }} catch(RuntimeException e) { MpsqCameraClient.LOGGER.warn("Zonen konnten nicht vollständig geladen werden",e); }
        if(forGame("red_light_green_light").isEmpty()) {
            JsonArray old=MpsqLocalWorldStore.array("rlgl_finish_"+Minecraft.getInstance().level.dimension().identifier());
            if(old.size()==6){zones.add(new Entry(0,new MpsqGameZone(new BlockPos(old.get(0).getAsInt(),old.get(1).getAsInt(),old.get(2).getAsInt()),new BlockPos(old.get(3).getAsInt(),old.get(4).getAsInt(),old.get(5).getAsInt()))));if(save())MpsqLocalWorldStore.setArray("rlgl_finish_"+Minecraft.getInstance().level.dimension().identifier(),new JsonArray());}
        }
        applyFinish();
    }
    public static String add(int kind, BlockPos first, BlockPos second) {
        return add(kind,first,second,"");
    }
    public static boolean cameraZoneExists(String name){return zones.stream().anyMatch(e->e.kind==CAMERA&&e.name.equalsIgnoreCase(name));}
    public static boolean inCameraZone(String name,net.minecraft.world.entity.Entity player){return player!=null&&zones.stream().anyMatch(e->e.kind==CAMERA&&e.name.equalsIgnoreCase(name)&&e.box.containsFeet(player));}
    public static String add(int kind, BlockPos first, BlockPos second,String name) {
        name=name.trim();
        if(kind==CAMERA&&(name.isEmpty()||name.length()>64))return "Name: 1–64 Zeichen.";
        if(kind==CAMERA&&cameraZoneExists(name))return "Dieser Kamerazonenname ist bereits vergeben.";
        if(zones.stream().filter(e->e.kind==kind).count()>=KINDS.get(kind).limit) return "Limit erreicht. Vorhandene Zone zuerst löschen.";
        var box=new MpsqGameZone(first,second);
        if(kind!=MASTER&&!inMaster(box))return "Bitte zuerst eine Gesamtfläche anlegen. Die gesamte Zone muss darin liegen.";
        for(var e:forGame(KINDS.get(kind).game)) if(box.minX()<e.box.maxX()&&box.maxX()>e.box.minX()&&box.minY()<e.box.maxY()&&box.maxY()>e.box.minY()&&box.minZ()<e.box.maxZ()&&box.maxZ()>e.box.minZ()) return "Zonen desselben Spiels dürfen sich nicht überschneiden.";
        Entry entry=new Entry(kind,box,name); zones.add(entry);
        if(!save()){zones.remove(entry);return "Speichern fehlgeschlagen.";}
        applyFinish(); return "Gespeichert: "+KINDS.get(kind).title()+(name.isEmpty()?"":" · "+name);
    }
    public static String removeLast(int kind) {
        for(int i=zones.size()-1;i>=0;i--) if(zones.get(i).kind==kind){Entry old=zones.remove(i);if(!save()){zones.add(i,old);return "Löschen fehlgeschlagen.";}applyFinish();return "Letzte Zone dieser Art gelöscht.";}
        return "Keine Zone dieser Art vorhanden.";
    }
    public static String remove(Entry entry){
        int index=zones.indexOf(entry);if(index<0)return "Zone nicht gefunden.";
        zones.remove(index);
        if(!save()){zones.add(index,entry);return "Löschen fehlgeschlagen.";}
        applyFinish();return "Zone gelöscht: "+KINDS.get(entry.kind()).title();
    }
    public static int frameColor(int kind){
        if(kind==0)return 0xFF55FF;
        int color=KINDS.get(kind).color();return color<0?0xFFFFFF:color;
    }
    private static boolean save(){JsonArray data=new JsonArray();for(var e:zones){JsonArray a=new JsonArray();for(int n:new int[]{e.kind,e.box.first().getX(),e.box.first().getY(),e.box.first().getZ(),e.box.second().getX(),e.box.second().getY(),e.box.second().getZ()})a.add(n);a.add(e.name);data.add(a);}boolean saved=MpsqLocalWorldStore.setArray(key(),data);if(saved)MpsqCinematicTriggers.invalidate();return saved;}
    private static void applyFinish(){MpsqMovementSensorSystem.clearSafeZone();for(var e:forGame("red_light_green_light"))MpsqMovementSensorSystem.setSafeZone(e.box.first(),e.box.second());}
}
