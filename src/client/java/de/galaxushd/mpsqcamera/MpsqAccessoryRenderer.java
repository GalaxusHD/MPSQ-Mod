package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import de.galaxushd.mpsqcamera.compat.WorldRenderEvents;
import de.galaxushd.mpsqcamera.compat.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import de.galaxushd.mpsqcamera.compat.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;

/** Head accessories rendered only for players actually visible in the current world. */
public final class MpsqAccessoryRenderer {
    private static float petWalkBlend=-1;
    static void drawBlendedPet(WorldRenderContext ctx,String url,double x,double y,double z,float yaw,String clip,float time,float scale,float blend){
        petWalkBlend=blend;
        try{drawCinematicModel(ctx,url,x,y,z,yaw,clip,time,0,0,scale);}finally{petWalkBlend=-1;}
    }
    private static org.joml.Quaternionf crabRotation(Model model,JsonObject bone,float time){
        var idle=findAnimation(model,"idle");var walk=findAnimation(model,"walk");
        float[] a=animationTrack(idle,bone,"rotation",animationTime(idle,time)),b=animationTrack(walk,bone,"rotation",animationTime(walk,time));
        float radians=(float)(Math.PI/180);
        return new org.joml.Quaternionf().rotationZYX(a[2]*radians,a[1]*radians,a[0]*radians).slerp(new org.joml.Quaternionf().rotationZYX(b[2]*radians,b[1]*radians,b[0]*radians),petWalkBlend);
    }
    private static JsonArray recordedNpcs=new JsonArray();
    static String replayScene(){JsonObject snapshot=new JsonObject();JsonArray recordedObjects=objectsSnapshot();MpsqCrate.record(recordedObjects);snapshot.add("objects",recordedObjects);snapshot.add("npcs",recordedNpcs.deepCopy());snapshot.add("timers",MpsqModularTimer.snapshot());return snapshot.toString();}
    private static void recordNpc(JsonObject definition,double x,double y,double z,float yaw,float headYaw,float pitch,float scale,String clip,float time){
        if(MpsqReplayPlayback.active())return;var row=definition.deepCopy();row.addProperty("world_x",x);row.addProperty("world_y",y);row.addProperty("world_z",z);row.addProperty("yaw",yaw);row.addProperty("pitch",pitch);row.addProperty("scale",scale);row.addProperty("replay_head_yaw",headYaw);row.addProperty("replay_clip",clip==null?"idle":clip);row.addProperty("replay_time",time);row.addProperty("animation","none");row.addProperty("face_player",false);recordedNpcs.add(row);
    }
    private record HeadDisplay(float[] translation,float[] rotation,float[] scale){}
    private record Model(JsonArray elements,JsonArray meshes,JsonArray bones,JsonArray animations,Map<String,Identifier> textures,Map<String,Integer> previewColors,List<RenderFace> bakedGeometry,HeadDisplay headDisplay,String skinTextureKey,boolean scenes){}
    private record HeadAngles(float yaw,float pitch){}
    private record HeadBounds(float centerX,float minY,float centerZ,float width,float depth){}
    public static final String WUMPUS_ASSET_ID="mpsq_wumpus";
    private static final String WUMPUS_MODEL_URL="builtin://mpsq/wumpus";
    public static final String HEDGEHOG_HAT_ASSET_ID="nm_hat_hedgehog";
    private static final String HEDGEHOG_HAT_MODEL_URL="builtin://mpsq/nm_hat_hedgehog";
    public static final String BUDGIE_HAT_ASSET_ID="nm_hat_budgie";
    private static final String BUDGIE_HAT_MODEL_URL="builtin://mpsq/nm_hat_budgie";
    public static final String NM_WUMPUS_HAT_ASSET_ID="nm_hat_wumpus";
    private static final String NM_WUMPUS_HAT_MODEL_URL="builtin://mpsq/nm_hat_wumpus";

