from pathlib import Path
import shutil,json,hashlib,zipfile
out=Path('../TV-Bedienung-Video-Fix');out.mkdir(exist_ok=True)
jar=Path('build/libs/mpsq-team-1.1.0-beta.1-local-mc26.3-tvcontrols1.jar')
with zipfile.ZipFile(jar) as z:
 meta=json.loads(z.read('fabric.mod.json'));assert meta['version'].endswith('tvcontrols1')
shutil.copy2(jar,out/jar.name)
for name in ['MpsqTvFurniture','ScreenRenderer','ScreenStatusTextures','CinemaBrowserManager']:
 rel=Path('src/client/java/de/galaxushd/mpsqcamera')/(name+'.java');dest=out/'Geaenderte-Dateien'/'Mod'/rel;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(rel,dest)
for name in ['TvModelCheck.java','VideoOverlayCheck.cjs']:
 dest=out/'Geaenderte-Dateien'/'Mod'/'checks'/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(Path('checks')/name,dest)
(out/'HINWEISE.md').write_text('''# TV-Bedienung und Videostart – tvcontrols1

Minecraft 26.3. Die bisherige MPSQ-JAR ersetzen, nicht zusätzlich laden.

- Pinke Knöpfe 1, 2 und 3: frei über `/mpsq-redstone` konfigurierbar. Die gespeicherten Aktionsplätze bleiben erhalten und sind nun den pinken Nummern zugeordnet.
- Graue Knöpfe: linker Pfeil zehn Sekunden zurück bzw. vorherige Kamera; Pause-Symbol Play/Pause bzw. Kameraansicht; rechter Pfeil zehn Sekunden vor bzw. nächste Kamera.
- Bildschirm-Platzhalter: hellere Farben und volle Deckkraft; transparente Hintergrundreste bleiben entfernt.
- YouTube: Die Mod entfernt die schwarze Untertitel-Abdeckung der alten MPSQ-Player-Seite, auch wenn sie nachträglich eingesetzt wird. Die Untertitel-Abschaltung selbst bleibt erhalten. Kein zusätzlicher Webseiten-Upload nötig.

Build erfolgreich. 364 TV-/Darstellungsprüfungen sowie 9 Prüfungen der Player-Abdeckung bestanden. Die tatsächliche YouTube-Wiedergabe und Darstellung im laufenden Minecraft sind noch zu bestätigen.
''',encoding='utf-8')
(out/'BUILD-INFO.json').write_text(json.dumps({'version':meta['version'],'bytes':jar.stat().st_size,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'checks':{'tv':364,'videoOverlay':9},'inGameVerified':False},indent=2),encoding='utf-8')
print(str((out/jar.name).resolve()))
