begin;

-- Name An/Aus: existing profiles and new registrations default to visible.
alter table public.mpsq_team_profiles
  add column if not exists name_visible boolean not null default true;

-- Replace only rank-related CHECK constraints so the new Streamer rank can
-- be stored without depending on the old constraint names.
do $$
declare
  constraint_row record;
begin
  for constraint_row in
    select conname
    from pg_constraint
    where conrelid = 'public.mpsq_team_profiles'::regclass
      and contype = 'c'
      and (
        pg_get_constraintdef(oid) ilike '%base_rank%'
        or pg_get_constraintdef(oid) ilike '%active_rank%'
      )
  loop
    execute format(
      'alter table public.mpsq_team_profiles drop constraint %I',
      constraint_row.conname
    );
  end loop;
end
$$;

alter table public.mpsq_team_profiles
  add constraint mpsq_team_profiles_base_rank_check
    check (base_rank in (
      'vip', 'spieler', 'streamer', '001', 'soldat',
      'arbeiter', 'offizier', 'frontman', 'sr_offizier'
    )),
  add constraint mpsq_team_profiles_active_rank_check
    check (
      active_rank is null
      or active_rank in (
        'vip', 'spieler', 'streamer', '001', 'soldat',
        'arbeiter', 'offizier', 'frontman', 'sr_offizier'
      )
    );

commit;
