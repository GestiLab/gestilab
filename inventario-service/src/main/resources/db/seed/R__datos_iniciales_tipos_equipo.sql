-- Datos iniciales SOLO de desarrollo (no se carga con el perfil "postgres").
-- Tres tipos con reglas distintas y uno con autorización previa obligatoria.
-- Migración repetible e idempotente: no choca con la numeración V# de otras ramas.
INSERT INTO tipo_equipo (nombre, descripcion, duracion_maxima_dias, requiere_autorizacion_previa)
SELECT 'Multímetro', 'Mide voltaje, corriente y resistencia', 7, FALSE
WHERE NOT EXISTS (SELECT 1 FROM tipo_equipo WHERE nombre = 'Multímetro');

INSERT INTO tipo_equipo (nombre, descripcion, duracion_maxima_dias, requiere_autorizacion_previa)
SELECT 'Portátil', 'Computador portátil para prácticas', 1, FALSE
WHERE NOT EXISTS (SELECT 1 FROM tipo_equipo WHERE nombre = 'Portátil');

INSERT INTO tipo_equipo (nombre, descripcion, duracion_maxima_dias, requiere_autorizacion_previa)
SELECT 'Cámara', 'Cámara fotográfica para evidencias y proyectos', 3, TRUE
WHERE NOT EXISTS (SELECT 1 FROM tipo_equipo WHERE nombre = 'Cámara');
