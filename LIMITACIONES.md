# Limitaciones

## Que quedo afuera y por que

| Requisito | Estado | Por que |
|---|---|---|
| RF-26 "mostrar el avance" | Parcial: indicador indeterminado con segundos transcurridos. | El lote se procesa en una transaccion sincrona (D-01); no hay avance intermedio que reportar. Avance real exige procesamiento asincrono. |
| RF-23 / PR-01 "cualquier combinacion" | Medidas 9 combinaciones, todas < 100 ms. | No se hizo una matriz exhaustiva ni prueba de carga concurrente. |
| Paginacion de facturas | Solo "Primera" y "Siguiente"; sin total ni salto a pagina N. | Costo aceptado de la paginacion por cursor (D-05). "Atras" funciona con el boton del navegador. |
| RF-15 usuario | Usuario fijo `cartera`. | Autenticacion fuera de alcance (seccion 1.2). |
| Residuo de centavos (CF-01) | Queda PARCIAL con 0,40 sin flujo de ajuste. | Decision contable pendiente de Cartera (A-02). |
| Pagos anteriores a la emision | No se validan. | Los propios casos frontera los usan (A-10). |
| Analisis SonarQube | No se ejecuto. | Tiempo. ESLint y `tsc` estricto pasan sin hallazgos. |

## Que esta mal o es fragil en esta solucion

1. **Bloqueos largos.** El lote de 50.000 lineas tiene bloqueadas ~41.000 facturas durante ~18 s.
   Cualquier otro lote que toque esas facturas espera ese tiempo. Con lotes grandes simultaneos el
   segundo puede superar el `proxy_read_timeout` de nginx (120 s) aunque termine bien en el backend
   (el reenvio de Tesoreria lo resolveria, por la idempotencia).
2. **Misma referencia nueva en dos lotes concurrentes.** Ambos pasan la verificacion en memoria y el
   indice unico rechaza al segundo entero con 409. Es correcto (nada se aplica dos veces) pero
   tosco: el lote completo debe reenviarse.
3. **Memoria.** El JSON y el CSV se leen completos en memoria antes de procesar. Bien para 50.000
   lineas (~2,6 MB); no escala a millones.
4. **Estado desnormalizado.** El estado de la factura se persiste (A-05). Es consistente mientras el
   unico camino de escritura sea `ProcessBatchService`; un `UPDATE` manual en la base lo puede
   desincronizar del saldo. No hay restriccion en la base que lo impida.
5. **Archivos que no son UTF-8** se rechazan enteros con un mensaje claro. Si Tesoreria exporta en
   Windows-1252, este sera el proximo "rechazo intermitente".
6. **Primer arranque lento** (~1,5 min por la siembra). El frontend responde 502 en `/api` hasta que
   el backend termina; no hay healthcheck del backend en el compose.
7. **Tablero sin rango** tarda ~0,5 s porque agrega 500.000 facturas en cada consulta.
8. **Detalle de factura** muestra como maximo 500 pagos.
9. La descarga del resultado reemplaza `;` dentro de los textos por `,` en lugar de escapar CSV.

## Que haria con dos dias mas

1. Procesamiento asincrono por bloques de facturas (ordenados por numero, una transaccion por
   bloque) con estado del lote y avance real por SSE o sondeo. Reduce el tiempo de bloqueo por
   factura y cumple RF-26 completo. Exige definir que significa un lote "parcialmente aplicado".
2. Prueba de carga de PR-01 (k6 o Gatling) con la matriz completa de filtros y usuarios
   concurrentes, y `EXPLAIN ANALYZE` de las combinaciones lentas para ajustar indices.
3. Tabla de totales por proveedor mantenida en la misma transaccion del lote, para que el tablero
   no agregue 500.000 filas.
4. Resolver con Cartera A-01, A-02 y A-10, y ajustar las reglas.
5. Healthcheck del backend en el compose y `depends_on: service_healthy` en el frontend.
6. Analisis SonarQube y correccion de hallazgos.
