# Limitaciones

## Que quedo afuera y por que

Se priorizo el nucleo que evalua la prueba: aplicar pagos correctamente (dinero, reenvios,
concurrencia, zona horaria) y el modulo heredado. Lo siguiente se dejo fuera a proposito:

| Requisito | Por que |
|---|---|
| RF-17, lote por JSON | Solo se recibe el CSV. El JSON entraria por el mismo caso de uso (`ProcessBatchUseCase`): faltan el endpoint y su DTO, no logica de negocio. |
| RF-23, filtros por rango de vencimiento y de saldo | La consulta filtra por NIT y estado, con paginacion estable. Los rangos son condiciones extra en la misma consulta. |
| RF-24 y RF-30, tablero de conciliacion | Es una consulta de lectura sobre datos que ya existen; no afecta la correccion de la conciliacion. |
| RF-26, filtrar rechazos por motivo | El resultado se ve completo y paginado, con los rechazos resaltados en rojo y su motivo; falta el filtro. |
| RF-26, avance del procesamiento | Se muestra un indicador de "procesando" con el tiempo transcurrido, no el avance linea a linea (ver D-01). |
| RF-22, descarga del resultado | El resultado si se puede consultar en cualquier momento (`GET /lotes/{id}/lineas`); falta exportarlo a archivo. |
| Paginacion de facturas | Solo "Primera" y "Siguiente"; sin total ni salto a una pagina (ver D-04). |
| RF-15, usuario de auditoria | Usuario fijo `cartera`; la autenticacion esta fuera de alcance. |
| Pruebas | Se probaron el dominio, el heredado y CF-03/CF-04 contra PostgreSQL real. Los demas casos frontera se verificaron a mano contra el sistema levantado. |

## Que esta mal o es fragil

1. Mientras se procesa un lote grande, sus facturas quedan bloqueadas (~13-18 s con 50.000 lineas).
   Otro lote que toque esas facturas espera.
2. Si dos lotes distintos traen al mismo tiempo la misma referencia nueva, el segundo falla entero
   con 409 y hay que reenviarlo.
3. El lote completo se carga en memoria antes de procesarlo. Bien para 50.000 lineas, no para
   millones.
4. El primer arranque tarda ~1,5 min por la siembra de facturas; mientras tanto el frontend responde
   502 en `/api`.
5. El CSV se lee siempre como UTF-8. Si Tesoreria enviara un archivo en otra codificacion (por
   ejemplo Windows-1252), las tildes y la enie llegarian como caracteres invalidos en vez de
   rechazarse con un mensaje claro.
6. Hasta un minuto despues de un lote grande, filtrar por varios estados a la vez puede pasar de
   300 ms (PR-01), mientras PostgreSQL actualiza sus estadisticas (ver D-09).

## Que haria con dos dias mas

1. El endpoint JSON y los filtros por rango (vencimiento y saldo), que reutilizan lo que ya existe.
2. El tablero (RF-24/30), el filtro por motivo y la descarga del resultado (RF-22).
3. Procesamiento en segundo plano con avance real (RF-26).
4. Prueba de carga de la consulta de facturas con todas las combinaciones de filtros.
5. Confirmar con Cartera las ambiguedades A-01 y A-02.
