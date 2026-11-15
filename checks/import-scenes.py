"""Import purchased Blockbench assets, never the server plugin or its implementation."""
import json, zipfile, copy
from pathlib import Path

root = Path(__file__).resolve().parents[1]
out = root / 'src/main/resources/assets/mpsqcamera/custom_models/scenes'
out.mkdir(parents=True, exist_ok=True)
catalog = []
for vol in (1, 5):
    with zipfile.ZipFile(Path.home() / 'Downloads' / f'6.0 Scenes Townsfolks Vol {vol}.zip') as archive:
        for name in archive.namelist():
            if not name.endswith('.bbmodel'): continue
            model = json.loads(archive.read(name))
            groups = {g['uuid']: g for g in model.get('groups', [])}
            elements, refs = [], {}
            for element in model['elements']:
                if element.get('type', 'cube') != 'cube': raise ValueError('Unsupported geometry')
                e = {k: copy.deepcopy(element.get(k, [0,0,0])) for k in ('from','to','origin','rotation')}
                e['faces'] = {}
                for side, f in element['faces'].items():
                    t = f.get('texture')
                    if t is None: continue
                    tex = model['textures'][int(t)]
                    w, h = tex.get('uv_width',tex.get('width',64)), tex.get('uv_height',tex.get('height',64))
                    uv = f['uv']
                    e['faces'][side] = dict(texture=str(t), uv=[uv[0]/w,uv[1]/h,uv[2]/w,uv[3]/h], rotation=f.get('rotation',0))
                refs[element['uuid']] = len(elements)
                elements.append(e)
            def bone(node):
                g = groups.get(node['uuid'], node)
                result = dict(name=g['name'], origin=g.get('origin',[0,0,0]), rotation=g.get('rotation',[0,0,0]), elements=[], children=[])
                for child in node.get('children', []):
                    if isinstance(child, str):
                        if child in refs: result['elements'].append(refs[child])
                    else: result['children'].append(bone(child))
                return result
            bones = [bone(n) for n in model['outliner'] if isinstance(n,dict)]
            animations = []
            for a in model.get('animations', []):
                anim = dict(name=a['name'], length=a['length'], loop=a['loop'], animators={})
                for uid, source in a['animators'].items():
                    if source.get('type') != 'bone': continue
                    tracks = {}
                    for key in source.get('keyframes', []):
                        if key['channel'] not in ('position','rotation','scale'): continue
                        values = [float(key['data_points'][0].get(axis,0)) for axis in ('x','y','z')]
                        tracks.setdefault(key['channel'], []).append(dict(time=key['time'],values=values,interpolation=key.get('interpolation','linear')))
                    for frames in tracks.values(): frames.sort(key=lambda k:k['time'])
                    anim['animators'][source['name']] = tracks
                animations.append(anim)
            bundle = dict(elements=elements,bones=bones,animations=animations,textures={str(i):t['source'] for i,t in enumerate(model['textures'])},skin_texture_key='0',scenes=True)
            stem=Path(name).stem
            def skin_layers(target, slim):
                # Preserve the authored skeleton/requisites; attach the player's second skin layer.
                def uv_box(u,v,w,h,d):
                    return {'north':[u+d,v+d,u+d+w,v+d+h], 'east':[u,v+d,u+d,v+d+h],
                        'south':[u+2*d+w,v+d,u+2*d+2*w,v+d+h], 'west':[u+d+w,v+d,u+2*d+w,v+d+h],
                        'up':[u+d+w,v+d,u+d,v], 'down':[u+d+2*w,v,u+d+w,v+d]}
                def visit(b):
                    part=b['name'].removeprefix('h_')
                    boxes={'head':(32,0,8,8,8), 'body':(16,32,8,12,4),
                        'right_arm':(40,32,3 if slim else 4,12,4),'left_arm':(48,48,3 if slim else 4,12,4),
                        'right_leg':(0,32,4,12,4),'left_leg':(0,48,4,12,4)}
                    if part in boxes:
                        for index in list(b['elements']):
                            base=target['elements'][index]
                            if not base['faces'] or any(f['texture']!='0' for f in base['faces'].values()):continue
                            if slim and part.endswith('_arm'):
                                center=(base['from'][0]+base['to'][0])/2
                                for bound in ('from','to'):base[bound][0]=center+(base[bound][0]-center)*.75
                                u,v=(40,16) if part=='right_arm' else (32,48)
                                base['faces']={side:dict(texture='0',uv=[n/64 for n in coords]) for side,coords in uv_box(u,v,3,12,4).items()}
                            overlay=copy.deepcopy(base);inflate=.45 if part=='head' else .23
                            overlay['from']=[n-inflate for n in overlay['from']];overlay['to']=[n+inflate for n in overlay['to']]
                            overlay['faces']={side:dict(texture='0',uv=[n/64 for n in coords]) for side,coords in uv_box(*boxes[part]).items()}
                            b['elements'].append(len(target['elements']));target['elements'].append(overlay)
                    for child in b['children']:visit(child)
                for b in target['bones']:visit(b)
            for slim in (False,True):
                variant=copy.deepcopy(bundle);skin_layers(variant,slim)
                (out / f'{stem}{"_slim" if slim else ""}.json').write_text(json.dumps(variant,separators=(',',':')),encoding='utf-8')
            for a in animations:
                catalog.append(dict(id=f'scenes/{stem}/{a["name"]}',model=stem,clip=a['name'],length=a['length'],volume=vol))
(out/'catalog.json').write_text(json.dumps(catalog,indent=2),encoding='utf-8')
print(f'Imported {len(catalog)} clips from both purchased packs')
