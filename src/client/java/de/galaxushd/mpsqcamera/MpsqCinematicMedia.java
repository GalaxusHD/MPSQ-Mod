package de.galaxushd.mpsqcamera;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.google.gson.JsonArray;
import net.minecraft.client.Minecraft;
import net.dimaskama.mcef.api.MCEFBrowser;

/** Separate scene channel; does not stop the user's music playlist. */
final class MpsqCinematicMedia {
    private MCEFBrowser browser;
    private long generation,deadline;
    void play(String id,float volume,float pitch,double seconds){
        stop();long request=++generation;deadline=System.nanoTime()+(long)(seconds*1_000_000_000L);
        MpsqApiClient.get("/sounds/"+id).whenComplete((data,error)->Minecraft.getInstance().execute(()->{
            if(request!=generation||System.nanoTime()>=deadline||!TeamVisibilitySettings.inArea())return;
            try{
                if(error!=null)throw new IllegalArgumentException("Sound-ID nicht erreichbar: "+id);
                String url=data.getAsJsonObject().get("url").getAsString();var uri=java.net.URI.create(url);
                if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException("Ungültige Audio-Quelle.");
                if(!CinemaBrowserManager.ensureAudioReady())throw new IllegalArgumentException("Audio-Browser ist noch nicht bereit.");
                JsonArray urls=new JsonArray();urls.add(url);
                String html="<!doctype html><audio id=a autoplay></audio><script>const a=document.getElementById('a');a.volume="+volume+";a.playbackRate="+pitch+";a.preservesPitch=false;a.src="+urls+"[0];a.play().catch(console.error);</script>";
                browser=McefModernBridge.createBrowser("data:text/html;base64,"+Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8)),false);
                CinemaAudioManager.registerBrowser(browser,CinemaAudioManager.AudioRoute.AMBIENT,volume);browser.setFocus(false);browser.resize(64,64);CinemaBrowserManager.requestGameMouseRestore();
            }catch(Exception e){stop();MpsqCameraClient.LOGGER.warn("Kamerafahrt-Audio fehlgeschlagen",e);var p=Minecraft.getInstance().player;if(p!=null)de.galaxushd.mpsqcamera.compat.PlayerMessages.send(p,net.minecraft.network.chat.Component.literal(e.getMessage()),false);}
        }));
    }
    void tick(){if(System.nanoTime()>=deadline||!TeamVisibilitySettings.inArea())stop();}
    void stop(){generation++;if(browser!=null){CinemaAudioManager.unregisterBrowser(browser);McefModernBridge.close(browser);browser=null;}}
}
