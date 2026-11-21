insert into public.ecotrack_rewards
    (catalog_key, name, category, cost, stock, max_per_user, details, fulfillment, pilot_active)
values (
    'guia-reciclaje-cajamarca', 'Guía práctica de reciclaje', 'Ayuda', 20, 20, 1,
    'Guía digital de EcoTrack para preparar papel, cartón, plástico y vidrio antes de entregarlos.',
    'El recolector confirma tu QR y la guía queda disponible en el comprobante de Mis premios.',
    true
)
on conflict (catalog_key) do nothing;
