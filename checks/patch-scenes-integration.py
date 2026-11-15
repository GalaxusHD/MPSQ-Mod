from pathlib import Path
p=Path(__file__).resolve().parents[1]/'src/client/java/de/galaxushd/mpsqcamera'
def edit(name, old, new):
    f=p/name;s=f.read_text(encoding='utf-8');assert old in s,(name,old[:80]);f.write_text(s.replace(old,new),encoding='utf-8')
edit('MpsqCameraClient.java','MpsqNpcManager.initialize();','MpsqNpcManager.initialize();\n        MpsqScenes.initialize();')
edit('MpsqCinematicScene.java','public double movementStart;','public double movementStart;\n        public String scenesClip="idle";\n        public float scenesScale=1;')
edit('MpsqCinematicScene.java','void restoreFrom(Npc n){name=n.name;','void restoreFrom(Npc n){scenesClip=n.scenesClip;scenesScale=n.scenesScale;name=n.name;')
edit('MpsqCinematicScene.java','if(linkedNpc==null)linkedNpc="";','if(scenesClip==null)scenesClip="idle";if(!Float.isFinite(scenesScale)||scenesScale<.25||scenesScale>3)throw new IllegalArgumentException("Ungültige NPC-Größe.");\n            if(linkedNpc==null)linkedNpc="";')
edit('MpsqCinematicActors.java','if(n.category.startsWith("npc_skin"))return java.util.List.of("idle","swing","jump");','if(n.category.startsWith("npc_skin")){var result=new java.util.ArrayList<String>(java.util.List.of("idle","swing","jump"));for(var clip:MpsqScenesClips.all())result.add(clip.id());return result;}')
edit('MpsqCinematicActors.java','(moving&&clips(n).contains("walk")?"walk":"idle")','(moving&&clips(n).contains("walk")?"walk":n.scenesClip)')
edit('MpsqCinematicActors.java','if(active!=null){\n            active.snapTo','if(active!=null){\n            active.setInvisible(MpsqScenesClips.get(clip)!=null);\n            active.snapTo')
edit('MpsqCinematicActors.java','if(model!=null&&pose!=null', '''if(active instanceof RemotePlayer player&&pose!=null&&MpsqScenesClips.get(pose.clip())!=null&&MpsqCinematicPreview.active())
                MpsqAccessoryRenderer.drawScenesActor(ctx,new MpsqNpcSkinRenderer.Skin(player.getSkin().body().texturePath(),settings.category.endsWith("slim")),pose,settings.scenesScale);
            if(model!=null&&pose!=null''')
edit('MpsqAccessoryRenderer.java','String skinTextureKey){}','String skinTextureKey,boolean scenes){}')
edit('MpsqAccessoryRenderer.java','model.headDisplay(),model.skinTextureKey());','model.headDisplay(),model.skinTextureKey(),model.scenes());')
edit('MpsqAccessoryRenderer.java','headDisplay,str(bundle,"skin_texture_key","")));','headDisplay,str(bundle,"skin_texture_key",""),bundle.has("scenes")&&bundle.get("scenes").getAsBoolean()));')
edit('MpsqAccessoryRenderer.java','return MpsqBuiltinFurnitureCatalog.byUrl(url)!=null||','return MpsqScenesClips.resource(url)!=null||MpsqBuiltinFurnitureCatalog.byUrl(url)!=null||')
edit('MpsqAccessoryRenderer.java','if(MpsqBuiltinFurnitureCatalog.byUrl(url)!=null){try(InputStream stream=', '''if(MpsqScenesClips.resource(url)!=null){try(InputStream stream=MpsqAccessoryRenderer.class.getResourceAsStream(MpsqScenesClips.resource(url))){if(stream==null)throw new IOException("Scenes-Modell fehlt");bytes=stream.readNBytes(12000001);}}
                else if(MpsqBuiltinFurnitureCatalog.byUrl(url)!=null){try(InputStream stream=''')
