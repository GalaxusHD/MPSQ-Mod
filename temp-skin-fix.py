from pathlib import Path
b=Path('src/client/java/de/galaxushd/mpsqcamera')
p=b/'MpsqPetRenderer.java';s=p.read_text(encoding='utf-8-sig');start=s.index('            NativeImage expanded = new NativeImage');end=s.index('            Identifier texture =',start);s=s[:start]+'            NativeImage expanded = MpsqSkinImages.normalize(source);\n'+s[end:];p.write_text(s,encoding='utf-8')
p=b/'MpsqNpcSkinRenderer.java';s=p.read_text(encoding='utf-8-sig');start=s.index('                if (image.getHeight() == 32)');end=s.index('                Identifier textureId',start);s=s[:start]+'                image = MpsqSkinImages.normalize(image);\n'+s[end:];p.write_text(s,encoding='utf-8')
# Keep the authored animation metadata alongside the embedded strip.
import json
for p in Path('src/client/resources/assets/mpsqcamera/custom_models/furniture').glob('*.json'):
 j=json.loads(p.read_text(encoding='utf-8-sig'));meta={k:{'frametime':2,'interpolate':True} for k in j['textures'] if 'sparkle' in k}
 if meta:
  j['texture_animations']=meta;p.write_text(json.dumps(j,separators=(',',':')),encoding='utf-8')
