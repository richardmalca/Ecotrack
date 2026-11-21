begin;

alter table public.ecotrack_sites
    drop constraint ecotrack_sites_materials_check,
    add constraint ecotrack_sites_materials_check check (
        cardinality(materials) > 0 and materials <@ array['Papel', 'Cartón', 'Vidrio', 'Plástico']
    );

alter table public.ecotrack_deliveries
    drop constraint ecotrack_deliveries_material_check,
    add constraint ecotrack_deliveries_material_check
        check (material in ('Papel', 'Cartón', 'Vidrio', 'Plástico'));

commit;