edit('MpsqAccessoryRenderer.java','for(var value:npcs){var o=value.getAsJsonObject();String assetId=', 'for(var value:npcs){var o=MpsqScenes.apply(value.getAsJsonObject());if(o.has("scenes_hidden")&&o.get("scenes_hidden").getAsBoolean())continue;String assetId=')
edit('MpsqAccessoryRenderer.java','if(playerSkin?skin==null:model==null)continue;', '''var scenesProfile=playerSkin?MpsqScenes.stored(str(o,"id","")):null;
                if(playerSkin&&scenesProfile!=null){if(scenesProfile.npc.type.equals("self")&&client.player!=null)skin=new MpsqNpcSkinRenderer.Skin(client.player.getSkin().body().texturePath(),slim);else if(skin==null)prepareCinematicSkin(url,slim);}
                if(playerSkin?skin==null:model==null)continue;''')
edit('MpsqAccessoryRenderer.java','if(playerSkin){var state=MpsqNpcSkinRenderer.createState', '''if(playerSkin&&scenesProfile!=null&&scenesProfile.enabled&&MpsqScenesClips.get(MpsqScenes.clip(scenesProfile))!=null){
                    drawScenesActor(context,skin,MpsqScenes.pose(scenesProfile),size);
                }
                else if(playerSkin){var state=MpsqNpcSkinRenderer.createState''')
edit('MpsqAccessoryRenderer.java','int light=net.minecraft.util.LightCoordsUtil.getLightCoords(client.level,net.minecraft.core.BlockPos.containing(x,y,z));MpsqNpcSkinRenderer.render(state', 'if(scenesProfile!=null)MpsqScenes.decorate(state,scenesProfile);int light=net.minecraft.util.LightCoordsUtil.getLightCoords(client.level,net.minecraft.core.BlockPos.containing(x,y,z));MpsqNpcSkinRenderer.render(state')
edit('MpsqAccessoryRenderer.java','position=model.skinTextureKey().isBlank()?animationTrack', 'position=model.scenes()?MpsqScenesClips.sample(clip,bone,"position",time):model.skinTextureKey().isBlank()?animationTrack')
edit('MpsqAccessoryRenderer.java','delta=model.skinTextureKey().isBlank()?animationTrack', 'delta=model.scenes()?MpsqScenesClips.sample(clip,bone,"rotation",time):model.skinTextureKey().isBlank()?animationTrack')
edit('MpsqAccessoryRenderer.java','matrices.translate(-origin[0],-origin[1],-origin[2]);\n        boolean hitbox=', 'if(model.scenes()){var scale=MpsqScenesClips.sample(clip,bone,"scale",time);matrices.scale(scale[0],scale[1],scale[2]);}matrices.translate(-origin[0],-origin[1],-origin[2]);\n        boolean hitbox=')
edit('MpsqAccessoryRenderer.java','    public static void drawGuiPreview(', '''    static void drawScenesActor(WorldRenderContext ctx,MpsqNpcSkinRenderer.Skin skin,MpsqCinematicActors.Pose pose,float scale){
        var clip=MpsqScenesClips.get(pose.clip());if(clip==null)return;String url=MpsqScenesClips.url(clip);Model model=models.get(url);
        if(model==null){if(models.size()+loading.size()<64)load(url,generation);return;}
        var textures=new HashMap<>(model.textures());textures.put("0",skin.texture());
        Model selected=new Model(model.elements(),model.meshes(),model.bones(),model.animations(),textures,model.previewColors(),model.bakedGeometry(),model.headDisplay(),model.skinTextureKey(),true);
        var m=ctx.matrixStack();var buffers=ctx.consumers();if(m==null||buffers==null)return;var camera=ctx.camera().getPos();
        m.pushPose();m.translate(pose.x()-camera.x,pose.y()-camera.y,pose.z()-camera.z);m.rotate(Axis.YP.rotationDegrees(180-pose.yaw()));m.scale(scale/16f,scale/16f,scale/16f);
        drawCinematicBbModel(selected,m,buffers,clip.clip(),pose.clipTime(),pose.headYaw(),pose.pitch());m.popPose();
    }
    public static void drawGuiPreview(''')
edit('MpsqNpcManager.java','JsonObject npc = element.getAsJsonObject();','JsonObject npc = MpsqScenes.apply(element.getAsJsonObject());\n            if(npc.has("scenes_hidden")&&npc.get("scenes_hidden").getAsBoolean())continue;')
edit('MpsqNpcManager.java','        interact(npc);','        if(!MpsqScenes.interact(npc))interact(npc);')
