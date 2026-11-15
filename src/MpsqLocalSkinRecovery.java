package de.galaxushd.mpsqcamera;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.google.common.collect.ArrayListMultimap;
import com.google.gson.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Recover only the local account's default fallback after a profile-service timeout. */
public final class MpsqLocalSkinRecovery {
    private static UUID account;
    private static PlayerSkin recovered;
    private static boolean pending;
    private static int attempts;
    private static long retryAt;
    private MpsqLocalSkinRecovery() {}
    public static void initialize() { ClientTickEvents.END_CLIENT_TICK.register(MpsqLocalSkinRecovery::tick); }
    public static boolean isDefault(GameProfile profile, PlayerSkin skin) {
        return skin == null || skin.body().texturePath().equals(DefaultPlayerSkin.get(profile).body().texturePath());
    }
    public static PlayerSkin recover(GameProfile profile, PlayerSkin original) {
        var c=Minecraft.getInstance();
        return c.isLocalServer() && c.player!=null && c.player.getUUID().equals(profile.id())
            && profile.id().equals(account) && recovered!=null && isDefault(profile,original) ? recovered : original;
    }
    private static Path cache(UUID id) { return FabricLoader.getInstance().getConfigDir().resolve("mpsq-local-skins").resolve(id+".json"); }
    private static void tick(Minecraft c) {
        if(!c.isLocalServer()||c.player==null)return;
        var profile=c.player.getGameProfile();
        if(!profile.id().equals(account)) {
            account=profile.id();recovered=null;pending=false;attempts=0;retryAt=System.currentTimeMillis()+5000;
            var saved=read(profile);
            if(saved!=null)load(c,saved,false);
            if(!profile.properties().get("textures").isEmpty())load(c,profile,true);
        }
        if(!isDefault(profile,c.player.getSkin()))return;
        long now=System.currentTimeMillis();
        if(pending||attempts>=3||now<retryAt)return;
        pending=true;attempts++;retryAt=now+30000;
        UUID requested=account;
        // No networking on the rendering thread. A bounded retry avoids continuous polling.
        CompletableFuture.supplyAsync(()->c.services().sessionService().fetchProfile(requested,true))
            .whenComplete((result,error)->c.execute(()->{
                if(!requested.equals(account))return;
                pending=false;
                if(error!=null||result==null)return;
                load(c,result.profile(),true);
            }));
    }
    private static void load(Minecraft c,GameProfile profile,boolean save) {
        if(profile.properties().get("textures").isEmpty())return;
        c.getSkinManager().get(profile).whenComplete((skin,error)->c.execute(()->{
            if(!profile.id().equals(account)||error!=null||skin==null||skin.isEmpty()||isDefault(profile,skin.get()))return;
            recovered=skin.get();
            if(save)CompletableFuture.runAsync(()->write(profile));
        }));
    }
    private static GameProfile read(GameProfile profile) {
        try {
            Path file=cache(profile.id());if(!Files.isRegularFile(file)||Files.size(file)>32768)return null;
            JsonObject json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if(!profile.id().toString().equals(json.get("uuid").getAsString()))return null;
            var map=ArrayListMultimap.<String,Property>create();
            map.put("textures",new Property("textures",json.get("value").getAsString(),json.get("signature").getAsString()));
            return new GameProfile(profile.id(),profile.name(),new PropertyMap(map));
        } catch(Exception ignored){return null;}
    }
    private static void write(GameProfile profile) {
        try {
            Property property=profile.properties().get("textures").iterator().next();
            if(property.signature()==null)return;
            var json=new JsonObject();json.addProperty("uuid",profile.id().toString());json.addProperty("value",property.value());json.addProperty("signature",property.signature());
            Path file=cache(profile.id());Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");
            Files.writeString(tmp,json.toString());Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception e){MpsqCameraClient.LOGGER.debug("Skin-Profil konnte nicht lokal gespeichert werden",e);}
    }
}
