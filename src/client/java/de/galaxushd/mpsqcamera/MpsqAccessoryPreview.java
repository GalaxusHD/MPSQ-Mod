package de.galaxushd.mpsqcamera;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import de.galaxushd.mpsqcamera.compat.MultiBufferSource;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Render-only carrier: each extracted GUI state retains its own accessory. */
public final class MpsqAccessoryPreview {
    private static final Map<LivingEntityRenderState,String> STATES=new WeakHashMap<>();
    private static String extracting;
    private static RemotePlayer carrier;

    private MpsqAccessoryPreview() { }

    static void draw(GuiGraphicsExtractor graphics,String url,int x,int y,float size){
        var client=Minecraft.getInstance();
        if(client.level==null)return;
        if(carrier==null||carrier.level()!=client.level){
            carrier=new RemotePlayer(client.level,new GameProfile(UUID.randomUUID(),"MPSQ Hat Preview"));
            carrier.setId(-1999999999);
        }
        int pixels=Math.max(20,Math.min(64,Math.round(20*size)));
        String previous=extracting;
        extracting=url;
        try{
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics,x-pixels/2,y-pixels/2,
                    x+(pixels+1)/2,y+(pixels+1)/2,Math.max(8,Math.round(pixels*.78f)),
                    .5f-carrier.getBbHeight()*.5f,x,y,carrier);
        }finally{extracting=previous;}
    }

    public static void capture(LivingEntityRenderState state){
        STATES.remove(state);
        if(extracting!=null)STATES.put(state,extracting);
    }

    public static boolean render(LivingEntityRenderState state,PoseStack matrices,MultiBufferSource buffers){
        String url=STATES.get(state);
        if(url==null)return false;
        MpsqAccessoryRenderer.renderHatIcon(url,matrices,buffers);
        buffers.endBatch();
        return true;
    }
}
