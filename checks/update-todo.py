from pathlib import Path
p=Path('../../../MPSQ-TODO.md');s=p.read_text(encoding='utf-8-sig')
s=s.replace('- [ ] Sky Squid Game im Spiel prüfen: weiße Teilnehmer vor Start, erster Knopf speichert Teilnehmer, 15 Minuten je Turm, Rot bei Ausscheiden, Grün beim Bestehen, Übergang ohne neue Teilnehmeraufnahme.','''- [x] Sky Squid Game: Teilnehmer bei Systemaktivierung in der Gesamtfläche erfassen; ausgeschiedene Spieler/Streamer auslassen. Eigene Redstone-Aktion „Sky Squid Game: Turm starten (15 Min.)“ je Turm. Grün erlaubt den Übergang, im nächsten Turm wieder Weiß. Die konfigurierbare Y-Grenze scheidet Weiß und Grün gleichermaßen aus.
- [ ] Sky Squid Game im Spiel prüfen: alle drei Turm-Knöpfe, vollständiger Wechsel Weiß → Grün → Weiß, sicheres Weiterlaufen, Y-Sturz vor/während/nach einer Runde und Ablauf der 15 Minuten. Bestehenden System-Knopf für die neue Y-Grenze neu speichern.
- [ ] Rundherum-Hologramm nach Renderkorrektur im Spiel prüfen: exakte Raummitte, Zählung und Sichtbarkeit durch Wände für Teammitglieder.''')
s=s.replace('- [ ] TV im Spiel prüfen:', '- [x] TV-Korrektur: Gehäuse-UVs auf die 8×8-Projektfläche berichtigt; pinkes Knopfmenü öffnet nach Abschluss der Chat-Eingabe.\n- [ ] TV im Spiel prüfen:')
p.write_text(s,encoding='utf-8')
