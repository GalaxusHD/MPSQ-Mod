-- Run before deploying the matching mpsq-api function. Existing quests remain standard.
alter table public.mpsq_quests add column if not exists category text not null default 'standard' check (category in ('standard', 'event'));
