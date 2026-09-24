-- MPSQ model catalogue and placed NPC records.
-- Safe to run after the existing MPSQ asset-library SQL.
begin;

alter table public.mpsq_assets drop constraint if exists mpsq_assets_kind_check;
alter table public.mpsq_assets add constraint mpsq_assets_kind_check
  check(kind in ('model','jar','sound','npc_skin'));
alter table public.mpsq_assets drop constraint if exists mpsq_assets_category_check;
alter table public.mpsq_assets add constraint mpsq_assets_category_check
  check(category in ('shared','furniture','accessory','sound','npc_skin','npc_model','npc_skin_normal','npc_skin_slim','mod_release'));

create table if not exists public.mpsq_world_npcs (
  id uuid primary key default gen_random_uuid(),
  server_id text not null,
  world_id text not null,
  x integer not null, y integer not null, z integer not null,
  model_id text not null references public.mpsq_assets(id) on delete cascade,
  created_by uuid not null references public.mpsq_clients(id),
  created_at timestamptz not null default now(),
  unique(server_id,world_id,x,y,z,model_id)
);
create index if not exists mpsq_world_npcs_scope_idx
  on public.mpsq_world_npcs(server_id,world_id,x,y,z);
alter table public.mpsq_world_npcs enable row level security;
revoke all on public.mpsq_world_npcs from anon, authenticated;

commit;
