from pathlib import Path
b=Path('src/client/java/de/galaxushd/mpsqcamera')
p=b/'MpsqAccessoryRenderer.java';s=p.read_text(encoding='utf-8-sig')
s=s.replace('HeadDisplay headDisplay){}','HeadDisplay headDisplay,String skinTextureKey){}')
s=s.replace('return WUMPUS_MODEL_URL.equals(url)||','return MpsqBuiltinFurnitureCatalog.byUrl(url)!=null||WUMPUS_MODEL_URL.equals(url)||',1)
s=s.replace('if(isBuiltinModel(url)){String path=', 'if(MpsqBuiltinFurnitureCatalog.byUrl(url)!=null){try(InputStream stream=MpsqAccessoryRenderer.class.getResourceAsStream(MpsqBuiltinFurnitureCatalog.byUrl(url).resource())){if(stream==null)throw new IOException("Möbelmodell fehlt");bytes=stream.readNBytes(12000001);}}\n                else if(isBuiltinModel(url)){String path=',1)
s=s.replace('bakeFurnitureGeometry(elements,meshes,bones,textures),headDisplay)', 'bakeFurnitureGeometry(elements,meshes,bones,textures),headDisplay,str(bundle,"skin_texture_key",""))')
s=s.replace('String url=localAssetUrls.get(id);if(url==null)continue;', 'String url=MpsqBuiltinFurnitureCatalog.byId(id)!=null?MpsqBuiltinFurnitureCatalog.byId(id).url():localAssetUrls.get(id);if(url==null)continue;')
s=s.replace('drawBakedFurniture(model,matrices,consumers,0xFFFFFFFF);', 'drawFurniture(model,o,matrices,consumers);',1)
where='    private static void drawBakedFurniture('
i=s.index(where)
s=s[:i]+'''    private static void drawFurniture(Model model,JsonObject object,PoseStack matrices,MultiBufferSource consumers){
        Model selected=model;
        String name=str(object,"skin_name","");
        if(!model.skinTextureKey().isBlank()&&!name.isBlank()){
            var skin=MpsqFigureSkins.get(name);
            if(skin!=null){
                Map<String,Identifier> textures=new HashMap<>(model.textures());
                textures.put(model.skinTextureKey(),skin.body().texturePath());
                selected=new Model(model.elements(),model.meshes(),model.bones(),model.animations(),textures,model.previewColors(),model.bakedGeometry(),model.headDisplay(),model.skinTextureKey());
            }
        }
        if(!model.skinTextureKey().isBlank())
            drawBbModel(selected,matrices,consumers,0xFFFFFFFF,"idle",(float)((System.nanoTime()/1_000_000L%3_600_000L)/1000.0));
        else drawBakedFurniture(selected,matrices,consumers,0xFFFFFFFF);
    }
''' +s[i:];p.write_text(s,encoding='utf-8')
p=b/'MpsqLocalObjectStore.java';s=p.read_text(encoding='utf-8-sig');needle='        if(!MpsqLocalWorldStore.available())return false;';s=s.replace(needle,'''        return set(world,x,y,z,model,rotation,displayName,scale,soundId,"",remove);
    }
    public static synchronized boolean set(String world,int x,int y,int z,String model,int rotation,String displayName,float scale,String soundId,String skinName,boolean remove){
'''+needle,1);s=s.replace('object.addProperty("sound_id",soundId==null?"":soundId);','object.addProperty("skin_name",skinName==null?"":skinName);\n            object.addProperty("sound_id",soundId==null?"":soundId);');p.write_text(s,encoding='utf-8')
p=b/'MpsqObjectScreen.java';s=p.read_text(encoding='utf-8-sig');s=s.replace('    private boolean pending;','    private boolean pending;\n    private String skinName="";')
s=s.replace('initialModel=str(existing,', 'skinName=str(existing,"skin_name","");initialModel=str(existing,')
s=s.replace('    @Override protected void init(){', '''    @Override protected void init(){
        String keepModel=model==null?initialModel:model.getValue();
        String keepName=displayName==null?initialName:displayName.getValue();
        String keepSound=soundId==null?initialSoundId:soundId.getValue();''')
s=s.replace('setValue(initialModel)','setValue(keepModel)').replace('setValue(initialName)','setValue(keepName)').replace('setValue(initialSoundId)','setValue(keepSound)')
s=s.replace('        addRenderableWidget(Button.builder(Component.literal(editing?', '''        Button figure=addRenderableWidget(Button.builder(Component.literal("Top-Figur: Spieler-Skin …"),b->minecraft.gui.setScreen(new MpsqFigureSkinScreen(this,skinName,value->skinName=value))).bounds(width/2-120,182,240,20).build());
        figure.active=MpsqBuiltinFurnitureCatalog.byId(model.getValue().trim())!=null;
        model.setResponder(value->figure.active=MpsqBuiltinFurnitureCatalog.byId(value.trim())!=null);
        addRenderableWidget(Button.builder(Component.literal(editing?''')
s=s.replace('.bounds(width/2-120,182,240,20).build());\n        addRenderableWidget(Button.builder(Component.literal("Möbel hier entfernen")','.bounds(width/2-120,208,240,20).build());\n        addRenderableWidget(Button.builder(Component.literal("Möbel hier entfernen")')
s=s.replace('b->save(true)).bounds(width/2-120,208,240,20)','b->save(true)).bounds(width/2-120,234,240,20)')
s=s.replace('if(server.isBlank() && minecraft.getSingleplayerServer()!=null){','if(MpsqLocalWorldStore.available()){')
s=s.replace('size,soundValue,remove);','size,soundValue,MpsqBuiltinFurnitureCatalog.byId(model.getValue().trim())!=null?skinName:"",remove);')
s=s.replace('body.addProperty("remove",remove);','body.addProperty("skinName",skinName);body.addProperty("remove",remove);');p.write_text(s,encoding='utf-8')
