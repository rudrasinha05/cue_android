-- Atomic, owner-scoped quota for optional Gemini reminder analysis.
create table if not exists cue_private.analysis_quota (
    user_id uuid primary key references auth.users(id) on delete cascade,
    minute_start timestamptz not null,
    minute_count integer not null,
    day_start date not null,
    day_count integer not null
);
alter table cue_private.analysis_quota enable row level security;
revoke all on cue_private.analysis_quota from public, anon, authenticated;

create or replace function cue_private.consume_analysis_quota()
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
declare
    owner uuid := (select auth.uid());
    now_utc timestamptz := clock_timestamp();
    allowed boolean;
begin
    if owner is null then return false; end if;
    insert into cue_private.analysis_quota as quota
        (user_id, minute_start, minute_count, day_start, day_count)
    values (owner, date_trunc('minute', now_utc), 1,
        (now_utc at time zone 'UTC')::date, 1)
    on conflict (user_id) do update set
        minute_start = excluded.minute_start,
        minute_count = case when quota.minute_start = excluded.minute_start
            then quota.minute_count + 1 else 1 end,
        day_start = excluded.day_start,
        day_count = case when quota.day_start = excluded.day_start
            then quota.day_count + 1 else 1 end
    where (quota.minute_start <> excluded.minute_start or quota.minute_count < 10)
      and (quota.day_start <> excluded.day_start or quota.day_count < 200)
    returning true into allowed;
    return coalesce(allowed, false);
end;
$function$;
revoke all on function cue_private.consume_analysis_quota() from public, anon, authenticated;
grant execute on function cue_private.consume_analysis_quota() to authenticated;

create or replace function public.consume_cue_analysis_quota()
returns boolean
language sql
security invoker
set search_path = ''
as $function$
    select cue_private.consume_analysis_quota();
$function$;
revoke all on function public.consume_cue_analysis_quota() from public, anon, authenticated;
grant execute on function public.consume_cue_analysis_quota() to authenticated;
