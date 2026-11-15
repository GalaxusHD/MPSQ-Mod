import de.galaxushd.mpsqcamera.*;
import net.minecraft.core.BlockPos;
import java.util.*;

public class CinematicZonesCheck {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
        var field=MpsqZoneCatalog.class.getDeclaredField("zones");field.setAccessible(true);
        var zones=(List<MpsqZoneCatalog.Entry>)field.get(null);
        try{
            check(MpsqZoneCatalog.MASTER==17&&MpsqZoneCatalog.CAMERA==18);
            check(MpsqZoneCatalog.KINDS.get(16).game().equals("sky_squid"));
            var master=new MpsqGameZone(new BlockPos(-10,-10,-10),new BlockPos(10,10,10));
            zones.add(new MpsqZoneCatalog.Entry(MpsqZoneCatalog.MASTER,master));
            var a=new BlockPos(0,0,0);var b=new BlockPos(3,5,3);
            zones.add(new MpsqZoneCatalog.Entry(MpsqZoneCatalog.CAMERA,new MpsqGameZone(a,b),"Eingang"));
            check(MpsqZoneCatalog.cameraZoneExists("eingang"));
            check(!MpsqZoneCatalog.cameraZoneExists("Eingang 2"));
            check(MpsqZoneCatalog.add(MpsqZoneCatalog.CAMERA,a,b," EINGANG ").contains("bereits vergeben"));
            check(MpsqZoneCatalog.add(MpsqZoneCatalog.CAMERA,a,b,"").contains("1–64"));
            check(MpsqZoneCatalog.add(MpsqZoneCatalog.CAMERA,new BlockPos(20,0,0),new BlockPos(21,1,1),"Außen").contains("Gesamtfläche"));
            check(master.containsFeet(0,8,0)); // flying entry uses height as well as X/Z
            check(!master.containsFeet(0,12,0));
            check(MpsqZoneCatalog.forGame("camera").size()==1);
            check(MpsqZoneCatalog.forGame("sky_squid").isEmpty());
            check(MpsqZoneCatalog.all().getFirst().name().isEmpty());
            System.out.println("PASS: stable existing zone indices, legacy entries, named camera zones, duplicate/blank names, master containment and flying bounds");
        }finally{zones.clear();}
    }
}
