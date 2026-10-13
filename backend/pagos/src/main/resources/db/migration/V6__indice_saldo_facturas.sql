-- PR-01. Un filtro por rango de saldo angosto (por ejemplo 1.000.000 a 1.001.000) casi no tiene
-- resultados, y sin indice PostgreSQL recorria las 500.000 facturas en orden de numero buscando las
-- que cumplen: 110 ms con un usuario y 435 ms en el p95 con diez en paralelo. Con este indice
-- encuentra las pocas filas directamente y las ordena (menos de 1 ms). Solo sirve si el plan se
-- arma mirando los valores del filtro: ver plan_cache_mode en application.properties (D-06).
CREATE INDEX ix_invoice_balance ON invoice (balance);

ANALYZE invoice;
