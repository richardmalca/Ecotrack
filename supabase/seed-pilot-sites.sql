begin;

insert into public.ecotrack_sites (id, name, details, lat, lng, materials)
values
    ('eca00000-0000-4000-8000-000000000001', 'Punto piloto Centro',
        'Ubicación de prueba', -7.1578, -78.5161, array['Papel','Cartón','Vidrio','Plástico']),
    ('eca00000-0000-4000-8000-000000000002', 'Punto piloto Santa Apolonia',
        'Ubicación de prueba', -7.1608, -78.5221, array['Papel']),
    ('eca00000-0000-4000-8000-000000000003', 'Punto piloto Chontapaccha',
        'Ubicación de prueba', -7.1489, -78.5210, array['Cartón']),
    ('eca00000-0000-4000-8000-000000000004', 'Punto piloto San Sebastián',
        'Ubicación de prueba', -7.1641, -78.5075, array['Vidrio']),
    ('eca00000-0000-4000-8000-000000000005', 'Punto piloto La Alameda',
        'Ubicación de prueba', -7.1533, -78.5088, array['Papel','Cartón','Plástico']),
    ('eca00000-0000-4000-8000-000000000006', 'Punto piloto Sur',
        'Ubicación de prueba', -7.1713, -78.5025, array['Cartón','Vidrio'])
on conflict (id) do nothing;

commit;
