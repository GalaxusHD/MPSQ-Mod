package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Head accessories rendered only for players actually visible in the current world. */
public final class MpsqAccessoryRenderer {
    private record Model(JsonArray elements,Map<String,Identifier> textures){}
    private static final Map<String,Model> models=new HashMap<>();
    private static final Map<String,String> wearers=new HashMap<>();
    private static JsonArray objects=new JsonArray();
    private static final Set<String> loading=new HashSet<>();
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static long next;
    private static boolean polling;
    private static int generation;
    private static String scope="";
    private MpsqAccessoryRenderer(){}
    public static void refresh(){next=0;}
    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            String current=MpsqActionSync.server()+"|"+MpsqActionSync.world();
            if(!scope.equals(current)){scope=current;generation++;polling=false;wearers.clear();objects=new JsonArray();loading.clear();for(Model m:models.values())for(Identifier id:m.textures.values())client.getTextureManager().destroyTexture(id);models.clear();next=0;}
            if(client.world==null||!MpsqApiClient.isReady()||polling||System.currentTimeMillis()<next)return;
            polling=true;next=System.currentTimeMillis()+15000;int epoch=generation;
            MpsqApiClient.get("/objects?server="+java.net.URLEncoder.encode(MpsqActionSync.server(),java.nio.charset.StandardCharsets.UTF_8)+"&world="+java.net.URLEncoder.encode(MpsqActionSync.world(),java.nio.charset.StandardCharsets.UTF_8)).whenComplete((data,error)->client.execute(()->{
                if(epoch!=generation||error!=null)return;objects=data.getAsJsonArray();
                for(var value:objects){var o=value.getAsJsonObject();String url=o.get("url").getAsString();if(!models.containsKey(url)&&models.size()+loading.size()<16)load(url,epoch);}
            }));
            MpsqApiClient.get("/accessory-wearers").whenComplete((data,error)->client.execute(()->{
                if(epoch!=generation)return;polling=false;
                if(error!=null)return;
                wearers.clear();
                for(JsonElement value:data.getAsJsonArray()){
                    JsonObject row=value.getAsJsonObject();String name=row.get("name").getAsString().toLowerCase(Locale.ROOT), url=row.get("url").getAsString();
                    wearers.put(name,url);
                }
                // Load only assets worn by players in this world, limiting texture memory.
                if(client.world!=null)for(var player:client.world.getPlayers()){
                    String url=wearers.get(player.getName().getString().toLowerCase(Locale.ROOT));
                    if(url!=null&&!models.containsKey(url)&&models.size()+loading.size()<16)load(url,epoch);
                }
            }));
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context->{
            var client=MinecraftClient.getInstance();var matrices=context.matrixStack();var consumers=context.consumers();
            if(client.world==null||matrices==null||consumers==null)return;
            var camera=context.camera().getPos();
            for(var value:objects){var o=value.getAsJsonObject();Model model=models.get(o.get("url").getAsString());if(model==null)continue;
                double x=o.get("x").getAsDouble(),y=o.get("y").getAsDouble(),z=o.get("z").getAsDouble();if(camera.squaredDistanceTo(x,y,z)>4096)continue;
                matrices.push();matrices.translate(x+0.5-camera.x,y-camera.y,z+0.5-camera.z);matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(o.get("rotation").getAsFloat()));matrices.translate(-0.5,0,-0.5);matrices.scale(1f/16,1f/16,1f/16);drawModel(model,matrices,consumers);matrices.pop();
            }
            for(var player:client.world.getPlayers()){
                if(player.isInvisible()||player.isSpectator()||(player==client.player&&client.options.getPerspective().isFirstPerson()))continue;
                if(player.squaredDistanceTo(camera)>4096)continue;
                Model model=models.get(wearers.get(player.getName().getString().toLowerCase(Locale.ROOT)));if(model==null)continue;
                matrices.push();
                matrices.translate(player.getX()-camera.x,player.getY()+player.getStandingEyeHeight()-camera.y,player.getZ()-camera.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-player.getHeadYaw()));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(player.getPitch()));
                matrices.translate(-0.5,-0.25,-0.5);matrices.scale(1f/16,1f/16,1f/16);
                drawModel(model,matrices,consumers);
                matrices.pop();
            }
        });
    }
    private static void drawModel(Model model,MatrixStack matrices,VertexConsumerProvider consumers){
                for(JsonElement value:model.elements){
                    JsonObject e=value.getAsJsonObject();float[] from=vec(e,"from"),to=vec(e,"to"),origin=vec(e,"origin"),rotation=vec(e,"rotation");
                    matrices.push();matrices.translate(origin[0],origin[1],origin[2]);
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation[2]));matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation[1]));matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation[0]));
                    matrices.translate(-origin[0],-origin[1],-origin[2]);
                    for(var face:e.getAsJsonObject("faces").entrySet()){
                        JsonObject f=face.getValue().getAsJsonObject();Identifier texture=model.textures.get(f.get("texture").getAsString());if(texture==null)continue;
                        float[][] points=points(face.getKey(),from,to);if(points==null)continue;
                        var buffer=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
                        JsonArray uv=f.getAsJsonArray("uv");float u0=uv.get(0).getAsFloat(),v0=uv.get(1).getAsFloat(),u1=uv.get(2).getAsFloat(),v1=uv.get(3).getAsFloat();
                        float[][] tex={{u0,v1},{u1,v1},{u1,v0},{u0,v0}};
                        int turn=f.has("rotation")?Math.floorMod(f.get("rotation").getAsInt()/90,4):0;
                        for(int i=0;i<4;i++){float[] p=points[i], t=tex[(i+turn)%4];buffer.vertex(matrices.peek(),p[0],p[1],p[2]).color(255,255,255,255).texture(t[0],t[1]).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,1,0);}
                    }
                    matrices.pop();
                }
    }
    private static float[] vec(JsonObject e,String key){JsonArray a=e.getAsJsonArray(key);return new float[]{a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()};}
    private static float[][] points(String side,float[] a,float[] b){float x=a[0],y=a[1],z=a[2],X=b[0],Y=b[1],Z=b[2];return switch(side){
        case "north"->new float[][]{{X,y,z},{x,y,z},{x,Y,z},{X,Y,z}};
        case "south"->new float[][]{{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}};
        case "east"->new float[][]{{X,y,Z},{X,y,z},{X,Y,z},{X,Y,Z}};
        case "west"->new float[][]{{x,y,z},{x,y,Z},{x,Y,Z},{x,Y,z}};
        case "up"->new float[][]{{x,Y,Z},{X,Y,Z},{X,Y,z},{x,Y,z}};
        case "down"->new float[][]{{x,y,z},{X,y,z},{X,y,Z},{x,y,Z}};
        default->null;};}
    private static void load(String url,int epoch){
        if(!loading.add(url))return;
        CompletableFuture.supplyAsync(()->{
            try{
                URI uri=URI.create(url), api=URI.create(MpsqApiClient.API_URL);
                if(!"https".equals(uri.getScheme())||!api.getHost().equals(uri.getHost()))throw new IOException("Unzulässige Modellquelle");
                var response=HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
                try(InputStream stream=response.body()){
                    if(response.statusCode()!=200)throw new IOException("Modell nicht verfügbar");
                    byte[] bytes=stream.readNBytes(12000001);if(bytes.length>12000000)throw new IOException("Modell zu groß");
                    return JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                }
            }catch(Exception e){throw new java.util.concurrent.CompletionException(e);}
        }).whenComplete((bundle,error)->MinecraftClient.getInstance().execute(()->{
            if(epoch!=generation)return;loading.remove(url);if(error!=null){MpsqCameraClient.LOGGER.debug("Accessoire konnte nicht geladen werden",error);return;}
            Map<String,Identifier> textures=new HashMap<>();
            try{
                if(bundle.getAsJsonArray("elements").size()>512||bundle.getAsJsonObject("textures").size()>32)throw new IOException("Modellgrenze überschritten");
                long pixels=0;
                for(var entry:bundle.getAsJsonObject("textures").entrySet()){
                    String data=entry.getValue().getAsString();byte[] png=Base64.getDecoder().decode(data.substring(data.indexOf(',')+1));
                    if(png.length<24||png.length>2250000)throw new IOException("Ungültige PNG");
                    var header=java.nio.ByteBuffer.wrap(png);int w=header.getInt(16),h=header.getInt(20);if(w<1||h<1||w>1024||h>1024)throw new IOException("Textur maximal 1024 × 1024");
                    pixels+=(long)w*h;if(pixels>2097152)throw new IOException("Texturpaket zu groß");
                    NativeImage image=NativeImage.read(new ByteArrayInputStream(png));Identifier id=Identifier.of(MpsqCameraClient.MOD_ID,"accessory/"+UUID.randomUUID());
                    var texture=new NativeImageBackedTexture(()->"MPSQ Accessoire",image);MinecraftClient.getInstance().getTextureManager().registerTexture(id,texture);texture.upload();textures.put(entry.getKey(),id);
                }
                models.put(url,new Model(bundle.getAsJsonArray("elements"),textures));
            }catch(Exception e){for(Identifier id:textures.values())MinecraftClient.getInstance().getTextureManager().destroyTexture(id);MpsqCameraClient.LOGGER.warn("Accessoire-Modell ungültig",e);}
        }));
    }
}
