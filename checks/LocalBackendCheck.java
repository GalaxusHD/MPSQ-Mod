import de.galaxushd.mpsqcamera.*;
import com.google.gson.*;
import java.nio.file.*;
import java.lang.reflect.*;

public class LocalBackendCheck {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static JsonElement call(String verb,String path,JsonObject body){return LocalMpsqBackend.request(verb,path,body).join();}
    public static void main(String[] args)throws Exception{
        Path config=Files.createTempDirectory("mpsq-local-check-");
        var loader=net.fabricmc.loader.api.FabricLoader.getInstance();
        Field cf=loader.getClass().getDeclaredField("configDir");cf.setAccessible(true);cf.set(loader,config);
        LocalMpsqBackend.initialize().join();
        check(call("GET","/team/me",null).getAsJsonObject().get("base_rank").getAsString().equals("sr_offizier"),"Default local rank");
        JsonObject body=new JsonObject();body.addProperty("mode","MPSQ_REDSTONE");body.addProperty("name","Local screen");
        JsonObject p=new JsonObject();p.addProperty("x",1);p.addProperty("y",2);p.addProperty("z",3);body.add("pos1",p);body.add("pos2",p.deepCopy());
        var screen=call("POST","/screens",body).getAsJsonObject();String id=screen.get("id").getAsString();
        check(screen.get("mode").getAsString().equals("REDSTONE"),"Redstone mode retained");
        check(screen.get("activation_code").getAsString().length()==6,"Six digit code");
        check(call("GET","/screens?server=&world=minecraft:overworld",null).getAsJsonArray().size()==1,"Queries accepted locally");
        JsonObject change=new JsonObject();change.addProperty("name","Updated");call("PATCH","/screens/"+id,change);
        Field data=LocalMpsqBackend.class.getDeclaredField("data");data.setAccessible(true);data.set(null,null);
        check(call("GET","/screens",null).getAsJsonArray().get(0).getAsJsonObject().get("name").getAsString().equals("Updated"),"Screen survives reload");
        JsonObject rank=new JsonObject();rank.addProperty("rank","001");call("POST","/team/me/event-rank",rank);
        check(call("GET","/team/me",null).getAsJsonObject().get("base_rank").getAsString().equals("sr_offizier"),"Temporary rank preserves permissions");
        check(call("GET","/team/me",null).getAsJsonObject().get("active_rank").getAsString().equals("001"),"Temporary rank displayed");
        call("DELETE","/team/me/event-rank",null);call("DELETE","/screens/"+id,null);data.set(null,null);
        check(call("GET","/screens",null).getAsJsonArray().isEmpty(),"Deletion persists");
        JsonObject todo=new JsonObject();todo.addProperty("text","Test");todo.addProperty("listKey","arbeiter");call("POST","/team/todos",todo);data.set(null,null);
        check(call("GET","/team/todos",null).getAsJsonArray().size()==1,"Todo persists");
        check(call("GET","/team/rank-log?offset=0",null).isJsonArray(),"Local log list");
        try{call("POST","/redeem",new JsonObject());throw new AssertionError("Remote redemption accepted");}catch(java.util.concurrent.CompletionException expected){checks++;}
        Field file=LocalMpsqBackend.class.getDeclaredField("file");file.setAccessible(true);Path saved=(Path)file.get(null);
        Files.writeString(saved,"broken");data.set(null,null);
        try{call("GET","/screens",null);throw new AssertionError("Corruption silently discarded");}catch(java.util.concurrent.CompletionException expected){checks++;}
        check(Files.readString(saved).equals("broken"),"Corrupt file preserved");
        System.out.println("PASS: "+checks+" local backend checks");
    }
}
