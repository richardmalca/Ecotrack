begin;

alter table public.ecotrack_rewards
    add column catalog_key text unique,
    add column category text not null default 'Materiales' check (category in ('Materiales','Servicios','Ayuda','Donaciones')),
    add column details text not null default '',
    add column fulfillment text not null default '',
    add column photo text,
    add column is_sample boolean not null default false,
    add column max_per_user integer not null default 1 check (max_per_user > 0);

alter table public.ecotrack_points add column request_id uuid;
create unique index ecotrack_points_request_uidx on public.ecotrack_points(resident_id, request_id) where request_id is not null;
create index ecotrack_points_reward_idx on public.ecotrack_points(resident_id, reward_id) where reward_id is not null;

create or replace function ecotrack_internal.redeem(reward_id uuid, request_key uuid) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
    resident uuid := auth.uid();
    selected_reward public.ecotrack_rewards;
    previous_reward uuid;
    receipt uuid;
begin
    if resident is null then raise exception 'Inicia sesión.'; end if;
    if request_key is null then raise exception 'La solicitud de canje necesita un identificador.'; end if;
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(resident::text, 0));

    select id, points.reward_id into receipt, previous_reward
    from public.ecotrack_points as points where resident_id = resident and request_id = request_key;
    if found then
        if previous_reward is distinct from redeem.reward_id then
            raise exception 'El identificador ya corresponde a otro premio.';
        end if;
        return receipt;
    end if;

    select * into selected_reward from public.ecotrack_rewards
    where id = redeem.reward_id for update;
    if not found then raise exception 'El premio ya no está disponible.'; end if;
    if selected_reward.stock <= 0 then raise exception 'El premio se agotó.'; end if;
    if (select count(*) from public.ecotrack_points as points
        where resident_id = resident and points.reward_id = redeem.reward_id) >= selected_reward.max_per_user
        then raise exception 'Ya alcanzaste el límite de canjes para este premio.'; end if;
    if coalesce((select sum(amount) from public.ecotrack_points where resident_id = resident), 0) < selected_reward.cost
        then raise exception 'Saldo insuficiente.'; end if;

    update public.ecotrack_rewards set stock = stock - 1 where id = selected_reward.id;
    insert into public.ecotrack_points(resident_id, amount, reward_id, request_id)
    values (resident, -selected_reward.cost, selected_reward.id, request_key) returning id into receipt;
    return receipt;
end;
$$;

create or replace function ecotrack_internal.redeem(reward_id uuid) returns uuid
language sql security invoker set search_path = '' as $$
    select ecotrack_internal.redeem(reward_id, gen_random_uuid());
$$;

create or replace function public.ecotrack_redeem(reward_id uuid, request_key uuid) returns uuid
language sql security invoker set search_path = '' as $$ select ecotrack_internal.redeem(reward_id, request_key); $$;

revoke execute on function ecotrack_internal.redeem(uuid, uuid), public.ecotrack_redeem(uuid, uuid) from public, anon;
grant execute on function ecotrack_internal.redeem(uuid, uuid), public.ecotrack_redeem(uuid, uuid) to authenticated;

alter publication supabase_realtime add table public.ecotrack_rewards;
commit;

