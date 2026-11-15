from pathlib import Path
import shutil,hashlib,json,zipfile
out=Path('../Sky-TV-Raum-Fix');jar=Path('build/libs/mpsq-team-1.1.0-beta.1-local-mc26.3-tvzones2.jar')
with zipfile.ZipFile(jar) as z:
 meta=json.loads(z.read('fabric.mod.json'))
 assert meta['version'].endswith('tvzones2'),meta['version']
 for name in ['MpsqSkyRound','MpsqGamesRuntime','MpsqActionSync','MpsqTvFurniture']:
  assert 'de/galaxushd/mpsqcamera/'+name+'.class' in z.namelist()
 model=json.loads(z.read('assets/mpsqcamera/custom_models/furniture/mpsq_tv.json'))
 assert model['elements'][0]['faces']['north']['uv'][0]==.0625
shutil.copy2(jar,out/jar.name)
info={'version':meta['version'],'minecraft':meta['depends']['minecraft'],'bytes':jar.stat().st_size,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest().upper(),'build':'SUCCESS','checks':'13 groups passed; 118 game checks; 320 TV checks','in_game_verified':False}
(out/'BUILD-INFO.json').write_text(json.dumps(info,indent=2),encoding='utf-8')
p=out/'HINWEISE.md';s=p.read_text(encoding='utf-8').replace(' Die letzte anschließende Änderung vereinheitlicht ausschließlich Zeilenumbrüche.','');p.write_text(s,encoding='utf-8')
print(json.dumps(info,indent=2))
