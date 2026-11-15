package de.galaxushd.mpsqcamera;

import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerSkin;
import java.lang.reflect.Method;
import java.util.*;

/** Optional Skin Layers bridge. Proxies supply skin data only and never enter the world. */
final class MpsqSkinLayersCompat {
    private static final boolean INSTALLED=FabricLoader.getInstance().isModLoaded("skinlayers3d");
    private record Key(PlayerSkin skin,double x,double y,double z) {}
    private static final Map<Key,RemotePlayer> PROXIES=new LinkedHashMap<>(16,.75f,true);
    private static Object world;
    private static Method attach;
    private static boolean failed;
    static void position(AvatarRenderState state,double x,double y,double z) {
        if(!INSTALLED||failed)return;
        var c=Minecraft.getInstance();if(c.level==null)return;
        try {
            if(world!=c.level){clear();world=c.level;}
            if(attach==null)attach=Class.forName("dev.tr7zw.transition.mc.entitywrapper.EntityRenderStateExtender").getMethod("setTransitionEntity",Entity.class);
            if(!attach.getDeclaringClass().isInstance(state))return;
            PlayerSkin skin=state.skin;
            var proxy=PROXIES.computeIfAbsent(new Key(skin,x,y,z),s->{
                var p=new RemotePlayer(c.level,new GameProfile(UUID.randomUUID(),"MPSQ Skin NPC")){
                    @Override public PlayerSkin getSkin(){return s.skin();}
                };
                p.setId(-1900000000-PROXIES.size());return p;
            });
            if(PROXIES.size()>256)PROXIES.remove(PROXIES.keySet().iterator().next());
            proxy.setPos(x,y,z);attach.invoke(state,proxy);
        }catch(ReflectiveOperationException|LinkageError e){failed=true;MpsqCameraClient.LOGGER.warn("3D Skin Layers: inkompatible Schnittstelle; normale NPC-Skins bleiben aktiv",e);}
    }
    static void clear(){PROXIES.clear();world=null;}
}
