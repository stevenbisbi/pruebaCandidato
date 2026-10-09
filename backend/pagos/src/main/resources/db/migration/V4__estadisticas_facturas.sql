-- PR-01. Por defecto PostgreSQL recalcula estadisticas cuando cambia el 10% de la tabla (50.000
-- facturas). Un lote de 50.000 lineas cambia el estado de ~40.000 facturas sin llegar al umbral, y
-- el planificador sigue creyendo que casi no hay facturas PARCIAL: filtrar por dos estados paso de
-- 2 ms a 160 ms por elegir un plan con datos viejos. Con 1% (5.000 cambios) el analisis automatico
-- se dispara despues de cualquier lote grande.
ALTER TABLE invoice SET (autovacuum_analyze_scale_factor = 0.01);
