from pathlib import Path
names=['MpsqSkyRound','MpsqGamesRuntime','MpsqFinishZoneTool','MpsqActionSetupScreen','MpsqActionSync','MpsqTriggerManager','MpsqTvFurniture']
for name in names:
 p=Path('src/client/java/de/galaxushd/mpsqcamera')/(name+'.java')
 s=p.read_bytes().decode('utf-8',errors='surrogateescape')
 s=''.join(bytes([ord(c)-0xdc00]).decode('cp1252') if 0xdc80<=ord(c)<=0xdcff else c for c in s)
 p.write_text(s,encoding='utf-8')
