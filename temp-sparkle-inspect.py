import json,base64,struct
from pathlib import Path
for p in Path('src/client/resources/assets/mpsqcamera/custom_models/furniture').glob('*.json'):
 j=json.loads(p.read_text()); print(p.stem)
 for k,v in j['textures'].items():
  d=base64.b64decode(v.split(',')[-1]); print(k,struct.unpack('>II',d[16:24]))
 if 'gold' in p.stem or 'donator' in p.stem:
  for e in j['elements']:
   if any('sparkle' in str(f.get('texture')) for f in e['faces'].values()): print(e);break
