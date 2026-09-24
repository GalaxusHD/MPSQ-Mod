# MPSQ-Modellquellen

Die ersten drei Pakete sind vorbereitet, damit sie im Adminbereich mit dem Modell-Importer angelegt werden können:

- `minecraft-cat-lying/model.obj` + `jellie.png` → Kategorie **Möbel**, ID `minecraft-cat-lying`.
- `coins-and-money/model.gltf` → Kategorie **Möbel**, ID `coins-and-money`. Die PNG-Textur ist im GLTF eingebettet.
- `among-us/among-us.gltf` → Kategorie **NPC-Modell**, ID `among-us`. Die PNG-Textur ist im GLTF eingebettet.
- `8f74992ddfa7277e.png` → als **NPC-Skin (Slim)** mit einer eigenen, eindeutigen ID hochladen.

Im Admin-Upload das jeweilige Modell und bei OBJ zusätzlich die PNG-Textur gemeinsam auswählen. Bei GLTF reicht die GLTF-Datei, solange die Textur eingebettet ist. ZIP-Dateien selbst werden nicht hochgeladen; die Originalarchive liegen daneben als Referenzkopien.

`Realistic Death.zip` enthält ModelEngine-/MythicMobs-Dateien für einen serverseitigen Plugin-Ablauf, keine Fabric-Animation für echte Minecraft-Spielermodelle. Die Mod verteilt deshalb bei `/p kick <Spieler>` eine eigene kurze Fallpose an MPSQ-Modnutzer in derselben Welt. Das Ergebnis wird clientseitig ausgelöst, sobald der Kick-Befehl gesendet wird; ohne Server-Plugin kann die Mod den Erfolg des Serverbefehls nicht bestätigen. Die Originaldateien sind als Quellenkopie abgelegt, aber werden nicht als spielbare Fabric-Animation importiert.

Die vier Modellpakete werden erst nach dem Admin-Upload im Online-Katalog beziehungsweise in den Accessoire-Ansichten angezeigt.
