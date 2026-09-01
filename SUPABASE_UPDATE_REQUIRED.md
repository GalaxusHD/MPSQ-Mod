# Erforderliche Supabase-/Edge-Function-Erweiterungen

Der Client ist auf den aktuellen MPSQ-Team-Funktionsstand gebracht. Damit die
neuen Funktionen zentral und autoritativ arbeiten, muss die bereitgestellte
`mpsq-api` Edge Function dieselben Erweiterungen erhalten. Ihr Quellcode und
das vollständige Datenbankschema waren in `MPSQ-Mod-main.zip` nicht enthalten.

## Teamprofile

- Rangwert `streamer` zwischen `spieler` und `001` zulassen.
- `GET /team/me` und `GET /team/members` liefern zusätzlich
  `name_visible` (Boolean, Standardwert `true`).
- `POST /team/me/name-visibility` akzeptiert `{ "visible": boolean }` und darf
  ausschließlich das Profil des authentifizierten Tokens ändern.
- Kamerazugriff für `streamer` zulassen.
- Das Sehen ausgeblendeter Namen für `arbeiter`, `soldat`, `offizier`,
  `sr_offizier` und `frontman` zulassen.

## Texte/Vorlagen

- Vorlagen erhalten `speaker` mit den erlaubten Werten `offizier` und
  `frontman`; bestehende Einträge verwenden standardmäßig `offizier`.
- `GET /team/templates` liefert `speaker`.
- `POST /team/templates` akzeptiert `text` und `speaker`.
- `PATCH /team/templates/:id` aktualisiert `text` und `speaker`.
- `DELETE /team/templates/:id` löscht eine Vorlage.
- Bearbeiten und Löschen müssen weiterhin serverseitig anhand des
  authentifizierten Rangs geprüft werden.

## Unverändert beibehalten

- automatische Registrierung und `x-mpsq-token`
- autoritative Rang- und Kameraberechtigungen
- kurzlebige R2-Upload- und Download-Links für Kamerabilder
- gemeinsame Supabase-To-dos einschließlich Backend-Zustand der Häkchen

