package de.galaxushd.mpsqcamera;
import com.google.gson.JsonObject;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

/** Reacts only to messages actually sent, never to previewing or copying. */
public final class MpsqTemplateAudio {
    private static List<TeamTemplate> templates=List.of();
    private static long next;
    private static boolean pending;
    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.world==null){templates=List.of();next=0;return;}
            if(pending||System.currentTimeMillis()<next||!MpsqApiClient.isReady())return;
            if(!TeamStateStore.self().map(p->p.baseRank().level()>=TeamRank.OFFICER.level()).orElse(false))return;
            next=System.currentTimeMillis()+30000;pending=true;
            MpsqApiClient.loadTeamTemplates().whenComplete((data,error)->client.execute(()->{pending=false;if(error==null)templates=data;}));
        });
        ClientSendMessageEvents.CHAT.register(message->{
            templates.stream().filter(t->t.text().equals(message)&&!t.sound().isBlank()).findFirst().ifPresent(t->{
                JsonObject data=new JsonObject(),body=new JsonObject();data.addProperty("sound",t.sound());
                body.addProperty("actionType","PLAY_AUDIO");body.add("actionData",data);body.addProperty("serverId",MpsqActionSync.server());body.addProperty("worldId",MpsqActionSync.world());
                MpsqApiClient.post("/actions",body).exceptionally(error->{MpsqCameraClient.LOGGER.debug("Textansage konnte nicht gestartet werden",error);return null;});
            });
        });
    }
}
