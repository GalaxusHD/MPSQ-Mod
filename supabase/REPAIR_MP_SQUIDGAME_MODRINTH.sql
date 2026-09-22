-- Reparatur fuer das bestaetigte Modrinth-Profil "Fabric 1.21.8" von MP_SquidGame.
-- Der aktuelle Modrinth-Zugang uebernimmt das vorhandene Root-Konto samt dessen
-- Daten. Der bisherige Root-Zugang wird ungueltig. Keine Schluessel ausgeben!
begin;
do $$
declare
    root_id constant uuid := 'd15839fc-0cf9-47b8-ad91-80d3f9f5d031';
    device_id constant uuid := 'ebc16317-ac10-4809-b7bc-737472128a9d';
    bound_id uuid;
    device_hash text;
begin
    select root_client_id into bound_id from public.mpsq_team_root where id = 1 for update;
    if bound_id is distinct from root_id then
        raise exception 'Abbruch: Root-Bindung stimmt nicht mit dem geprueften Konto ueberein.';
    end if;
    perform 1 from public.mpsq_clients where id in (root_id, device_id) order by id for update;
    if not exists (select 1 from public.mpsq_clients where id = root_id and lower(display_name) = 'mp_squidgame')
       or not exists (select 1 from public.mpsq_team_profiles where client_id = root_id and base_rank = 'sr_offizier') then
        raise exception 'Abbruch: Bestaetigtes Sr-Offizier-Konto nicht gefunden.';
    end if;
    select token_hash into device_hash from public.mpsq_clients
      where id = device_id and lower(display_name) = 'mp_squidgame';
    if device_hash is null or device_hash = '' then
        raise exception 'Abbruch: Bestaetigtes Modrinth-Konto nicht gefunden.';
    end if;
    if not exists (select 1 from public.mpsq_team_profiles where client_id = device_id and base_rank = 'spieler') then
        raise exception 'Abbruch: Doppelprofil wurde bereits bereinigt oder inzwischen veraendert. Nicht erneut anwenden.';
    end if;
    -- Zuerst den eindeutigen Tokenhash freigeben. Der Zufallsersatz ist kein Login-Token.
    update public.mpsq_clients set token_hash = encode(sha256(convert_to(gen_random_uuid()::text, 'UTF8')), 'hex')
      where id = device_id;
    update public.mpsq_clients set token_hash = device_hash where id = root_id;
    update public.mpsq_team_profiles set active_rank = null, updated_at = now() where client_id = root_id;
    -- Nur die bestaetigte doppelte Rangzuordnung entfernen; Clientdaten bleiben erhalten.
    delete from public.mpsq_team_profiles where client_id = device_id;
end $$;
commit;
select c.id, c.display_name, p.base_rank, p.active_rank
from public.mpsq_clients c join public.mpsq_team_profiles p on p.client_id = c.id
where c.id = 'd15839fc-0cf9-47b8-ad91-80d3f9f5d031';
