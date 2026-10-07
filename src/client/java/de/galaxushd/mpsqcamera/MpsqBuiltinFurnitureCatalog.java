package de.galaxushd.mpsqcamera;

import java.util.List;

/** One registration point for built-in furniture models and their preview labels. */
final class MpsqBuiltinFurnitureCatalog {
    record Furniture(String id,String slug,String name,String resource) {
        String url(){return "builtin://mpsq/bobblehead/"+slug;}
    }

    // Add future bobblehead/statue skins here; rendering and the local furniture catalogue
    // both resolve this same entry list, so a new model does not need parallel hard-coded lists.
    private static final List<Furniture> ITEMS=List.of(
            item("donator","Donator You"),
            item("helpful","Helpful You"),
            item("parkour","Parkour You"),
            item("pvp","PvP You"),
            item("trophy_bronze","Trophy Bronze You"),
            item("trophy_silver","Trophy Silver You"),
            item("trophy_gold","Trophy Gold You"),
            item("voter","Voter You"));

    private MpsqBuiltinFurnitureCatalog() { }
    private static Furniture item(String slug,String name){return new Furniture("mpsq_bobblehead_"+slug+"_you",slug,name,"/assets/mpsqcamera/models/furniture/nm_bobblehead_"+slug+"_you.json");}
    static List<Furniture> all(){return ITEMS;}
    static Furniture byId(String id){return ITEMS.stream().filter(item->item.id().equals(id)).findFirst().orElse(null);}
    static Furniture byUrl(String url){return ITEMS.stream().filter(item->item.url().equals(url)).findFirst().orElse(null);}
}
