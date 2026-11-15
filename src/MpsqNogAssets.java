package de.galaxushd.mpsqcamera;
/** Explicit resource whitelist: purchased assets stay bundled and work offline. */
final class MpsqNogAssets {
    static boolean isPet(String url){return url!=null&&(url.equals("builtin://mpsq/nm_tiger")||url.equals("builtin://mpsq/nm_tiger_baby")||url.equals("builtin://mpsq/bs_vm_crab")||url.equals("builtin://mpsq/bs_vm_penguin")||url.equals("builtin://mpsq/baby_happy_ghast_mount")||url.equals("builtin://mpsq/nm_colin_the_creeper")||url.equals("builtin://mpsq/nm_exo_the_enderman")||url.equals("builtin://mpsq/nm_slak_the_skeleton")||url.equals("builtin://mpsq/nm_zekk_the_zombie"));}
    static boolean isHat(String url){return "builtin://mpsq/nm_hat_seagull".equals(url)||"builtin://mpsq/nm_hat_owl_long_eared".equals(url);}
    static String resource(String url){
        if(url!=null&&url.matches("builtin://mpsq/nm_chess_(white|black)_(king|queen|rook|bishop|knight|pawn)"))return "/assets/mpsqcamera/custom_models/"+url.substring(url.lastIndexOf('/')+1)+".json";
        if("builtin://mpsq/timer".equals(url))return "/assets/mpsqcamera/custom_models/timer.json";
        if("builtin://mpsq/numbers".equals(url))return "/assets/mpsqcamera/custom_models/numbers.json";
        if("builtin://mpsq/bs_vm_crab".equals(url))return "/assets/mpsqcamera/custom_models/bs_vm_crab.json";
        if("builtin://mpsq/bs_vm_penguin".equals(url))return "/assets/mpsqcamera/custom_models/bs_vm_penguin.json";
        if("builtin://mpsq/baby_happy_ghast_mount".equals(url))return "/assets/mpsqcamera/custom_models/baby_happy_ghast_mount.json";
        if(url!=null&&url.startsWith("builtin://mpsq/nm_")){String model=url.substring("builtin://mpsq/".length());if(java.util.Set.of("nm_tiger","nm_tiger_baby","nm_colin_the_creeper","nm_exo_the_enderman","nm_slak_the_skeleton","nm_zekk_the_zombie").contains(model))return "/assets/mpsqcamera/custom_models/"+model+".json";}
        if(isHat(url))return "/assets/mpsqcamera/custom_models/accessoires/"+url.substring(url.lastIndexOf('/')+1)+".json";
        if("builtin://mpsq/nm_seagull_poop".equals(url))return "/assets/mpsqcamera/custom_models/nm_seagull_poop.json";
        if("builtin://mpsq/nm_seagull".equals(url))return "/assets/mpsqcamera/custom_models/nm_seagull.json";
        return null;
    }
}
