from pathlib import Path
b=Path('src/client/java/de/galaxushd/mpsqcamera')
p=b/'MpsqAccessoryRenderer.java';s=p.read_text(encoding='utf-8');at=s.index('    private static final Map<String,Long> figureWobbles')
s=s[:at]+'''    /** Head-only pick volume in the same world transform used to draw this statue. */
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
''' +s[at:];p.write_text(s,encoding='utf-8')
p=b/'MpsqFurnitureManager.java';s=p.read_text(encoding='utf-8-sig');s=s.replace('        double nearest = Double.MAX_VALUE;', '''        double nearest = Double.MAX_VALUE;
        boolean selectedHead=false;
        var block=client.level.clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        double blockDistance=block.getType()==net.minecraft.world.phys.HitResult.Type.MISS?Double.MAX_VALUE:eye.distanceToSqr(block.getLocation());''')
s=s.replace('            var hit = hitbox.clip(eye, end);','''            var headHit=MpsqAccessoryRenderer.figureHeadHit(furniture,eye,end);
            var hit = headHit.isPresent()?headHit:hitbox.clip(eye, end);''')
s=s.replace('            if (distance < nearest) {','            if (distance < nearest && distance<=blockDistance+.001) {');s=s.replace('                selected = furniture;','                selected = furniture;\n                selectedHead=headHit.isPresent();');s=s.replace('        String soundId = selected.has', '        if(selectedHead)MpsqAccessoryRenderer.wobbleFigure(selected);\n\n        String soundId = selected.has');p.write_text(s,encoding='utf-8')