    private static boolean isBuiltinModel(String url){return MpsqNogAssets.resource(url)!=null|| MpsqScenesClips.resource(url)!=null||MpsqBuiltinFurnitureCatalog.byUrl(url)!=null||WUMPUS_MODEL_URL.equals(url)||HEDGEHOG_HAT_MODEL_URL.equals(url)||BUDGIE_HAT_MODEL_URL.equals(url)||NM_WUMPUS_HAT_MODEL_URL.equals(url);}
    public static String assetPreviewUrl(String assetId,String fallback){if(MpsqNogAssets.resource("builtin://mpsq/"+assetId)!=null)return "builtin://mpsq/"+assetId;if(WUMPUS_ASSET_ID.equals(assetId))return WUMPUS_MODEL_URL;if("discord_hat".equalsIgnoreCase(assetId))return NM_WUMPUS_HAT_MODEL_URL;if(HEDGEHOG_HAT_ASSET_ID.equalsIgnoreCase(assetId))return HEDGEHOG_HAT_MODEL_URL;if(BUDGIE_HAT_ASSET_ID.equalsIgnoreCase(assetId))return BUDGIE_HAT_MODEL_URL;if(NM_WUMPUS_HAT_ASSET_ID.equalsIgnoreCase(assetId))return NM_WUMPUS_HAT_MODEL_URL;return fallback;}
    private static String builtinAccessoryKey(String value){
        String name=value==null?"":value.replace('\\','/').toLowerCase(Locale.ROOT);
        int slash=name.lastIndexOf('/');if(slash>=0)name=name.substring(slash+1);
        int query=name.indexOf('?');if(query>=0)name=name.substring(0,query);
        if(name.endsWith(".bbmodel"))name=name.substring(0,name.length()-8);
        else if(name.endsWith(".json"))name=name.substring(0,name.length()-5);
        name=name.replaceAll("[^a-z0-9]","");
        return switch(name){
            case "nmhathedgehog","hedgehoghat","hedgehoghead","igelhat","igelhead"->HEDGEHOG_HAT_MODEL_URL;
            case "nmhatbudgie","budgiehat","budgiehead","birdhat","birdhead","vogelhat","vogelhead"->BUDGIE_HAT_MODEL_URL;
            // The current Wumpus cosmetic is the same model formerly listed as Discord Head.
            case "nmhatwumpus","wumpus","wumpushat","wumpushead","discordhat","discordhead"->NM_WUMPUS_HAT_MODEL_URL;
            default->null;
        };
    }
    /** Resolves model files that are bundled directly with the mod. */
    public static String builtinAccessoryUrl(String filename){
        String name=filename==null?"":filename.replace('\\','/');
        name=name.substring(name.lastIndexOf('/')+1).toLowerCase(Locale.ROOT);
        return switch(name){
            // Legacy Discord Head entries now resolve to the single Wumpus Hat asset.
            case "discord_hat.json","discord_hat.bbmodel"->NM_WUMPUS_HAT_MODEL_URL;
            case "wumpus.json","wumpus.bbmodel"->WUMPUS_MODEL_URL;
            case "nm_hat_hedgehog.json","nm_hat_hedgehog.bbmodel"->HEDGEHOG_HAT_MODEL_URL;
            case "nm_hat_budgie.json","nm_hat_budgie.bbmodel","nm_hat_budgie (1).bbmodel"->BUDGIE_HAT_MODEL_URL;
            case "nm_hat_wumpus.json","nm_hat_wumpus.bbmodel"->NM_WUMPUS_HAT_MODEL_URL;
            case "nm_hat_owl_long_eared.json","nm_hat_owl_long_eared.bbmodel"->"builtin://mpsq/nm_hat_owl_long_eared";
            case "nm_hat_seagull.json","nm_hat_seagull.bbmodel"->"builtin://mpsq/nm_hat_seagull";
            default->null;
        };
    }
    /** Resolves a catalog row to a bundled model URL when one is available. */
    public static String resolveBuiltinAccessory(JsonObject row){
        if(row==null)return null;
        JsonObject definition=row.has("mpsq_accessories")&&row.get("mpsq_accessories").isJsonObject()
                ?row.getAsJsonObject("mpsq_accessories"):row;
        String builtin;
        for(String key:new String[]{"accessory_key","url","filename"}){String value=str(definition,key,"").replace(".json","");if(!value.startsWith("builtin://"))value="builtin://mpsq/"+value;if(MpsqNogAssets.isHat(value))return value;}
        for(JsonObject source:new JsonObject[]{row,definition}){
            for(String key:new String[]{"filename","path","url","accessory_key","accessory_id","asset_id","model_id","id","display_name"}){
                String value=str(source,key,"");
                for(String known:new String[]{"nm_hat_budgie","nm_hat_hedgehog","nm_hat_wumpus"})if(java.util.UUID.nameUUIDFromBytes(known.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().equals(value))return "builtin://mpsq/"+known;
                builtin=builtinAccessoryUrl(value);if(builtin!=null)return builtin;
                builtin=builtinAccessoryKey(value);if(builtin!=null)return builtin;
            }
        }
        String assetId=str(row,"asset_id",str(definition,"asset_id",str(definition,"model_id","")));
        return assetPreviewUrl(assetId,null);
    }
    /** Collapse legacy/imported aliases without deleting ownership or changing saved IDs. */
    public static JsonArray uniqueAccessories(JsonArray input){
        Map<String,JsonObject> unique=new LinkedHashMap<>();
        for(JsonElement value:input){
            if(!value.isJsonObject())continue;
            JsonObject row=value.getAsJsonObject().deepCopy();String key=resolveBuiltinAccessory(row);
            if(isHatUrl(key)){row.addProperty("url",key);row.addProperty("display_name",key.endsWith("budgie")?"Wellensittich":key.endsWith("hedgehog")?"Igel":key.endsWith("seagull")?"Möwe":key.endsWith("owl_long_eared")?"Eule":"Wumpus");}
            if(key==null)key=str(row,"accessory_id",str(row,"id",row.toString()));
            JsonObject old=unique.get(key);
            boolean equipped=row.has("equipped")&&row.get("equipped").getAsBoolean();
            boolean owned=row.has("owned")&&row.get("owned").getAsBoolean();
            boolean oldEquipped=old!=null&&old.has("equipped")&&old.get("equipped").getAsBoolean();
            if(old==null||equipped||(!oldEquipped&&owned))unique.put(key,row.deepCopy());
        }
        JsonArray result=new JsonArray();unique.values().forEach(result::add);return result;
    }
    static boolean isHatUrl(String url){return MpsqNogAssets.isHat(url)|| HEDGEHOG_HAT_MODEL_URL.equals(url)||BUDGIE_HAT_MODEL_URL.equals(url)||NM_WUMPUS_HAT_MODEL_URL.equals(url);}
    private static final Set<net.minecraft.client.renderer.entity.state.AvatarRenderState> menuHatStates=Collections.newSetFromMap(new WeakHashMap<>());

    private record RenderFace(Identifier texture,float[][] vertices,float nx,float ny,float nz){}
    private record PreviewPoint(double x,double y,double z){}
    private record PreviewPolygon(double[][] points,int color,double depth){}
    private static final class NpcRotation {
        float bodyYaw;
        float relativeHeadYaw;
        float pitch;
        long updatedAt;
        NpcRotation(float bodyYaw,float relativeHeadYaw,float pitch,long updatedAt){this.bodyYaw=bodyYaw;this.relativeHeadYaw=relativeHeadYaw;this.pitch=pitch;this.updatedAt=updatedAt;}
    }
    private static final Map<String,Model> models=new HashMap<>();
    private static final Map<String,Identifier> previews=new HashMap<>();
    private static boolean previewCustomPet;
    private static final Map<String,String> wearers=new HashMap<>();
    private static JsonArray objects=new JsonArray();
    private static JsonArray npcs=new JsonArray();
    /** True when at least one currently loaded NPC needs the vanilla entity-outline post pass. */
    public static boolean hasGlowingNpcs(){
        if(MpsqCinematicActors.hasGlowingModel()||MpsqSceneWorldNpcs.hasGlowingModel())return true;
        for(JsonElement element:npcs){
            if(!element.isJsonObject())continue;
            if(shouldGlow(element.getAsJsonObject()))return true;
        }
        return false;
    }
    static boolean shouldGlow(JsonObject npc){
        String id=str(npc,"id","");var profile=MpsqScenes.stored(id);
        return profile!=null?MpsqNpcGlow.enabled(id,profile.npc):MpsqNpcVisitStore.isGlowEnabled(id,false);
    }
    private static final Set<String> loading=new HashSet<>();
    private static final Map<String,String> localAssetUrls=new HashMap<>();
    private static final Map<String,String> localAssetCategories=new HashMap<>();
    private static final Map<String,NpcRotation> npcRotations=new HashMap<>();
    private static boolean localCatalogRequested;
    private static boolean localAccessoryCatalogRequested;
    private static String tryOnUrl;
    private static String menuPreviewUrl;
    private static boolean menuPlayerPreviewActive;
    private static final Map<net.minecraft.client.renderer.entity.state.AvatarRenderState,String> menuPreviewStates = new java.util.WeakHashMap<>();
    private static net.minecraft.world.phys.Vec3 tryOnStart;
    private static final java.util.BitSet pressedKeys=new java.util.BitSet();
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static long next;
    private static boolean polling;
    private static int generation;
    private static int objectRevision;
    private static String scope="";
    private MpsqAccessoryRenderer(){}
    public static void refresh(){next=0;}
    static boolean isCurrentGeneration(int epoch){return epoch==generation;}
    public static JsonArray npcsSnapshot(){return MpsqSharedPaths.append(npcs.deepCopy());}
    public static JsonArray objectsSnapshot(){JsonArray result=new JsonArray();for(var e:objects)result.add(MpsqFurniturePositions.apply(e.getAsJsonObject()).deepCopy());return result;}
    /** Removes furniture at one world position from the visible snapshot immediately. */
    public static void removeFurnitureAt(int x,int y,int z){
        objectRevision++;
        JsonArray remaining=new JsonArray();
        for(JsonElement element:objects){
            if(!element.isJsonObject()){remaining.add(element.deepCopy());continue;}
            JsonObject furniture=element.getAsJsonObject();
            if(!hasBlockPosition(furniture,x,y,z))remaining.add(furniture.deepCopy());
        }
        objects=remaining;
    }
    private static boolean hasBlockPosition(JsonObject value,int x,int y,int z){
        try{return value.has("x")&&value.has("y")&&value.has("z")
                &&value.get("x").getAsDouble()==x&&value.get("y").getAsDouble()==y&&value.get("z").getAsDouble()==z;}
        catch(RuntimeException ignored){return false;}
    }
    public static double furnitureHitboxCenterY(JsonObject furniture){
        double y=furniture.has("y")?furniture.get("y").getAsDouble():0.0;
        if(MpsqCrate.isCrate(furniture))return y+0.5;
        String url=str(furniture,"url","");Model model=models.get(url);
        if(model==null)return y+0.5;
        float scale=furniture.has("scale")?furniture.get("scale").getAsFloat():1.0f;
        scale=Float.isFinite(scale)?Math.max(0.25f,Math.min(3.0f,scale)):1.0f;
        FurnitureBounds bounds=furnitureBounds(model);
        return y+(bounds.maxY()-bounds.minY())*scale/32.0;
    }
    /** Updates the visible snapshot without discarding skin textures or reloading the NPC. */
    public static void markTutorialCompleted(String npcId){
        for(JsonElement element:npcs){
            if(!element.isJsonObject())continue;
            JsonObject npc=element.getAsJsonObject();
            if(npcId.equals(str(npc,"id",""))){npc.addProperty("tutorial_completed",true);return;}
        }
    }
    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(tryOnUrl!=null&&client.player!=null){
                if(tryOnStart==null)tryOnStart=client.player.position();
                if(client.player.position().distanceToSqr(tryOnStart)>0.0004){clearTryOn();}
            }
            String current=MpsqActionSync.server()+"|"+MpsqActionSync.world();
            if(!scope.equals(current)){scope=current;generation++;polling=false;wearers.clear();builtinOverride=null;objects=new JsonArray();npcs=new JsonArray();loading.clear();npcRotations.clear();MpsqNpcSkinRenderer.clear(client);clearModels(client);localAssetUrls.clear();localAssetCategories.clear();localCatalogRequested=false;localAccessoryCatalogRequested=false;next=0;}
            boolean localWorld=MpsqActionSync.server().isBlank()&&client.getSingleplayerServer()!=null;
            if(client.level==null||!TeamVisibilitySettings.visible()||(!MpsqActionSync.server().isBlank()&&!MpsqActionSync.isMpsqServer())||(!localWorld&&!MpsqApiClient.isReady())||polling||System.currentTimeMillis()<next)return;
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
                // Built-in models such as Wumpus do not depend on the remote catalog.
                // Start loading local NPCs before the API-ready early return below.
                for(JsonElement value:npcs){JsonObject npc=value.getAsJsonObject();loadNpcAsset(npc,epoch);}
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
                int objectVersion=objectRevision;
                MpsqApiClient.get("/objects?server="+java.net.URLEncoder.encode(MpsqActionSync.server(),java.nio.charset.StandardCharsets.UTF_8)+"&world="+java.net.URLEncoder.encode(MpsqActionSync.world(),java.nio.charset.StandardCharsets.UTF_8)).whenComplete((data,error)->client.execute(()->{
                    if(epoch!=generation||objectVersion!=objectRevision||error!=null)return;objects=activeFurniture(data.getAsJsonArray());
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
                    String builtin=builtinAccessoryUrl(url);if(builtin==null)builtin=resolveBuiltinAccessory(row);if(builtin!=null)url=builtin;
                    wearers.put(name,url);
                }
                // Load only assets worn by players in this world, limiting texture memory.
                if(client.level!=null)for(var player:client.level.players()){
                    String url=wearers.get(player.getName().getString().toLowerCase(Locale.ROOT));
                    if(url!=null&&!models.containsKey(url)&&models.size()+loading.size()<64)load(url,epoch);
                }
            }));
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context->{
            var client=net.minecraft.client.Minecraft.getInstance();var matrices=context.matrixStack();var consumers=context.consumers();
            boolean replay=MpsqReplayPlayback.active();if(!replay)recordedNpcs=new JsonArray();
            if(client.level==null||matrices==null||consumers==null||(!replay&&!TeamVisibilitySettings.inArea()))return;
            var replayScene=replay?MpsqReplayPlayback.sceneSnapshot():null;
            JsonArray renderObjects=replay?(replayScene.has("objects")?replayScene.getAsJsonArray("objects"):new JsonArray()):objectsSnapshot();
            JsonArray renderNpcs=replay?(replayScene.has("npcs")?replayScene.getAsJsonArray("npcs"):new JsonArray()):npcsSnapshot();
            MpsqAnimatedTextures.update();
            var camera=context.camera().getPos();
            float tickProgress=context.tickCounter().getGameTimeDeltaPartialTick(false);
            for(var value:renderObjects){var o=value.getAsJsonObject();String objectUrl=o.get("url").getAsString();Model model=models.get(objectUrl);if(model==null){if(replay)load(objectUrl,generation);continue;}
                double x=o.get("x").getAsDouble(),y=o.get("y").getAsDouble(),z=o.get("z").getAsDouble();if(camera.distanceToSqr(x,y,z)>MpsqRenderRange.squared())continue;
                float objectScale=o.has("scale")?o.get("scale").getAsFloat():1.0f;objectScale=Float.isFinite(objectScale)?Math.max(0.25f,Math.min(3.0f,objectScale)):1.0f;if(MpsqCrate.isCrate(o))objectScale=crateScale(model);FurnitureBounds bounds=furnitureBounds(model);matrices.pushPose();matrices.translate(x+0.5-camera.x,y-camera.y,z+0.5-camera.z);matrices.rotate(com.mojang.math.Axis.YP.rotationDegrees(o.get("rotation").getAsFloat()));matrices.translate(-0.5,0,-0.5);matrices.translate(0.5-objectScale*bounds.centerX()/16.0,-objectScale*bounds.minY()/16.0,0.5-objectScale*bounds.centerZ()/16.0);matrices.scale(objectScale/16,objectScale/16,objectScale/16);drawFurniture(model,o,matrices,consumers);matrices.popPose();String furnitureName=o.has("display_name")?o.get("display_name").getAsString():"";MpsqFurnitureNameTagRenderer.draw(context,matrices,consumers,furnitureName,x+0.5,y+2.0*objectScale+0.15,z+0.5);
            }
            for(var value:renderNpcs){var o=replay?value.getAsJsonObject():MpsqScenes.apply(value.getAsJsonObject());if(o.has("scenes_hidden")&&o.get("scenes_hidden").getAsBoolean())continue;String assetId=str(o,"asset_id","");if(WUMPUS_ASSET_ID.equals(assetId))o.addProperty("url",WUMPUS_MODEL_URL);if(!o.has("url")||o.get("url").isJsonNull())continue;String category=str(o,"category","npc_model");boolean playerSkin="npc_skin_normal".equals(category)||"npc_skin_slim".equals(category);boolean slim="npc_skin_slim".equals(category);String url=o.get("url").getAsString();Model model=playerSkin?null:models.get(url);MpsqNpcSkinRenderer.Skin skin=playerSkin?MpsqNpcSkinRenderer.get(url,slim):null;var scenesProfile=replay?null:MpsqScenes.stored(str(o,"id",""));if(!playerSkin&&model==null&&!url.isBlank())loadNpcAsset(o,generation);
                if(playerSkin&&scenesProfile!=null){if(scenesProfile.npc.type.equals("self")&&client.player!=null)skin=new MpsqNpcSkinRenderer.Skin(client.player.getSkin().body().texturePath(),client.player.getSkin().model()==net.minecraft.world.entity.player.PlayerModelType.SLIM);else if(skin==null)prepareCinematicSkin(url,slim);}
                if(replay&&playerSkin&&skin==null)prepareCinematicSkin(url,slim);if(playerSkin?skin==null:model==null)continue;
                double x=o.has("world_x")?o.get("world_x").getAsDouble():o.get("x").getAsDouble()+0.5,y=o.has("world_y")?o.get("world_y").getAsDouble():o.get("y").getAsDouble(),z=o.has("world_z")?o.get("world_z").getAsDouble():o.get("z").getAsDouble()+0.5;var linkedPosition=MpsqCinematicActors.linkedPose(str(o,"id",assetId));if(linkedPosition!=null){x=linkedPosition.x();y=linkedPosition.y();z=linkedPosition.z();}
                boolean wumpus=WUMPUS_ASSET_ID.equals(assetId);String npcId=str(o,"id",assetId);boolean wumpusLooking=wumpus&&MpsqNpcManager.isLookingAt(o);if(!replay&&wumpus&&scenesProfile==null&&MpsqCinematicActors.linkedPose(npcId)==null)MpsqWumpusBehavior.observeLook(npcId,wumpusLooking);
                String clip=wumpus?MpsqWumpusBehavior.animation(npcId):null;
                float size=o.has("scale")?o.get("scale").getAsFloat():1f;String animation=o.has("animation")?o.get("animation").getAsString():"none";float phase=(System.currentTimeMillis()%4000L)/1000f;float bob=animation.equals("bob")?(float)Math.sin(phase*Math.PI*2)*0.08f:0;float pulse=animation.equals("pulse")?1f+(float)Math.sin(phase*Math.PI*2)*0.08f:1f;float configuredYaw=o.has("yaw")?o.get("yaw").getAsFloat():0f,pitch=o.has("pitch")?o.get("pitch").getAsFloat():0f,relativeHeadYaw=0f;boolean face=o.has("face_player")&&o.get("face_player").getAsBoolean();float npcHeight=playerSkin?1.8f*size:(wumpus?1.2f*size:size);
                float bodyYaw=configuredYaw,targetYaw=bodyYaw;

                boolean trackingPlayer=!replay&&client.player!=null&&(wumpus||face)&&MpsqNpcManager.withinLookRange(client.player.position(),new net.minecraft.world.phys.Vec3(x,y,z));
                // Tracking is an absolute world direction, not an offset from placement yaw.
                if(trackingPlayer&&wumpus){LookAngles look=calculateVillagerLookAngles(x,y,z,size,false,client.player);targetYaw=look.yaw();pitch=0f;}
                else if(trackingPlayer){LookAngles look=calculateVillagerLookAngles(x,y,z,size,playerSkin,client.player);targetYaw=look.yaw();pitch=look.pitch();}
                String rotationKey=o.has("id")?o.get("id").getAsString():x+":"+y+":"+z;
                if(trackingPlayer){if(wumpus)bodyYaw=smoothFullBodyYaw(rotationKey,bodyYaw,targetYaw);else{float[] smoothed=smoothNpcRotation(rotationKey,bodyYaw,targetYaw,pitch);bodyYaw=smoothed[0];relativeHeadYaw=smoothed[1];pitch=smoothed[2];}}
                else npcRotations.remove(rotationKey);
                if(animation.equals("turn"))bodyYaw+=phase*90f;
                if(animation.equals("nod"))pitch+=(float)Math.sin(phase*Math.PI*2)*12f;
                if(animation.equals("tilt"))bodyYaw+=(float)Math.sin(phase*Math.PI*2)*14f;
                if(animation.equals("look_around"))bodyYaw+=(float)Math.sin(phase*Math.PI)*28f;
                if(animation.equals("shake"))bodyYaw+=(float)Math.sin(phase*Math.PI*8)*5f;
                if(animation.equals("wave"))pulse=1f+(float)Math.sin(phase*Math.PI*2)*0.035f;
                var cinematic=MpsqCinematicActors.linkedPose(npcId);if(cinematic==null&&scenesProfile!=null)cinematic=MpsqScenes.pose(scenesProfile);
                if(cinematic!=null){x=cinematic.x();y=cinematic.y();z=cinematic.z();bodyYaw=cinematic.yaw();relativeHeadYaw=cinematic.headYaw();pitch=cinematic.pitch();clip=cinematic.clip();bob=0;pulse=1;}
                if(replay){x=o.get("world_x").getAsDouble();y=o.get("world_y").getAsDouble();z=o.get("world_z").getAsDouble();bodyYaw=o.get("yaw").getAsFloat();pitch=o.get("pitch").getAsFloat();relativeHeadYaw=o.get("replay_head_yaw").getAsFloat();clip=str(o,"replay_clip","idle");cinematic=new MpsqCinematicActors.Pose(x,y,z,bodyYaw,relativeHeadYaw,pitch,clip,o.get("replay_time").getAsFloat());bob=0;pulse=1;}
                else recordNpc(o,x,y+bob,z,bodyYaw,relativeHeadYaw,pitch,size*pulse,clip,cinematic!=null?cinematic.clipTime():wumpus?MpsqWumpusBehavior.elapsedSeconds(npcId):0);
                if(camera.distanceToSqr(x,y,z)>MpsqRenderRange.squared())continue;
                // Placement buttons snap to 15 degrees; tracking and animation stay continuous.
                String task=o.has("task_type")?o.get("task_type").getAsString():"none";
                boolean hasSpecialRole=switch(task){case "accessories","quest","tutorial"->true;default->false;};
                String defaultColor=wumpus?"#7582e3":defaultGlowColor(task);
                String configuredGlow=str(o,"glow_color",defaultColor);
                if("none".equalsIgnoreCase(configuredGlow))configuredGlow=defaultColor;
                int glow=MpsqNpcGlow.color(task,assetId);
                // Vanilla's outline post-pass ignores world depth and therefore shows
                // through blocks. Only submit an NPC outline while its body is visible
                // from the camera; the regular model render continues to use depth.
                boolean glowing=shouldGlow(o)&&MpsqNpcGlow.clear(x,y,z,size);
                if(playerSkin&&cinematic!=null&&MpsqScenesClips.get(cinematic.clip())!=null){
                    drawScenesActor(context,skin,cinematic,size,glowing?glow:0,scenesProfile==null?null:scenesProfile.npc);
                }
                else if(playerSkin&&scenesProfile==null){var state=MpsqNpcSkinRenderer.createState(skin,bodyYaw,relativeHeadYaw,pitch,(System.currentTimeMillis()%100000L)/50.0f,glowing);if(scenesProfile!=null)MpsqScenes.decorate(state,scenesProfile);int light=net.minecraft.util.LightCoordsUtil.getLightCoords(client.level,net.minecraft.core.BlockPos.containing(x,y,z));MpsqNpcSkinRenderer.render(state,x-camera.x,y+bob-camera.y,z-camera.z,size*pulse,matrices,consumers,light,glow);}
                else if(!playerSkin){float clipTime=cinematic!=null?cinematic.clipTime():wumpus?MpsqWumpusBehavior.elapsedSeconds(npcId):0;if(wumpus&&cinematic==null)pitch=0f;matrices.pushPose();matrices.translate(x-camera.x,y+bob-camera.y,z-camera.z);matrices.rotate(com.mojang.math.Axis.YP.rotationDegrees(-bodyYaw+(wumpus||"builtin://mpsq/nm_seagull".equals(url)?180f:0f)));matrices.rotate(com.mojang.math.Axis.XP.rotationDegrees(cinematic==null?pitch:0));matrices.scale(size/16f*pulse,size/16f*pulse,size/16f*pulse);drawCinematicBbModel(model,matrices,consumers,clip,clipTime,cinematic==null?0:relativeHeadYaw,cinematic==null?0:pitch);
                    if(glowing){var outline=consumers.outline(glow);drawCinematicBbModel(model,matrices,outline,clip,clipTime,cinematic==null?0:relativeHeadYaw,cinematic==null?0:pitch);outline.endBatch();}
                    matrices.popPose();}
                boolean tutorialDone=o.has("tutorial_completed")&&o.get("tutorial_completed").getAsBoolean();
                String tag=switch(task){case "accessories"->"accessories";case "quest"->"quests";case "tutorial"->"wiki";default->null;};
                if(tag!=null&&scenesProfile==null){
                    // Keep the tag's world position fixed above the NPC. Only the
                    // billboard itself faces the camera; translating it toward the
                    // camera makes it orbit around the NPC as the viewer moves.
                    double tagX=x-camera.x,tagZ=z-camera.z;
                    float tagScale=size,tagHeight=0.20f*tagScale;
                    // Vanilla player nameplates use a 0.5-block offset above the head;
                    // keep NPC role tags at two thirds of that distance, scaled with the NPC.
                    float tagVerticalOffset=0.5f*(2f/3f)*tagScale;
                    float aspect=switch(tag){case "accessories"->2624f/320f;case "quests"->1504f/320f;case "wiki"->2176f/320f;default->1952f/320f;};
                    drawBillboard(context,matrices,consumers,Identifier.fromNamespaceAndPath("mpsqcamera","textures/gui/npc_tags/"+tag+".png"),tagX,y+npcHeight+bob+tagVerticalOffset-camera.y,tagZ,tagHeight*aspect,tagHeight);
                    if("tutorial".equals(task)&&!tutorialDone){float hover=(float)Math.sin(System.currentTimeMillis()/360.0)*0.07f*tagScale;float iconOffset=tagVerticalOffset+0.36f*tagScale;drawBillboard(context,matrices,consumers,Identifier.fromNamespaceAndPath("mpsqcamera","textures/gui/npc_tags/tutorial_exclamation.png"),tagX,y+npcHeight+bob+iconOffset+hover-camera.y,tagZ,0.42f*tagScale,0.42f*tagScale);}}
                if(wumpus&&scenesProfile==null&&npcBoolean(o,"discord_tag_enabled","discordTagEnabled")){
                    double tagX=x-camera.x,tagZ=z-camera.z;
                    float tagScale=size,tagHeight=0.20f*tagScale,tagGap=0.5f*(2f/3f)*tagScale;
                    float tagCenterOffset=tagGap+tagHeight*0.5f;
                    drawBillboard(context,matrices,consumers,Identifier.fromNamespaceAndPath("mpsqcamera","textures/gui/npc_tags/discord.png"),tagX,y+npcHeight+bob+tagCenterOffset-camera.y,tagZ,tagHeight*(2176f/320f),tagHeight);
                }
            }
        });
    }
    private static boolean hasClearView(Minecraft client,net.minecraft.world.phys.Vec3 camera,double x,double y,double z){
        if(client.level==null||client.player==null)return false;
        var target=new net.minecraft.world.phys.Vec3(x,y,z);
        return client.level.clip(new ClipContext(camera,target,ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,client.player)).getType()==net.minecraft.world.phys.HitResult.Type.MISS;
    }
    private record LookAngles(float yaw,float pitch){}

    /** Uses the same eye-to-eye target as vanilla look-at behavior: the NPC's
     * eye point tracks the player's actual eye height, including crouching. */
    private static LookAngles calculateVillagerLookAngles(double npcX,double npcY,double npcZ,float npcScale,boolean playerSkin,net.minecraft.client.player.LocalPlayer player){
        double dx=player.getX()-npcX;
        double dz=player.getZ()-npcZ;
        double npcEyeY=npcY+(playerSkin?1.62d*npcScale:0.9d*npcScale);
        double dy=player.getEyeY()-npcEyeY;
        double horizontalDistance=Math.sqrt(dx*dx+dz*dz);
        float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
        float pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.max(1.0e-4,horizontalDistance)));
        return new LookAngles(yaw,pitch);
    }
    private static float[] smoothNpcRotation(String key,float initialBodyYaw,float targetYaw,float targetPitch){
        long now=System.nanoTime();
        NpcRotation rotation=npcRotations.get(key);
        final float headLimit=75f;
        if(rotation==null){
            rotation=new NpcRotation(initialBodyYaw,0f,targetPitch,now);
            npcRotations.put(key,rotation);
        }
        float deltaSeconds=Math.max(0f,Math.min(0.1f,(now-rotation.updatedAt)/1_000_000_000f));
        float amount=1f-(float)Math.exp(-deltaSeconds/0.14f);
        float wantedRelative=wrapDegrees(targetYaw-rotation.bodyYaw);
        boolean headReachedLimit=Math.abs(rotation.relativeHeadYaw)>=headLimit-0.5f
                && Math.signum(rotation.relativeHeadYaw)==Math.signum(wantedRelative);
        // Keep the body still until the head itself reaches its yaw limit.
        if(Math.abs(wantedRelative)>headLimit&&headReachedLimit){
            float bodyTarget=wrapDegrees(targetYaw-Math.copySign(headLimit,wantedRelative));
            rotation.bodyYaw=wrapDegrees(rotation.bodyYaw+wrapDegrees(bodyTarget-rotation.bodyYaw)*amount);
        }
        float headTarget=Math.max(-headLimit,Math.min(headLimit,wrapDegrees(targetYaw-rotation.bodyYaw)));
        rotation.relativeHeadYaw+=wrapDegrees(headTarget-rotation.relativeHeadYaw)*amount;
        rotation.pitch+=(targetPitch-rotation.pitch)*amount;
        rotation.updatedAt=now;
        if(npcRotations.size()>512)npcRotations.entrySet().removeIf(entry->now-entry.getValue().updatedAt>60_000_000_000L);
        return new float[]{rotation.bodyYaw,rotation.relativeHeadYaw,rotation.pitch};
    }
    private static float smoothFullBodyYaw(String key,float initialYaw,float targetYaw){
        long now=System.nanoTime();NpcRotation rotation=npcRotations.get(key);
        if(rotation==null){rotation=new NpcRotation(initialYaw,0f,0f,now);npcRotations.put(key,rotation);}
        float deltaSeconds=Math.max(0f,Math.min(0.1f,(now-rotation.updatedAt)/1_000_000_000f));
        float amount=1f-(float)Math.exp(-deltaSeconds/0.14f);
        rotation.bodyYaw=wrapDegrees(rotation.bodyYaw+wrapDegrees(targetYaw-rotation.bodyYaw)*amount);
        rotation.relativeHeadYaw=0f;rotation.pitch=0f;rotation.updatedAt=now;
        if(npcRotations.size()>512)npcRotations.entrySet().removeIf(entry->now-entry.getValue().updatedAt>60_000_000_000L);
        return rotation.bodyYaw;
    }
    private static float wrapDegrees(float degrees){degrees%=360f;if(degrees>=180f)degrees-=360f;if(degrees< -180f)degrees+=360f;return degrees;}
    private static void mapCatalog(JsonArray values,String fallback){for(JsonElement value:values){if(!value.isJsonObject())continue;JsonObject asset=value.getAsJsonObject();if(asset.has("id")&&asset.has("url")){String id=asset.get("id").getAsString();localAssetUrls.put(id,asset.get("url").getAsString());localAssetCategories.put(id,str(asset,"category",fallback));}}}
    private static void applyLocalEquippedAccessory(JsonArray values){String id=MpsqLocalWorldStore.equipped();var player=Minecraft.getInstance().player;if(player==null)return;String playerKey=player.getName().getString().toLowerCase(Locale.ROOT);if(id==null){wearers.remove(playerKey);return;}String builtin=assetPreviewUrl(id,null);if(builtin!=null){setLocalEquippedAccessory(builtin);return;}for(JsonElement e:values){if(!e.isJsonObject())continue;JsonObject row=e.getAsJsonObject();if(id.equals(str(row,"accessory_id",""))){builtin=resolveBuiltinAccessory(row);String url=builtin!=null?builtin:str(row,"url","");if(!url.isBlank()){setLocalEquippedAccessory(url);return;}}}}
    /** Applies the just-equipped model immediately while the server wearer list catches up. */
    private static String builtinOverride;
    static boolean hasBuiltinOverride(){return builtinOverride!=null;}
    static void setBuiltinOverride(String url){builtinOverride=url;setLocalEquippedAccessory(url);}
    public static void setLocalEquippedAccessory(String url){var player=Minecraft.getInstance().player;if(player==null)return;String playerKey=player.getName().getString().toLowerCase(Locale.ROOT);if(url==null||url.isBlank()){wearers.remove(playerKey);return;}wearers.put(playerKey,url);if(!models.containsKey(url)&&models.size()+loading.size()<64)load(url,generation);}
    public static void tryOn(String url){tryOnUrl=url;tryOnStart=Minecraft.getInstance().player==null?null:Minecraft.getInstance().player.position();pressedKeys.clear();if(url!=null&&Minecraft.getInstance().player!=null)de.galaxushd.mpsqcamera.compat.PlayerMessages.send(Minecraft.getInstance().player, net.minecraft.network.chat.Component.literal("Vorschau aktiv · Bewegung oder eine Taste beendet sie (F5 bleibt erlaubt)."),true);}
    /** Temporarily scopes accessory rendering to the player rendered inside the collection GUI. */
    public static void beginMenuPlayerPreview(String url){menuPreviewUrl=url;menuPlayerPreviewActive=true;}
    public static void endMenuPlayerPreview(){menuPlayerPreviewActive=false;}
    public static void captureMenuPlayerPreview(net.minecraft.client.renderer.entity.state.AvatarRenderState state){
        menuPreviewStates.remove(state);
        menuHatStates.remove(state);
        if(menuPlayerPreviewActive)menuHatStates.add(state);
        if(menuPlayerPreviewActive && menuPreviewUrl!=null && !menuPreviewUrl.isBlank())menuPreviewStates.put(state,menuPreviewUrl);
    }
    public static void captureWornAccessory(net.minecraft.client.player.AbstractClientPlayer player,net.minecraft.client.renderer.entity.state.AvatarRenderState state){
        if(menuPreviewStates.containsKey(state)||menuPlayerPreviewActive)return;
        if(!TeamVisibilitySettings.visible()||player.isInvisible()||player.isSpectator())return;
        String url=player==Minecraft.getInstance().player&&tryOnUrl!=null?tryOnUrl:player==Minecraft.getInstance().player&&builtinOverride!=null?builtinOverride:wearers.get(player.getGameProfile().name().toLowerCase(Locale.ROOT));
        if(url!=null)menuPreviewStates.put(state,url);
    }
    /** Called inside the player's model transform, just like vanilla helmet layers. */
    public static void renderAttachedHat(net.minecraft.client.renderer.entity.state.AvatarRenderState state,net.minecraft.client.model.geom.ModelPart head,PoseStack matrices,MultiBufferSource consumers){
        String url=menuPreviewStates.get(state);if(url==null)return;
        Model model=models.get(url);
        if(model==null){if(models.size()+loading.size()<64)load(url,generation);return;}
        matrices.pushPose();
        head.translateAndRotate(matrices);
        matrices.scale(-1f,-1f,1f);
        matrices.rotate(Axis.YP.rotationDegrees(180));
        // These hats were authored in different forward orientations. Apply
        // these corrections only to the worn model; the standalone slot preview
        // uses MpsqAccessoryPreview and intentionally keeps its current pose.
        if(BUDGIE_HAT_MODEL_URL.equals(url))matrices.rotate(Axis.YP.rotationDegrees(0));
        if("builtin://mpsq/nm_hat_owl_long_eared".equals(url)||"builtin://mpsq/nm_hat_seagull".equals(url)){
            matrices.rotate(Axis.YP.rotationDegrees(180));
            matrices.scale(.72f,.72f,.72f);
        }
        HeadDisplay display=model.headDisplay();
        if(display!=null){
            matrices.translate(display.translation()[0]/16.0,.225+display.translation()[1]/16.0,display.translation()[2]/16.0);
            matrices.rotate(Axis.ZP.rotationDegrees(display.rotation()[2]));
            matrices.rotate(Axis.YP.rotationDegrees(display.rotation()[1]));
            matrices.rotate(Axis.XP.rotationDegrees(display.rotation()[0]));
            matrices.scale(display.scale()[0]/16,display.scale()[1]/16,display.scale()[2]/16);
            matrices.translate(-8,-8,-8);
        }else{
            HeadBounds bounds=headBounds(model);float fit=Math.min(1f,10f/Math.max(bounds.width(),bounds.depth()))/16;
            matrices.translate(-bounds.centerX()*fit,.5-bounds.minY()*fit,-bounds.centerZ()*fit);matrices.scale(fit,fit,fit);
        }
        if(isHatUrl(url))drawHatGeometry(model,matrices,consumers,menuHatStates.contains(state));
        else drawBbModel(model,matrices,consumers,0xFFFFFFFF);
        matrices.popPose();
    }
    private static HeadAngles interpolatedHeadAngles(net.minecraft.world.entity.player.Player player,float tickProgress){
        return new HeadAngles(Mth.rotLerp(tickProgress,player.yHeadRotO,player.getYHeadRot()),player.getViewXRot(tickProgress));
    }

    private static void applyHeadAccessoryTransform(Model model,PoseStack matrices,float headYaw,float headPitch,float headHeight,float eyeHeight){
        float headCenter=headHeight-0.25f;
        if(model.headDisplay()!=null){
            HeadDisplay display=model.headDisplay();
            matrices.translate(0.0,headCenter,0.0);
            matrices.rotate(Axis.YP.rotationDegrees(-headYaw));
            matrices.rotate(Axis.XP.rotationDegrees(headPitch));
            matrices.translate(display.translation()[0]/16.0,eyeHeight-headCenter+display.translation()[1]/16.0,display.translation()[2]/16.0);
            matrices.rotate(Axis.ZP.rotationDegrees(display.rotation()[2]));
            matrices.rotate(Axis.YP.rotationDegrees(display.rotation()[1]));
            matrices.rotate(Axis.XP.rotationDegrees(display.rotation()[0]));
            matrices.scale(display.scale()[0]/16.0f,display.scale()[1]/16.0f,display.scale()[2]/16.0f);
            matrices.translate(-8.0,-8.0,-8.0);
            return;
        }
        HeadBounds bounds=headBounds(model);
        float horizontalSpan=Math.max(bounds.width(),bounds.depth());
        // A Minecraft player head is 8x8 model units. A 10-unit cap leaves a
        // small, deliberate overhang while shrinking oversized imported heads.
        float fit=horizontalSpan>0?Math.min(1.0f,10.0f/horizontalSpan):1.0f;
        float modelScale=fit/16.0f;
        matrices.translate(0.0,headCenter,0.0);
        matrices.rotate(Axis.YP.rotationDegrees(-headYaw));
        // Positive Minecraft pitch follows the player's downward head movement.
        matrices.rotate(Axis.XP.rotationDegrees(headPitch));
        matrices.translate(-bounds.centerX()*modelScale,headHeight-headCenter-bounds.minY()*modelScale,-bounds.centerZ()*modelScale);
        matrices.scale(modelScale,modelScale,modelScale);
    }
    private static HeadBounds headBounds(Model model){
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY;
        float maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
        for(RenderFace face:model.bakedGeometry())for(float[] vertex:face.vertices()){
            minX=Math.min(minX,vertex[0]);maxX=Math.max(maxX,vertex[0]);
            minY=Math.min(minY,vertex[1]);maxY=Math.max(maxY,vertex[1]);
            minZ=Math.min(minZ,vertex[2]);maxZ=Math.max(maxZ,vertex[2]);
        }
        if(!Float.isFinite(minX))return new HeadBounds(8.0f,0.0f,8.0f,16.0f,16.0f);
        return new HeadBounds((minX+maxX)*0.5f,minY,(minZ+maxZ)*0.5f,maxX-minX,maxZ-minZ);
    }
    public static void onPreviewKey(int key) { if(key!=com.mojang.blaze3d.platform.InputConstants.KEY_F5) clearTryOn(); }
    private static void clearTryOn(){tryOnUrl=null;tryOnStart=null;pressedKeys.clear();}
    /** Draws a compact isometric model thumbnail with texture-derived face colors. */
    static void prepareCinematicSkin(String url,boolean slim){if(url!=null&&!url.isBlank())MpsqNpcSkinRenderer.load(url,generation,slim);}
    static java.util.List<String> cinematicClips(String url){
        Model model=models.get(url);if(model==null){if(url!=null&&!url.isBlank()&&models.size()+loading.size()<64)load(url,generation);return java.util.List.of("idle");}
        var result=new java.util.LinkedHashSet<String>();result.add("idle");if(model.animations()!=null)for(var clip:model.animations())if(clip.isJsonObject())result.add(str(clip.getAsJsonObject(),"name","idle"));return java.util.List.copyOf(result);
    }
    static void drawCinematicModel(WorldRenderContext ctx,String url,double x,double y,double z,float yaw,String clip,float clipTime,float headYaw,float pitch){drawCinematicModel(ctx,url,x,y,z,yaw,clip,clipTime,headYaw,pitch,1);}
    static void drawCinematicModel(WorldRenderContext ctx,String url,double x,double y,double z,float yaw,String clip,float clipTime,float headYaw,float pitch,float scale){drawCinematicModel(ctx,url,x,y,z,yaw,clip,clipTime,headYaw,pitch,scale,false);}
    static void drawCinematicModel(WorldRenderContext ctx,String url,double x,double y,double z,float yaw,String clip,float clipTime,float headYaw,float pitch,float scale,boolean glow){drawCinematicModel(ctx,url,x,y,z,yaw,clip,clipTime,headYaw,pitch,scale,glow?0xFFEC2F53:0);}
    static void drawCinematicModel(WorldRenderContext ctx,String url,double x,double y,double z,float yaw,String clip,float clipTime,float headYaw,float pitch,float scale,int glowColor){
        if(url==null||url.isBlank())return;
        Model model=models.get(url);
        if(model==null){if((isBuiltinModel(url)||MpsqApiClient.isReady())&&(models.size()+loading.size()<64||url.matches("builtin://mpsq/nm_chess_(white|black)_(king|queen|rook|bishop|knight|pawn)")))load(url,generation);return;}
        var m=ctx.matrixStack();var buffers=ctx.consumers();if(m==null||buffers==null)return;
        var camera=ctx.camera().getPos();
        double limit=MpsqCustomPetRenderer.isModelUrl(url)?MpsqPetGrounding.viewDistanceSquared():MpsqRenderRange.squared();if(camera.distanceToSqr(x,y,z)>limit)return;
        m.pushPose();m.translate(x-camera.x,y-camera.y,z-camera.z);
        boolean reverse=MpsqCustomPetRenderer.isModelUrl(url)||WUMPUS_MODEL_URL.equals(url)||"builtin://mpsq/nm_seagull".equals(url)||"builtin://mpsq/bs_vm_crab".equals(url)||"builtin://mpsq/bs_vm_penguin".equals(url)||"builtin://mpsq/baby_happy_ghast_mount".equals(url)||"builtin://mpsq/nm_colin_the_creeper".equals(url)||"builtin://mpsq/nm_exo_the_enderman".equals(url)||"builtin://mpsq/nm_slak_the_skeleton".equals(url)||"builtin://mpsq/nm_zekk_the_zombie".equals(url);
        m.rotate(com.mojang.math.Axis.YP.rotationDegrees(-yaw+(reverse?180f:0f)));m.scale(scale/16f,scale/16f,scale/16f);
        drawCinematicBbModel(model,m,buffers,clip,clipTime,headYaw,pitch);if(glowColor!=0&&MpsqNpcGlow.clear(x,y,z,scale)){var outline=buffers.outline(glowColor);drawCinematicBbModel(model,m,outline,clip,clipTime,headYaw,pitch);outline.endBatch();}m.popPose();
    }
    static void drawScenesActor(WorldRenderContext ctx,MpsqNpcSkinRenderer.Skin skin,MpsqCinematicActors.Pose pose,float scale){drawScenesActor(ctx,skin,pose,scale,0);}
    static void drawScenesActor(WorldRenderContext ctx,MpsqNpcSkinRenderer.Skin skin,MpsqCinematicActors.Pose pose,float scale,int glowColor){drawScenesActor(ctx,skin,pose,scale,glowColor,null);}
    private static MpsqCinematicScene.Npc scenesCosmetic;
    static void drawScenesActor(WorldRenderContext ctx,MpsqNpcSkinRenderer.Skin skin,MpsqCinematicActors.Pose pose,float scale,int glowColor,MpsqCinematicScene.Npc settings){
        var clip=MpsqScenesClips.get(pose.clip());if(clip==null)return;String url=MpsqScenesClips.url(clip)+(skin.slim()?"_slim":"");Model model=models.get(url);
        if(model==null){if(models.size()+loading.size()<64)load(url,generation);return;}
        var textures=new HashMap<>(model.textures());textures.put("0",skin.texture());
        Model selected=new Model(model.elements(),model.meshes(),model.bones(),model.animations(),textures,model.previewColors(),model.bakedGeometry(),model.headDisplay(),model.skinTextureKey(),true);
        var m=ctx.matrixStack();var buffers=ctx.consumers();if(m==null||buffers==null)return;var camera=ctx.camera().getPos();
        m.pushPose();m.translate(pose.x()-camera.x,pose.y()-camera.y,pose.z()-camera.z);m.rotate(Axis.YP.rotationDegrees(180-pose.yaw()));m.scale(scale/16f,scale/16f,scale/16f);
        scenesCosmetic=settings;try{drawCinematicBbModel(selected,m,buffers,clip.clip(),pose.clipTime(),pose.headYaw(),pose.pitch());}finally{scenesCosmetic=null;}if(glowColor!=0&&MpsqNpcGlow.clear(pose.x(),pose.y(),pose.z(),scale)){var outline=buffers.outline(glowColor);drawCinematicBbModel(selected,m,outline,clip.clip(),pose.clipTime(),pose.headYaw(),pose.pitch());outline.endBatch();}m.popPose();
    }
    public static void drawGuiPreview(net.minecraft.client.gui.GuiGraphicsExtractor context,String url,int x,int y,float size){if(isHatUrl(url)){MpsqAccessoryPreview.draw(context,url,x,y,size);return;}int px=Math.max(20,Math.min(64,Math.round(20*size)));Model model=models.get(url);if(model==null){if(url!=null&&(isBuiltinModel(url)||MpsqApiClient.isReady())&&models.size()+loading.size()<64)load(url,generation);context.fill(x-px/2-1,y-px/2-1,x+px/2+1,y+px/2+1,0xAA222222);return;}Identifier preview=previews.computeIfAbsent(url,key->bakePreview(key,model));context.fill(x-px/2-1,y-px/2-1,x+px/2+1,y+px/2+1,0xFF151820);if(preview!=null)context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,preview,x-px/2,y-px/2,0,0,px,px,48,48,48,48);}
    /** Animated, centred entity preview for custom animal-pet slots. */
    static void renderPetPreview(String id,PoseStack matrices,MultiBufferSource buffers,float time){
        String url=MpsqCustomPetRenderer.url(id);Model model=models.get(url);
        if(model==null){if(models.size()+loading.size()<64)load(url,generation);return;}
        FurnitureBounds bounds=furnitureBounds(model);
        float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,minZ=Float.POSITIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
        for(RenderFace face:model.bakedGeometry)for(float[] vertex:face.vertices){minX=Math.min(minX,vertex[0]);maxX=Math.max(maxX,vertex[0]);minZ=Math.min(minZ,vertex[2]);maxZ=Math.max(maxZ,vertex[2]);}
        float projectedMin=Float.POSITIVE_INFINITY,projectedMax=Float.NEGATIVE_INFINITY;
        for(RenderFace face:model.bakedGeometry)for(float[] vertex:face.vertices){float projected=vertex[0]*.9063078f+vertex[2]*.4226183f;projectedMin=Math.min(projectedMin,projected);projectedMax=Math.max(projectedMax,projected);}
        float span=Math.max(bounds.maxY()-bounds.minY(),Float.isFinite(projectedMin)?projectedMax-projectedMin:16f);float fit=28f/Math.max(1f,span);
        // Match the centre supplied to InventoryScreen by the pet menu.
        matrices.pushPose();matrices.translate(0,.84f,0);matrices.rotate(Axis.YP.rotationDegrees(25));matrices.scale(fit/16f,fit/16f,fit/16f);matrices.translate(-bounds.centerX(),-(bounds.minY()+bounds.maxY())*.5f-(id.equals("mpsq_baby_happy_ghast")?9:0),-bounds.centerZ());drawBbModel(model,matrices,buffers,0xFFFFFFFF,"idle",time);matrices.popPose();
    }
    public static void drawGuiSkinPreview(net.minecraft.client.gui.GuiGraphicsExtractor context,String url,int x,int y,float size,boolean slim){int px=Math.max(20,Math.min(64,Math.round(20*size)));MpsqNpcSkinRenderer.load(url,generation,slim);MpsqNpcSkinRenderer.Skin skin=MpsqNpcSkinRenderer.get(url,slim);context.fill(x-px/2-1,y-px/2-1,x+px/2+1,y+px/2+1,0xFF151820);if(skin!=null)context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,skin.texture(),x-px/2,y-px/2,8,8,px,px,8,8,64,64);}
    /** Actual textured 3D geometry, shared by slot thumbnails and worn hats. */
    static void renderHatIcon(String url,PoseStack matrices,MultiBufferSource buffers){
        Model model=models.get(url);
        if(model==null){if(models.size()+loading.size()<64)load(url,generation);return;}
        FurnitureBounds b=furnitureBounds(model);
        HeadBounds head=headBounds(model);
        float span=Math.max(b.maxY()-b.minY(),Math.max(head.width(),head.depth()));
        float scale=1f/Math.max(1f,span);
        matrices.pushPose();
        matrices.translate(0,.5,0);
        // Match the equipped head orientation, with a small three-quarter turn.
        matrices.rotate(Axis.YP.rotationDegrees(25));
        matrices.scale(scale,scale,scale);
        matrices.translate(-b.centerX(),-(b.minY()+b.maxY())*.5f,-b.centerZ());
        drawHatGeometry(model,matrices,buffers,true);
        matrices.popPose();
    }
    private static void drawHatGeometry(Model model,PoseStack matrices,MultiBufferSource buffers,boolean gui){
        for(RenderFace face:model.bakedGeometry()){
            var buffer=buffers.getBuffer(gui
                    ?net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(face.texture())
                    :net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(face.texture()));
            for(int i=0;i<4;i++){
                float[] v=face.vertices()[Math.min(i,face.vertices().length-1)];
                buffer.addVertex(matrices.last(),v[0],v[1],v[2]).setColor(0xFFFFFFFF).setUv(v[3],v[4])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                        .setNormal(matrices.last(),face.nx(),face.ny(),face.nz());
            }
        }
    }

    private static void clearModels(Minecraft client){MpsqAnimatedTextures.clear();figureWobbles.clear();MpsqCrate.clear();for(Model model:models.values())for(Identifier id:model.textures.values())client.getTextureManager().release(id);models.clear();for(Identifier id:previews.values())client.getTextureManager().release(id);previews.clear();}
    private static Identifier bakePreview(String url,Model model){previewCustomPet=MpsqCustomPetRenderer.isModelUrl(url);try{List<PreviewPolygon> polygons=new ArrayList<>();java.util.BitSet used=new java.util.BitSet(model.elements.size());for(JsonElement bone:model.bones)collectPreviewBone(model,bone.getAsJsonObject(),new ArrayList<>(),used,polygons);for(int i=used.nextClearBit(0);i<model.elements.size();i=used.nextClearBit(i+1))collectPreviewElement(model,i,new ArrayList<>(),polygons);for(JsonElement mesh:model.meshes){JsonObject m=mesh.getAsJsonObject();JsonArray vertices=m.getAsJsonArray("vertices"),indices=m.getAsJsonArray("indices");int color=model.previewColors.getOrDefault(str(m,"texture",""),0xFFB0B0B0);for(int i=0;i+2<indices.size();i+=3){double[][] p=new double[3][2];double depth=0;for(int k=0;k<3;k++){JsonArray v=vertices.get(indices.get(i+k).getAsInt()).getAsJsonArray();PreviewPoint q=project(v.get(0).getAsDouble(),v.get(1).getAsDouble(),v.get(2).getAsDouble());p[k][0]=q.x;p[k][1]=q.y;depth+=q.y;}polygons.add(new PreviewPolygon(p,color,depth/3));}}previewCustomPet=false;
        if(polygons.isEmpty())return null;double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;for(PreviewPolygon poly:polygons)for(double[] p:poly.points){minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);}double span=Math.max(1,Math.max(maxX-minX,maxY-minY)),scale=42/span,offX=(48-(maxX-minX)*scale)/2-minX*scale,offY=(48-(maxY-minY)*scale)/2-minY*scale;polygons.sort(java.util.Comparator.comparingDouble(PreviewPolygon::depth));NativeImage image=new NativeImage(48,48,true);for(PreviewPolygon poly:polygons){double[][] pts=new double[poly.points.length][2];for(int i=0;i<pts.length;i++){pts[i][0]=poly.points[i][0]*scale+offX;pts[i][1]=poly.points[i][1]*scale+offY;}fillPreviewPolygon(image,pts,poly.color);}Identifier id=Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID,"model_preview/"+UUID.randomUUID());DynamicTexture texture=new DynamicTexture(()->"MPSQ model preview",image);Minecraft.getInstance().getTextureManager().register(id,texture);texture.upload();return id;
        }catch(Exception ex){previewCustomPet=false;MpsqCameraClient.LOGGER.debug("Modellvorschau konnte nicht erzeugt werden",ex);return null;}}
    private static void collectPreviewBone(Model model,JsonObject bone,List<JsonObject> chain,java.util.BitSet used,List<PreviewPolygon> out){chain.add(bone);boolean hitbox="hitbox".equalsIgnoreCase(str(bone,"name",""));if(bone.has("elements"))for(JsonElement ref:bone.getAsJsonArray("elements")){int index=ref.getAsInt();if(index>=0&&index<model.elements.size()&&!used.get(index)){used.set(index);if(!hitbox)collectPreviewElement(model,index,chain,out);}}if(bone.has("children"))for(JsonElement child:bone.getAsJsonArray("children"))collectPreviewBone(model,child.getAsJsonObject(),chain,used,out);chain.remove(chain.size()-1);}
    private static void collectPreviewElement(Model model,int index,List<JsonObject> chain,List<PreviewPolygon> out){JsonObject e=model.elements.get(index).getAsJsonObject();float[] from=vec(e,"from"),to=vec(e,"to");for(var entry:e.getAsJsonObject("faces").entrySet()){float[][] raw=points(entry.getKey(),from,to);if(raw==null)continue;JsonObject face=entry.getValue().getAsJsonObject();int color=model.previewColors.getOrDefault(previewColorKey(face),model.previewColors.getOrDefault(str(face,"texture",""),0xFFB0B0B0));color=shade(color,entry.getKey());double[][] pts=new double[4][2];double depth=0;for(int i=0;i<4;i++){PreviewPoint p=rotatePreview(raw[i],e,chain);PreviewPoint projected=project(p.x,p.y,p.z);pts[i][0]=projected.x;pts[i][1]=projected.y;depth+=projected.y;}out.add(new PreviewPolygon(pts,color,depth/4));}}
    private static PreviewPoint rotatePreview(float[] point,JsonObject element,List<JsonObject> chain){PreviewPoint p=new PreviewPoint(point[0],point[1],point[2]);float[] eo=vec(element,"origin"),er=vec(element,"rotation");p=rotateAround(p,eo,er);for(int i=chain.size()-1;i>=0;i--){JsonObject b=chain.get(i);p=rotateAround(p,vec(b,"origin"),vec(b,"rotation"));}return p;}
    private static PreviewPoint rotateAround(PreviewPoint p,float[] origin,float[] rotation){double x=p.x-origin[0],y=p.y-origin[1],z=p.z-origin[2],a=Math.toRadians(rotation[0]),b=Math.toRadians(rotation[1]),c=Math.toRadians(rotation[2]);double ny=y*Math.cos(a)-z*Math.sin(a),nz=y*Math.sin(a)+z*Math.cos(a);y=ny;z=nz;double nx=x*Math.cos(b)+z*Math.sin(b);nz=-x*Math.sin(b)+z*Math.cos(b);x=nx;z=nz;nx=x*Math.cos(c)-y*Math.sin(c);ny=x*Math.sin(c)+y*Math.cos(c);return new PreviewPoint(nx+origin[0],ny+origin[1],z+origin[2]);}
    private static PreviewPoint project(double x,double y,double z){if(previewCustomPet){double a=Math.toRadians(32),c=Math.cos(a),s=Math.sin(a),rx=x*c-z*s,rz=x*s+z*c;x=rx;z=rz;}return new PreviewPoint((x-z)*0.70710678,(x+z)*0.35355339-y*0.81649658,0);}
    private static int shade(int color,String face){double factor=switch(face){case "up"->1.16;case "down"->0.62;case "east","west"->0.82;default->1.0;};int a=(color>>>24)&255,r=(int)(((color>>>16)&255)*factor),g=(int)(((color>>>8)&255)*factor),b=(int)((color&255)*factor);return(a<<24)|(Math.min(255,r)<<16)|(Math.min(255,g)<<8)|Math.min(255,b);}
    private static void fillPreviewPolygon(NativeImage image,double[][] points,int color){double min=48,max=-1;for(double[] p:points){min=Math.min(min,p[1]);max=Math.max(max,p[1]);}int y0=Math.max(0,(int)Math.floor(min)),y1=Math.min(47,(int)Math.ceil(max));for(int y=y0;y<=y1;y++){double scan=y+0.5;double[] xs=new double[points.length];int count=0;for(int i=0,j=points.length-1;i<points.length;j=i++){double[] a=points[j],b=points[i];if((a[1]<=scan&&b[1]>scan)||(b[1]<=scan&&a[1]>scan))xs[count++]=a[0]+(scan-a[1])*(b[0]-a[0])/(b[1]-a[1]);}if(count<2)continue;java.util.Arrays.sort(xs,0,count);int x0=Math.max(0,(int)Math.ceil(xs[0])),x1=Math.min(47,(int)Math.floor(xs[count-1]));for(int x=x0;x<=x1;x++)image.setPixel(x,y,color);}}
    private static JsonArray localObjectsSnapshot(int epoch){
        JsonArray resolved=new JsonArray();
        for(JsonElement value:MpsqLocalObjectStore.loadWorld(MpsqActionSync.world())){
            if(!value.isJsonObject())continue;JsonObject source=value.getAsJsonObject();String id=source.has("model_id")?source.get("model_id").getAsString():"";String url=MpsqBuiltinFurnitureCatalog.byId(id)!=null?MpsqBuiltinFurnitureCatalog.byId(id).url():localAssetUrls.get(id);if(url==null)continue;
            JsonObject row=source.deepCopy();row.addProperty("url",url);if(!row.has("rotation"))row.addProperty("rotation",0);resolved.add(row);
        }
        return resolved;
    }
    private static JsonArray activeFurniture(JsonArray source){JsonArray active=new JsonArray();for(JsonElement value:source){if(!value.isJsonObject()||MpsqBuiltinFurnitureCatalog.isRetired(str(value.getAsJsonObject(),"model_id","")))continue;active.add(value.deepCopy());}return active;}
    private static JsonArray localNpcsSnapshot(){JsonArray resolved=new JsonArray();for(JsonElement value:MpsqLocalNpcStore.loadWorld(MpsqActionSync.world())){JsonObject row=value.getAsJsonObject().deepCopy();String id=row.has("asset_id")?row.get("asset_id").getAsString():"";if("mpsq_tutorial".equalsIgnoreCase(id))continue;String url=WUMPUS_ASSET_ID.equals(id)?WUMPUS_MODEL_URL:localAssetUrls.get(id);if(url==null)url=str(row,"url","");if(url.isBlank())continue;row.addProperty("url",url);row.addProperty("asset_id",id);row.addProperty("category",WUMPUS_ASSET_ID.equals(id)?"npc_model":localAssetCategories.getOrDefault(id,str(row,"category","npc_model")));resolved.add(row);}return resolved;}
    private static void loadNpcAsset(JsonObject npc,int epoch){String id=str(npc,"asset_id","");if(WUMPUS_ASSET_ID.equals(id))npc.addProperty("url",WUMPUS_MODEL_URL);if(!npc.has("url")||npc.get("url").isJsonNull())return;String url=npc.get("url").getAsString(),category=str(npc,"category","npc_model");if("npc_skin_normal".equals(category)||"npc_skin_slim".equals(category)){MpsqNpcSkinRenderer.load(url,epoch,"npc_skin_slim".equals(category));return;}if(!models.containsKey(url)&&models.size()+loading.size()<64)load(url,epoch);}
    private static String defaultGlowColor(String task){return switch(task){case "tutorial"->"#c3971f";case "accessories"->"#8027b0";case "quest"->"#2149c4";default->"#ec2f53";};}
    private static boolean npcBoolean(JsonObject npc,String snakeCase,String camelCase){return npc.has(snakeCase)?npc.get(snakeCase).getAsBoolean():npc.has(camelCase)&&npc.get(camelCase).getAsBoolean();}
    private static int glowColor(String color){
        if(color!=null&&color.matches("#[0-9a-fA-F]{6}"))return 0xFF000000|Integer.parseInt(color.substring(1),16);
        return switch(String.valueOf(color).toLowerCase(Locale.ROOT)){case "white"->0xFFFFFFFF;case "orange"->0xFFFFAA33;case "magenta"->0xFFFF55FF;case "light_blue"->0xFF55AAFF;case "yellow"->0xFFFFFF55;case "lime"->0xFF55FF55;case "pink"->0xFFFF88BB;case "gray"->0xFF666666;case "light_gray"->0xFFBBBBBB;case "cyan"->0xFF55FFFF;case "purple"->0xFFAA55FF;case "blue"->0xFF5555FF;case "brown"->0xFF8B5A2B;case "green"->0xFF55AA33;case "red"->0xFFFF5555;case "black"->0xFF333333;default->0xFFEC2F53;};
    }
    static void drawBillboard(WorldRenderContext context,PoseStack matrices,MultiBufferSource consumers,Identifier texture,double x,double y,double z,float width,float height){
        matrices.pushPose();matrices.translate(x,y,z);matrices.rotate(context.camera().getRotation());var entry=matrices.last();var buffer=consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture));
        buffer.addVertex(entry,-width/2,-height/2,0).setColor(255,255,255,255).setUv(0,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0,0,1);
        buffer.addVertex(entry,width/2,-height/2,0).setColor(255,255,255,255).setUv(1,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0,0,1);
        buffer.addVertex(entry,width/2,height/2,0).setColor(255,255,255,255).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0,0,1);
        buffer.addVertex(entry,-width/2,height/2,0).setColor(255,255,255,255).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0,0,1);matrices.popPose();
    }
    private static void drawModel(Model model,PoseStack matrices,MultiBufferSource consumers,int tint){drawElements(model,matrices,consumers,tint,null);drawMeshes(model,matrices,consumers,tint);}
    private record FurnitureBounds(float centerX,float minY,float maxY,float centerZ){}
    private static FurnitureBounds furnitureBounds(Model model){float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;for(RenderFace face:model.bakedGeometry)for(float[] vertex:face.vertices){minX=Math.min(minX,vertex[0]);minY=Math.min(minY,vertex[1]);minZ=Math.min(minZ,vertex[2]);maxX=Math.max(maxX,vertex[0]);maxY=Math.max(maxY,vertex[1]);maxZ=Math.max(maxZ,vertex[2]);}if(!Float.isFinite(minX))return new FurnitureBounds(8f,0f,16f,8f);return new FurnitureBounds((minX+maxX)*0.5f,minY,maxY,(minZ+maxZ)*0.5f);}
    /** Head-only pick volume in the same world transform used to draw this statue. */
    public static java.util.Optional<net.minecraft.world.phys.Vec3> figureHeadHit(JsonObject object,net.minecraft.world.phys.Vec3 start,net.minecraft.world.phys.Vec3 end){
        Model model=models.get(str(object,"url",""));
        if(model==null||model.skinTextureKey().isBlank())return java.util.Optional.empty();
        List<RenderFace> faces=new ArrayList<>();
        for(JsonElement bone:model.bones())collectHeadFaces(model,bone.getAsJsonObject(),new ArrayList<>(),faces);
        FurnitureBounds bounds=furnitureBounds(model);
        float scale=object.has("scale")?object.get("scale").getAsFloat():1f;
        scale=Float.isFinite(scale)?Math.max(.25f,Math.min(3f,scale)):1f;float unit=scale/16f;
        var transform=new org.joml.Matrix4f().translation(object.get("x").getAsFloat()+.5f,object.get("y").getAsFloat(),object.get("z").getAsFloat()+.5f)
                .rotateY((float)Math.toRadians(object.has("rotation")?object.get("rotation").getAsFloat():0))
                .translate(-bounds.centerX()*unit,-bounds.minY()*unit,-bounds.centerZ()*unit).scale(unit);
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(RenderFace face:faces)for(float[] v:face.vertices()){
            var point=transform.transformPosition(new org.joml.Vector3f(v[0],v[1],v[2]));
            minX=Math.min(minX,point.x);minY=Math.min(minY,point.y);minZ=Math.min(minZ,point.z);
            maxX=Math.max(maxX,point.x);maxY=Math.max(maxY,point.y);maxZ=Math.max(maxZ,point.z);
        }
        if(!Double.isFinite(minX))return java.util.Optional.empty();
        return new net.minecraft.world.phys.AABB(minX,minY,minZ,maxX,maxY,maxZ).inflate(.05*scale).clip(start,end);
    }
    private static void collectHeadFaces(Model model,JsonObject bone,List<JsonObject> chain,List<RenderFace> faces){
        if("head".equalsIgnoreCase(str(bone,"name",""))){
            bakeFurnitureBone(model.elements(),bone,chain,new java.util.BitSet(),faces,model.textures());return;
        }
        chain.add(bone);
        if(bone.has("children"))for(JsonElement child:bone.getAsJsonArray("children"))collectHeadFaces(model,child.getAsJsonObject(),chain,faces);
        chain.remove(chain.size()-1);
    }
    private static final Map<String,Long> figureWobbles=new HashMap<>();
    private static String figureKey(JsonObject object){return str(object,"id",object.toString());}
    public static void wobbleFigure(JsonObject object){figureWobbles.put(figureKey(object),System.nanoTime());}
    private static void drawFurniture(Model model,JsonObject object,PoseStack matrices,MultiBufferSource consumers){
        if(MpsqCrate.isCrate(object)){var pose=MpsqCrate.pose(MpsqCrate.elapsed(object));drawBbModel(model,matrices,consumers,0xFFFFFFFF,pose.clip(),pose.time());return;}
        Model selected=model;
        String name=str(object,"skin_name","");
        if(!model.skinTextureKey().isBlank()&&!name.isBlank()){
            var skin=MpsqFigureSkins.get(name);
            if(skin!=null){
                Map<String,Identifier> textures=new HashMap<>(model.textures());
                textures.put(model.skinTextureKey(),skin.body().texturePath());
                selected=new Model(model.elements(),model.meshes(),model.bones(),model.animations(),textures,model.previewColors(),model.bakedGeometry(),model.headDisplay(),model.skinTextureKey(),model.scenes());
            }
        }
        if(!model.skinTextureKey().isBlank())
            {
                Long started=figureWobbles.get(figureKey(object));
                float elapsed=started==null?0:(System.nanoTime()-started)/1_000_000_000f;
                JsonObject clip=findAnimation(selected,"idle");
                float length=clip!=null&&clip.has("length")?clip.get("length").getAsFloat():1.25f;
                if(elapsed>=length){figureWobbles.remove(figureKey(object));elapsed=0;}
                drawBbModel(selected,matrices,consumers,0xFFFFFFFF,"idle",elapsed);
            }
        else drawBakedFurniture(selected,matrices,consumers,0xFFFFFFFF);
    }
    private static float crateScale(Model model){
        float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY},max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for(var face:model.bakedGeometry())for(var v:face.vertices())for(int a=0;a<3;a++){min[a]=Math.min(min[a],v[a]);max[a]=Math.max(max[a],v[a]);}
        return 16f/Math.max(1,Math.max(max[0]-min[0],Math.max(max[1]-min[1],max[2]-min[2])));
    }
    private static void drawBakedFurniture(Model model,PoseStack matrices,MultiBufferSource consumers,int tint){for(RenderFace face:model.bakedGeometry){var buffer=consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(face.texture));for(int i=0;i<4;i++){float[] vertex=face.vertices[Math.min(i,face.vertices.length-1)];buffer.addVertex(matrices.last(),vertex[0],vertex[1],vertex[2]).setColor((tint>>16)&255,(tint>>8)&255,tint&255,(tint>>>24)&255).setUv(vertex[3],vertex[4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(matrices.last(),face.nx,face.ny,face.nz);}}}
    private static float cinematicHeadYaw,cinematicHeadPitch;
    private static void drawCinematicBbModel(Model model,PoseStack m,MultiBufferSource buffers,String clip,float time,float headYaw,float pitch){
        cinematicHeadYaw=headYaw;cinematicHeadPitch=pitch;
        try{drawBbModel(model,m,buffers,0xFFFFFFFF,clip,time);}finally{cinematicHeadYaw=0;cinematicHeadPitch=0;}
    }
    private static void drawBbModel(Model model,PoseStack matrices,MultiBufferSource consumers,int tint){drawBbModel(model,matrices,consumers,tint,null,0);}
    private static void drawBbModel(Model model,PoseStack matrices,MultiBufferSource consumers,int tint,String animation,float elapsed){if(model.bones==null||model.bones.isEmpty()){drawModel(model,matrices,consumers,tint);return;}JsonObject clip=findAnimation(model,animation);float time=animationTime(clip,elapsed);java.util.BitSet used=new java.util.BitSet(model.elements.size());for(JsonElement bone:model.bones)drawBone(model,bone.getAsJsonObject(),matrices,consumers,tint,used,clip,time);for(int i=used.nextClearBit(0);i<model.elements.size();i=used.nextClearBit(i+1))drawElements(model,matrices,consumers,tint,i);drawMeshes(model,matrices,consumers,tint);}
    /** Original ModularTimer bones with independently animated, replaceable digits. */
    static void drawTimer(WorldRenderContext ctx,MpsqModularTimer.Timer timer){
        String timerUrl="builtin://mpsq/timer",numbersUrl="builtin://mpsq/numbers";
        Model base=models.get(timerUrl),numbers=models.get(numbersUrl);
        if(base==null){load(timerUrl,generation);return;}if(numbers==null){load(numbersUrl,generation);return;}
        var m=ctx.matrixStack();var camera=ctx.camera().getPos();m.pushPose();
        m.translate(timer.x-camera.x,timer.y-camera.y,timer.z-camera.z);
        m.rotate(Axis.YP.rotationDegrees(-timer.yaw));m.rotate(Axis.XP.rotationDegrees(timer.pitch));
        m.scale(timer.scale/16f,timer.scale/16f,timer.scale/16f);
        for(JsonElement bone:base.bones)drawTimerBone(base,numbers,bone.getAsJsonObject(),m,ctx.consumers(),timer);
        m.popPose();
    }
    private static void drawTimerBone(Model base,Model numbers,JsonObject bone,PoseStack m,MultiBufferSource buffers,MpsqModularTimer.Timer timer){
        String name=str(bone,"name","");if(name.equals("hitbox"))return;
        int digit=switch(name){case "h_min2"->0;case "h_min1"->1;case "h_sec2"->2;case "h_sec1"->3;default->-1;};
        JsonObject clip=findAnimation(base,digit<0?timer.idle:name.substring(2)+"_"+timer.animation);
        float elapsed=(digit<0?timer.age:timer.age-timer.changed[digit])/1000f;
        float time=animationTime(clip,elapsed);float[] origin=vec(bone,"origin"),rotation=vec(bone,"rotation"),delta=animationTrack(clip,bone,"rotation",time),pos=animationTrack(clip,bone,"position",time),size=animationTrack(clip,bone,"scale",time);
        m.pushPose();m.translate(origin[0]+pos[0],origin[1]+pos[1],origin[2]+pos[2]);
        m.rotate(Axis.ZP.rotationDegrees(rotation[2]+delta[2]));m.rotate(Axis.YP.rotationDegrees(rotation[1]+delta[1]));m.rotate(Axis.XP.rotationDegrees(rotation[0]+delta[0]));m.scale(size[0],size[1],size[2]);
        if(digit>=0){
            String value=String.valueOf(timer.digits().charAt(digit));
            for(JsonElement n:numbers.bones)if(value.equals(str(n.getAsJsonObject(),"name","")))drawBone(numbers,n.getAsJsonObject(),m,buffers,timer.displayColor(),new java.util.BitSet(),null,0);
        }else{
            m.translate(-origin[0],-origin[1],-origin[2]);
            if(bone.has("elements"))for(JsonElement i:bone.getAsJsonArray("elements"))drawElements(base,m,buffers,timer.displayColor(),i.getAsInt());
        }
        if(bone.has("children"))for(JsonElement child:bone.getAsJsonArray("children"))drawTimerBone(base,numbers,child.getAsJsonObject(),m,buffers,timer);
        m.popPose();
    }
    static double clipLength(String url,String name){var model=models.get(url);if(model==null)return 0;var clip=findAnimation(model,name);return clip==null||!clip.has("length")?0:clip.get("length").getAsDouble();}
    private static JsonObject findAnimation(Model model,String name){if(name==null||model.animations==null)return null;for(JsonElement value:model.animations)if(value.isJsonObject()&&name.equals(str(value.getAsJsonObject(),"name","")))return value.getAsJsonObject();return null;}
    private static float animationTime(JsonObject clip,float elapsed){if(clip==null)return 0;float length=clip.has("length")?clip.get("length").getAsFloat():1f;if(length<=0)return 0;String loop=str(clip,"loop","once");return ("loop".equalsIgnoreCase(loop)||"true".equalsIgnoreCase(loop))?elapsed%length:Math.min(elapsed,length);}
    private static float[] animationTrack(JsonObject clip,JsonObject bone,String channel,float time){float identity=channel.equals("scale")?1:0;if(clip==null)return new float[]{identity,identity,identity};JsonObject animators=clip.has("animators")?clip.getAsJsonObject("animators"):null;JsonObject animator=animators!=null&&animators.has(str(bone,"name",""))?animators.getAsJsonObject(str(bone,"name","")):null;JsonArray frames=animator!=null&&animator.has(channel)&&animator.get(channel).isJsonArray()?animator.getAsJsonArray(channel):null;if(frames==null||frames.isEmpty())return new float[]{identity,identity,identity};JsonObject first=frames.get(0).getAsJsonObject(),last=frames.get(frames.size()-1).getAsJsonObject();if(time<=first.get("time").getAsFloat())return vec(first,"values");if(time>=last.get("time").getAsFloat())return vec(last,"values");for(int i=1;i<frames.size();i++){JsonObject b=frames.get(i).getAsJsonObject(),a=frames.get(i-1).getAsJsonObject();float bt=b.get("time").getAsFloat();if(time<=bt){float at=a.get("time").getAsFloat(),p=bt<=at?0:(time-at)/(bt-at);float[] av=vec(a,"values"),bv=vec(b,"values");return new float[]{lerp(av[0],bv[0],p),lerp(av[1],bv[1],p),lerp(av[2],bv[2],p)};}}return new float[]{0,0,0};}
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    private static void drawBone(Model model,JsonObject bone,PoseStack matrices,MultiBufferSource consumers,int tint,java.util.BitSet used,JsonObject clip,float time){drawBone(model,bone,matrices,consumers,tint,used,clip,time,false);}
    private static void drawBone(Model model,JsonObject bone,PoseStack matrices,MultiBufferSource consumers,int tint,java.util.BitSet used,JsonObject clip,float time,boolean inheritedHead){
        boolean headBone=str(bone,"name","").toLowerCase(java.util.Locale.ROOT).matches("(^head$|.*_head$)");
        if(model.scenes()&&str(bone,"name","").startsWith("scenes_bench")){hideBone(bone,used);return;}
        float[] origin=vec(bone,"origin"),rotation=vec(bone,"rotation"),position=model.scenes()?MpsqScenesClips.transformSample(clip,bone,"position",time):model.skinTextureKey().isBlank()?animationTrack(clip,bone,"position",time):MpsqFigureAnimation.sample(clip,bone,"position",time),delta=model.scenes()?MpsqScenesClips.transformSample(clip,bone,"rotation",time):model.skinTextureKey().isBlank()?animationTrack(clip,bone,"rotation",time):MpsqFigureAnimation.sample(clip,bone,"rotation",time);matrices.pushPose();matrices.translate(origin[0]+position[0],origin[1]+position[1],origin[2]+position[2]);if(tracksHead(str(bone,"name",""),inheritedHead)){matrices.rotate(Axis.YP.rotationDegrees(-cinematicHeadYaw));matrices.rotate(Axis.XP.rotationDegrees(-cinematicHeadPitch));}if(petWalkBlend>=0&&str(bone,"name","").equals("bs_vm_crab")){matrices.rotate(crabRotation(model,bone,time));}else{matrices.rotate(Axis.ZP.rotationDegrees(rotation[2]+delta[2]));matrices.rotate(Axis.YP.rotationDegrees(rotation[1]+delta[1]));matrices.rotate(Axis.XP.rotationDegrees(rotation[0]+delta[0]));}if(model.scenes()){var scale=MpsqScenesClips.sample(clip,bone,"scale",time);matrices.scale(scale[0],scale[1],scale[2]);}else if(model.skinTextureKey().isBlank()){var scale=animationTrack(clip,bone,"scale",time);matrices.scale(scale[0],scale[1],scale[2]);}matrices.translate(-origin[0],-origin[1],-origin[2]);
        if(model.scenes()&&scenesCosmetic!=null&&str(bone,"name","").equals(scenesCosmetic.cosmeticBone))drawScenesCosmetic(matrices,consumers,origin);
        boolean hitbox="hitbox".equalsIgnoreCase(str(bone,"name",""));if(bone.has("elements"))for(JsonElement index:bone.getAsJsonArray("elements")){int i=index.getAsInt();if(i>=0&&i<model.elements.size()&&!used.get(i)){used.set(i);if(!hitbox)drawElements(model,matrices,consumers,tint,i);}}
        if(bone.has("children"))for(JsonElement child:bone.getAsJsonArray("children"))drawBone(model,child.getAsJsonObject(),matrices,consumers,tint,used,clip,time,inheritedHead||headBone);matrices.popPose();
    }
    private static void drawScenesCosmetic(PoseStack m,MultiBufferSource buffers,float[] origin){
        var n=scenesCosmetic;if(n.cosmeticModel==null||n.cosmeticModel.isBlank())return;String url="builtin://mpsq/"+n.cosmeticModel;var hat=models.get(url);if(hat==null){if(models.size()+loading.size()<64)load(url,generation);return;}
        var bounds=headBounds(hat);float fit=Math.min(1f,10f/Math.max(bounds.width(),bounds.depth()))*n.cosmeticScale;
        m.pushPose();m.translate(origin[0]+n.cosmeticX,origin[1]+(n.cosmeticBone.equals("h_head")?8:0)+n.cosmeticY,origin[2]+n.cosmeticZ);m.rotate(Axis.ZP.rotationDegrees(n.cosmeticRZ));m.rotate(Axis.YP.rotationDegrees(n.cosmeticRY));m.rotate(Axis.XP.rotationDegrees(n.cosmeticRX));m.scale(fit,fit,fit);m.translate(-bounds.centerX(),-bounds.minY(),-bounds.centerZ());drawHatGeometry(hat,m,buffers,false);m.popPose();
    }
    static boolean tracksHead(String name,boolean inherited){return !inherited&&name.toLowerCase(java.util.Locale.ROOT).matches("(^head$|.*_head$)");}
    private static void hideBone(JsonObject bone,java.util.BitSet used){
        if(bone.has("elements"))for(var i:bone.getAsJsonArray("elements"))used.set(i.getAsInt());
        if(bone.has("children"))for(var child:bone.getAsJsonArray("children"))hideBone(child.getAsJsonObject(),used);
    }
    private static void drawElements(Model model,PoseStack matrices,MultiBufferSource consumers,int tint,Integer only){
                for(int elementIndex=0;elementIndex<model.elements.size();elementIndex++){if(only!=null&&only!=elementIndex)continue;JsonElement value=model.elements.get(elementIndex);
                    JsonObject e=value.getAsJsonObject();float[] from=vec(e,"from"),to=vec(e,"to"),origin=vec(e,"origin"),rotation=vec(e,"rotation");
                    matrices.pushPose();matrices.translate(origin[0],origin[1],origin[2]);
                    matrices.rotate(Axis.ZP.rotationDegrees(rotation[2]));matrices.rotate(Axis.YP.rotationDegrees(rotation[1]));matrices.rotate(Axis.XP.rotationDegrees(rotation[0]));
                    matrices.translate(-origin[0],-origin[1],-origin[2]);
                    boolean flatX=Math.abs(to[0]-from[0])<1.0e-5f,flatY=Math.abs(to[1]-from[1])<1.0e-5f,flatZ=Math.abs(to[2]-from[2])<1.0e-5f;
                    for(var face:e.getAsJsonObject("faces").entrySet()){
                        JsonObject f=face.getValue().getAsJsonObject();Identifier texture=resolveTexture(model.textures,f.get("texture").getAsString());if(texture==null)continue;
                        float[][] points=points(face.getKey(),from,to);if(points==null)continue;
                        // Keep distinct front/back UVs on planes; culling prevents
                        // their coincident surfaces from fighting for the same pixel.
                        var buffer=consumers.getBuffer(flatX||flatY||flatZ?net.minecraft.client.renderer.rendertype.RenderTypes.entityCutoutCull(texture):net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture));
                        JsonArray uv=f.getAsJsonArray("uv");float u0=uv.get(0).getAsFloat(),v0=uv.get(1).getAsFloat(),u1=uv.get(2).getAsFloat(),v1=uv.get(3).getAsFloat();
                        float[][] tex={{u0,v1},{u1,v1},{u1,v0},{u0,v0}}; float[] normal=switch(face.getKey()){case "north"->new float[]{0,0,-1};case "south"->new float[]{0,0,1};case "east"->new float[]{1,0,0};case "west"->new float[]{-1,0,0};case "up"->new float[]{0,1,0};default->new float[]{0,-1,0};};
                        int turn=f.has("rotation")?Math.floorMod(f.get("rotation").getAsInt()/90,4):0;
                        for(int i=0;i<4;i++){float[] p=points[i], t=tex[(i+turn)%4];buffer.addVertex(matrices.last(),p[0],p[1],p[2]).setColor((tint>>16)&255,(tint>>8)&255,tint&255,(tint>>>24)&255).setUv(t[0],t[1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(matrices.last(),normal[0],normal[1],normal[2]);}
                    }
                    matrices.popPose();
                }
    }
    private static void drawMeshes(Model model,PoseStack matrices,MultiBufferSource consumers,int tint){for(JsonElement value:model.meshes){
                    JsonObject mesh=value.getAsJsonObject();Identifier texture=resolveTexture(model.textures,mesh.get("texture").getAsString());if(texture==null)continue;
                    JsonArray vertices=mesh.getAsJsonArray("vertices"),indices=mesh.getAsJsonArray("indices");var buffer=consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture));
                    for(int i=0;i+2<indices.size();i+=3)for(int k=0;k<4;k++){
                        JsonArray v=vertices.get(indices.get(i+Math.min(k,2)).getAsInt()).getAsJsonArray();
                        buffer.addVertex(matrices.last(),v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat()).setColor((tint>>16)&255,(tint>>8)&255,tint&255,(tint>>>24)&255).setUv(v.get(3).getAsFloat(),v.get(4).getAsFloat()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0,1,0);
                    }
                }}
    private static float[] vec(JsonObject o,String key){
        var value=o.get(key);
        if(key.equals("origin")&&value==null&&o.has("rotation")&&o.get("rotation").isJsonObject())value=o.getAsJsonObject("rotation").get("origin");
        if(value==null||value.isJsonNull())return new float[]{0,0,0};
        if(key.equals("rotation")&&value.isJsonObject()){var rotation=value.getAsJsonObject();float[] result={0,0,0};String axis=str(rotation,"axis","y");int i=axis.equals("x")?0:axis.equals("z")?2:1;result[i]=rotation.has("angle")?rotation.get("angle").getAsFloat():0;return result;}
        var a=value.getAsJsonArray();return new float[]{a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()};
    }
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
                byte[] bytes;
                if(MpsqScenesClips.resource(url)!=null){try(InputStream stream=MpsqAccessoryRenderer.class.getResourceAsStream(MpsqScenesClips.resource(url))){if(stream==null)throw new IOException("Scenes-Modell fehlt");bytes=stream.readNBytes(12000001);}}
                else if(MpsqBuiltinFurnitureCatalog.byUrl(url)!=null){try(InputStream stream=MpsqAccessoryRenderer.class.getResourceAsStream(MpsqBuiltinFurnitureCatalog.byUrl(url).resource())){if(stream==null)throw new IOException("Möbelmodell fehlt");bytes=stream.readNBytes(12000001);}}
                else if(isBuiltinModel(url)){String path=MpsqNogAssets.resource(url)!=null?MpsqNogAssets.resource(url):BUDGIE_HAT_MODEL_URL.equals(url)?"/assets/mpsqcamera/custom_models/accessoires/nm_hat_budgie.json":HEDGEHOG_HAT_MODEL_URL.equals(url)?"/assets/mpsqcamera/custom_models/accessoires/nm_hat_hedgehog.json":NM_WUMPUS_HAT_MODEL_URL.equals(url)?"/assets/mpsqcamera/custom_models/accessoires/nm_hat_wumpus.json":"/assets/mpsqcamera/custom_models/wumpus.json";try(InputStream stream=MpsqAccessoryRenderer.class.getResourceAsStream(path)){if(stream==null)throw new IOException("Modell fehlt: "+path);bytes=stream.readNBytes(12000001);}}
                else {bytes=MpsqLocalWorldStore.readAsset(url,12000000);if(bytes==null)throw new IOException("Modell ist nicht lokal gespeichert. Kein Cloud-Abruf in der lokalen Testversion.");}
                return JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            }catch(Exception e){throw new java.util.concurrent.CompletionException(e);}
        }).whenComplete((bundle,error)->Minecraft.getInstance().execute(()->{
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
                    var header=java.nio.ByteBuffer.wrap(png);int w=header.getInt(16),h=header.getInt(20);validateTextureSize(url,w,h);
                    pixels+=(long)w*h;if(pixels>(isBuiltinModel(url)?8388608:2097152))throw new IOException("Texturpaket zu groß");
                    NativeImage image=NativeImage.read(new ByteArrayInputStream(png));previewColors.put(entry.getKey(),averageColor(image));previewImages.put(entry.getKey(),image);Identifier id=Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID,"accessory/"+UUID.randomUUID());
                    JsonObject textureAnimation=bundle.has("texture_animations")&&bundle.getAsJsonObject("texture_animations").has(entry.getKey())?bundle.getAsJsonObject("texture_animations").getAsJsonObject(entry.getKey()):null;
                    var texture=MpsqAnimatedTextures.create(id,image,textureAnimation);Minecraft.getInstance().getTextureManager().register(id,texture);texture.upload();textures.put(entry.getKey(),id);
                }
                for(JsonElement element:elements){JsonObject faces=element.getAsJsonObject().getAsJsonObject("faces");for(var face:faces.entrySet()){JsonObject f=face.getValue().getAsJsonObject();String textureReference=str(f,"texture","");NativeImage image=previewImages.get(textureReference.startsWith("#")?textureReference.substring(1):textureReference);if(image!=null&&f.has("uv"))previewColors.put(previewColorKey(f),averageColor(image,f.getAsJsonArray("uv")));}}
                JsonArray bones=bundle.has("bones")&&bundle.get("bones").isJsonArray()?bundle.getAsJsonArray("bones"):new JsonArray();if(bones.size()>256)throw new IOException("Zu viele Modellknochen");normalizeBones(bones);validateBones(bones,0);
                JsonArray animations=bundle.has("animations")&&bundle.get("animations").isJsonArray()?bundle.getAsJsonArray("animations"):new JsonArray();if(animations.size()>32)throw new IOException("Zu viele Modellanimationen");
                HeadDisplay headDisplay=parseHeadDisplay(bundle);
                models.put(url,new Model(elements,meshes,bones,animations,textures,previewColors,bakeFurnitureGeometry(elements,meshes,bones,textures),headDisplay,str(bundle,"skin_texture_key",""),bundle.has("scenes")&&bundle.get("scenes").getAsBoolean()));
            }catch(Exception e){for(Identifier id:textures.values()){MpsqAnimatedTextures.remove(id);Minecraft.getInstance().getTextureManager().release(id);}MpsqCameraClient.LOGGER.warn("Accessoire-Modell ungültig",e);}
        }));
    }
    private static void validateTextureSize(String url,int w,int h)throws IOException{int limit=isBuiltinModel(url)?2048:1024;if(w<1||h<1||w>limit||h>limit)throw new IOException("Textur maximal "+limit+" × "+limit);}
    private static HeadDisplay parseHeadDisplay(JsonObject bundle)throws IOException{
        if(!bundle.has("display")||!bundle.get("display").isJsonObject())return null;
        JsonObject display=bundle.getAsJsonObject("display");if(!display.has("head")||!display.get("head").isJsonObject())return null;
        JsonObject head=display.getAsJsonObject("head");
        return new HeadDisplay(displayVector(head,"translation",new float[]{0,0,0}),displayVector(head,"rotation",new float[]{0,0,0}),displayVector(head,"scale",new float[]{1,1,1}));
    }
    private static float[] displayVector(JsonObject object,String key,float[] fallback)throws IOException{
        if(!object.has(key))return fallback;
        JsonElement value=object.get(key);if(!value.isJsonArray()||value.getAsJsonArray().size()!=3)throw new IOException("Ungültiger display.head-Transform: "+key);
        JsonArray vector=value.getAsJsonArray();float[] result={vector.get(0).getAsFloat(),vector.get(1).getAsFloat(),vector.get(2).getAsFloat()};
        for(float component:result)if(!Float.isFinite(component)||Math.abs(component)>("rotation".equals(key)?360:64))throw new IOException("Ungültiger display.head-Transform: "+key);
        return result;
    }
    private static List<RenderFace> bakeFurnitureGeometry(JsonArray elements,JsonArray meshes,JsonArray bones,Map<String,Identifier> textures){List<RenderFace> result=new ArrayList<>();java.util.BitSet used=new java.util.BitSet(elements.size());for(JsonElement bone:bones)bakeFurnitureBone(elements,bone.getAsJsonObject(),new ArrayList<>(),used,result,textures);for(int i=used.nextClearBit(0);i<elements.size();i=used.nextClearBit(i+1))bakeFurnitureElement(elements,i,new ArrayList<>(),result,textures);
        for(JsonElement value:meshes){JsonObject mesh=value.getAsJsonObject();Identifier texture=resolveTexture(textures,str(mesh,"texture",""));if(texture==null)continue;JsonArray vertices=mesh.getAsJsonArray("vertices"),indices=mesh.getAsJsonArray("indices");for(int i=0;i+2<indices.size();i+=3){float[][] tri=new float[3][5];for(int k=0;k<3;k++){JsonArray v=vertices.get(indices.get(i+k).getAsInt()).getAsJsonArray();for(int j=0;j<5;j++)tri[k][j]=v.get(j).getAsFloat();}result.add(renderFace(texture,tri));}}return List.copyOf(result);}
    private static void bakeFurnitureBone(JsonArray elements,JsonObject bone,List<JsonObject> chain,java.util.BitSet used,List<RenderFace> result,Map<String,Identifier> textures){chain.add(bone);if(bone.has("elements"))for(JsonElement ref:bone.getAsJsonArray("elements")){int i=ref.getAsInt();if(i>=0&&i<elements.size()&&!used.get(i)){used.set(i);if(!"hitbox".equalsIgnoreCase(str(bone,"name","")))bakeFurnitureElement(elements,i,chain,result,textures);}}if(bone.has("children"))for(JsonElement child:bone.getAsJsonArray("children"))bakeFurnitureBone(elements,child.getAsJsonObject(),chain,used,result,textures);chain.remove(chain.size()-1);}
    private static void bakeFurnitureElement(JsonArray elements,int index,List<JsonObject> chain,List<RenderFace> result,Map<String,Identifier> textures){JsonObject e=elements.get(index).getAsJsonObject();float[][] raw;for(var entry:e.getAsJsonObject("faces").entrySet()){JsonObject face=entry.getValue().getAsJsonObject();Identifier texture=resolveTexture(textures,str(face,"texture",""));raw=points(entry.getKey(),vec(e,"from"),vec(e,"to"));if(texture==null||raw==null)continue;JsonArray uv=face.getAsJsonArray("uv");float u0=uv.get(0).getAsFloat(),v0=uv.get(1).getAsFloat(),u1=uv.get(2).getAsFloat(),v1=uv.get(3).getAsFloat();float[][] tex={{u0,v1},{u1,v1},{u1,v0},{u0,v0}},vertices=new float[4][5];int turn=face.has("rotation")?Math.floorMod(face.get("rotation").getAsInt()/90,4):0;for(int i=0;i<4;i++){PreviewPoint p=rotatePreview(raw[i],e,chain);float[] t=tex[(i+turn)%4];vertices[i]=new float[]{(float)p.x,(float)p.y,(float)p.z,t[0],t[1]};}result.add(renderFace(texture,vertices));}}
    private static Identifier resolveTexture(Map<String,Identifier> textures,String reference){if(reference==null)return null;Identifier texture=textures.get(reference);return texture==null&&reference.startsWith("#")?textures.get(reference.substring(1)):texture;}
    private static RenderFace renderFace(Identifier texture,float[][] vertices){float ax=vertices[1][0]-vertices[0][0],ay=vertices[1][1]-vertices[0][1],az=vertices[1][2]-vertices[0][2],bx=vertices[2][0]-vertices[0][0],by=vertices[2][1]-vertices[0][1],bz=vertices[2][2]-vertices[0][2];float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx,length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);if(length>1.0e-6f){nx/=length;ny/=length;nz/=length;}return new RenderFace(texture,vertices,nx,ny,nz);}
    private static int averageColor(NativeImage image){long r=0,g=0,b=0,n=0;int step=Math.max(1,Math.min(image.getWidth(),image.getHeight())/16);for(int y=0;y<image.getHeight();y+=step)for(int x=0;x<image.getWidth();x+=step){int c=image.getPixel(x,y),a=(c>>>24)&255;if(a<32)continue;r+=((c>>>16)&255)*a;g+=((c>>>8)&255)*a;b+=(c&255)*a;n+=a;}return n==0?0xFFB0B0B0:0xFF000000|((int)(r/n)<<16)|((int)(g/n)<<8)|(int)(b/n);}
    private static int averageColor(NativeImage image,JsonArray uv){double u0=uv.get(0).getAsDouble(),v0=uv.get(1).getAsDouble(),u1=uv.get(2).getAsDouble(),v1=uv.get(3).getAsDouble();int x0=Math.max(0,Math.min(image.getWidth()-1,(int)Math.floor(Math.min(u0,u1)*image.getWidth()))),x1=Math.max(x0+1,Math.min(image.getWidth(),(int)Math.ceil(Math.max(u0,u1)*image.getWidth()))),y0=Math.max(0,Math.min(image.getHeight()-1,(int)Math.floor(Math.min(v0,v1)*image.getHeight()))),y1=Math.max(y0+1,Math.min(image.getHeight(),(int)Math.ceil(Math.max(v0,v1)*image.getHeight())));long r=0,g=0,b=0,n=0;int sx=Math.max(1,(x1-x0)/8),sy=Math.max(1,(y1-y0)/8);for(int y=y0;y<y1;y+=sy)for(int x=x0;x<x1;x+=sx){int c=image.getPixel(x,y),a=(c>>>24)&255;if(a<32)continue;r+=((c>>>16)&255)*a;g+=((c>>>8)&255)*a;b+=(c&255)*a;n+=a;}return n==0?averageColor(image):0xFF000000|((int)(r/n)<<16)|((int)(g/n)<<8)|(int)(b/n);}
    private static String previewColorKey(JsonObject face){JsonArray uv=face.has("uv")?face.getAsJsonArray("uv"):new JsonArray();return str(face,"texture","")+"|"+uv;}
    /** Blockbench exports some otherwise valid bone vectors/lists as whitespace strings. */
    private static void normalizeBones(JsonArray bones)throws IOException{
        for(JsonElement value:bones){if(!value.isJsonObject())throw new IOException("Ungültiger Knochen");JsonObject bone=value.getAsJsonObject();
            for(String key:new String[]{"origin","rotation"}){JsonElement raw=bone.get(key);if(raw!=null&&raw.isJsonArray())continue;if(raw==null||!raw.isJsonPrimitive())throw new IOException("Ungültiger Knochen-Drehpunkt");JsonArray vector=new JsonArray();for(String part:raw.getAsString().trim().split("[\\s,]+")){if(part.isBlank())continue;try{vector.add(Float.parseFloat(part));}catch(NumberFormatException ex){throw new IOException("Ungültiger Knochen-Drehpunkt",ex);}}if(vector.size()!=3)throw new IOException("Ungültiger Knochen-Drehpunkt");bone.add(key,vector);}
            JsonElement rawElements=bone.get("elements");if(rawElements!=null&&rawElements.isJsonPrimitive()){JsonArray refs=new JsonArray();for(String part:rawElements.getAsString().trim().split("[\\s,]+")){if(part.isBlank())continue;try{refs.add(Integer.parseInt(part));}catch(NumberFormatException ex){throw new IOException("Ungültiger Knochen-Verweis",ex);}}bone.add("elements",refs);}
            JsonElement children=bone.get("children");if(children==null||!children.isJsonArray())bone.add("children",new JsonArray());normalizeBones(bone.getAsJsonArray("children"));
        }
    }
    private static void validateBones(JsonArray bones,int depth)throws IOException{if(depth>32)throw new IOException("Knochenhierarchie ist zu tief");for(JsonElement value:bones){if(!value.isJsonObject())throw new IOException("Ungültiger Knochen");JsonObject bone=value.getAsJsonObject();for(String key:new String[]{"origin","rotation"})if(!bone.has(key)||!bone.get(key).isJsonArray()||bone.getAsJsonArray(key).size()!=3)throw new IOException("Ungültiger Knochen-Drehpunkt");if(bone.has("elements")){JsonArray refs=bone.getAsJsonArray("elements");if(refs.size()>512)throw new IOException("Zu viele Knochen-Elemente");for(JsonElement ref:refs)if(ref.getAsInt()<0)throw new IOException("Ungültiger Knochen-Verweis");}if(bone.has("children"))validateBones(bone.getAsJsonArray("children"),depth+1);}}
}





