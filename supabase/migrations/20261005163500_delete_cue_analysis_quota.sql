-- A data-erasure request also removes the owner's analysis quota counters.
create or replace function cue_private.delete_my_cue_data()
returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare owner uuid := (select auth.uid());
begin
    if owner is null then
        raise exception 'Sign in to delete Cue data' using errcode = '28000';
    end if;
    delete from cue_private.analysis_quota where user_id = owner;
    delete from public.history_batches where user_id = owner;
    delete from public.reminder_events where user_id = owner;
    delete from public.sources where user_id = owner;
    delete from public.commitments where user_id = owner;
end;
$function$;
