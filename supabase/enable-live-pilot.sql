begin;

alter table public.ecotrack_sites
    add column pilot_active boolean not null default false,
    add column verification_source text not null default '',
    add constraint ecotrack_pilot_site_has_source
        check (not pilot_active or btrim(verification_source) <> '');

alter table public.ecotrack_rewards
    add column pilot_active boolean not null default false,
    add constraint ecotrack_pilot_reward_is_real
        check (not pilot_active or (
            not is_sample and btrim(details) <> '' and btrim(fulfillment) <> ''
        ));

drop policy ecotrack_sites_read on public.ecotrack_sites;
create policy ecotrack_sites_read on public.ecotrack_sites
    for select to authenticated using (pilot_active);

drop policy ecotrack_rewards_read on public.ecotrack_rewards;
create policy ecotrack_rewards_read on public.ecotrack_rewards
    for select to authenticated using (
        pilot_active or exists (
            select 1 from public.ecotrack_points as receipt
            where receipt.reward_id = ecotrack_rewards.id
                and receipt.resident_id = (select auth.uid())
        )
    );

create function ecotrack_internal.require_live_reward() returns trigger
language plpgsql set search_path = '' as $$
begin
    if new.reward_id is not null and not exists (
        select 1 from public.ecotrack_rewards
        where id = new.reward_id and pilot_active
    ) then
        raise exception 'Este premio no está disponible para el piloto.';
    end if;
    return new;
end;
$$;

create trigger ecotrack_require_live_reward
    before insert on public.ecotrack_points
    for each row execute function ecotrack_internal.require_live_reward();

revoke execute on function ecotrack_internal.require_live_reward() from public, anon;

commit;
