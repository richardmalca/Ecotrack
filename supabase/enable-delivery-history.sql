begin;

alter table public.ecotrack_deliveries
    add column resolved_at timestamptz,
    drop constraint ecotrack_deliveries_status_check,
    add constraint ecotrack_deliveries_status_check
        check (status in ('pending', 'approved', 'rejected', 'cancelled'));

update public.ecotrack_deliveries as delivery
set resolved_at = points.created_at
from public.ecotrack_points as points
where delivery.code = points.delivery_code and delivery.status = 'approved';

create or replace function ecotrack_internal.stamp_delivery_resolution() returns trigger
language plpgsql set search_path = '' as $$
begin
    if old.status = 'pending' and new.status <> 'pending' then
        new.resolved_at := clock_timestamp();
    end if;
    return new;
end;
$$;

create trigger ecotrack_delivery_resolution before update of status on public.ecotrack_deliveries
for each row execute function ecotrack_internal.stamp_delivery_resolution();

create or replace function ecotrack_internal.cancel_delivery(delivery_code uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
    update public.ecotrack_deliveries
    set status = 'cancelled'
    where code = delivery_code and resident_id = auth.uid() and status = 'pending'
        and created_at >= now() - interval '30 minutes';
    if not found then raise exception 'El QR venció o ya fue revisado.'; end if;
end;
$$;

create or replace function public.ecotrack_cancel_delivery(delivery_code uuid) returns void
language sql security invoker set search_path = '' as $$ select ecotrack_internal.cancel_delivery(delivery_code); $$;

revoke execute on function ecotrack_internal.stamp_delivery_resolution(), ecotrack_internal.cancel_delivery(uuid)
    from public, anon;
grant execute on function ecotrack_internal.cancel_delivery(uuid) to authenticated;
revoke execute on function public.ecotrack_cancel_delivery(uuid) from public, anon;
grant execute on function public.ecotrack_cancel_delivery(uuid) to authenticated;

commit;
