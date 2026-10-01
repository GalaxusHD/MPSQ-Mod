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
    private record Model(JsonArray elements,JsonArray meshes,JsonArray bones,Map<String,Identifier> textures,Map<String,Integer> previewColors,List<RenderFace> bakedGeometry){}
    private record RenderFace(Identifier texture,float[][] vertices,float nx,float ny,float nz){}
    private record PreviewPoint(double x,double y,double z){}
    private record PreviewPolygon(double[][] points,int color,double depth){}
    private static final class NpcRotation {
        float bodyYaw;
        float pitch;
        long updatedAt;
        NpcRotation(float bodyYaw,float pitch,long updatedAt){this.bodyYaw=bodyYaw;this.pitch=pitch;this.updatedAt=updatedAt;}
    }
    private static final Map<String,Model> models=new HashMap<>();
    private static final Map<String,Identifier> previews=new HashMap<>();
    private static final Map<String,String> wearers=new HashMap<>();
    private static JsonArray objects=new JsonArray();
    private static JsonArray npcs=new JsonArray();
    /** True when at least one currently loaded NPC needs the vanilla entity-outline post pass. */
    public static boolean hasGlowingNpcs(){
        for(JsonElement element:npcs){
            if(!element.isJsonObject())continue;
            JsonObject npc=element.getAsJsonObject();
            String task=npc.has("task_type")&&!npc.get("task_type").isJsonNull()?npc.get("task_type").getAsString():"none";
            boolean special=switch(task){case "accessories","quest","tutorial"->true;default->false;};
            String id=str(npc,"id","");
            if(!MpsqNpcVisitStore.isGlowDisabled(id)&&(!special||!MpsqNpcVisitStore.hasVisited(task)))return true;
        }
        return false;
    }
    private static final Set<String> loading=new HashSet<>();
    private static final Map<String,String> localAssetUrls=new HashMap<>();
    private static final Map<String,String> localAssetCategories=new HashMap<>();
    private static final Map<String,NpcRotation> npcRotations=new HashMap<>();
    private static boolean localCatalogRequested;
    private static boolean localAccessoryCatalogRequested;
    private static String tryOnUrl;
    private static net.minecraft.util.math.Vec3d tryOnStart;
    private static final java.util.BitSet pressedKeys=new java.util.BitSet();
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static long next;
    private static boolean polling;
    private static int generation;
    private static String scope="";
    private MpsqAccessoryRenderer(){}
    public static void refresh(){generation++;polling=false;loading.clear();npcRotations.clear();MpsqNpcSkinRenderer.clear(MinecraftClient.getInstance());clearModels(MinecraftClient.getInstance());localAssetUrls.clear();localAssetCategories.clear();localCatalogRequested=false;next=0;}
    static boolean isCurrentGeneration(int epoch){return epoch==generation;}
    public static JsonArray npcsSnapshot(){return npcs.deepCopy();}
    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(tryOnUrl!=null&&client.player!=null){
                if(tryOnStart==null)tryOnStart=client.player.getPos();
                if(client.player.getPos().squaredDistanceTo(tryOnStart)>0.0004){clearTryOn();}
                else {long window=client.getWindow().getHandle();for(int key=1;key<=org.lwjgl.glfw.GLFW.GLFW_KEY_LAST;key++){boolean down=org.lwjgl.glfw.GLFW.glfwGetKey(window,key)==org.lwjgl.glfw.GLFW.GLFW_PRESS;if(down&&!pressedKeys.get(key)&&key!=org.lwjgl.glfw.GLFW.GLFW_KEY_F5){clearTryOn();break;}if(down)pressedKeys.set(key);else pressedKeys.clear(key);}}
            }
            String current=MpsqActionSync.server()+"|"+MpsqActionSync.world();
            if(!scope.equals(current)){scope=current;generation++;polling=false;wearers.clear();objects=new JsonArray();npcs=new JsonArray();loading.clear();npcRotations.clear();MpsqNpcSkinRenderer.clear(client);clearModels(client);localAssetUrls.clear();localAssetCategories.clear();localCatalogRequested=false;localAccessoryCatalogRequested=false;next=0;}
            boolean localWorld=MpsqActionSync.server().isBlank()&&client.getServer()!=null;
            if(client.world==null||!TeamVisibilitySettings.visible()||(!MpsqActionSync.server().isBlank()&&!MpsqActionSync.isMpsqServer())||(!localWorld&&!MpsqApiClient.isReady())||polling||System.currentTimeMillis()<next)return;
            polling=true;next=System.currentTimeMillis()+15000;int epoch=generation;
            if(MpsqActionSync.server().isBlank()){
                objects=localObjectsSnapshot(epoch);
                npcs=localNpcsSnapshot();
                if(!localCatalogRequested){localCatalogRequested=true;mapCatalog(MpsqLocalWorldStore.array("furniture_catalog"),"furniture");mapCatalog(MpsqLocalWorldStore.array("model_catalog"),"npc_model");objects=localObjectsSnapshot(epoch);npcs=localNpcsSnapshot();MpsqApiClient.get("/furniture/catalog").whenComplete((data,error)->client.execute(()->{
                    if(epoch!=generation)return;
                    if(error!=null||!data.isJsonArray()){localCatalogRequested=false;return;}
                    MpsqLocalWorldStore.setArray("furniture_catalog",data.getAsJsonArray());mapCatalog(data.getAsJsonArray(),"furniture");
                    objects=localObjectsSnapshot(epoch);
                    for(JsonElement value:objects){JsonObject row=value.getAsJsonObject();String url=row.get("url").getAsString();if(!models.containsKey(url)&&models.size()+loading.size()<64)load(url,epoch);}
                }));}
                if(!MpsqApiClient.isReady()){polling=false;return;}
                MpsqApiClient.get("/models/catalog").whenComplete((data,error)->client.execute(()->{
                    if(epoch!=generation)return;
                    if(error!=null||!data.isJsonArray())return;
                    MpsqLocalWorldStore.setArray("model_catalog",data.getAsJsonArray());mapCatalog(data.getAsJsonArray(),"npc_model");
                    npcs=localNpcsSnapshot();
                    for(JsonElement value:npcs){JsonObject npc=value.getAsJsonObject();loadNpcAsset(npc,epoch);}
                }));
                applyLocalEquippedAccessory(MpsqLocalWorldStore.array("accessory_catalog"));
                if(!localAccessoryCatalogRequested&&MpsqApiClient.isReady()){localAccessoryCatalogRequested=true;MpsqApiClient.get("/accessory-catalog").whenComplete((data,error)->client.execute(()->{if(epoch!=generation)return;if(error!=null||!data.isJsonArray()){localAccessoryCatalogRequested=false;return;}MpsqLocalWorldStore.setArray("accessory_catalog",data.getAsJsonArray());applyLocalEquippedAccessory(data.getAsJsonArray());}));}
                for(JsonElement value:objects){JsonObject row=value.getAsJsonObject();if(row.has("url")&&!row.get("url").isJsonNull()){String url=row.get("url").getAsString();if(!models.containsKey(url)&&models.size()+loading.size()<64)load(url,epoch);}}
            }else{
                MpsqApiClient.get("/objects?server="+java.net.URLEncoder.encode(MpsqActionSync.server(),java.nio.charset.StandardCharsets.UTF_8)+"&world="+java.net.URLEncoder.encode(MpsqActionSync.world(),java.nio.charset.StandardCharsets.UTF_8)).whenComplete((data,error)->client.execute(()->{
                    if(epoch!=generation||error!=null)return;objects=data.getAsJsonArray();
                    for(var value:objects){var o=value.getAsJsonObject();String url=o.get("url").getAsString();if(!models.containsKey(url)&&models.size()+loading.size()<64)load(url,epoch);}
                }));
                MpsqApiClient.get("/npcs?server="+java.net.URLEncoder.encode(MpsqActionSync.server(),java.nio.charset.StandardCharsets.UTF_8)+"&world="+java.net.URLEncoder.encode(MpsqActionSync.world(),java.nio.charset.StandardCharsets.UTF_8)).whenComplete((data,error)->client.execute(()->{
                    if(epoch!=generation||error!=null||!data.isJsonArray())return;npcs=data.getAsJsonArray();
                    for(JsonElement value:npcs){JsonObject npc=value.getAsJsonObject();loadNpcAsset(npc,epoch);}
                }));
            }
            if(localWorld){polling=false;return;}
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
            if(client.world==null||matrices==null||consumers==null||!TeamVisibilitySettings.visible())return;
            var camera=context.camera().getPos();
            for(var value:objects){var o=value.getAsJsonObject();Model model=models.get(o.get("url").getAsString());if(model==null)continue;
                double x=o.get("x").getAsDouble(),y=o.get("y").getAsDouble(),z=o.get("z").getAsDouble();if(camera.squaredDistanceTo(x,y,z)>4096)continue;
                matrices.push();matrices.translate(x+0.5-camera.x,y-camera.y,z+0.5-camera.z);matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(o.get("rotation").getAsFloat()));matrices.translate(-0.5,0,-0.5);matrices.scale(1f/16,1f/16,1f/16);drawBakedFurniture(model,matrices,consumers,0xFFFFFFFF);matrices.pop();
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
                drawBbModel(model,matrices,consumers,0xFFFFFFFF);
                matrices.pop();
            }
            for(var value:npcs){var o=value.getAsJsonObject();if(!o.has("url")||o.get("url").isJsonNull())continue;String category=str(o,"category","npc_model");boolean playerSkin="npc_skin_normal".equals(category)||"npc_skin_slim".equals(category);boolean slim="npc_skin_slim".equals(category);String url=o.get("url").getAsString();Model model=playerSkin?null:models.get(url);MpsqNpcSkinRenderer.Skin skin=playerSkin?MpsqNpcSkinRenderer.get(url,slim):null;if(playerSkin?skin==null:model==null)continue;
                double x=o.has("world_x")?o.get("world_x").getAsDouble():o.get("x").getAsDouble()+0.5,y=o.has("world_y")?o.get("world_y").getAsDouble():o.get("y").getAsDouble(),z=o.has("world_z")?o.get("world_z").getAsDouble():o.get("z").getAsDouble()+0.5;if(camera.squaredDistanceTo(x,y,z)>4096)continue;
                float size=o.has("scale")?o.get("scale").getAsFloat():1f;String animation=o.has("animation")?o.get("animation").getAsString():"none";float phase=(System.currentTimeMillis()%4000L)/1000f;float bob=animation.equals("bob")?(float)Math.sin(phase*Math.PI*2)*0.08f:0;float pulse=animation.equals("pulse")?1f+(float)Math.sin(phase*Math.PI*2)*0.08f:1f;float yaw=o.has("yaw")?o.get("yaw").getAsFloat():0f,pitch=o.has("pitch")?o.get("pitch").getAsFloat():0f;boolean face=o.has("face_player")&&o.get("face_player").getAsBoolean();float npcHeight=playerSkin?1.8f*size:size;
                float headYaw=0f;
                if(face&&client.player!=null&&client.player.squaredDistanceTo(x,y+npcHeight*0.5,z)<=900){double dx=client.player.getX()-x,dz=client.player.getZ()-z,lookFromY=y+npcHeight*0.85,targetY=client.player.getY()+0.9,dy=targetY-lookFromY;float targetYaw=(float)Math.toDegrees(Math.atan2(-dx,dz));float targetPitch=(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)));float[] facing=smoothNpcFacing(rotationKey,yaw,targetYaw,targetPitch);yaw=facing[0];headYaw=facing[1];pitch=facing[2];}
                String rotationKey=o.has("id")?o.get("id").getAsString():x+":"+y+":"+z;
                if(!face)npcRotations.remove(rotationKey);
                if(animation.equals("turn"))yaw+=phase*90f;
                if(animation.equals("nod"))pitch+=(float)Math.sin(phase*Math.PI*2)*12f;
                if(animation.equals("tilt"))yaw+=(float)Math.sin(phase*Math.PI*2)*14f;
                if(animation.equals("look_around"))yaw+=(float)Math.sin(phase*Math.PI)*28f;
                if(animation.equals("shake"))yaw+=(float)Math.sin(phase*Math.PI*8)*5f;
                if(animation.equals("wave"))pulse=1f+(float)Math.sin(phase*Math.PI*2)*0.035f;
                String task=o.has("task_type")?o.get("task_type").getAsString():"none";
                boolean hasSpecialRole=switch(task){case "accessories","quest","tutorial"->true;default->false;};
                String configuredGlow=str(o,"glow_color",defaultGlowColor(task));
                if("none".equalsIgnoreCase(configuredGlow))configuredGlow=defaultGlowColor(task);
                int glow=glowColor(configuredGlow);
                boolean glowDisabled=MpsqNpcVisitStore.isGlowDisabled(str(o,"id",""));
                boolean glowing=!glowDisabled&&(!hasSpecialRole||!MpsqNpcVisitStore.hasVisited(task));
                if(playerSkin){var state=MpsqNpcSkinRenderer.createState(skin,yaw,headYaw,pitch,(System.currentTimeMillis()%100000L)/50.0f,glowing);int light=WorldRenderer.getLightmapCoordinates(client.world,net.minecraft.util.math.BlockPos.ofFloored(x,y,z));MpsqNpcSkinRenderer.render(state,x-camera.x,y+bob-camera.y,z-camera.z,size*pulse,matrices,consumers,light,glow);}
                else {matrices.push();matrices.translate(x-camera.x,y+bob-camera.y,z-camera.z);matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));matrices.scale(size/16f*pulse,size/16f*pulse,size/16f*pulse);drawBbModel(model,matrices,consumers,0xFFFFFFFF);
                    if(glowing){var outline=client.getBufferBuilders().getOutlineVertexConsumers();outline.setColor((glow>>16)&255,(glow>>8)&255,glow&255,255);drawBbModel(model,matrices,outline,0xFFFFFFFF);outline.draw();}
                    matrices.pop();}
                boolean tutorialDone=o.has("tutorial_completed")&&o.get("tutorial_completed").getAsBoolean();
                String tag=switch(task){case "accessories"->"accessories";case "quest"->"quests";case "tutorial"->"tutorial";default->null;};
                if(tag!=null){
                    double towardX=camera.x-x,towardZ=camera.z-z,horizontalLength=Math.sqrt(towardX*towardX+towardZ*towardZ);
                    double tagX=x-camera.x,tagZ=z-camera.z;
                    if(horizontalLength>1.0e-4){tagX+=(towardX/horizontalLength)*0.30*size;tagZ+=(towardZ/horizontalLength)*0.30*size;}
                    float tagScale=size,tagHeight=0.20f*tagScale;
                    float aspect=switch(tag){case "accessories"->2624f/320f;case "quests"->1504f/320f;default->1952f/320f;};
                    drawBillboard(context,matrices,consumers,Identifier.of("mpsqcamera","textures/gui/npc_tags/"+tag+".png"),tagX,y+npcHeight+bob+0.12*tagScale-camera.y,tagZ,tagHeight*aspect,tagHeight);
                    if("tutorial".equals(tag)&&!tutorialDone){float hover=(float)Math.sin(System.currentTimeMillis()/360.0)*0.07f*tagScale;drawBillboard(context,matrices,consumers,Identifier.of("mpsqcamera","textures/gui/npc_tags/tutorial_exclamation.png"),tagX,y+npcHeight+bob+(0.48f*tagScale)+hover-camera.y,tagZ,0.42f*tagScale,0.42f*tagScale);}}
            }
        });
    }
    private static float[] smoothNpcFacing(String key,float initialBodyYaw,float targetYaw,float targetPitch){
        long now=System.nanoTime();
        NpcRotation rotation=npcRotations.get(key);
        if(rotation==null){rotation=new NpcRotation(initialBodyYaw,targetPitch,now);npcRotations.put(key,rotation);}
        float deltaSeconds=Math.max(0f,Math.min(0.1f,(now-rotation.updatedAt)/1_000_000_000f));
        // Keep the head centered until it reaches ±60°; only then smoothly turn the body underneath it.
        float offset=wrapDegrees(targetYaw-rotation.bodyYaw);
        if(Math.abs(offset)>60f){float desiredBodyYaw=targetYaw-Math.copySign(60f,offset);float amount=1f-(float)Math.exp(-deltaSeconds/0.22f);rotation.bodyYaw=wrapDegrees(rotation.bodyYaw+wrapDegrees(desiredBodyYaw-rotation.bodyYaw)*amount);}
        float relativeHeadYaw=Math.max(-60f,Math.min(60f,wrapDegrees(targetYaw-rotation.bodyYaw)));
        float pitch=Math.max(-35f,Math.min(35f,targetPitch));
        float pitchAmount=1f-(float)Math.exp(-deltaSeconds/0.14f);
        rotation.pitch+=(pitch-rotation.pitch)*pitchAmount;
        rotation.updatedAt=now;
        if(npcRotations.size()>512)npcRotations.entrySet().removeIf(entry->now-entry.getValue().updatedAt>60_000_000_000L);
        return new float[]{rotation.bodyYaw,relativeHeadYaw,rotation.pitch};
    }
    private static float wrapDegrees(float degrees){degrees%=360f;if(degrees>=180f)degrees-=360f;if(degrees< -180f)degrees+=360f;return degrees;}
    private static void mapCatalog(JsonArray values,String fallback){for(JsonElement value:values){if(!value.isJsonObject())continue;JsonObject asset=value.getAsJsonObject();if(asset.has("id")&&asset.has("url")){String id=asset.get("id").getAsString();localAssetUrls.put(id,asset.get("url").getAsString());localAssetCategories.put(id,str(asset,"category",fallback));}}}
    private static void applyLocalEquippedAccessory(JsonArray values){String id=MpsqLocalWorldStore.equipped();var player=MinecraftClient.getInstance().player;if(id==null||player==null)return;for(JsonElement e:values){if(!e.isJsonObject())continue;JsonObject row=e.getAsJsonObject();if(id.equals(str(row,"accessory_id",""))&&row.has("url")&&!row.get("url").isJsonNull()){wearers.put(player.getName().getString().toLowerCase(Locale.ROOT),row.get("url").getAsString());return;}}}
    public static void tryOn(String url){tryOnUrl=url;tryOnStart=MinecraftClient.getInstance().player==null?null:MinecraftClient.getInstance().player.getPos();pressedKeys.clear();if(url!=null&&MinecraftClient.getInstance().player!=null)MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.literal("Vorschau aktiv · Bewegung oder eine Taste beendet sie (F5 bleibt erlaubt)."),true);}
    private static void clearTryOn(){tryOnUrl=null;tryOnStart=null;pressedKeys.clear();}
    /** Draws a compact isometric model thumbnail with texture-derived face colors. */
    public static void drawGuiPreview(net.minecraft.client.gui.DrawContext context,String url,int x,int y,float size){int px=Math.max(20,Math.min(64,Math.round(20*size)));Model model=models.get(url);if(model==null){if(url!=null&&MpsqApiClient.isReady()&&models.size()+loading.size()<64)load(url,generation);context.fill(x-px/2-1,y-px/2-1,x+px/2+1,y+px/2+1,0xAA222222);return;}Identifier preview=previews.computeIfAbsent(url,key->bakePreview(key,model));context.fill(x-px/2-1,y-px/2-1,x+px/2+1,y+px/2+1,0xFF151820);if(preview!=null)context.drawTexturedQuad(preview,x-px/2,y-px/2,px,px,0,0,1,1);}
    public static void drawGuiSkinPreview(net.minecraft.client.gui.DrawContext context,String url,int x,int y,float size,boolean slim){int px=Math.max(20,Math.min(64,Math.round(20*size)));MpsqNpcSkinRenderer.load(url,generation,slim);MpsqNpcSkinRenderer.Skin skin=MpsqNpcSkinRenderer.get(url,slim);context.fill(x-px/2-1,y-px/2-1,x+px/2+1,y+px/2+1,0xFF151820);if(skin!=null)context.drawTexturedQuad(skin.texture(),x-px/2,y-px/2,px,px,8f/64f,8f/64f,16f/64f,16f/64f);}
    private static void clearModels(MinecraftClient client){for(Model model:models.values())for(Identifier id:model.textures.values())client.getTextureManager().destroyTexture(id);models.clear();for(Identifier id:previews.values())client.getTextureManager().destroyTexture(id);previews.clear();}
    private static Identifier bakePreview(String url,Model model){try{List<PreviewPolygon> polygons=new ArrayList<>();java.util.BitSet used=new java.util.BitSet(model.elements.size());for(JsonElement bone:model.bones)collectPreviewBone(model,bone.getAsJsonObject(),new ArrayList<>(),used,polygons);for(int i=used.nextClearBit(0);i<model.elements.size();i=used.nextClearBit(i+1))collectPreviewElement(model,i,new ArrayList<>(),polygons);for(JsonElement mesh:model.meshes){JsonObject m=mesh.getAsJsonObject();JsonArray vertices=m.getAsJsonArray("vertices"),indices=m.getAsJsonArray("indices");int color=model.previewColors.getOrDefault(str(m,"texture",""),0xFFB0B0B0);for(int i=0;i+2<indices.size();i+=3){double[][] p=new double[3][2];double depth=0;for(int k=0;k<3;k++){JsonArray v=vertices.get(indices.get(i+k).getAsInt()).getAsJsonArray();PreviewPoint q=project(v.get(0).getAsDouble(),v.get(1).getAsDouble(),v.get(2).getAsDouble());p[k][0]=q.x;p[k][1]=q.y;depth+=q.y;}polygons.add(new PreviewPolygon(p,color,depth/3));}}
        if(polygons.isEmpty())return null;double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;for(PreviewPolygon poly:polygons)for(double[] p:poly.points){minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);}double span=Math.max(1,Math.max(maxX-minX,maxY-minY)),scale=42/span,offX=(48-(maxX-minX)*scale)/2-minX*scale,offY=(48-(maxY-minY)*scale)/2-minY*scale;polygons.sort(java.util.Comparator.comparingDouble(PreviewPolygon::depth));NativeImage image=new NativeImage(48,48,true);for(PreviewPolygon poly:polygons){double[][] pts=new double[poly.points.length][2];for(int i=0;i<pts.length;i++){pts[i][0]=poly.points[i][0]*scale+offX;pts[i][1]=poly.points[i][1]*scale+offY;}fillPreviewPolygon(image,pts,poly.color);}Identifier id=Identifier.of(MpsqCameraClient.MOD_ID,"model_preview/"+UUID.randomUUID());NativeImageBackedTexture texture=new NativeImageBackedTexture(()->"MPSQ model preview",image);MinecraftClient.getInstance().getTextureManager().registerTexture(id,texture);texture.upload();return id;
        }catch(Exception ex){MpsqCameraClient.LOGGER.debug("Modellvorschau konnte nicht erzeugt werden",ex);return null;}}
    private static void collectPreviewBone(Model model,JsonObject bone,List<JsonObject> chain,java.util.BitSet used,List<PreviewPolygon> out){chain.add(bone);if(bone.has("elements"))for(JsonElement ref:bone.getAsJsonArray("elements")){int index=ref.getAsInt();if(index>=0&&index<model.elements.size()&&!used.get(index)){used.set(index);collectPreviewElement(model,index,chain,out);}}if(bone.has("children"))for(JsonElement child:bone.getAsJsonArray("children"))collectPreviewBone(model,child.getAsJsonObject(),chain,used,out);chain.remove(chain.size()-1);}
    private static void collectPreviewElement(Model model,int index,List<JsonObject> chain,List<PreviewPolygon> out){JsonObject e=model.elements.get(index).getAsJsonObject();float[] from=vec(e,"from"),to=vec(e,"to");for(var entry:e.getAsJsonObject("faces").entrySet()){float[][] raw=points(entry.getKey(),from,to);if(raw==null)continue;JsonObject face=entry.getValue().getAsJsonObject();int color=model.previewColors.getOrDefault(previewColorKey(face),model.previewColors.getOrDefault(str(face,"texture",""),0xFFB0B0B0));color=shade(color,entry.getKey());double[][] pts=new double[4][2];double depth=0;for(int i=0;i<4;i++){PreviewPoint p=rotatePreview(raw[i],e,chain);PreviewPoint projected=project(p.x,p.y,p.z);pts[i][0]=projected.x;pts[i][1]=projected.y;depth+=projected.y;}out.add(new PreviewPolygon(pts,color,depth/4));}}
    private static PreviewPoint rotatePreview(float[] point,JsonObject element,List<JsonObject> chain){PreviewPoint p=new PreviewPoint(point[0],point[1],point[2]);float[] eo=vec(element,"origin"),er=vec(element,"rotation");p=rotateAround(p,eo,er);for(int i=chain.size()-1;i>=0;i--){JsonObject b=chain.get(i);p=rotateAround(p,vec(b,"origin"),vec(b,"rotation"));}return p;}
    private static PreviewPoint rotateAround(PreviewPoint p,float[] origin,float[] rotation){double x=p.x-origin[0],y=p.y-origin[1],z=p.z-origin[2],a=Math.toRadians(rotation[0]),b=Math.toRadians(rotation[1]),c=Math.toRadians(rotation[2]);double ny=y*Math.cos(a)-z*Math.sin(a),nz=y*Math.sin(a)+z*Math.cos(a);y=ny;z=nz;double nx=x*Math.cos(b)+z*Math.sin(b);nz=-x*Math.sin(b)+z*Math.cos(b);x=nx;z=nz;nx=x*Math.cos(c)-y*Math.sin(c);ny=x*Math.sin(c)+y*Math.cos(c);return new PreviewPoint(nx+origin[0],ny+origin[1],z+origin[2]);}
    private static PreviewPoint project(double x,double y,double z){return new PreviewPoint((x-z)*0.70710678,(x+z)*0.35355339-y*0.81649658,0);}
    private static int shade(int color,String face){double factor=switch(face){case "up"->1.16;case "down"->0.62;case "east","west"->0.82;default->1.0;};int a=(color>>>24)&255,r=(int)(((color>>>16)&255)*factor),g=(int)(((color>>>8)&255)*factor),b=(int)((color&255)*factor);return(a<<24)|(Math.min(255,r)<<16)|(Math.min(255,g)<<8)|Math.min(255,b);}
    private static void fillPreviewPolygon(NativeImage image,double[][] points,int color){double min=48,max=-1;for(double[] p:points){min=Math.min(min,p[1]);max=Math.max(max,p[1]);}int y0=Math.max(0,(int)Math.floor(min)),y1=Math.min(47,(int)Math.ceil(max));for(int y=y0;y<=y1;y++){double scan=y+0.5;double[] xs=new double[points.length];int count=0;for(int i=0,j=points.length-1;i<points.length;j=i++){double[] a=points[j],b=points[i];if((a[1]<=scan&&b[1]>scan)||(b[1]<=scan&&a[1]>scan))xs[count++]=a[0]+(scan-a[1])*(b[0]-a[0])/(b[1]-a[1]);}if(count<2)continue;java.util.Arrays.sort(xs,0,count);int x0=Math.max(0,(int)Math.ceil(xs[0])),x1=Math.min(47,(int)Math.floor(xs[count-1]));for(int x=x0;x<=x1;x++)image.setColorArgb(x,y,color);}}
    private static JsonArray localObjectsSnapshot(int epoch){
        JsonArray resolved=new JsonArray();
        for(JsonElement value:MpsqLocalObjectStore.loadWorld(MpsqActionSync.world())){
            if(!value.isJsonObject())continue;JsonObject source=value.getAsJsonObject();String id=source.has("model_id")?source.get("model_id").getAsString():"";String url=localAssetUrls.get(id);if(url==null)continue;
            JsonObject row=source.deepCopy();row.addProperty("url",url);if(!row.has("rotation"))row.addProperty("rotation",0);resolved.add(row);
        }
        return resolved;
    }
    private static JsonArray localNpcsSnapshot(){JsonArray resolved=new JsonArray();for(JsonElement value:MpsqLocalNpcStore.loadWorld(MpsqActionSync.world())){JsonObject row=value.getAsJsonObject().deepCopy();String id=row.has("asset_id")?row.get("asset_id").getAsString():"";String url=localAssetUrls.get(id);if(url==null)continue;row.addProperty("url",url);row.addProperty("asset_id",id);row.addProperty("category",localAssetCategories.getOrDefault(id,str(row,"category","npc_model")));resolved.add(row);}return resolved;}
    private static void loadNpcAsset(JsonObject npc,int epoch){if(!npc.has("url")||npc.get("url").isJsonNull())return;String url=npc.get("url").getAsString(),category=str(npc,"category","npc_model");if("npc_skin_normal".equals(category)||"npc_skin_slim".equals(category)){MpsqNpcSkinRenderer.load(url,epoch,"npc_skin_slim".equals(category));return;}if(!models.containsKey(url)&&models.size()+loading.size()<64)load(url,epoch);}
    private static String defaultGlowColor(String task){return switch(task){case "tutorial"->"#c3971f";case "accessories"->"#8027b0";case "quest"->"#2149c4";default->"#ec2f53";};}
    private static int glowColor(String color){
        if(color!=null&&color.matches("#[0-9a-fA-F]{6}"))return 0xFF000000|Integer.parseInt(color.substring(1),16);
        return switch(String.valueOf(color).toLowerCase(Locale.ROOT)){case "white"->0xFFFFFFFF;case "orange"->0xFFFFAA33;case "magenta"->0xFFFF55FF;case "light_blue"->0xFF55AAFF;case "yellow"->0xFFFFFF55;case "lime"->0xFF55FF55;case "pink"->0xFFFF88BB;case "gray"->0xFF666666;case "light_gray"->0xFFBBBBBB;case "cyan"->0xFF55FFFF;case "purple"->0xFFAA55FF;case "blue"->0xFF5555FF;case "brown"->0xFF8B5A2B;case "green"->0xFF55AA33;case "red"->0xFFFF5555;case "black"->0xFF333333;default->0xFFEC2F53;};
    }
    private static void drawBillboard(WorldRenderContext context,MatrixStack matrices,VertexConsumerProvider consumers,Identifier texture,double x,double y,double z,float width,float height){
        matrices.push();matrices.translate(x,y,z);matrices.multiply(context.camera().getRotation());var entry=matrices.peek();var buffer=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        buffer.vertex(entry,-width/2,-height/2,0).color(255,255,255,255).texture(0,1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,0,1);
        buffer.vertex(entry,width/2,-height/2,0).color(255,255,255,255).texture(1,1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,0,1);
        buffer.vertex(entry,width/2,height/2,0).color(255,255,255,255).texture(1,0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,0,1);
        buffer.vertex(entry,-width/2,height/2,0).color(255,255,255,255).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,0,1);matrices.pop();
    }
    private static void drawModel(Model model,MatrixStack matrices,VertexConsumerProvider consumers,int tint){drawElements(model,matrices,consumers,tint,null);drawMeshes(model,matrices,consumers,tint);}
    private static void drawBakedFurniture(Model model,MatrixStack matrices,VertexConsumerProvider consumers,int tint){for(RenderFace face:model.bakedGeometry){var buffer=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(face.texture));for(int i=0;i<4;i++){float[] vertex=face.vertices[Math.min(i,face.vertices.length-1)];buffer.vertex(matrices.peek(),vertex[0],vertex[1],vertex[2]).color((tint>>16)&255,(tint>>8)&255,tint&255,(tint>>>24)&255).texture(vertex[3],vertex[4]).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(matrices.peek(),face.nx,face.ny,face.nz);}}}
    private static void drawBbModel(Model model,MatrixStack matrices,VertexConsumerProvider consumers,int tint){if(model.bones==null||model.bones.isEmpty()){drawModel(model,matrices,consumers,tint);return;}java.util.BitSet used=new java.util.BitSet(model.elements.size());for(JsonElement bone:model.bones)drawBone(model,bone.getAsJsonObject(),matrices,consumers,tint,used);for(int i=used.nextClearBit(0);i<model.elements.size();i=used.nextClearBit(i+1))drawElements(model,matrices,consumers,tint,i);drawMeshes(model,matrices,consumers,tint);}
    private static void drawBone(Model model,JsonObject bone,MatrixStack matrices,VertexConsumerProvider consumers,int tint,java.util.BitSet used){
        float[] origin=vec(bone,"origin"),rotation=vec(bone,"rotation");matrices.push();matrices.translate(origin[0],origin[1],origin[2]);matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation[2]));matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation[1]));matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation[0]));matrices.translate(-origin[0],-origin[1],-origin[2]);
        if(bone.has("elements"))for(JsonElement index:bone.getAsJsonArray("elements")){int i=index.getAsInt();if(i>=0&&i<model.elements.size()&&!used.get(i)){used.set(i);drawElements(model,matrices,consumers,tint,i);}}
        if(bone.has("children"))for(JsonElement child:bone.getAsJsonArray("children"))drawBone(model,child.getAsJsonObject(),matrices,consumers,tint,used);matrices.pop();
    }
    private static void drawElements(Model model,MatrixStack matrices,VertexConsumerProvider consumers,int tint,Integer only){
                for(int elementIndex=0;elementIndex<model.elements.size();elementIndex++){if(only!=null&&only!=elementIndex)continue;JsonElement value=model.elements.get(elementIndex);
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
    }
    private static void drawMeshes(Model model,MatrixStack matrices,VertexConsumerProvider consumers,int tint){for(JsonElement value:model.meshes){
                    JsonObject mesh=value.getAsJsonObject();Identifier texture=model.textures.get(mesh.get("texture").getAsString());if(texture==null)continue;
                    JsonArray vertices=mesh.getAsJsonArray("vertices"),indices=mesh.getAsJsonArray("indices");var buffer=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
                    for(int i=0;i+2<indices.size();i+=3)for(int k=0;k<4;k++){
                        JsonArray v=vertices.get(indices.get(i+Math.min(k,2)).getAsInt()).getAsJsonArray();
                        buffer.vertex(matrices.peek(),v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat()).color((tint>>16)&255,(tint>>8)&255,tint&255,(tint>>>24)&255).texture(v.get(3).getAsFloat(),v.get(4).getAsFloat()).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,1,0);
                    }
                }}
    private static float[] vec(JsonObject e,String key){JsonArray a=e.getAsJsonArray(key);return new float[]{a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()};}
    private static float[][] points(String side,float[] a,float[] b){float x=a[0],y=a[1],z=a[2],X=b[0],Y=b[1],Z=b[2];return switch(side){
        case "north"->new float[][]{{X,y,z},{x,y,z},{x,Y,z},{X,Y,z}};
        case "south"->new float[][]{{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}};
        case "east"->new float[][]{{X,y,Z},{X,y,z},{X,Y,z},{X,Y,Z}};
        case "west"->new float[][]{{x,y,z},{x,y,Z},{x,Y,Z},{x,Y,z}};
        case "up"->new float[][]{{x,Y,Z},{X,Y,Z},{X,Y,z},{x,Y,z}};
        case "down"->new float[][]{{x,y,z},{X,y,z},{X,y,Z},{x,y,Z}};
        default->null;};}
    private static String str(JsonObject object,String key,String fallback){return object.has(key)&&!object.get(key).isJsonNull()?object.get(key).getAsString():fallback;}
    private static void load(String url,int epoch){
        if(!loading.add(url))return;
        CompletableFuture.supplyAsync(()->{
            try{
                URI uri=URI.create(url), api=URI.create(MpsqApiClient.API_URL);
                if(!"https".equals(uri.getScheme())||!api.getHost().equals(uri.getHost()))throw new IOException("Unzulässige Modellquelle");
                byte[] bytes=MpsqLocalWorldStore.readAsset(url,12000000);
                if(bytes==null){var response=HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());try(InputStream stream=response.body()){if(response.statusCode()!=200)throw new IOException("Modell nicht verfügbar");bytes=stream.readNBytes(12000001);if(bytes.length>12000000)throw new IOException("Modell zu groß");MpsqLocalWorldStore.writeAsset(url,bytes);}}
                return JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            }catch(Exception e){throw new java.util.concurrent.CompletionException(e);}
        }).whenComplete((bundle,error)->MinecraftClient.getInstance().execute(()->{
            if(epoch!=generation)return;loading.remove(url);if(error!=null){MpsqCameraClient.LOGGER.debug("Accessoire konnte nicht geladen werden",error);return;}
            Map<String,Identifier> textures=new HashMap<>();Map<String,Integer> previewColors=new HashMap<>();Map<String,NativeImage> previewImages=new HashMap<>();
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
                    NativeImage image=NativeImage.read(new ByteArrayInputStream(png));previewColors.put(entry.getKey(),averageColor(image));previewImages.put(entry.getKey(),image);Identifier id=Identifier.of(MpsqCameraClient.MOD_ID,"accessory/"+UUID.randomUUID());
                    var texture=new NativeImageBackedTexture(()->"MPSQ Accessoire",image);MinecraftClient.getInstance().getTextureManager().registerTexture(id,texture);texture.upload();textures.put(entry.getKey(),id);
                }
                for(JsonElement element:elements){JsonObject faces=element.getAsJsonObject().getAsJsonObject("faces");for(var face:faces.entrySet()){JsonObject f=face.getValue().getAsJsonObject();NativeImage image=previewImages.get(str(f,"texture",""));if(image!=null&&f.has("uv"))previewColors.put(previewColorKey(f),averageColor(image,f.getAsJsonArray("uv")));}}
                JsonArray bones=bundle.has("bones")&&bundle.get("bones").isJsonArray()?bundle.getAsJsonArray("bones"):new JsonArray();if(bones.size()>256)throw new IOException("Zu viele Modellknochen");validateBones(bones,0);
                models.put(url,new Model(elements,meshes,bones,textures,previewColors,bakeFurnitureGeometry(elements,meshes,bones,textures)));
            }catch(Exception e){for(Identifier id:textures.values())MinecraftClient.getInstance().getTextureManager().destroyTexture(id);MpsqCameraClient.LOGGER.warn("Accessoire-Modell ungültig",e);}
        }));
    }
    private static List<RenderFace> bakeFurnitureGeometry(JsonArray elements,JsonArray meshes,JsonArray bones,Map<String,Identifier> textures){List<RenderFace> result=new ArrayList<>();java.util.BitSet used=new java.util.BitSet(elements.size());for(JsonElement bone:bones)bakeFurnitureBone(elements,bone.getAsJsonObject(),new ArrayList<>(),used,result,textures);for(int i=used.nextClearBit(0);i<elements.size();i=used.nextClearBit(i+1))bakeFurnitureElement(elements,i,new ArrayList<>(),result,textures);
        for(JsonElement value:meshes){JsonObject mesh=value.getAsJsonObject();Identifier texture=textures.get(str(mesh,"texture",""));if(texture==null)continue;JsonArray vertices=mesh.getAsJsonArray("vertices"),indices=mesh.getAsJsonArray("indices");for(int i=0;i+2<indices.size();i+=3){float[][] tri=new float[3][5];for(int k=0;k<3;k++){JsonArray v=vertices.get(indices.get(i+k).getAsInt()).getAsJsonArray();for(int j=0;j<5;j++)tri[k][j]=v.get(j).getAsFloat();}result.add(renderFace(texture,tri));}}return List.copyOf(result);}
    private static void bakeFurnitureBone(JsonArray elements,JsonObject bone,List<JsonObject> chain,java.util.BitSet used,List<RenderFace> result,Map<String,Identifier> textures){chain.add(bone);if(bone.has("elements"))for(JsonElement ref:bone.getAsJsonArray("elements")){int i=ref.getAsInt();if(i>=0&&i<elements.size()&&!used.get(i)){used.set(i);bakeFurnitureElement(elements,i,chain,result,textures);}}if(bone.has("children"))for(JsonElement child:bone.getAsJsonArray("children"))bakeFurnitureBone(elements,child.getAsJsonObject(),chain,used,result,textures);chain.remove(chain.size()-1);}
    private static void bakeFurnitureElement(JsonArray elements,int index,List<JsonObject> chain,List<RenderFace> result,Map<String,Identifier> textures){JsonObject e=elements.get(index).getAsJsonObject();float[][] raw;for(var entry:e.getAsJsonObject("faces").entrySet()){JsonObject face=entry.getValue().getAsJsonObject();Identifier texture=textures.get(str(face,"texture",""));raw=points(entry.getKey(),vec(e,"from"),vec(e,"to"));if(texture==null||raw==null)continue;JsonArray uv=face.getAsJsonArray("uv");float u0=uv.get(0).getAsFloat(),v0=uv.get(1).getAsFloat(),u1=uv.get(2).getAsFloat(),v1=uv.get(3).getAsFloat();float[][] tex={{u0,v1},{u1,v1},{u1,v0},{u0,v0}},vertices=new float[4][5];int turn=face.has("rotation")?Math.floorMod(face.get("rotation").getAsInt()/90,4):0;for(int i=0;i<4;i++){PreviewPoint p=rotatePreview(raw[i],e,chain);float[] t=tex[(i+turn)%4];vertices[i]=new float[]{(float)p.x,(float)p.y,(float)p.z,t[0],t[1]};}result.add(renderFace(texture,vertices));}}
    private static RenderFace renderFace(Identifier texture,float[][] vertices){float ax=vertices[1][0]-vertices[0][0],ay=vertices[1][1]-vertices[0][1],az=vertices[1][2]-vertices[0][2],bx=vertices[2][0]-vertices[0][0],by=vertices[2][1]-vertices[0][1],bz=vertices[2][2]-vertices[0][2];float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx,length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);if(length>1.0e-6f){nx/=length;ny/=length;nz/=length;}return new RenderFace(texture,vertices,nx,ny,nz);}
    private static int averageColor(NativeImage image){long r=0,g=0,b=0,n=0;int step=Math.max(1,Math.min(image.getWidth(),image.getHeight())/16);for(int y=0;y<image.getHeight();y+=step)for(int x=0;x<image.getWidth();x+=step){int c=image.getColorArgb(x,y),a=(c>>>24)&255;if(a<32)continue;r+=((c>>>16)&255)*a;g+=((c>>>8)&255)*a;b+=(c&255)*a;n+=a;}return n==0?0xFFB0B0B0:0xFF000000|((int)(r/n)<<16)|((int)(g/n)<<8)|(int)(b/n);}
    private static int averageColor(NativeImage image,JsonArray uv){double u0=uv.get(0).getAsDouble(),v0=uv.get(1).getAsDouble(),u1=uv.get(2).getAsDouble(),v1=uv.get(3).getAsDouble();int x0=Math.max(0,Math.min(image.getWidth()-1,(int)Math.floor(Math.min(u0,u1)*image.getWidth()))),x1=Math.max(x0+1,Math.min(image.getWidth(),(int)Math.ceil(Math.max(u0,u1)*image.getWidth()))),y0=Math.max(0,Math.min(image.getHeight()-1,(int)Math.floor(Math.min(v0,v1)*image.getHeight()))),y1=Math.max(y0+1,Math.min(image.getHeight(),(int)Math.ceil(Math.max(v0,v1)*image.getHeight())));long r=0,g=0,b=0,n=0;int sx=Math.max(1,(x1-x0)/8),sy=Math.max(1,(y1-y0)/8);for(int y=y0;y<y1;y+=sy)for(int x=x0;x<x1;x+=sx){int c=image.getColorArgb(x,y),a=(c>>>24)&255;if(a<32)continue;r+=((c>>>16)&255)*a;g+=((c>>>8)&255)*a;b+=(c&255)*a;n+=a;}return n==0?averageColor(image):0xFF000000|((int)(r/n)<<16)|((int)(g/n)<<8)|(int)(b/n);}
    private static String previewColorKey(JsonObject face){JsonArray uv=face.has("uv")?face.getAsJsonArray("uv"):new JsonArray();return str(face,"texture","")+"|"+uv;}
    private static void validateBones(JsonArray bones,int depth)throws IOException{if(depth>32)throw new IOException("Knochenhierarchie ist zu tief");for(JsonElement value:bones){if(!value.isJsonObject())throw new IOException("Ungültiger Knochen");JsonObject bone=value.getAsJsonObject();for(String key:new String[]{"origin","rotation"})if(!bone.has(key)||bone.getAsJsonArray(key).size()!=3)throw new IOException("Ungültiger Knochen-Drehpunkt");if(bone.has("elements")){JsonArray refs=bone.getAsJsonArray("elements");if(refs.size()>512)throw new IOException("Zu viele Knochen-Elemente");for(JsonElement ref:refs)if(ref.getAsInt()<0)throw new IOException("Ungültiger Knochen-Verweis");}if(bone.has("children"))validateBones(bone.getAsJsonArray("children"),depth+1);}}
}
