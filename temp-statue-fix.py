from pathlib import Path
p=Path('src/client/java/de/galaxushd/mpsqcamera/MpsqAccessoryRenderer.java');s=p.read_text(encoding='utf-8-sig')
s=s.replace('            var camera=context.camera().getPos();','            MpsqAnimatedTextures.update();\n            var camera=context.camera().getPos();',1)
s=s.replace('var texture=new DynamicTexture(()->"MPSQ Accessoire",image);','JsonObject textureAnimation=bundle.has("texture_animations")&&bundle.getAsJsonObject("texture_animations").has(entry.getKey())?bundle.getAsJsonObject("texture_animations").getAsJsonObject(entry.getKey()):null;\n                    var texture=MpsqAnimatedTextures.create(id,image,textureAnimation);')
s=s.replace('private static void clearModels(Minecraft client){','private static void clearModels(Minecraft client){MpsqAnimatedTextures.clear();figureWobbles.clear();')
s=s.replace('for(Identifier id:textures.values())Minecraft.getInstance().getTextureManager().release(id);MpsqCameraClient.LOGGER.warn', 'for(Identifier id:textures.values()){MpsqAnimatedTextures.remove(id);Minecraft.getInstance().getTextureManager().release(id);}MpsqCameraClient.LOGGER.warn')
# Remove the extra cached tick of head lag; vanilla interpolates previous/current entity angles.
start=s.index('        HeadAngleHistory history=playerHeadAngles.get',s.index('private static HeadAngles interpolatedHeadAngles'))
end=s.index('\n    private static void applyHeadAccessoryTransform',start)
s=s[:start]+'''        return new HeadAngles(Mth.rotLerp(tickProgress,player.yHeadRotO,player.getYHeadRot()),player.getViewXRot(tickProgress));
    }
'''+s[end:]
s=s.replace('player.getEyeHeight(net.minecraft.world.entity.Pose.STANDING)','player.getEyeHeight(player.getPose())')
s=s.replace('        if(model.headDisplay()!=null){\n            HeadDisplay display=model.headDisplay();\n            matrices.translate(0.0,eyeHeight,0.0);','        float headCenter=headHeight-0.25f;\n        if(model.headDisplay()!=null){\n            HeadDisplay display=model.headDisplay();\n            matrices.translate(0.0,headCenter,0.0);')
s=s.replace('matrices.translate(display.translation()[0]/16.0,display.translation()[1]/16.0,display.translation()[2]/16.0);','matrices.translate(display.translation()[0]/16.0,eyeHeight-headCenter+display.translation()[1]/16.0,display.translation()[2]/16.0);')
s=s.replace('        matrices.translate(0.0,eyeHeight,0.0);\n        matrices.rotate(Axis.YP.rotationDegrees(-headYaw));','        matrices.translate(0.0,headCenter,0.0);\n        matrices.rotate(Axis.YP.rotationDegrees(-headYaw));')
s=s.replace('headHeight-eyeHeight-bounds.minY()*modelScale','headHeight-headCenter-bounds.minY()*modelScale')
# Per-placement gesture; the rest pose is the first authored keyframe, not a looping wobble.
s=s.replace('    private static void drawFurniture(Model model,','    private static final Map<String,Long> figureWobbles=new HashMap<>();\n    private static String figureKey(JsonObject object){return str(object,"id",object.toString());}\n    public static void wobbleFigure(JsonObject object){figureWobbles.put(figureKey(object),System.nanoTime());}\n    private static void drawFurniture(Model model,')
s=s.replace('drawBbModel(selected,matrices,consumers,0xFFFFFFFF,"idle",(float)((System.nanoTime()/1_000_000L%3_600_000L)/1000.0));','{\n                Long started=figureWobbles.get(figureKey(object));\n                float elapsed=started==null?0:(System.nanoTime()-started)/1_000_000_000f;\n                JsonObject clip=findAnimation(selected,"idle");\n                float length=clip!=null&&clip.has("length")?clip.get("length").getAsFloat():1.25f;\n                if(elapsed>=length){figureWobbles.remove(figureKey(object));elapsed=0;}\n                drawBbModel(selected,matrices,consumers,0xFFFFFFFF,"idle",elapsed);\n            }')
p.write_text(s,encoding='utf-8')
