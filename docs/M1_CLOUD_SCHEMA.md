# Cue M1 cloud schema

This is the SQL applied to Cue project `gukhuakkguzzhrvtyszx` through the Supabase migration `cue_m1_owner_scoped_store`. It is recorded here for code review and reproducibility; it has already been applied to that project.

```sql
create table public.commitments (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  title text not null check (length(trim(title)) > 0),
  details text,
  due_at timestamptz,
  timezone text not null default 'UTC',
  status text not null default 'active' check (status in ('active', 'completed', 'cancelled', 'archived')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (id, user_id)
);
create index commitments_owner_due_idx on public.commitments (user_id, due_at);
create index commitments_owner_updated_idx on public.commitments (user_id, updated_at desc);
alter table public.commitments enable row level security;
create policy commitments_owner on public.commitments
  for all to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));
revoke all on public.commitments from anon;
grant select, insert, update, delete on public.commitments to authenticated;

create table public.sources (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  commitment_id uuid not null,
  origin_type text not null,
  origin_key text,
  title text,
  excerpt text,
  original_uri text,
  permission_state text not null default 'available' check (permission_state in ('available', 'revoked', 'deleted')),
  captured_at timestamptz not null default now(),
  foreign key (commitment_id, user_id) references public.commitments(id, user_id) on delete cascade,
  unique (id, user_id),
  unique (user_id, origin_type, origin_key)
);
create index sources_commitment_idx on public.sources (user_id, commitment_id);
alter table public.sources enable row level security;
create policy sources_owner on public.sources
  for all to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));
revoke all on public.sources from anon;
grant select, insert, update, delete on public.sources to authenticated;

create table public.reminder_events (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  commitment_id uuid not null,
  event_type text not null,
  change_data jsonb not null default '{}'::jsonb,
  actor text not null default 'app',
  idempotency_key text not null,
  occurred_at timestamptz not null default now(),
  foreign key (commitment_id, user_id) references public.commitments(id, user_id) on delete cascade,
  unique (user_id, idempotency_key)
);
create index reminder_events_timeline_idx on public.reminder_events (user_id, commitment_id, occurred_at desc);
alter table public.reminder_events enable row level security;
create policy reminder_events_owner on public.reminder_events
  for select to authenticated
  using (user_id = (select auth.uid()));
create policy reminder_events_insert on public.reminder_events
  for insert to authenticated
  with check (user_id = (select auth.uid()));
revoke all on public.reminder_events from anon;
grant select, insert on public.reminder_events to authenticated;
```
