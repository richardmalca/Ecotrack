begin;

create schema if not exists ecotrack_internal;
revoke all on schema ecotrack_internal from public;
grant usage on schema ecotrack_internal to authenticated;

create table public.ecotrack_sites (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    details text not null default '',
    lat double precision not null check (lat between -90 and 90),
    lng double precision not null check (lng between -180 and 180),
    materials text[] not null check (
        cardinality(materials) > 0 and materials <@ array['Papel', 'Cartón', 'Vidrio', 'Plástico']
    )
);

create table public.ecotrack_rewards (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    cost integer not null check (cost > 0),
    stock integer not null check (stock >= 0)
);

create table public.ecotrack_deliveries (
    code uuid primary key default gen_random_uuid(),
    resident_id uuid not null references auth.users(id),
    material text not null check (material in ('Papel', 'Cartón', 'Vidrio', 'Plástico')),
    status text not null default 'pending' check (status in ('pending', 'approved', 'rejected', 'cancelled')),
    created_at timestamptz not null default now(),
    resolved_at timestamptz,
    reviewed_by uuid references auth.users(id)
);

create table public.ecotrack_points (
    id uuid primary key default gen_random_uuid(),
    resident_id uuid not null references auth.users(id),
    amount integer not null,
    delivery_code uuid unique references public.ecotrack_deliveries(code),
    reward_id uuid references public.ecotrack_rewards(id),
    created_at timestamptz not null default now(),
    check (
        (amount > 0 and delivery_code is not null and reward_id is null)
        or (amount < 0 and reward_id is not null and delivery_code is null)
    )
);

create table ecotrack_internal.collectors (
    user_id uuid primary key references auth.users(id)
);

create index ecotrack_deliveries_resident_idx on public.ecotrack_deliveries(resident_id);
create index ecotrack_points_resident_idx on public.ecotrack_points(resident_id);

alter table public.ecotrack_sites enable row level security;
alter table public.ecotrack_rewards enable row level security;
alter table public.ecotrack_deliveries enable row level security;
alter table public.ecotrack_points enable row level security;
alter table ecotrack_internal.collectors enable row level security;

revoke all on public.ecotrack_sites, public.ecotrack_rewards, public.ecotrack_deliveries, public.ecotrack_points
    from public, anon, authenticated;
grant select on public.ecotrack_sites, public.ecotrack_rewards, public.ecotrack_deliveries, public.ecotrack_points
    to authenticated;

create policy ecotrack_sites_read on public.ecotrack_sites for select to authenticated using (true);
create policy ecotrack_rewards_read on public.ecotrack_rewards for select to authenticated using (true);
create policy ecotrack_own_deliveries on public.ecotrack_deliveries for select to authenticated
    using (resident_id = (select auth.uid()));
create policy ecotrack_own_points on public.ecotrack_points for select to authenticated
    using (resident_id = (select auth.uid()));

create function ecotrack_internal.wallet() returns jsonb
language sql stable security definer set search_path = '' as $$
    select jsonb_build_object(
        'balance', coalesce((
            select sum(amount) from public.ecotrack_points where resident_id = auth.uid()
        ), 0),
        'collector', exists (
            select 1 from ecotrack_internal.collectors where user_id = auth.uid()
        )
    ) where auth.uid() is not null;
$$;

create function ecotrack_internal.create_delivery(kind text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
    delivery uuid;
begin
    if auth.uid() is null then raise exception 'Inicia sesión.'; end if;
    insert into public.ecotrack_deliveries(resident_id, material)
    values (auth.uid(), kind) returning code into delivery;
    return delivery;
end;
$$;

create function ecotrack_internal.lookup_delivery(delivery_code uuid) returns text
language plpgsql stable security definer set search_path = '' as $$
declare
    kind text;
begin
    if auth.uid() is null or not exists (
        select 1 from ecotrack_internal.collectors where user_id = auth.uid()
    ) then raise exception 'Se requiere una cuenta de recolector.'; end if;

    select material into kind from public.ecotrack_deliveries
    where code = delivery_code and resident_id <> auth.uid() and status = 'pending'
        and created_at >= now() - interval '30 minutes';
    if not found then raise exception 'El QR venció o la entrega ya fue revisada.'; end if;
    return kind;
end;
$$;

create function ecotrack_internal.review_delivery(delivery_code uuid, approve boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare
    delivery public.ecotrack_deliveries;
begin
    if auth.uid() is null or not exists (
        select 1 from ecotrack_internal.collectors where user_id = auth.uid()
    ) then raise exception 'Se requiere una cuenta de recolector.'; end if;

    update public.ecotrack_deliveries
    set status = case when approve then 'approved' else 'rejected' end, reviewed_by = auth.uid()
    where code = delivery_code and status = 'pending' and resident_id <> auth.uid()
        and created_at >= now() - interval '30 minutes'
    returning * into delivery;
    if not found then raise exception 'El QR venció o la entrega ya fue revisada.'; end if;

    if approve then
        perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(delivery.resident_id::text, 0));
        insert into public.ecotrack_points(resident_id, amount, delivery_code)
        values (delivery.resident_id, 20, delivery.code);
    end if;
end;
$$;

create function ecotrack_internal.redeem(reward_id uuid) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
    resident uuid := auth.uid();
    price integer;
    receipt uuid;
begin
    if resident is null then raise exception 'Inicia sesión.'; end if;
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(resident::text, 0));

    update public.ecotrack_rewards set stock = stock - 1
    where id = reward_id and stock > 0 returning cost into price;
    if not found then raise exception 'El premio se agotó.'; end if;
    if coalesce((select sum(amount) from public.ecotrack_points where resident_id = resident), 0) < price
        then raise exception 'Saldo insuficiente.'; end if;
    insert into public.ecotrack_points(resident_id, amount, reward_id)
    values (resident, -price, redeem.reward_id) returning id into receipt;
    return receipt;
end;
$$;

create function public.ecotrack_wallet() returns jsonb
language sql security invoker set search_path = '' as $$ select ecotrack_internal.wallet(); $$;
create function public.ecotrack_create_delivery(kind text) returns uuid
language sql security invoker set search_path = '' as $$ select ecotrack_internal.create_delivery(kind); $$;
create function public.ecotrack_lookup_delivery(delivery_code uuid) returns text
language sql security invoker set search_path = '' as $$ select ecotrack_internal.lookup_delivery(delivery_code); $$;
create function public.ecotrack_review_delivery(delivery_code uuid, approve boolean) returns void
language sql security invoker set search_path = '' as $$ select ecotrack_internal.review_delivery(delivery_code, approve); $$;
create function public.ecotrack_redeem(reward_id uuid) returns uuid
language sql security invoker set search_path = '' as $$ select ecotrack_internal.redeem(reward_id); $$;

revoke execute on all functions in schema ecotrack_internal from public, anon;
grant execute on all functions in schema ecotrack_internal to authenticated;
revoke execute on function public.ecotrack_wallet(), public.ecotrack_create_delivery(text),
    public.ecotrack_lookup_delivery(uuid), public.ecotrack_review_delivery(uuid, boolean), public.ecotrack_redeem(uuid)
    from public, anon;
grant execute on function public.ecotrack_wallet(), public.ecotrack_create_delivery(text),
    public.ecotrack_lookup_delivery(uuid), public.ecotrack_review_delivery(uuid, boolean), public.ecotrack_redeem(uuid)
    to authenticated;

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

alter publication supabase_realtime add table public.ecotrack_deliveries, public.ecotrack_points;
commit;


