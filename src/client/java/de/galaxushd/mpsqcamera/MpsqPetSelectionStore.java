package de.galaxushd.mpsqcamera;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Stores and clears the local selected pet preference. */
public final class MpsqPetSelectionStore {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mpsq-pets.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static String selected = load();
    private static String budgieVariant = loadBudgieVariant();
    private static String owlVariant = loadOwlVariant();
    private static String tigerVariant = loadTigerVariant();
    public static boolean isTiger(String id){return "mpsq_tiger".equals(id)||"mpsq_tiger_baby".equals(id);}
    public static synchronized String tigerVariant(){return tigerVariant;}
    private static String loadTigerVariant(){
        if(isTiger(selected))return selected;
        var data=readData();String id=data.has("tiger_variant")&&data.get("tiger_variant").isJsonPrimitive()?data.get("tiger_variant").getAsString():"mpsq_tiger";
        return isTiger(id)?id:"mpsq_tiger";
    }
    public static synchronized boolean selectTigerVariant(String id){
        if(!isTiger(id))return false;
        var data=readData();data.addProperty("tiger_variant",id);boolean equipped=isTiger(selected);
        if(equipped)data.addProperty("selected_pet",id);
        if(!save(data))return false;tigerVariant=id;if(equipped)selected=id;return true;
    }
    public static synchronized String budgieVariant() { return budgieVariant; }
    public static synchronized boolean selectBudgieVariant(String variant) {
        if (!MpsqBudgiePetRenderer.isVariant(variant)) return false;
        JsonObject data = readData();
        data.addProperty("budgie_variant", variant);
        if (!save(data)) return false;
        budgieVariant = variant;
        return true;
    }

    public static synchronized String owlVariant(){ return owlVariant; }
    private static String loadOwlVariant(){
        if(selected!=null&&selected.startsWith("nogs_owl_"))return selected;
        var data=readData();String id=data.has("owl_variant")&&data.get("owl_variant").isJsonPrimitive()?data.get("owl_variant").getAsString():"nogs_owl_barn";
        return MpsqPetCatalog.byId(id)!=null&&id.startsWith("nogs_owl_")?id:"nogs_owl_barn";
    }
    public static synchronized boolean selectOwlVariant(String id){
        if(id==null||!id.startsWith("nogs_owl_")||MpsqPetCatalog.byId(id)==null)return false;
        var data=readData();data.addProperty("owl_variant",id);
        boolean equipped=selected!=null&&selected.startsWith("nogs_owl_");
        if(equipped)data.addProperty("selected_pet",id);
        if(!save(data))return false;owlVariant=id;if(equipped)selected=id;return true;
    }
    private MpsqPetSelectionStore() { }

    public static synchronized String selectedId() { return selected; }
    public static synchronized String activeId() { return MpsqPetVisibility.suppressed()?null:selected; }

    public static synchronized boolean select(String id) {
        if (id != null && MpsqPetCatalog.byId(id) == null) return false;
        JsonObject data = readData();
        data.addProperty("schema", 2);
        if(isTiger(id))data.addProperty("tiger_variant",id);
        if(id!=null&&id.startsWith("nogs_owl_"))data.addProperty("owl_variant",id);
        if (id == null) data.add("selected_pet", com.google.gson.JsonNull.INSTANCE);
        else data.addProperty("selected_pet", id);
        if (!save(data)) return false;
        selected = id;
        if(isTiger(id))tigerVariant=id;
        if(id!=null&&id.startsWith("nogs_owl_"))owlVariant=id;
        return true;
    }

    private static JsonObject readData() {
        try {
            if (Files.isRegularFile(FILE)) return JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ignored) { }
        return new JsonObject();
    }

    private static boolean save(JsonObject data) {
        try {
            Files.createDirectories(FILE.getParent());
            Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(data), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try { Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (Exception error) {
            MpsqCameraClient.LOGGER.warn("Pet-Auswahl konnte nicht gespeichert werden", error);
            return false;
        }
    }

    private static String load() {
        try {
            if (!Files.isRegularFile(FILE)) return null;
            JsonObject data = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            String id = data.has("selected_pet") && !data.get("selected_pet").isJsonNull()
                    ? data.get("selected_pet").getAsString() : null;
            return MpsqPetCatalog.byId(id) == null ? null : id;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String loadBudgieVariant() {
        JsonObject data = readData();
        String variant = data.has("budgie_variant") ? data.get("budgie_variant").getAsString() : "green";
        return MpsqBudgiePetRenderer.isVariant(variant) ? variant : "green";
    }
}



