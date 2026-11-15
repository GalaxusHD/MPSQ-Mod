from pathlib import Path
for name in ['MpsqSkyRound','MpsqGamesRuntime','MpsqFinishZoneTool','MpsqActionSetupScreen','MpsqActionSync','MpsqTriggerManager','MpsqTvFurniture']:
 p=Path('src/client/java/de/galaxushd/mpsqcamera')/(name+'.java')
 p.write_bytes(p.read_bytes().replace(b'\r\r\n',b'\r\n'))
