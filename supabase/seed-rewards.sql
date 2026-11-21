begin;

insert into public.ecotrack_rewards
    (catalog_key, name, category, cost, stock, max_per_user, details, fulfillment, photo, is_sample)
values
    ('cap', 'Gorro EcoTrack', 'Materiales', 20, 4, 1, 'Gorro ajustable de algodón reciclado, en verde bosque.', 'Recojo en un punto participante.', 'gorro', true),
    ('botella', 'Botella térmica', 'Materiales', 90, 9, 1, 'Botella reutilizable de acero de 500 ml con tapa roscada.', 'Recojo en un punto participante.', 'botella', true),
    ('bolsa', 'Bolsa de tela', 'Materiales', 30, 16, 2, 'Bolsa resistente de algodón para compras y uso diario.', 'Recojo en un punto participante.', null, true),
    ('libreta', 'Libreta reciclada', 'Materiales', 35, 14, 3, 'Libreta A5 con 60 hojas recicladas y tapa de cartón.', 'Recojo en un punto participante.', null, true),
    ('taza', 'Taza reutilizable', 'Materiales', 65, 8, 1, 'Taza de 350 ml con tapa para llevar bebidas calientes.', 'Recojo en un punto participante.', null, true),
    ('estuche', 'Estuche de retazos', 'Materiales', 45, 7, 2, 'Estuche con cierre elaborado con retazos textiles.', 'Recojo en un punto participante.', null, true),
    ('bici', 'Ajuste de bicicleta', 'Servicios', 85, 6, 1, 'Ajuste básico de frenos, cambios y presión de llantas.', 'Reserva una cita tras el canje.', null, true),
    ('taller', 'Taller de compostaje', 'Servicios', 60, 12, 2, 'Cupo para una práctica guiada de compostaje doméstico.', 'Inscripción a la siguiente sesión disponible.', null, true),
    ('reparacion', 'Reparación de prendas', 'Servicios', 65, 5, 1, 'Arreglo sencillo de una costura, un botón o un dobladillo.', 'Coordina la entrega de la prenda.', null, true),
    ('huerto', 'Asesoría para tu huerto', 'Servicios', 75, 4, 1, 'Sesión de 30 minutos para elegir plantas y preparar el sustrato.', 'Agenda una sesión de atención.', null, true),
    ('trueque', 'Cupo en feria de trueque', 'Servicios', 25, 10, 2, 'Espacio para ofrecer o intercambiar artículos reutilizables.', 'Reserva un cupo para la próxima feria.', null, true),
    ('recorrido', 'Visita guiada ambiental', 'Servicios', 50, 0, 2, 'Cupo para conocer cómo se clasifican y recuperan los residuos.', 'Inscripción cuando haya una nueva fecha.', null, true),
    ('tree', 'Árbol para plantar', 'Ayuda', 40, 3, 2, 'Plantón de tara para cuidar y trasplantar en un lugar adecuado.', 'Recojo con instrucciones de plantación.', 'arbol', true),
    ('semillas', 'Kit de semillas', 'Ayuda', 30, 12, 2, 'Semillas de hortalizas con una guía breve de siembra.', 'Recojo en un punto participante.', null, true),
    ('escolar', 'Kit escolar', 'Ayuda', 110, 6, 1, 'Cuadernos, lápices y una cartuchera para una familia participante.', 'Entrega a través de la campaña piloto.', null, true),
    ('huerto-comunal', 'Kit para huerto comunal', 'Ayuda', 120, 4, 1, 'Semillas, guantes y herramientas básicas para un huerto vecinal.', 'Entrega programada al grupo beneficiario.', null, true),
    ('alimentario', 'Apoyo alimentario', 'Ayuda', 150, 3, 1, 'Aporte para una canasta básica destinada a una familia participante.', 'Asignación mediante la campaña piloto.', null, true),
    ('guantes', 'Guantes para voluntarios', 'Ayuda', 55, 10, 2, 'Un par de guantes reutilizables para jornadas de limpieza.', 'Entrega al equipo inscrito en la jornada.', null, true),
    ('arborizacion', 'Plantación comunitaria', 'Donaciones', 50, 20, 3, 'Aporte para plantar y cuidar un árbol en una jornada comunitaria.', 'Se asigna a la próxima jornada del piloto.', null, true),
    ('comedor', 'Aporte a comedor vecinal', 'Donaciones', 100, 12, 2, 'Contribución para insumos de cocina de un comedor participante.', 'El canje asigna los puntos a la campaña.', null, true),
    ('biblioteca', 'Libros para una biblioteca', 'Donaciones', 75, 8, 2, 'Aporte para incorporar libros a una biblioteca barrial.', 'El canje asigna los puntos a la campaña.', null, true),
    ('limpieza', 'Jornada de limpieza', 'Donaciones', 45, 18, 3, 'Aporte para bolsas, pinzas y guantes de una jornada vecinal.', 'Se asigna al equipo de la próxima jornada.', null, true),
    ('refugio', 'Alimento para un refugio', 'Donaciones', 95, 7, 2, 'Aporte para una entrega de alimento a un refugio participante.', 'El canje asigna los puntos a la campaña.', null, true),
    ('acopio', 'Equipar un punto de acopio', 'Donaciones', 130, 5, 1, 'Aporte para señalética y recipientes de separación de residuos.', 'Se asigna al fondo piloto de acopio.', null, true)
on conflict (catalog_key) do nothing;

commit;
