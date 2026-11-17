-- GestiLab: columnas para fotografías de préstamos
-- PostgreSQL

ALTER TABLE prestamo
    ADD COLUMN IF NOT EXISTS foto_entrega_clave VARCHAR(255);

ALTER TABLE prestamo
    ADD COLUMN IF NOT EXISTS foto_devolucion_clave VARCHAR(255);