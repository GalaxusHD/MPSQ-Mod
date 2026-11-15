package de.galaxushd.mpsqcamera;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;

/** Optional Flashback control; external classes are loaded only when the mod is present. */
final class MpsqExternalReplay {
    private static Object owned;
    private static String game="";
    static String provider(){return FabricLoader.getInstance().isModLoaded("flashback")?"Flashback":"";}
    static boolean active(){return !game.isEmpty();}
    private static Class<?> flash() throws ClassNotFoundException {return Class.forName("com.moulberry.flashback.Flashback");}
    private static Object recorder() throws ReflectiveOperationException {return flash().getField("RECORDER").get(null);}
    static boolean start(String id){
        if(provider().isEmpty())return false;
        try {
            if((boolean)flash().getMethod("isInReplay").invoke(null)){status("Keine Aufnahme während einer Replay-Wiedergabe.");return true;}
            if(recorder()!=null){game=id;status("Flashback: bestehende Aufnahme läuft weiter (manuell verwaltet).");return true;}
            flash().getMethod("startRecordingReplay").invoke(null);owned=recorder();
            if(owned==null)throw new IllegalStateException("Flashback hat den Aufnahmestart abgelehnt; dessen Kompatibilitätshinweis prüfen.");
            game=id;status("Flashback: Aufnahme gestartet – "+MpsqZoneCatalog.label(id));
        }catch(ReflectiveOperationException|RuntimeException|LinkageError e){owned=null;game="";status("Flashback: Aufnahme konnte nicht starten. Details im Log.");MpsqCameraClient.LOGGER.error("Externe Replay-Anbindung: Start fehlgeschlagen",e);}
        return true;
    }
    static void finish(){
        Object token=owned;owned=null;game="";
        if(token==null)return;
        try{
            if(recorder()!=token)return; // Never stop a replacement recording started by the user.
            flash().getMethod("finishRecordingReplay").invoke(null);
            status("Flashback: Aufnahme beendet; Speicherung über Flashback.");
        }catch(ReflectiveOperationException|RuntimeException|LinkageError e){status("Aufnahme bitte in Flashback beenden; automatisches Stoppen fehlgeschlagen.");MpsqCameraClient.LOGGER.error("Externe Replay-Anbindung: Stopp fehlgeschlagen",e);}
    }
    static void openDirectory(){
        try {var path=directory();java.nio.file.Files.createDirectories(path);java.awt.Desktop.getDesktop().open(path.toFile());}
        catch(Exception e){status("Aufnahmeordner: "+directory());}
    }
    static Path directory(){
        try{if(!provider().isEmpty())return (Path)flash().getMethod("getReplayFolder").invoke(null);}catch(ReflectiveOperationException ignored){}
        return FabricLoader.getInstance().getGameDir().resolve("flashback/replays");
    }
    private static void status(String message){
        MpsqReplayManager.status=message;
        var c=net.minecraft.client.Minecraft.getInstance();
        if(c.player!=null)de.galaxushd.mpsqcamera.compat.PlayerMessages.send(c.player,net.minecraft.network.chat.Component.literal(message),false);
    }
}
