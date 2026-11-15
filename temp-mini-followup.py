from pathlib import Path
b=Path('src/client/java/de/galaxushd/mpsqcamera')
p=b/'MpsqAccessoryRenderer.java';s=p.read_text(encoding='utf-8');s=s.replace('Axis.XP.rotationDegrees(-headPitch)','Axis.XP.rotationDegrees(headPitch)');s=s.replace("// Minecraft pitch is positive while looking down; the accessory's local\n        // forward axis uses the opposite sign.","// Positive Minecraft pitch follows the player's downward head movement.")
p.write_text(s,encoding='utf-8')
p=b/'MpsqMiniYouPetRenderer.java';s=p.read_text(encoding='utf-8-sig');import re
s=re.sub(r'RETURN_DISTANCE\s*=\s*[\d.]+', 'RETURN_DISTANCE = 5.0',s);s=re.sub(r'ROAM_RADIUS\s*=\s*[\d.]+','ROAM_RADIUS = 4.5',s)
s=s.replace('ownerDistance > RETURN_DISTANCE || now-ownerMovedAt<1000','ownerDistance > RETURN_DISTANCE')
s=s.replace('if (returning || now >= nextWanderAt)', 'if (returning || now >= nextWanderAt || horizontalDistance(targetX,targetZ,owner.getX(),owner.getZ())>RETURN_DISTANCE)')
s=s.replace('/away*1.15','/away*4.0')
s=s.replace('                pauseUntil = 0;\n            } else chooseTarget', '                pauseUntil = 0;\n                nextWanderAt = 0;\n            } else chooseTarget')
s=s.replace('owner.getX(),owner.getZ())>10','owner.getX(),owner.getZ())>RETURN_DISTANCE')
s=s.replace('if(distance>3 || ownerSpeed>.16)','if(distance>8 || ownerSpeed>.16)')
p.write_text(s,encoding='utf-8')
p=b/'MpsqModelsScreen.java';s=p.read_text(encoding='utf-8-sig').replace('if(!MpsqActionSync.server().isBlank())return;for(var item:MpsqBuiltinFurnitureCatalog.all())','for(var item:MpsqBuiltinFurnitureCatalog.all())');p.write_text(s,encoding='utf-8')
