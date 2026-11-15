package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.*;
import com.google.common.collect.ArrayListMultimap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.PlayerSkin;
import net.fabricmc.loader.api.FabricLoader;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Name lookup only on demand; signed profiles and Minecraft skin files are cached locally. */
final class MpsqFigureSkins {
    private static final Map<String,PlayerSkin> SKINS=new HashMap<>();
    private static final Map<String,String> STATUS=new HashMap<>();
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    static boolean valid(String name){return name!=null&&name.matches("[A-Za-z0-9_]{1,16}");}
    static String status(String name){return STATUS.getOrDefault(name.toLowerCase(Locale.ROOT),"");}
    static PlayerSkin peek(String name){return find(name,false);}
    static PlayerSkin get(String name){return find(name,true);}
    private static PlayerSkin find(String name,boolean request){
        if(!valid(name))return null;
        var client=Minecraft.getInstance();String key=name.toLowerCase(Locale.ROOT);
        if(client.level!=null)for(var p:client.level.players())if(p.getGameProfile().name().equalsIgnoreCase(name))return p.getSkin();
        if(SKINS.containsKey(key))return SKINS.get(key);
        if(request&&!STATUS.containsKey(key))load(name);
        return null;
    }
    static void retry(String name){if(valid(name)){STATUS.remove(name.toLowerCase(Locale.ROOT));get(name);}}
    private static Path cache(String name){return FabricLoader.getInstance().getConfigDir().resolve("mpsq-figure-skins").resolve(name.toLowerCase(Locale.ROOT)+".json");}
    private static void load(String name){
        var client=Minecraft.getInstance();String key=name.toLowerCase(Locale.ROOT);STATUS.put(key,"Skin wird geladen …");
        CompletableFuture.supplyAsync(()->{
            try{
                Path path=cache(name);
                if(Files.isRegularFile(path)&&Files.size(path)<32768){
                    try{
                        var saved=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                        var props=ArrayListMultimap.<String,Property>create();
                        props.put("textures",new Property("textures",saved.get("value").getAsString(),saved.get("signature").getAsString()));
                        return new GameProfile(UUID.fromString(saved.get("uuid").getAsString()),name,new PropertyMap(props));
                    }catch(Exception invalidCache){/* Recover using the profile service. */}
                }
                var response=HTTP.send(HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/"+name)).timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofString());
                if(response.statusCode()!=200||response.body().length()>32768)throw new IllegalArgumentException("Spieler nicht gefunden oder Profildienst nicht erreichbar.");
                String raw=JsonParser.parseString(response.body()).getAsJsonObject().get("id").getAsString();
                if(!raw.matches("[a-fA-F0-9]{32}"))throw new IllegalArgumentException("Ungültiges Spielerprofil.");
                UUID uuid=UUID.fromString(raw.substring(0,8)+"-"+raw.substring(8,12)+"-"+raw.substring(12,16)+"-"+raw.substring(16,20)+"-"+raw.substring(20));
                var result=client.services().sessionService().fetchProfile(uuid,true);
                if(result==null)throw new IllegalArgumentException("Skin-Profil nicht verfügbar.");
                GameProfile profile=result.profile();
                for(Property prop:profile.properties().get("textures"))if(prop.signature()!=null){
                    var saved=new JsonObject();saved.addProperty("uuid",uuid.toString());saved.addProperty("value",prop.value());saved.addProperty("signature",prop.signature());
                    Files.createDirectories(path.getParent());Files.writeString(path,saved.toString());break;
                }
                return profile;
            }catch(Exception e){throw new java.util.concurrent.CompletionException(e);}
        }).whenComplete((profile,error)->client.execute(()->{
            if(error!=null){STATUS.put(key,"Skin nicht verfügbar. Namen und Verbindung prüfen.");return;}
            client.getSkinManager().get(profile).whenComplete((skin,failure)->client.execute(()->{
                if(failure!=null||skin==null||skin.isEmpty()){STATUS.put(key,"Skin konnte nicht geladen werden.");return;}
                SKINS.put(key,skin.get());STATUS.put(key,"Skin geladen.");
            }));
        }));
    }
}
