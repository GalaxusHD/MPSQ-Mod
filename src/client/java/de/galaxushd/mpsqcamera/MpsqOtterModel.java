package de.galaxushd.mpsqcamera;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads the supplied Blockbench model and its keyed poses for the local otter companion. */
final class MpsqOtterModel {
    private static final Identifier MODEL_TEXTURE = Identifier.of(MpsqCameraClient.MOD_ID,
            "textures/pets/nocsy_otter_v2.png");
    private static final String MODEL_RESOURCE = "/assets/mpsqcamera/models/pets/nocsy_otter_v2.bbmodel";
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static ModelData data;

    private MpsqOtterModel() { }

    static double animationLength(String name) {
        Animation animation = model().animations.get(name);
        return animation == null ? 1.0 : animation.length;
    }

    static void render(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                       float x, float y, float z, float yaw, float scale,
                       String animation, double timeSeconds) {
        ModelData model = model();
        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - yaw));
        matrices.scale(scale, scale, scale);
        VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(MODEL_TEXTURE));
        Animation pose = model.animations.get(animation);
        if (pose == null) pose = model.animations.get("idle");
        double poseTime = pose == null ? 0 : pose.loop
                ? timeSeconds % Math.max(0.001, pose.length) : Math.min(timeSeconds, pose.length);
        renderGroup(model.root, matrices, vertices, light, pose, poseTime, 0, 0, 0);
        matrices.pop();
    }

    private static void renderGroup(Group group, MatrixStack matrices, VertexConsumer vertices, int light,
                                    Animation animation, double time, double parentX, double parentY,
                                    double parentZ) {
        double gx = number(group.origin, 0), gy = number(group.origin, 1), gz = number(group.origin, 2);
        matrices.push();
        matrices.translate((gx - parentX) / 16.0, (gy - parentY) / 16.0, (gz - parentZ) / 16.0);
        Vec3 rotation = group.rotation;
        Vec3 animatedRotation = animation == null ? Vec3.ZERO : animation.value(group.id, "rotation", time);
        Vec3 animatedPosition = animation == null ? Vec3.ZERO : animation.value(group.id, "position", time);
        matrices.translate(animatedPosition.x / 16.0, animatedPosition.y / 16.0, animatedPosition.z / 16.0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (rotation.z + animatedRotation.z)));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (rotation.y + animatedRotation.y)));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) (rotation.x + animatedRotation.x)));

        for (Cube cube : group.cubes) renderCube(cube, matrices, vertices, light, gx, gy, gz);
        for (Group child : group.children) renderGroup(child, matrices, vertices, light, animation, time, gx, gy, gz);
        matrices.pop();
    }

    private static void renderCube(Cube cube, MatrixStack matrices, VertexConsumer buffer, int light,
                                  double gx, double gy, double gz) {
        matrices.push();
        matrices.translate((cube.origin.x - gx) / 16.0, (cube.origin.y - gy) / 16.0,
                (cube.origin.z - gz) / 16.0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) cube.rotation.z));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) cube.rotation.y));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) cube.rotation.x));

        double x0 = (cube.from.x - cube.origin.x) / 16.0;
        double y0 = (cube.from.y - cube.origin.y) / 16.0;
        double z0 = (cube.from.z - cube.origin.z) / 16.0;
        double x1 = (cube.to.x - cube.origin.x) / 16.0;
        double y1 = (cube.to.y - cube.origin.y) / 16.0;
        double z1 = (cube.to.z - cube.origin.z) / 16.0;
        face(cube, "north", buffer, matrices, light, new double[][]{{x0,y0,z0},{x1,y0,z0},{x1,y1,z0},{x0,y1,z0}}, 0,0,-1);
        face(cube, "south", buffer, matrices, light, new double[][]{{x1,y0,z1},{x0,y0,z1},{x0,y1,z1},{x1,y1,z1}}, 0,0,1);
        face(cube, "west", buffer, matrices, light, new double[][]{{x0,y0,z1},{x0,y0,z0},{x0,y1,z0},{x0,y1,z1}}, -1,0,0);
        face(cube, "east", buffer, matrices, light, new double[][]{{x1,y0,z0},{x1,y0,z1},{x1,y1,z1},{x1,y1,z0}}, 1,0,0);
        face(cube, "up", buffer, matrices, light, new double[][]{{x0,y1,z0},{x1,y1,z0},{x1,y1,z1},{x0,y1,z1}}, 0,1,0);
        face(cube, "down", buffer, matrices, light, new double[][]{{x0,y0,z1},{x1,y0,z1},{x1,y0,z0},{x0,y0,z0}}, 0,-1,0);
        matrices.pop();
    }

    private static void face(Cube cube, String name, VertexConsumer buffer, MatrixStack matrices,
                            int light, double[][] points, float nx, float ny, float nz) {
        double[] uv = cube.uv.get(name);
        if (uv == null) return;
        float u0 = (float) (uv[0] / 64.0), v0 = (float) (uv[1] / 64.0);
        float u1 = (float) (uv[2] / 64.0), v1 = (float) (uv[3] / 64.0);
        float[][] tex = {{u0,v0},{u1,v0},{u1,v1},{u0,v1}};
        for (int i = 0; i < 4; i++) {
            double[] p = points[i];
            buffer.vertex(matrices.peek(), (float)p[0], (float)p[1], (float)p[2])
                    .color(255,255,255,255).texture(tex[i][0], tex[i][1])
                    .overlay(OverlayTexture.DEFAULT_UV).light(light == 0 ? FULL_BRIGHT : light)
                    .normal(matrices.peek(), nx, ny, nz);
        }
    }

    private static ModelData model() {
        if (data != null) return data;
        try (InputStream stream = MpsqOtterModel.class.getResourceAsStream(MODEL_RESOURCE)) {
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
                if (object.get("name").getAsString().equals("death")) continue;
                Animation animation = Animation.parse(object);
                animations.put(animation.name, animation);
            }
            data = new ModelData(top, animations);
            return data;
        } catch (Exception error) {
            throw new IllegalStateException("Could not load Nocsy's Otter Blockbench model", error);
        }
    }

    private static Group parseGroup(JsonObject object, Map<String, JsonObject> elements) {
        Group group = new Group(object.get("uuid").getAsString(), vectorArray(object, "origin"),
                vector(object, "rotation"));
        if (object.has("children")) for (JsonElement child : object.getAsJsonArray("children")) {
            if (child.isJsonPrimitive()) {
                JsonObject element = elements.get(child.getAsString());
                if (element != null && element.get("type").getAsString().equals("cube")) group.cubes.add(Cube.parse(element));
            } else {
                JsonObject nested = child.getAsJsonObject();
                if (nested.has("name") && !nested.get("name").getAsString().equals("tag_name")) {
                    group.children.add(parseGroup(nested, elements));
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
                if (!faceObject.has("uv")) continue;
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
                    if (!channel.equals("rotation") && !channel.equals("position")) continue;
                    JsonElement points=key.get("data_points");
                    if (!points.isJsonArray() || points.getAsJsonArray().isEmpty()) continue;
                    JsonObject point=points.getAsJsonArray().get(0).getAsJsonObject();
                    Vec3 value=new Vec3(read(point,"x"),read(point,"y"),read(point,"z"));
                    channels.computeIfAbsent(channel, unused -> new ArrayList<>()).add(new Key(key.get("time").getAsDouble(),value));
                }
                channels.values().forEach(list -> list.sort(java.util.Comparator.comparingDouble(Key::time)));
                animation.tracks.put(entry.getKey(),channels);
            }
            return animation;
        }
        Vec3 value(String bone, String channel, double time) {
            List<Key> keys=tracks.getOrDefault(bone,Map.of()).get(channel);
            if (keys==null || keys.isEmpty()) return Vec3.ZERO;
            if (time <= keys.get(0).time) return keys.get(0).value;
            for (int i=1;i<keys.size();i++) if (time<=keys.get(i).time) {
                Key a=keys.get(i-1), b=keys.get(i); double d=Math.max(1e-6,b.time-a.time);
                return Vec3.mix(a.value,b.value,(time-a.time)/d);
            }
            return keys.get(keys.size()-1).value;
        }
        private static double read(JsonObject object,String key) { try{return object.get(key).getAsDouble();}catch(RuntimeException ignored){return 0;} }
    }
    private record Key(double time, Vec3 value) { }
}
