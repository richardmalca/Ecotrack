begin;

alter table public.ecotrack_points
    add column reward_delivery text check (reward_delivery in ('pickup', 'contribution')),
    add column fulfilled_at timestamptz,
    add column fulfilled_by uuid references auth.users(id);

update public.ecotrack_points as entry
set reward_delivery = case when reward.category = 'Donaciones' then 'contribution' else 'pickup' end,
    fulfilled_at = case when reward.category = 'Donaciones' then entry.created_at end
from public.ecotrack_rewards as reward
where reward.id = entry.reward_id;

create function ecotrack_internal.set_reward_delivery() returns trigger
language plpgsql set search_path = '' as $$
begin
    if new.reward_id is not null then
        select case when category = 'Donaciones' then 'contribution' else 'pickup' end
        into new.reward_delivery from public.ecotrack_rewards where id = new.reward_id;
        if new.reward_delivery = 'contribution' then new.fulfilled_at := new.created_at; end if;
    end if;
    return new;
end;
$$;

create trigger ecotrack_reward_delivery before insert on public.ecotrack_points
for each row execute function ecotrack_internal.set_reward_delivery();

alter table public.ecotrack_points add constraint ecotrack_reward_delivery_matches_entry
    check ((reward_id is null) = (reward_delivery is null));

create function ecotrack_internal.inspect_reward(receipt_id uuid) returns text
language plpgsql stable security definer set search_path = '' as $$
declare
    prize text;
begin
    if auth.uid() is null or not exists (
        select 1 from ecotrack_internal.collectors where user_id = auth.uid()
    ) then raise exception 'Se requiere una cuenta de recolector.'; end if;

    select reward.name into prize
    from public.ecotrack_points as entry
    join public.ecotrack_rewards as reward on reward.id = entry.reward_id
    where entry.id = receipt_id and entry.resident_id <> auth.uid()
        and entry.reward_delivery = 'pickup' and entry.fulfilled_at is null;
    if not found then raise exception 'El premio ya fue reclamado o el QR no está disponible.'; end if;
    return prize;
end;
$$;

create function ecotrack_internal.fulfill_reward(receipt_id uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
    if auth.uid() is null or not exists (
        select 1 from ecotrack_internal.collectors where user_id = auth.uid()
    ) then raise exception 'Se requiere una cuenta de recolector.'; end if;

    update public.ecotrack_points
    set fulfilled_at = clock_timestamp(), fulfilled_by = auth.uid()
    where id = receipt_id and resident_id <> auth.uid() and reward_delivery = 'pickup' and fulfilled_at is null;
    if not found then raise exception 'El premio ya fue reclamado o el QR no está disponible.'; end if;
end;
$$;

create function public.ecotrack_inspect_reward(receipt_id uuid) returns text
language sql security invoker set search_path = '' as $$ select ecotrack_internal.inspect_reward(receipt_id); $$;

create function public.ecotrack_fulfill_reward(receipt_id uuid) returns void
language sql security invoker set search_path = '' as $$ select ecotrack_internal.fulfill_reward(receipt_id); $$;

revoke execute on function ecotrack_internal.set_reward_delivery(), ecotrack_internal.inspect_reward(uuid),
    ecotrack_internal.fulfill_reward(uuid), public.ecotrack_inspect_reward(uuid), public.ecotrack_fulfill_reward(uuid)
    from public, anon;
grant execute on function ecotrack_internal.inspect_reward(uuid), ecotrack_internal.fulfill_reward(uuid),
    public.ecotrack_inspect_reward(uuid), public.ecotrack_fulfill_reward(uuid) to authenticated;

commit;
