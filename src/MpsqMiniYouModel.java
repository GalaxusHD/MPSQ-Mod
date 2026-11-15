package de.galaxushd.mpsqcamera;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import de.galaxushd.mpsqcamera.compat.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Loads Mini You Blockbench geometry and authored animation tracks. */
final class MpsqMiniYouModel {
    private static final Identifier MODEL_TEXTURE = Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID,
            "textures/pets/mini_you_neutral.png");
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final Map<String, ModelData> MODELS = new HashMap<>();


    private MpsqMiniYouModel() { }

    static double animationLength(String petId, String name) {
        Animation animation = model(petId,false).animations.get(name);
        return animation == null ? 1.0 : animation.length;
    }

    /** Keep the waving face aimed at the viewer, including the authored head turn. */
    static float waveRootYaw(String petId,boolean slim,double seconds){
        ModelData data=model(petId,slim);Animation wave=data.animations.get("wave");
        if(wave==null)return 0;
        float yaw=waveFaceYaw(data.root,wave,Math.min(seconds,wave.length),0);
        return Float.isFinite(yaw)?yaw:0;
    }
    private static float waveFaceYaw(Group group,Animation wave,double time,float inherited){
        float yaw=inherited+(float)(group.rotation.y+wave.value(group.id,"rotation",time).y);
        if(group.name.equalsIgnoreCase("head")||group.name.equalsIgnoreCase("h_head"))return yaw;
        for(Group child:group.children){float result=waveFaceYaw(child,wave,time,yaw);if(!Float.isNaN(result))return result;}
        return Float.NaN;
    }

    static void render(PoseStack matrices, MultiBufferSource consumers, int light,
                       String petId, Identifier skinTexture, boolean slim,
                       float x, float y, float z, float yaw, float scale,
                       String animation, double timeSeconds) {
        ModelData model = model(petId, slim);
        matrices.pushPose();
        matrices.translate(x, y, z);
        matrices.rotate(Axis.YP.rotationDegrees(180.0f - yaw));
        matrices.scale(scale, scale, scale);
        // BBModel outer-layer planes overlap the base cubes. Culling the back
        // faces avoids drawing both sides of those planes into the same pixels.
        VertexConsumer vertices = consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityCutoutCull(skinTexture==null?MODEL_TEXTURE:skinTexture));
        Animation pose = model.animations.get(animation);
        if (pose == null) pose = model.animations.get("idle");
        double poseTime = pose == null ? 0 : pose.loop
                ? timeSeconds % Math.max(0.001, pose.length) : Math.min(timeSeconds, pose.length);
        renderGroup(model.root, matrices, vertices, light, pose, poseTime, 0, 0, 0);
        matrices.popPose();
    }

    private static void renderGroup(Group group, PoseStack matrices, VertexConsumer vertices, int light,
                                    Animation animation, double time, double parentX, double parentY,
                                    double parentZ) {
        double gx = number(group.origin, 0), gy = number(group.origin, 1), gz = number(group.origin, 2);
        matrices.pushPose();
        matrices.translate((gx - parentX) / 16.0, (gy - parentY) / 16.0, (gz - parentZ) / 16.0);
        Vec3 rotation = group.rotation;
        Vec3 animatedRotation = animation == null ? Vec3.ZERO : animation.value(group.id, "rotation", time);
        Vec3 animatedPosition = animation == null ? Vec3.ZERO : animation.value(group.id, "position", time);
        if(animation!=null&&animation.name.equals("teleport"))animatedRotation=new Vec3(-animatedRotation.x,-animatedRotation.y,animatedRotation.z);
        matrices.translate(animatedPosition.x / 16.0, animatedPosition.y / 16.0, animatedPosition.z / 16.0);
        matrices.rotate(Axis.ZP.rotationDegrees((float) (rotation.z + animatedRotation.z)));
        matrices.rotate(Axis.YP.rotationDegrees((float) (rotation.y + animatedRotation.y)));
        matrices.rotate(Axis.XP.rotationDegrees((float) (rotation.x + animatedRotation.x)));
        if(animation!=null){Vec3 scale=animation.value(group.id,"scale",time);matrices.scale((float)Math.max(0,scale.x),(float)Math.max(0,scale.y),(float)Math.max(0,scale.z));}

        for (Cube cube : group.cubes) renderCube(cube, matrices, vertices, light, gx, gy, gz);
        for (Group child : group.children) renderGroup(child, matrices, vertices, light, animation, time, gx, gy, gz);
        matrices.popPose();
    }

    private static void renderCube(Cube cube, PoseStack matrices, VertexConsumer buffer, int light,
                                  double gx, double gy, double gz) {
        matrices.pushPose();
        matrices.translate((cube.origin.x - gx) / 16.0, (cube.origin.y - gy) / 16.0,
                (cube.origin.z - gz) / 16.0);
        matrices.rotate(Axis.ZP.rotationDegrees((float) cube.rotation.z));
        matrices.rotate(Axis.YP.rotationDegrees((float) cube.rotation.y));
        matrices.rotate(Axis.XP.rotationDegrees((float) cube.rotation.x));

        double x0 = (cube.from.x - cube.origin.x) / 16.0;
        double y0 = (cube.from.y - cube.origin.y) / 16.0;
        double z0 = (cube.from.z - cube.origin.z) / 16.0;
        double x1 = (cube.to.x - cube.origin.x) / 16.0;
        double y1 = (cube.to.y - cube.origin.y) / 16.0;
        double z1 = (cube.to.z - cube.origin.z) / 16.0;
        face(cube, "north", buffer, matrices, light, new double[][]{{x0,y0,z0},{x0,y1,z0},{x1,y1,z0},{x1,y0,z0}}, 0,0,-1);
        face(cube, "south", buffer, matrices, light, new double[][]{{x1,y0,z1},{x1,y1,z1},{x0,y1,z1},{x0,y0,z1}}, 0,0,1);
        face(cube, "west", buffer, matrices, light, new double[][]{{x0,y0,z1},{x0,y1,z1},{x0,y1,z0},{x0,y0,z0}}, -1,0,0);
        face(cube, "east", buffer, matrices, light, new double[][]{{x1,y0,z0},{x1,y1,z0},{x1,y1,z1},{x1,y0,z1}}, 1,0,0);
        face(cube, "up", buffer, matrices, light, new double[][]{{x0,y1,z0},{x0,y1,z1},{x1,y1,z1},{x1,y1,z0}}, 0,1,0);
        face(cube, "down", buffer, matrices, light, new double[][]{{x0,y0,z1},{x0,y0,z0},{x1,y0,z0},{x1,y0,z1}}, 0,-1,0);
        matrices.popPose();
    }

    private static void face(Cube cube, String name, VertexConsumer buffer, PoseStack matrices,
                            int light, double[][] points, float nx, float ny, float nz) {
        double[] uv = cube.uv.get(name);
        if (uv == null || uv[0] == uv[2] || uv[1] == uv[3]) return;
        float u0 = (float) (uv[0] / 64.0), v0 = (float) (uv[1] / 64.0);
        float u1 = (float) (uv[2] / 64.0), v1 = (float) (uv[3] / 64.0);
        // The vertices above are wound outward; keep the Blockbench UV corner
        // pairing intact while reversing the quad's winding.
        float[][] tex = name.equals("up") || name.equals("down")
                ? new float[][]{{u0,v0},{u0,v1},{u1,v1},{u1,v0}}
                : new float[][]{{u1,v1},{u1,v0},{u0,v0},{u0,v1}};
        for (int i = 0; i < 4; i++) {
            double[] p = points[i];
            buffer.addVertex(matrices.last(), (float)p[0], (float)p[1], (float)p[2])
                    .setColor(255,255,255,255).setUv(tex[i][0], tex[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light == 0 ? FULL_BRIGHT : light)
                    .setNormal(matrices.last(), nx, ny, nz);
        }
    }

    private static ModelData model(String petId,boolean slim) {
        MpsqPetCatalog.Pet pet=MpsqPetCatalog.byId(petId);if(pet==null||pet.miniModel()==null)throw new IllegalArgumentException("Mini-You-Modell nicht registriert: "+petId);
        String modelName=pet.miniModel();String slimResource="/assets/mpsqcamera/models/pets/"+modelName+"_slim.bbmodel";
        if(slim&&MpsqMiniYouModel.class.getResource(slimResource)!=null)modelName+="_slim";
        ModelData cached = MODELS.get(modelName);
        if (cached != null) return cached;
        String resource = "/assets/mpsqcamera/models/pets/"+modelName+".bbmodel";
        try (InputStream stream = MpsqMiniYouModel.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing Blockbench model resource");
            JsonObject root = JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject();
            Map<String, JsonObject> elements = new HashMap<>();
            for (JsonElement element : root.getAsJsonArray("elements")) {
                JsonObject object = element.getAsJsonObject();
                elements.put(object.get("uuid").getAsString(), object);
            }
            Group top = parseGroup(root.getAsJsonArray("outliner").get(0).getAsJsonObject(), elements);
            Map<String, Animation> animations = new HashMap<>();
            for (JsonElement animElement : root.getAsJsonArray("animations")) {
                JsonObject object = animElement.getAsJsonObject();
                Animation animation = Animation.parse(object);
                animations.put(animation.name, animation);
            }
            ModelData data = new ModelData(top, animations);
            MODELS.put(modelName, data);
            return data;
        } catch (Exception error) {
            throw new IllegalStateException("Could not load Mini You Blockbench model", error);
        }
    }

    private static Group parseGroup(JsonObject object, Map<String, JsonObject> elements) {
        if (object.has("visibility") && !object.get("visibility").getAsBoolean()) return null;
        String name = object.has("name") ? object.get("name").getAsString() : "";
        if (name.equals("hitbox") || name.equals("tag_name") || name.equals("l_leash")) return null;
        Group group = new Group(object.get("uuid").getAsString(), vectorArray(object, "origin"),
                vector(object, "rotation"));
        group.name=name;
        if (object.has("children")) for (JsonElement child : object.getAsJsonArray("children")) {
            if (child.isJsonPrimitive()) {
                JsonObject element = elements.get(child.getAsString());
                if (element != null && (!element.has("visibility") || element.get("visibility").getAsBoolean()) && element.get("type").getAsString().equals("cube")) group.cubes.add(Cube.parse(element));
            } else {
                JsonObject nested = child.getAsJsonObject();
                if (nested.has("name") && !nested.get("name").getAsString().equals("tag_name")) {
                    Group parsed = parseGroup(nested, elements);
                    if (parsed != null) group.children.add(parsed);
                }
            }
        }
        return group;
    }

    private static Vec3 vector(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) return Vec3.ZERO;
        JsonArray array = object.getAsJsonArray(key);
        return new Vec3(number(array, 0), number(array, 1), number(array, 2));
    }

    private static JsonArray vectorArray(JsonObject object, String key) {
        if (object.has(key) && object.get(key).isJsonArray()) return object.getAsJsonArray(key);
        JsonArray zero = new JsonArray();
        zero.add(0); zero.add(0); zero.add(0);
        return zero;
    }

    private static double number(JsonArray array, int index) {
        try { return array.get(index).getAsDouble(); } catch (RuntimeException ignored) { return 0; }
    }
    private static double number(JsonArray array, int index, double fallback) {
        try { return array.get(index).getAsDouble(); } catch (RuntimeException ignored) { return fallback; }
    }

    private record ModelData(Group root, Map<String, Animation> animations) { }
    private static final class Group {
        String name="";
        final String id; final JsonArray origin; final Vec3 rotation; final List<Cube> cubes = new ArrayList<>();
        final List<Group> children = new ArrayList<>();
        Group(String id, JsonArray origin, Vec3 rotation) { this.id=id; this.origin=origin; this.rotation=rotation; }
    }
    private record Vec3(double x, double y, double z) {
        static final Vec3 ZERO = new Vec3(0,0,0);
        Vec3 subtract(Vec3 other) { return new Vec3(x-other.x,y-other.y,z-other.z); }
        Vec3 add(Vec3 other) { return new Vec3(x+other.x,y+other.y,z+other.z); }
        Vec3 multiply(double amount) { return new Vec3(x*amount,y*amount,z*amount); }
        static Vec3 mix(Vec3 a, Vec3 b, double t) { return a.add(b.subtract(a).multiply(t)); }
            static Vec3 catmull(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
            double t2=t*t, t3=t2*t;
            return new Vec3(catmull(p0.x,p1.x,p2.x,p3.x,t,t2,t3),
                    catmull(p0.y,p1.y,p2.y,p3.y,t,t2,t3),
                    catmull(p0.z,p1.z,p2.z,p3.z,t,t2,t3));
        }
        private static double catmull(double p0,double p1,double p2,double p3,double t,double t2,double t3) {
            return 0.5*((2*p1)+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t2+(-p0+3*p1-3*p2+p3)*t3);
        }
    }

    private static final class Cube {
        final Vec3 from, to, origin, rotation; final Map<String,double[]> uv = new HashMap<>();
        Cube(Vec3 from, Vec3 to, Vec3 origin, Vec3 rotation) { this.from=from; this.to=to; this.origin=origin; this.rotation=rotation; }
        static Cube parse(JsonObject object) {
            Cube cube = new Cube(vector(object,"from"), vector(object,"to"), vector(object,"origin"), vector(object,"rotation"));
            JsonObject faces=object.getAsJsonObject("faces");
            for (String face : new String[]{"north","south","east","west","up","down"}) {
                if (!faces.has(face)) continue;
                JsonObject faceObject=faces.getAsJsonObject(face);
                if (!faceObject.has("uv") || !faceObject.has("texture") || faceObject.get("texture").isJsonNull()) continue;
                JsonArray rect=faceObject.getAsJsonArray("uv");
                cube.uv.put(face,new double[]{number(rect,0),number(rect,1),number(rect,2),number(rect,3)});
            }
            return cube;
        }
    }

    private static final class Animation {
        final String name; final double length; final boolean loop;
        final Map<String, Map<String,List<Key>>> tracks = new HashMap<>();
        Animation(String name, double length, boolean loop) { this.name=name; this.length=length; this.loop=loop; }
        static Animation parse(JsonObject object) {
            Animation animation = new Animation(object.get("name").getAsString(),
                    object.get("length").getAsDouble(), object.get("loop").getAsString().equals("loop"));
            JsonObject animators=object.getAsJsonObject("animators");
            for (var entry : animators.entrySet()) {
                JsonArray keyframes=entry.getValue().getAsJsonObject().getAsJsonArray("keyframes");
                Map<String,List<Key>> channels=new HashMap<>();
                for (JsonElement element : keyframes) {
                    JsonObject key=element.getAsJsonObject();
                    String channel=key.get("channel").getAsString();
                    if (!channel.equals("rotation") && !channel.equals("position") && !channel.equals("scale")) continue;
                    JsonElement points=key.get("data_points");
                    if (!points.isJsonArray() || points.getAsJsonArray().isEmpty()) continue;
                    JsonObject point=points.getAsJsonArray().get(0).getAsJsonObject();
                    Vec3 value=new Vec3(read(point,"x"),read(point,"y"),read(point,"z"));
                    channels.computeIfAbsent(channel, unused -> new ArrayList<>()).add(new Key(key.get("time").getAsDouble(),value,key.has("interpolation")?key.get("interpolation").getAsString():"linear"));
                }
                channels.values().forEach(list -> list.sort(java.util.Comparator.comparingDouble(Key::time)));
                animation.tracks.put(entry.getKey(),channels);
            }
            return animation;
        }
        Vec3 value(String bone, String channel, double time) {
            List<Key> keys=tracks.getOrDefault(bone,Map.of()).get(channel);
            if (keys==null || keys.isEmpty()) return channel.equals("scale")?new Vec3(1,1,1):Vec3.ZERO;
            if (time <= keys.get(0).time) return keys.get(0).value;
            for (int i=1;i<keys.size();i++) if (time<=keys.get(i).time) {
                Key a=keys.get(i-1), b=keys.get(i); double d=Math.max(1e-6,b.time-a.time);
                double t=(time-a.time)/d;
                if(a.interpolation.equals("step"))return a.value;
                if(a.interpolation.equals("catmullrom"))return Vec3.catmull(keys.get(Math.max(0,i-2)).value,a.value,b.value,keys.get(Math.min(keys.size()-1,i+1)).value,t);
                return Vec3.mix(a.value,b.value,t);
            }
            return keys.get(keys.size()-1).value;
        }
        private static double read(JsonObject object,String key) { try{return object.get(key).getAsDouble();}catch(RuntimeException ignored){return 0;} }
    }
    private record Key(double time, Vec3 value, String interpolation) { }
}

