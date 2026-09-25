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
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Head accessories rendered only for players actually visible in the current world. */
public final class MpsqAccessoryRenderer {
    private record Model(JsonArray elements,JsonArray meshes,Map<String,Identifier> textures){}
    private static final Map<String,Model> models=new HashMap<>();
    private static final Map<String,String> wearers=new HashMap<>();
    private static JsonArray objects=new JsonArray();
    private static JsonArray npcs=new JsonArray();
    private static final Set<String> loading=new HashSet<>();
    private static final Map<String,String> localAssetUrls=new HashMap<>();
    private static boolean localCatalogRequested;
    private static String tryOnUrl;
    private static net.minecraft.util.math.Vec3d tryOnStart;
    private static final java.util.BitSet pressedKeys=new java.util.BitSet();
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static long next;
    private static boolean polling;
    private static int generation;
    private static String scope="";
    private MpsqAccessoryRenderer(){}
    public static void refresh(){generation++;polling=false;loading.clear();localAssetUrls.clear();localCatalogRequested=false;next=0;}
    public static JsonArray npcsSnapshot(){return npcs.deepCopy();}
    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(tryOnUrl!=null&&client.player!=null){
                if(tryOnStart==null)tryOnStart=client.player.getPos();
                if(client.player.getPos().squaredDistanceTo(tryOnStart)>0.0004){clearTryOn();}
                else {long window=client.getWindow().getHandle();for(int key=1;key<=org.lwjgl.glfw.GLFW.GLFW_KEY_LAST;key++){boolean down=org.lwjgl.glfw.GLFW.glfwGetKey(window,key)==org.lwjgl.glfw.GLFW.GLFW_PRESS;if(down&&!pressedKeys.get(key)&&key!=org.lwjgl.glfw.GLFW.GLFW_KEY_F5){clearTryOn();break;}if(down)pressedKeys.set(key);else pressedKeys.clear(key);}}
            }
            String current=MpsqActionSync.server()+"|"+MpsqActionSync.world();
            if(!scope.equals(current)){scope=current;generation++;polling=false;wearers.clear();objects=new JsonArray();npcs=new JsonArray();loading.clear();localAssetUrls.clear();localCatalogRequested=false;for(Model m:models.values())for(Identifier id:m.textures.values())client.getTextureManager().destroyTexture(id);models.clear();next=0;}
            if(client.world==null||!MpsqApiClient.isReady()||polling||System.currentTimeMillis()<next)return;
            polling=true;next=System.currentTimeMillis()+15000;int epoch=generation;
            if(MpsqActionSync.server().isBlank()){
                objects=localObjectsSnapshot(epoch);
                npcs=localNpcsSnapshot();
                if(!localCatalogRequested){localCatalogRequested=true;MpsqApiClient.get("/furniture/catalog").whenComplete((data,error)->client.execute(()->{
                    if(epoch!=generation)return;
                    if(error!=null||!data.isJsonArray()){localCatalogRequested=false;return;}
                    for(JsonElement value:data.getAsJsonArray()){JsonObject asset=value.getAsJsonObject();if(asset.has("id")&&asset.has("url"))localAssetUrls.put(asset.get("id").getAsString(),asset.get("url").getAsString());}
                    objects=localObjectsSnapshot(epoch);
                    for(JsonElement value:objects){JsonObject row=value.getAsJsonObject();String url=row.get("url").getAsString();if(!models.containsKey(url)&&models.size()+loading.size()<64)load(url,epoch);}
                }));}
                MpsqApiClient.get("/models/catalog").whenComplete((data,error)->client.execute(()->{
                    if(epoch!=generation)return;
                    if(error!=null||!data.isJsonArray())return;
                    for(JsonElement value:data.getAsJsonArray()){JsonObject asset=value.getAsJsonObject();if(asset.has("id")&&asset.has("url"))localAssetUrls.put(asset.get("id").getAsString(),asset.get("url").getAsString());}
                    npcs=localNpcsSnapshot();
                    for(JsonElement value:npcs){JsonObject npc=value.getAsJsonObject();String url=npc.get("url").getAsString();if(!models.containsKey(url)&&models.size()+loading.size()<64)load(url,epoch,"npc_skin_slim".equals(str(npc,"category","")));}
                }));
                for(JsonElement value:objects){JsonObject row=value.getAsJsonObject();if(row.has("url")&&!row.get("url").isJsonNull()){String url=row.get("url").getAsString();if(!models.containsKey(url)&&models.size()+loading.size()<64)load(url,epoch);}}
            }else{
                MpsqApiClient.get("/objects?server="+java.net.URLEncoder.encode(MpsqActionSync.server(),java.nio.charset.StandardCharsets.UTF_8)+"&world="+java.net.URLEncoder.encode(MpsqActionSync.world(),java.nio.charset.StandardCharsets.UTF_8)).whenComplete((data,error)->client.execute(()->{
                    if(epoch!=generation||error!=null)return;objects=data.getAsJsonArray();
                    for(var value:objects){var o=value.getAsJsonObject();String url=o.get("url").getAsString();if(!models.containsKey(url)&&models.size()+loading.size()<64)load(url,epoch);}
                }));
                MpsqApiClient.get("/npcs?server="+java.net.URLEncoder.encode(MpsqActionSync.server(),java.nio.charset.StandardCharsets.UTF_8)+"&world="+java.net.URLEncoder.encode(MpsqActionSync.world(),java.nio.charset.StandardCharsets.UTF_8)).whenComplete((data,error)->client.execute(()->{
                    if(epoch!=generation||error!=null||!data.isJsonArray())return;npcs=data.getAsJsonArray();
                    for(JsonElement value:npcs){JsonObject npc=value.getAsJsonObject();if(npc.has("url")&&!npc.get("url").isJsonNull()){String url=npc.get("url").getAsString();if(!models.containsKey(url)&&models.size()+loading.size()<64)load(url,epoch,"npc_skin_slim".equals(str(npc,"category","")));}}
                }));
            }
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
                    if(url!=null&&!models.containsKey(url)&&models.size()+loading.size()<64)load(url,epoch);
                }
            }));
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context->{
            var client=MinecraftClient.getInstance();var matrices=context.matrixStack();var consumers=context.consumers();
            if(client.world==null||matrices==null||consumers==null)return;
            var camera=context.camera().getPos();
            for(var value:objects){var o=value.getAsJsonObject();Model model=models.get(o.get("url").getAsString());if(model==null)continue;
                double x=o.get("x").getAsDouble(),y=o.get("y").getAsDouble(),z=o.get("z").getAsDouble();if(camera.squaredDistanceTo(x,y,z)>4096)continue;
                matrices.push();matrices.translate(x+0.5-camera.x,y-camera.y,z+0.5-camera.z);matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(o.get("rotation").getAsFloat()));matrices.translate(-0.5,0,-0.5);matrices.scale(1f/16,1f/16,1f/16);drawModel(model,matrices,consumers,0xFFFFFFFF);matrices.pop();
            }
            for(var player:client.world.getPlayers()){
                if(player.isInvisible()||player.isSpectator()||(player==client.player&&client.options.getPerspective().isFirstPerson()))continue;
                if(player.squaredDistanceTo(camera)>4096)continue;
                String wornUrl=player==client.player&&tryOnUrl!=null?tryOnUrl:wearers.get(player.getName().getString().toLowerCase(Locale.ROOT));
                Model model=models.get(wornUrl);if(model==null)continue;
                matrices.push();
                matrices.translate(player.getX()-camera.x,player.getY()+player.getStandingEyeHeight()-camera.y,player.getZ()-camera.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-player.getHeadYaw()));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(player.getPitch()));
                matrices.translate(-0.5,-0.25,-0.5);matrices.scale(1f/16,1f/16,1f/16);
                drawModel(model,matrices,consumers,0xFFFFFFFF);
                matrices.pop();
            }
            for(var value:npcs){var o=value.getAsJsonObject();if(!o.has("url")||o.get("url").isJsonNull())continue;Model model=models.get(o.get("url").getAsString());if(model==null)continue;
                double x=o.has("world_x")?o.get("world_x").getAsDouble():o.get("x").getAsDouble()+0.5,y=o.has("world_y")?o.get("world_y").getAsDouble():o.get("y").getAsDouble(),z=o.has("world_z")?o.get("world_z").getAsDouble():o.get("z").getAsDouble()+0.5;if(camera.squaredDistanceTo(x,y,z)>4096)continue;
                float size=o.has("scale")?o.get("scale").getAsFloat():1f;String animation=o.has("animation")?o.get("animation").getAsString():"none";float phase=(System.currentTimeMillis()%4000L)/1000f;float bob=animation.equals("bob")?(float)Math.sin(phase*Math.PI*2)*0.08f:0;float pulse=animation.equals("pulse")?1f+(float)Math.sin(phase*Math.PI*2)*0.08f:1f;float yaw=o.has("yaw")?o.get("yaw").getAsFloat():0f,pitch=o.has("pitch")?o.get("pitch").getAsFloat():0f;boolean face=o.has("face_player")&&o.get("face_player").getAsBoolean();
                if(face&&client.player!=null&&client.player.squaredDistanceTo(x,y+0.5,z)<=900){double dx=client.player.getX()-x,dz=client.player.getZ()-z,dy=client.player.getEyeY()-(y+0.5);yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)));}
                if(animation.equals("turn"))yaw+=phase*90f;int tint=glowColor(o.has("glow_color")?o.get("glow_color").getAsString():"none");
                matrices.push();matrices.translate(x-camera.x,y+bob-camera.y,z-camera.z);matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));matrices.scale(size/16f*pulse,size/16f*pulse,size/16f*pulse);drawModel(model,matrices,consumers,tint);matrices.pop();
                String task=o.has("task_type")?o.get("task_type").getAsString():"none";boolean tutorialDone=o.has("tutorial_completed")&&o.get("tutorial_completed").getAsBoolean();
                String tag=switch(task){case "accessories"->"accessories";case "quest"->"quests";case "tutorial"->tutorialDone?null:"tutorial";default->null;};
                if(tag!=null){drawBillboard(context,matrices,consumers,Identifier.of("mpsqcamera","textures/gui/npc_tags/"+tag+".png"),x-camera.x,y+size+bob+0.12-camera.y,z-camera.z,1.65f,0.20f);
                    if("tutorial".equals(tag)){float hover=(float)Math.sin(System.currentTimeMillis()/360.0)*0.07f;drawBillboard(context,matrices,consumers,Identifier.of("mpsqcamera","textures/gui/npc_tags/tutorial_exclamation.png"),x-camera.x,y+size+bob+0.48f+hover-camera.y,z-camera.z,0.42f,0.42f);}}
            }
        });
    }
    public static void tryOn(String url){tryOnUrl=url;tryOnStart=MinecraftClient.getInstance().player==null?null:MinecraftClient.getInstance().player.getPos();pressedKeys.clear();if(url!=null&&MinecraftClient.getInstance().player!=null)MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.literal("Vorschau aktiv · Bewegung oder eine Taste beendet sie (F5 bleibt erlaubt)."),true);}
    private static void clearTryOn(){tryOnUrl=null;tryOnStart=null;pressedKeys.clear();}
    /** Shows an actual model face in catalog rows instead of shrinking the entire atlas. */
    public static void drawGuiPreview(net.minecraft.client.gui.DrawContext context,String url,int x,int y,float size){Model model=models.get(url);if(model==null){if(url!=null&&MpsqApiClient.isReady()&&models.size()+loading.size()<64)load(url,generation);context.fill(x-10,y-10,x+10,y+10,0xAA222222);return;}if(!model.textures.isEmpty()){Identifier texture=model.textures.values().iterator().next();float u0=0,v0=0,u1=1,v1=1;outer:for(JsonElement element:model.elements){JsonObject faces=element.getAsJsonObject().getAsJsonObject("faces");for(var face:faces.entrySet()){JsonObject data=face.getValue().getAsJsonObject();Identifier faceTexture=model.textures.get(data.get("texture").getAsString());if(faceTexture==null)continue;texture=faceTexture;JsonArray uv=data.getAsJsonArray("uv");u0=uv.get(0).getAsFloat();v0=uv.get(1).getAsFloat();u1=uv.get(2).getAsFloat();v1=uv.get(3).getAsFloat();break outer;}}context.fill(x-11,y-11,x+11,y+11,0xFF151515);context.drawTexturedQuad(texture,x-10,y-10,20,20,u0,v0,u1,v1);}}
    private static JsonArray localObjectsSnapshot(int epoch){
        JsonArray resolved=new JsonArray();
        for(JsonElement value:MpsqLocalObjectStore.loadWorld(MpsqActionSync.world())){
            if(!value.isJsonObject())continue;JsonObject source=value.getAsJsonObject();String id=source.has("model_id")?source.get("model_id").getAsString():"";String url=localAssetUrls.get(id);if(url==null)continue;
            JsonObject row=source.deepCopy();row.addProperty("url",url);if(!row.has("rotation"))row.addProperty("rotation",0);resolved.add(row);
        }
        return resolved;
    }
    private static JsonArray localNpcsSnapshot(){JsonArray resolved=new JsonArray();for(JsonElement value:MpsqLocalNpcStore.loadWorld(MpsqActionSync.world())){JsonObject row=value.getAsJsonObject().deepCopy();String id=row.has("asset_id")?row.get("asset_id").getAsString():"";String url=localAssetUrls.get(id);if(url==null)continue;row.addProperty("url",url);row.addProperty("asset_id",id);resolved.add(row);}return resolved;}
    private static int glowColor(String color){return switch(color){case "white"->0xFFFFFFFF;case "orange"->0xFFFFAA33;case "magenta"->0xFFFF55FF;case "light_blue"->0xFF55AAFF;case "yellow"->0xFFFFFF55;case "lime"->0xFF55FF55;case "pink"->0xFFFF88BB;case "gray"->0xFF666666;case "light_gray"->0xFFBBBBBB;case "cyan"->0xFF55FFFF;case "purple"->0xFFAA55FF;case "blue"->0xFF5555FF;case "brown"->0xFF8B5A2B;case "green"->0xFF55AA33;case "red"->0xFFFF5555;case "black"->0xFF333333;default->0xFFFFFFFF;};}
    private static void drawBillboard(WorldRenderContext context,MatrixStack matrices,VertexConsumerProvider consumers,Identifier texture,double x,double y,double z,float width,float height){
        matrices.push();matrices.translate(x,y,z);matrices.multiply(context.camera().getRotation());var entry=matrices.peek();var buffer=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        buffer.vertex(entry,-width/2,-height/2,0).color(255,255,255,255).texture(0,1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,0,1);
        buffer.vertex(entry,width/2,-height/2,0).color(255,255,255,255).texture(1,1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,0,1);
        buffer.vertex(entry,width/2,height/2,0).color(255,255,255,255).texture(1,0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,0,1);
        buffer.vertex(entry,-width/2,height/2,0).color(255,255,255,255).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,0,1);matrices.pop();
    }
    private static void drawModel(Model model,MatrixStack matrices,VertexConsumerProvider consumers,int tint){
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
                        for(int i=0;i<4;i++){float[] p=points[i], t=tex[(i+turn)%4];buffer.vertex(matrices.peek(),p[0],p[1],p[2]).color((tint>>16)&255,(tint>>8)&255,tint&255,(tint>>>24)&255).texture(t[0],t[1]).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,1,0);}
                    }
                    matrices.pop();
                }
                for(JsonElement value:model.meshes){
                    JsonObject mesh=value.getAsJsonObject();Identifier texture=model.textures.get(mesh.get("texture").getAsString());if(texture==null)continue;
                    JsonArray vertices=mesh.getAsJsonArray("vertices"),indices=mesh.getAsJsonArray("indices");var buffer=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
                    for(int i=0;i+2<indices.size();i+=3)for(int k=0;k<3;k++){
                        JsonArray v=vertices.get(indices.get(i+k).getAsInt()).getAsJsonArray();
                        buffer.vertex(matrices.peek(),v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat()).color((tint>>16)&255,(tint>>8)&255,tint&255,(tint>>>24)&255).texture(v.get(3).getAsFloat(),v.get(4).getAsFloat()).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,1,0);
                    }
                }
    }
    private static float[] vec(JsonObject e,String key){JsonArray a=e.getAsJsonArray(key);return new float[]{a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()};}
    private static JsonObject playerSkinBundle(byte[] png,boolean slim){
        JsonObject bundle=new JsonObject();JsonArray elements=new JsonArray();JsonObject textures=new JsonObject();textures.addProperty("skin",Base64.getEncoder().encodeToString(png));bundle.add("textures",textures);bundle.add("elements",elements);bundle.add("meshes",new JsonArray());
        skinCube(elements,"head",4,24,4,12,32,12,new int[][]{{8,0,16,8},{16,0,24,8},{0,8,8,16},{8,8,16,16},{16,8,24,16},{24,8,32,16}});
        skinCube(elements,"body",4,12,4,12,24,8,new int[][]{{20,16,28,20},{28,16,36,20},{16,20,20,32},{20,20,28,32},{28,20,32,32},{32,20,40,32}});
        skinCube(elements,"right_arm",slim?1:0,12,4,4,24,8,slim?new int[][]{{44,16,47,20},{47,16,50,20},{40,20,44,32},{44,20,47,32},{47,20,51,32},{51,20,54,32}}:new int[][]{{44,16,48,20},{48,16,52,20},{40,20,44,32},{44,20,48,32},{48,20,52,32},{52,20,56,32}});
        skinCube(elements,"left_arm",12,12,4,slim?15:16,24,8,slim?new int[][]{{36,48,39,52},{39,48,42,52},{32,52,36,64},{36,52,39,64},{39,52,43,64},{43,52,46,64}}:new int[][]{{36,48,40,52},{40,48,44,52},{32,52,36,64},{36,52,40,64},{40,52,44,64},{44,52,48,64}});
        skinCube(elements,"right_leg",4,0,4,8,12,8,new int[][]{{4,16,8,20},{8,16,12,20},{0,20,4,32},{4,20,8,32},{8,20,12,32},{12,20,16,32}});
        skinCube(elements,"left_leg",8,0,4,12,12,8,new int[][]{{20,48,24,52},{24,48,28,52},{16,52,20,64},{20,52,24,64},{24,52,28,64},{28,52,32,64}});
        return bundle;
    }
    private static void skinCube(JsonArray out,String name,float x,float y,float z,float X,float Y,float Z,int[][] uv){
        JsonObject e=new JsonObject();e.addProperty("name",name);e.add("from",jsonVec(x,y,z));e.add("to",jsonVec(X,Y,Z));e.add("origin",jsonVec(8,0,8));e.add("rotation",jsonVec(0,0,0));JsonObject faces=new JsonObject();String[] sides={"up","down","east","north","west","south"};for(int i=0;i<sides.length;i++){JsonObject f=new JsonObject();f.addProperty("texture","skin");JsonArray rect=new JsonArray();rect.add(uv[i][0]/64f);rect.add(uv[i][1]/64f);rect.add(uv[i][2]/64f);rect.add(uv[i][3]/64f);f.add("uv",rect);f.addProperty("rotation",0);faces.add(sides[i],f);}e.add("faces",faces);out.add(e);
    }
    private static JsonArray jsonVec(float x,float y,float z){JsonArray a=new JsonArray();a.add(x);a.add(y);a.add(z);return a;}
    private static float[][] points(String side,float[] a,float[] b){float x=a[0],y=a[1],z=a[2],X=b[0],Y=b[1],Z=b[2];return switch(side){
        case "north"->new float[][]{{X,y,z},{x,y,z},{x,Y,z},{X,Y,z}};
        case "south"->new float[][]{{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}};
        case "east"->new float[][]{{X,y,Z},{X,y,z},{X,Y,z},{X,Y,Z}};
        case "west"->new float[][]{{x,y,z},{x,y,Z},{x,Y,Z},{x,Y,z}};
        case "up"->new float[][]{{x,Y,Z},{X,Y,Z},{X,Y,z},{x,Y,z}};
        case "down"->new float[][]{{x,y,z},{X,y,z},{X,y,Z},{x,y,Z}};
        default->null;};}
    private static String str(JsonObject object,String key,String fallback){return object.has(key)&&!object.get(key).isJsonNull()?object.get(key).getAsString():fallback;}
    private static void load(String url,int epoch){load(url,epoch,false);}
    private static void load(String url,int epoch,boolean slim){
        if(!loading.add(url))return;
        CompletableFuture.supplyAsync(()->{
            try{
                URI uri=URI.create(url), api=URI.create(MpsqApiClient.API_URL);
                if(!"https".equals(uri.getScheme())||!api.getHost().equals(uri.getHost()))throw new IOException("Unzulässige Modellquelle");
                var response=HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
                try(InputStream stream=response.body()){
                    if(response.statusCode()!=200)throw new IOException("Modell nicht verfügbar");
                    byte[] bytes=stream.readNBytes(12000001);if(bytes.length>12000000)throw new IOException("Modell zu groß");
                    if(bytes.length>=8&&(bytes[0]&255)==137&&bytes[1]=='P'&&bytes[2]=='N'&&bytes[3]=='G')return playerSkinBundle(bytes,slim);
                    return JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                }
            }catch(Exception e){throw new java.util.concurrent.CompletionException(e);}
        }).whenComplete((bundle,error)->MinecraftClient.getInstance().execute(()->{
            if(epoch!=generation)return;loading.remove(url);if(error!=null){MpsqCameraClient.LOGGER.debug("Accessoire konnte nicht geladen werden",error);return;}
            Map<String,Identifier> textures=new HashMap<>();
            try{
                JsonArray elements=bundle.getAsJsonArray("elements"),meshes=bundle.has("meshes")?bundle.getAsJsonArray("meshes"):new JsonArray();
                if(elements.size()>512||meshes.size()>512||bundle.getAsJsonObject("textures").size()>32)throw new IOException("Modellgrenze überschritten");
                int vertexCount=0;for(JsonElement meshElement:meshes){JsonObject mesh=meshElement.getAsJsonObject();JsonArray vertices=mesh.getAsJsonArray("vertices"),indices=mesh.getAsJsonArray("indices");vertexCount+=vertices.size();if(vertexCount>200000||indices.size()>600000||indices.size()%3!=0)throw new IOException("Mesh-Grenze überschritten");for(JsonElement index:indices)if(index.getAsInt()<0||index.getAsInt()>=vertices.size())throw new IOException("Mesh-Index ungültig");}
                long pixels=0;
                for(var entry:bundle.getAsJsonObject("textures").entrySet()){
                    String data=entry.getValue().getAsString();byte[] png=Base64.getDecoder().decode(data.substring(data.indexOf(',')+1));
                    if(png.length<24||png.length>2250000)throw new IOException("Ungültige PNG");
                    var header=java.nio.ByteBuffer.wrap(png);int w=header.getInt(16),h=header.getInt(20);if(w<1||h<1||w>1024||h>1024)throw new IOException("Textur maximal 1024 × 1024");
                    pixels+=(long)w*h;if(pixels>2097152)throw new IOException("Texturpaket zu groß");
                    NativeImage image=NativeImage.read(new ByteArrayInputStream(png));Identifier id=Identifier.of(MpsqCameraClient.MOD_ID,"accessory/"+UUID.randomUUID());
                    var texture=new NativeImageBackedTexture(()->"MPSQ Accessoire",image);MinecraftClient.getInstance().getTextureManager().registerTexture(id,texture);texture.upload();textures.put(entry.getKey(),id);
                }
                models.put(url,new Model(elements,meshes,textures));
            }catch(Exception e){for(Identifier id:textures.values())MinecraftClient.getInstance().getTextureManager().destroyTexture(id);MpsqCameraClient.LOGGER.warn("Accessoire-Modell ungültig",e);}
        }));
    }
}
