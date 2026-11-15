package de.galaxushd.mpsqcamera;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.SimpleParticleType;

/** Per-playback event state. Nothing before the selected start is replayed. */
final class MpsqCinematicEvents {
    private final List<MpsqCinematicScene.Event> events;
    private final Set<String> fired=new HashSet<>();
    private final Map<SoundInstance,Double> sounds=new HashMap<>();
    private final MpsqCinematicMedia media=new MpsqCinematicMedia();
    private final double start;
    private double overlayTick=-1; private boolean titleShown,overlayShown;
    MpsqCinematicEvents(MpsqCinematicScene scene,double start){this.events=new ArrayList<>(scene.events);events.sort(Comparator.comparingDouble(e->e.time));this.start=start;}
    static Component text(String value){return Component.literal(value.replaceAll("&([0-9a-fk-orA-FK-OR])","§$1"));}
    void advance(double time){
        var c=Minecraft.getInstance();if(c.player==null||c.level==null||!TeamVisibilitySettings.inArea()){stop();return;}
        for(var e:events)if(e.time>=start&&e.time<=time&&fired.add(e.id))try{
            switch(e.type){
                case "CHAT" -> de.galaxushd.mpsqcamera.compat.PlayerMessages.send(c.player,text(e.value),false);
                case "TITLE" -> {titleShown=true;c.gui.hud.setTimes(5,(int)(e.duration*20),5);c.gui.hud.setSubtitle(text(e.extra));c.gui.hud.setTitle(text(e.value));}
                case "COMMAND" -> c.player.connection.sendCommand(e.value.startsWith("/")?e.value.substring(1):e.value);
                case "SOUND" -> {var id=Identifier.tryParse(e.value);if(id==null||c.getSoundManager().getSoundEvent(id)==null)throw new IllegalArgumentException("Sound fehlt: "+e.value);
                    var sound=new SimpleSoundInstance(id,SoundSource.AMBIENT,e.volume,e.pitch,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,0,0,0,true);sounds.put(sound,e.time+e.duration);c.getSoundManager().play(sound);}
                case "MEDIA" -> {String local=MpsqBuiltinMusic.sound(e.value);if(local==null)media.play(e.value,e.volume,e.pitch,Math.max(0.1,e.time+e.duration-time));else{
                    var id=Identifier.parse(local);if(c.getSoundManager().getSoundEvent(id)==null)throw new IllegalArgumentException("Lokale Musik fehlt: "+e.value);
                    var sound=new SimpleSoundInstance(id,SoundSource.AMBIENT,e.volume,e.pitch,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,0,0,0,true);sounds.put(sound,e.time+e.duration);c.getSoundManager().play(sound);
                }}
                case "PARTICLE" -> {var id=Identifier.tryParse(e.value);if(id==null||!(BuiltInRegistries.PARTICLE_TYPE.getValue(id) instanceof SimpleParticleType particle))throw new IllegalArgumentException("Partikel nicht unterstützt: "+e.value);
                    int count=e.extra.isBlank()?20:Integer.parseInt(e.extra);for(int i=0;i<Math.min(200,Math.max(1,count));i++)c.level.addParticle(particle,e.x+(Math.random()-.5),e.y+Math.random(),e.z+(Math.random()-.5),0,.03,0);}
            }
        }catch(Exception error){MpsqCameraClient.LOGGER.warn("Kamerafahrt-Ereignis {} fehlgeschlagen",e.type,error);de.galaxushd.mpsqcamera.compat.PlayerMessages.send(c.player,Component.literal("Kamerafahrt: "+error.getMessage()),false);}
        if(time-overlayTick>=.25){overlayTick=time;boolean shown=false;for(var e:events)if(e.type.equals("ACTIONBAR")&&e.time>=start&&time>=e.time&&time<e.time+e.duration){shown=true;de.galaxushd.mpsqcamera.compat.PlayerMessages.send(c.player,text(e.value),true);}if(overlayShown&&!shown)c.gui.hud.setOverlayMessage(Component.empty(),false);overlayShown=shown;}
        sounds.entrySet().removeIf(entry->{if(time>=entry.getValue()){c.getSoundManager().stop(entry.getKey());return true;}return false;});
        media.tick();
    }
    Component dialogue(double time){for(int i=events.size()-1;i>=0;i--){var e=events.get(i);if(e.type.equals("DIALOGUE")&&time>=e.time&&time<e.time+e.duration)return text((e.extra.isBlank()?"":e.extra+": ")+e.value);}return Component.empty();}
    int fade(double time){double opacity=0;for(var e:events)if(e.type.equals("FADE")&&e.time>=start&&time>=e.time&&time<e.time+e.duration){double t=(time-e.time)/e.duration;opacity=Math.max(opacity,1-Math.abs(2*t-1));}return (int)(opacity*255);}
    void stop(){var c=Minecraft.getInstance();for(var sound:sounds.keySet())c.getSoundManager().stop(sound);sounds.clear();media.stop();if(titleShown)c.gui.hud.clearTitles();if(overlayShown)c.gui.hud.setOverlayMessage(Component.empty(),false);titleShown=false;overlayShown=false;}
}
