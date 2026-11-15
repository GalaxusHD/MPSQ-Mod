from pathlib import Path
b=Path('src/client/java/de/galaxushd/mpsqcamera')
p=b/'MpsqMiniNavigation.java';s=p.read_text(encoding='utf-8-sig').replace('double tx,double tz,long now){','double tx,double tz,long now,boolean returning,double ownerX,double ownerZ){');s=s.replace('// Sample the complete edge','if(!returning&&Math.hypot(nx-ownerX,nz-ownerZ)>10)return Double.NaN;\n                // Sample the complete edge');p.write_text(s,encoding='utf-8')
p=b/'MpsqMiniYouPetRenderer.java';s=p.read_text(encoding='utf-8-sig').replace('targetX,targetZ,now);','targetX,targetZ,now,returning,owner.getX(),owner.getZ());');p.write_text(s,encoding='utf-8')
