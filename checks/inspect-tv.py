import json,base64,io
from pathlib import Path
from PIL import Image
m=json.loads(Path('C:/Users/lanck_m63m7au/Downloads/tv.bbmodel').read_text())
print('meta',m.get('meta'),'resolution',m.get('resolution'))
for i,t in enumerate(m['textures']):
 print(i,{k:v for k,v in t.items() if k not in ('source','path')})
 im=Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1])))
 im.resize((512,512)).save('checks/tv-texture-'+str(i)+'.png')
for i,e in enumerate(m['elements']):
 print(i,e.get('name'),e.get('faces'))
