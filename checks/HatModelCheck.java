import com.google.gson.*;
import java.nio.file.*;
public class HatModelCheck {
    public static void main(String[] args)throws Exception{
        var c=Class.forName("de.galaxushd.mpsqcamera.MpsqAccessoryRenderer");
        var parse=c.getDeclaredMethod("parseHeadDisplay",JsonObject.class);parse.setAccessible(true);
        for(String name:new String[]{"budgie","hedgehog","wumpus"}){
            var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/mpsqcamera/custom_models/accessoires/nm_hat_"+name+".json"))).getAsJsonObject();
            if(parse.invoke(null,json)==null)throw new AssertionError(name);
            System.out.println("PASS: hat head transform "+name);
        }
        var dedupe=c.getMethod("uniqueAccessories",JsonArray.class);
        JsonArray rows=JsonParser.parseString("[{\"accessory_id\":\"old\",\"filename\":\"nm_hat_budgie.bbmodel\"},{\"accessory_id\":\"equipped\",\"accessory_key\":\"nm_hat_budgie\",\"equipped\":true},{\"accessory_id\":\"igel\",\"filename\":\"nm_hat_hedgehog.json\"}]").getAsJsonArray();
        JsonArray unique=(JsonArray)dedupe.invoke(null,rows);
        if(unique.size()!=2||!unique.get(0).getAsJsonObject().get("accessory_id").getAsString().equals("equipped"))throw new AssertionError("Duplicate hats or equipped alias lost");
        if(rows.size()!=3)throw new AssertionError("Ownership data was mutated");
        var resolve=c.getMethod("resolveBuiltinAccessory",JsonObject.class);
        for(String key:new String[]{"nm_hat_budgie","nm_hat_hedgehog","nm_hat_wumpus"}){
            JsonObject row=new JsonObject();row.addProperty("accessory_id",java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
            if(!("builtin://mpsq/"+key).equals(resolve.invoke(null,row)))throw new AssertionError("Lost catalog metadata: "+key);
        }
        JsonObject legacy=new JsonObject();legacy.addProperty("accessory_id","discord_hat");
        if(!"builtin://mpsq/nm_hat_wumpus".equals(resolve.invoke(null,legacy)))throw new AssertionError("Legacy Wumpus lost");
        var pets=de.galaxushd.mpsqcamera.MpsqPetCatalog.group(de.galaxushd.mpsqcamera.MpsqPetCatalog.Group.MINI_YOU).stream().map(p->p.id()).toList();
        if(!pets.equals(java.util.List.of("monty","hecamus","harryron","der_pandi","de_merzli","der_wut_knut","endermansdl","agent_chicken_789","mr_creeper","my_skin","seong_gi_hun","kreis_minime","soldat_minime","offizier_minime","sr_offizier_minime","frontman_minime")))throw new AssertionError("Pet slots: "+pets);
        try(var texture=HatModelCheck.class.getResourceAsStream("/assets/mpsqcamera/textures/pets/sr_offizier.png")){if(texture==null)throw new AssertionError("SR-Offizier skin missing");}
        System.out.println("PASS: duplicate aliases, equipped selection, ownership preservation and 16 pet slots including Officer");
        var vector=c.getDeclaredMethod("displayVector",JsonObject.class,String.class,float[].class);vector.setAccessible(true);
        try{vector.invoke(null,JsonParser.parseString("{\"translation\":[0,180,0]}").getAsJsonObject(),"translation",new float[3]);throw new AssertionError("Unsafe translation accepted");}
        catch(java.lang.reflect.InvocationTargetException e){if(!(e.getCause() instanceof java.io.IOException))throw e;}
        System.out.println("PASS: translation limit preserved");
    }
}

