# Limitaciones

## Que quedo afuera y por que

| Requisito | Estado | Por que |
|---|---|---|
| RF-26, avance del procesamiento | No hay avance linea a linea | El lote se procesa dentro de la peticion, en una sola transaccion: 7 s para 50.000 lineas. La pantalla muestra una barra indeterminada y los segundos transcurridos mientras espera la respuesta. |
| RF-15, usuario de la auditoria | Usuario fijo `cartera` | La autenticacion esta fuera de alcance (seccion 1.2). El usuario se configura con `APP_USER`. |
| Paginacion de facturas | Saltos lentos al medio con carga | Saltar a una pagina del medio (la 5.000 de 10.000) tarda 151 ms con un usuario y 607 ms en el p95 con diez en paralelo: OFFSET recorre las filas anteriores (D-03). Avanzar, la primera y la ultima pagina siguen por debajo de 45 ms. |
| Contrato del Anexo A | Rutas en ingles | Quien pruebe con las rutas sugeridas recibe 404; la equivalencia esta en D-05 y en el README. |
| RF-24, otras fechas en el tablero | Solo por vencimiento | El tablero filtra por fecha de vencimiento, como la consulta de facturas (A-08); filtrar tambien por emision o por fecha de pago necesitaria indices propios. |

## Que esta mal o es fragil

1. **Bloqueo durante un lote grande.** Mientras se procesa un lote, sus facturas quedan bloqueadas
   hasta que termina (7 s con 50.000 lineas; 15 s con carga de consultas en paralelo). Otro lote que
   toque esas facturas espera. Las consultas no esperan: leen la ultima version confirmada.
2. **Referencia repetida en dos lotes concurrentes.** Si dos lotes a facturas distintas traen la misma
   referencia nueva al mismo tiempo, el segundo se deshace entero y responde 409; hay que reenviarlo
   (D-02). La referencia nunca se aplica dos veces, pero el reenvio queda en manos de Tesoreria.
3. **Todo el lote en memoria.** Las lineas y su resultado se cargan completos antes de guardar. Esta
   bien para el limite de 100.000 lineas; no para lotes de millones.
4. **Tablero lento sin rango.** Sin filtro de vencimiento suma las 500.000 facturas: 229 ms (p50) y
   409 ms (p95). Con un mes, 105 ms (D-07).
5. **Textos recortados en la auditoria.** Una linea rechazada con una referencia de mas de 80
   caracteres, una factura de mas de 30 o un detalle de mas de 300 se guarda recortada. Asi no tumba
   el lote, pero el valor original completo no queda guardado.
6. **La zona horaria original de la fecha de pago no se guarda.** La base guarda el instante: un pago
   enviado como `00:00-05:00` se devuelve como `05:00Z`. Es el mismo instante y la pantalla lo muestra
   en hora de Colombia, pero quien use la API directamente recibe la hora en UTC.
7. **Archivos en otra codificacion.** El CSV se lee como UTF-8. Una linea con caracteres de otra
   codificacion (por ejemplo una enie de Windows-1252) se rechaza sola con su motivo; si el archivo
   entero viene en otra codificacion, se rechazan todas las lineas que tengan tildes.
8. **Primer arranque.** La siembra de 500.000 facturas tarda entre uno y dos minutos; mientras tanto
   el backend no responde.
9. **Las pruebas de integracion necesitan Docker.** Sin Docker fallan (a proposito: no se saltan en
   silencio).

## Que haria con dos dias mas

1. Procesar los lotes en segundo plano, con un estado consultable y avance real (RF-26), sin bloquear
   la peticion.
2. Guardar los totales del tablero por mes y actualizarlos al aplicar cada lote, para que responda
   igual de rapido con o sin rango (D-07).
3. Guardar el valor original completo y la zona horaria de cada pago junto al valor normalizado.
4. Una prueba de carga mas realista (k6 o Gatling) con lotes y consultas mezclados, en lugar de
   `curl` en paralelo.
5. Confirmar con Cartera las ambiguedades resueltas por supuesto: A-01 (orden), A-02 (la lectura de
   "redondee a los centavos") y A-04 (pago tardio).
