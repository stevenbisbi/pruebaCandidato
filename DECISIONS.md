# Decisiones tecnicas

## 1. Ambiguedades del enunciado

### A-01. RF-18 contradice a RF-19 (CF-06)

RF-18 pide aplicar en el orden del lote; RF-19 pide que el resultado no dependa del orden. Con
saldo 100 y pagos A=60, B=70 no se cumplen las dos: el orden decide cual se aplica.

**Supuesto:** RF-18 manda para pagos de una misma factura. RF-19 se cumple entre facturas distintas,
que se procesan de forma independiente.
**Descartado:** rechazar todos los pagos de una factura si su suma excede el saldo. Cumple RF-19,
pero viola RF-18 y deja sin aplicar un pago de 60 que si cabia.

### A-02. Facturas con centavos, pagos en pesos enteros (RF-04 vs RF-10) — CF-01

Una factura de 37.500.483,40 nunca puede quedar en cero con pagos enteros.
**Supuesto:** se aplica la regla tal cual: queda PARCIAL con saldo 0,40.
**Descartado:** condonar residuos menores a un peso. Es una decision contable que debe tomar
Cartera, no el sistema.

### A-03. Que fecha define el pago extemporaneo (RF-13) — CF-05

**Supuesto:** la fecha del pago, no la hora en que se procesa el lote. La factura vence al terminar
el dia de vencimiento en hora de Colombia. La zona es una constante del dominio
(`DueDatePolicy.OPERATION_ZONE`), nunca la del servidor, que en el contenedor es UTC. Asi, un pago
a las 23:30 -05:00 (04:30 UTC del dia siguiente) queda a tiempo.

### A-04. Como se reconoce un reenvio (RF-20) — CF-03

**Decision:** el CSV no trae identificador, asi que el id del lote es una huella SHA-256 de su
contenido (o el `loteId` opcional que se envie junto al archivo). Mismo id y mismo contenido: se
devuelve el resultado original sin reaplicar nada. Mismo id con contenido distinto: error 409.
Ademas, una referencia de pago ya aplicada se rechaza siempre (RF-12), incluso en otro lote.

### A-05. Lineas invalidas

Tres lineas del lote de abril no traen fecha. **Decision:** se rechaza solo esa linea con su motivo
(RF-21), no el lote completo.

## 2. Decisiones de arquitectura

### D-01. Un lote = una transaccion

Registro del lote, aplicacion de pagos, auditoria y totales van en una sola transaccion. Si algo
falla no queda nada a medias, y el reenvio procesa desde cero.
**Descartado:** procesar en segundo plano con avance real. Mostraria mejor el progreso (RF-26),
pero exige manejar lotes que quedan a medias. Con 50.000 lineas en ~18 s no hacia falta.

### D-02. Concurrencia: bloqueo de filas ordenado — CF-04

`SELECT ... FOR UPDATE` sobre las facturas del lote, siempre ordenadas por numero. Si dos lotes
tocan la misma factura, el segundo espera y ve el saldo ya reducido. El orden fijo evita bloqueos
cruzados.
**Descartado:** bloqueo optimista (columna de version). Ante un conflicto obliga a reintentar el
lote entero, caro con lotes grandes.

### D-03. Idempotencia en la base

`INSERT ... ON CONFLICT (id) DO NOTHING` sobre la tabla de lotes. Si llegan dos reenvios a la vez,
el segundo espera a que el primero termine y luego devuelve su resultado.

### D-04. Paginacion por cursor (PR-03)

Las facturas se ordenan por numero, y la pagina siguiente pide las de numero mayor al ultimo que
se vio (`WHERE number > ?`). El numero de una factura nunca cambia.
**Descartado:** LIMIT/OFFSET ("salta 50 filas"). Si mientras se navega un lote cambia el estado de
una factura y esta sale del filtro, todas las siguientes se corren una posicion y se salta una.
**Costo aceptado:** no hay total de resultados ni salto a la pagina N, y el orden es por numero de
factura, no por vencimiento.

### D-05. Dinero sin perdida de precision

`BigDecimal` en Java, `NUMERIC(18,2)` en la base, y en la API los montos viajan como texto
(`"37500483.40"`). En el frontend se formatean sobre el texto, sin convertir a `number`.

### D-06. Arquitectura hexagonal

- `domain`: modelo, reglas y puertos de salida (`repository`). No depende de Spring ni de JDBC:
  es lo que exige el enunciado.
- `application/usecase`: puertos de entrada, una interfaz por caso de uso (`ProcessBatchUseCase`,
  `GetBatchResultUseCase`, `SearchInvoicesUseCase`). Los controladores solo conocen estas interfaces.
