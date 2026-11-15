package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.util.*;

/** User supplied skins travel with the mod, independent of any world's cache. */
final class MpsqBuiltinNpcCatalog {
    static final List<String> SKINS=List.of("Ausrufezeichen","Fragezeichen","Arbeiter","Soldat","Offizier","Sr_Offizier","Frontman","456","Thanos","spieler_2e8f14af0c41d273","spieler_3ad39cfade1fd14f","spieler_4a405f86399148c4","spieler_4a91630d30c401e3","spieler_6d931248371ccd15","spieler_6ae06848186802ce","spieler_8b134f3e667c3f1f","spieler_9a7b90a7b4984053","spieler_39af63147613ece1","spieler_43c828ed2a8f1f1f","spieler_053d85e34fa7827d","spieler_73e2ae228b71363b","spieler_79fad63b2b25bf20","spieler_84f4f720b477be8a","spieler_94bdd423f55ab0b6","spieler_120f94ae06ef9f3b","spieler_247ae83edb963e6d","spieler_453a0757b92b3b6f","spieler_475c2c08ebee7cfe","spieler_0612cdcec659387d","spieler_7304f1a6a5900fc7","spieler_64531f9d9a237226","spieler_a1785a391624f4cb","spieler_c2a4a872929b11ad","spieler_c73c8ba2394f785f","spieler_c1088a1a11aff327","spieler_ca740ea3a0e7b2f8","spieler_ccc669f819f19b09","spieler_cd23701fec1f6df3","spieler_0a64b893d42e1338","spieler_1e04decad02c230e","spieler_1f8e21ab536788ff","spieler_02e6c4b1643b7461","spieler_e1165eb94fd6b0f6","spieler_ea4bf8077d088c46","spieler_f1b7b6795daf59a1","spieler_f33420658615092f");
    static String skinResource(String url){
        for(String id:SKINS)if(url.equals("builtin://mpsq/skin/"+id.toLowerCase(Locale.ROOT)))return "/assets/mpsqcamera/textures/npc_skins/"+id.toLowerCase(Locale.ROOT)+".png";
        return null;
    }
    static JsonArray catalog(){
        JsonArray result=new JsonArray();Set<String> seen=new HashSet<>();
        for(String id:SKINS){JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("name",id);row.addProperty("display_name",id.startsWith("spieler_")?"Spielerskin "+(SKINS.indexOf(id)-8):id);row.addProperty("category",id.equals("Sr_Offizier")?"npc_skin_slim":"npc_skin_normal");row.addProperty("url","builtin://mpsq/skin/"+id.toLowerCase(Locale.ROOT));result.add(row);seen.add(id.toLowerCase(Locale.ROOT));}
        for(String id:List.of(MpsqAccessoryRenderer.WUMPUS_ASSET_ID,"nm_seagull")){JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("display_name",id.equals("nm_seagull")?"Möwe":"Wumpus");row.addProperty("category","npc_model");row.addProperty("url",MpsqAccessoryRenderer.assetPreviewUrl(id,""));result.add(row);seen.add(id.toLowerCase(Locale.ROOT));}
        for(var e:MpsqLocalWorldStore.array("model_catalog"))if(e.isJsonObject()){
            var row=e.getAsJsonObject();String id=MpsqScenes.str(row,"id","").toLowerCase(Locale.ROOT);
            if(!id.equals("mpsq_tutorial")&&seen.add(id))result.add(row.deepCopy());
        }
        return result;
    }
}

