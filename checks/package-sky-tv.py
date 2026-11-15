from pathlib import Path
import shutil
out=Path('../Sky-TV-Raum-Fix');out.mkdir(exist_ok=True)
notes='''# Sky Squid Game, Rundherum und TV – tvzones2

Minecraft 26.3, lokale SR-Offizier-Version. Bestehende Mod-JAR ersetzen; nicht beide Versionen gleichzeitig laden.

## Einrichtung von Sky Squid Game

1. Gesamtfläche und drei Turmzonen anlegen bzw. beibehalten.
2. Den System-Knopf mit `/mpsq-redstone` neu speichern: „Aktion / System starten oder wechseln“, Sky Squid Game wählen und die Ausscheidungs-Höhe Y eintragen. Alte Konfigurationen ohne Höhe müssen ergänzt werden.
3. In jedem Turm einen Knopf mit `/mpsq-redstone` auf „Sky Squid Game: Turm starten (15 Min.)“ setzen. Nur eingerichtete Turmknöpfe starten die Runde.
4. Vor Aktivierung müssen alle Teilnehmer in der Gesamtfläche sein. Bei Aktivierung wird die Teilnehmerliste eingefroren; spätere Besucher werden nicht aufgenommen. VIPs, Teammitglieder und bereits per `/p kick` ausgeschiedene Spieler/Streamer zählen nicht. Dorfbewohner und andere Mobs bleiben als Testteilnehmer möglich.

## Ablauf

- Zu Beginn Weiß. Der Turmknopf startet genau 15 Minuten, sobald alle verbleibenden Teilnehmer im aktuellen Turm stehen. Wiederholtes Drücken verlängert die Zeit nicht.
- Verlässt während der laufenden Runde jemand die Turmbox, wird diese Person rot, die übrigen werden grün. Gleichzeitige Aussteiger werden ebenfalls rot.
- Grün erlaubt den Weg zum nächsten Turm. Dort wird der Glow wieder weiß; erst der nächste Turmknopf startet dessen Timer.
- Wer unter die eingestellte Y-Grenze fällt, wird immer rot: vor dem Timer, während der Runde, auf dem grünen Übergang und auch nach Bestehen des dritten Turms. Genau auf der Grenze ist man noch nicht ausgeschieden.
- Läuft ein Timer ohne Ausscheiden ab, verlieren alle verbliebenen Teilnehmer.
- Kick-Ausschlüsse bleiben beim Wechsel zwischen Spielsystemen erhalten und werden beim Wechsel/Verlassen der Welt zurückgesetzt.

## Weitere Korrekturen

- Rundherum: „N Personen“ mit Minecraft-Namensschild-Rendering in der XYZ-Mitte der Raumzone; Teammitglieder sehen den Text auch durch Wände.
- TV: korrigierte UV-Zuordnung für Rahmen und Gehäuse, alle eingebetteten Texturen bleiben enthalten.
- Pinken TV-Knopf direkt anvisieren und `/mpsq-redstone` eingeben. Das Einstellungsmenü öffnet nach Schließen des Chats. Kleine Klicktoleranz ergänzt; gespeicherte Verknüpfungen werden vor dem Einrichten neu geladen.

## Prüfung

Gradle-Build und alle 13 vorhandenen Prüfgruppen bestanden, darunter 118 Spielsystem-Prüfungen und 320 TV-Prüfungen. Die letzte anschließende Änderung vereinheitlicht ausschließlich Zeilenumbrüche. Sichtbarkeit, Knopfbedienung und vollständiger Spielablauf sind noch im laufenden Minecraft zu bestätigen. Die Spielauswertung verwendet die vom Client geladenen Entitäten; sie ist keine serverweite Erfassung ungeladener Chunks.
'''
(out/'HINWEISE.md').write_text(notes,encoding='utf-8')
paths=['src/client/java/de/galaxushd/mpsqcamera/'+n+'.java' for n in ['MpsqSkyRound','MpsqGamesRuntime','MpsqFinishZoneTool','MpsqActionSetupScreen','MpsqActionSync','MpsqTriggerManager','MpsqTvFurniture']]+['src/client/resources/assets/mpsqcamera/custom_models/furniture/mpsq_tv.json','checks/GameSystemsCheck.java','checks/TvModelCheck.java']
for name in paths:
 dest=out/'Geaenderte-Dateien'/'Mod'/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(name,dest)
shutil.copy2('../../../MPSQ-TODO.md',out/'MPSQ-TODO.md')
print(str(out.resolve()))