- `application/service`: implementa los casos de uso. Usa `@Service` y `@Transactional` de Spring.
- `adapters`: REST y CSV de entrada, JDBC de salida (`@Repository`).

**Descartado:** que tambien la capa de aplicacion estuviera libre de Spring, cableando todo a mano
en una clase de configuracion y abstrayendo la transaccion en una interfaz propia. Es mas puro,
pero agrega dos clases y una indireccion que no compran nada en este proyecto; el enunciado solo
exige que el dominio no dependa del framework.

Flujo de una peticion: HTTP → controlador → caso de uso (interfaz) → servicio → puerto de
repositorio (interfaz) → adaptador JDBC → PostgreSQL.

### D-07. Modulo heredado (seccion 8)

**Defectos:**
1. **BOM:** el archivo de marzo empieza con el BOM UTF-8. Java lo lee como un caracter
   invisible pegado a `referencia`: el encabezado no coincide y se rechaza el archivo entero.
2. **Campo final vacio:** `"a;b;c;".split(";")` devuelve 3 campos, no 4. Las lineas de abril sin
   fecha lanzan `ArrayIndexOutOfBoundsException` y se rechaza el archivo entero.

**Por que no pasaba en desarrollo:** los archivos de prueba se generan con scripts: sin BOM y con
todas las columnas llenas. Los de Tesoreria pasan por Excel o el Bloc de notas, que agregan el BOM
o dejan celdas vacias, segun quien exporte. Por eso "a veces entra y a veces no".

**Decision:** reemplazarlo por `CsvBatchReader`, que quita el BOM, usa `split(";", -1)` y rechaza
linea por linea, como pide RF-21. Las pruebas de caracterizacion fijan el comportamiento original
sobre una copia textual del modulo.

### D-08. Frontend

- Filtros y paginacion viven solo en la URL (RF-25): pegar el enlace reproduce la vista.
- Redux Toolkit, usado a traves de RTK Query: cache de consultas, estados de carga y error, y
  refresco de facturas tras cargar un lote.
- Doble envio (RF-27): boton bloqueado mientras se procesa, mas la idempotencia del backend.

## 3. Casos frontera (Anexo B)

| Caso | Resultado | Por que |
|---|---|---|
| CF-01 | Se aplica; queda PARCIAL con saldo 0,40. | A-02 |
| CF-02 | Rechazado: excede el saldo por 0,60. | RF-09 |
| CF-03 | El segundo envio devuelve el resultado original; el saldo no cambia. | A-04, D-03 |
| CF-04 | Se aplica uno de los dos pagos, nunca ambos. | D-02 |
| CF-05 | PAGADA, no extemporanea. | A-03 |
| CF-06 | A se aplica (saldo 40); B se rechaza por exceder el saldo. | A-01 |
| CF-07 | 50.000 lineas en ~23 s (limite: 60 s). | D-01 |
| CF-08 | No se repiten ni se omiten facturas. | D-04 |

## 4. Mediciones

Con la base sembrada, en Docker local, 20 peticiones por consulta con `curl` (p95):

- El lote de 50.000 lineas tarda ~23 s (limite PR-02: 60 s).
- La consulta de facturas responde por debajo de 100 ms con NIT, estado o ambos (limite PR-01: 300 ms).

**Problema encontrado y corregido (D-09).** Despues de cargar el lote de 50.000, filtrar por dos
estados a la vez (`estado=PAGADA&estado=PARCIAL`) subio a 383 ms. `EXPLAIN ANALYZE` mostro que
PostgreSQL estimaba 1 factura PARCIAL cuando habia 39.568: las estadisticas eran de cuando todas
estaban PENDIENTE. Con esa estimacion recorria las 39.568 y las ordenaba (161 ms) en vez de
recorrer el indice por fecha y parar en 51 (2 ms).

### D-09. Estadisticas de la tabla de facturas

PostgreSQL recalcula estadisticas solas cuando cambia el 10% de la tabla (50.000 filas); un lote
de 50.000 lineas cambia ~40.000 y no alcanza. La migracion V4 baja ese umbral al 1% para `invoice`.
**Descartado:** ejecutar `ANALYZE` al final de cada lote. Es inmediato, pero recorre las 500.000
facturas dentro de cada carga, incluso de lotes de una linea.
**Costo aceptado:** el analisis automatico corre cada minuto, asi que justo despues de un lote
grande hay hasta un minuto en que esa consulta puede ir lenta.
