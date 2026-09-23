package de.galaxushd.mpsqcamera;

/**
 * Local tool and audio preferences.
 */
public final class ModConfig {

    private ModConfig() {}

    /** Item-ID des Erstellungs-Werkzeugs (Standard: minecraft:ink_sac). */
    public static String toolItemId = "minecraft:ink_sac";

    /** Globale Wiedergabe-Lautstärke (0.0 – 1.0). */
    public static float volume = 1.0f;

    private static final java.nio.file.Path FILE=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("mpsq-settings.json");
    public static void load(){
        if(!java.nio.file.Files.exists(FILE))return;
        try{
            var data=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(FILE)).getAsJsonObject();
            if(data.has("toolItemId")&&net.minecraft.util.Identifier.tryParse(data.get("toolItemId").getAsString())!=null)toolItemId=data.get("toolItemId").getAsString();
            if(data.has("volume")){float v=data.get("volume").getAsFloat();if(Float.isFinite(v))volume=Math.max(0,Math.min(1,v));}
        }catch(Exception e){MpsqCameraClient.LOGGER.warn("MPSQ-Einstellungen konnten nicht gelesen werden",e);}
    }
    public static void save(){
        try{var data=new com.google.gson.JsonObject();data.addProperty("toolItemId",toolItemId);data.addProperty("volume",volume);java.nio.file.Files.createDirectories(FILE.getParent());java.nio.file.Files.writeString(FILE,data.toString());}
        catch(Exception e){MpsqCameraClient.LOGGER.warn("MPSQ-Einstellungen konnten nicht gespeichert werden",e);}
    }
}
