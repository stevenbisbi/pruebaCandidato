# Decisiones tecnicas

Formato: que se decidio, que alternativa se descarto, que resolvia mejor la alternativa, que
costaba, y por que en este contexto se eligio lo que se eligio.

## 1. Ambiguedades y contradicciones del enunciado

Ninguna de estas preguntas se envio al contacto antes de decidir. Las que considero mas importantes
de confirmar estan marcadas con **(preguntar)**.

### A-01. RF-18 contradice a RF-19 **(preguntar)**

RF-18 exige aplicar en el orden del lote, reduciendo el saldo de forma progresiva. RF-19 exige que
el resultado no dependa del orden de las lineas. Con CF-06 (saldo 100, A=60, B=70) no se pueden
cumplir las dos: en orden A,B se aplica A y se rechaza B; en orden B,A se aplica B y se rechaza A.

**Supuesto adoptado:** RF-18 manda para lineas de una misma factura. RF-19 se cumple para lineas
de facturas distintas: cada factura se procesa de forma independiente, asi que reordenar lineas de
facturas distintas no cambia nada (`BatchReconcilerTest.linesForDifferentInvoicesGiveTheSameResultInAnyOrder`).

**Alternativa descartada:** hacer el resultado independiente del orden rechazando todos los pagos de
una factura cuya suma exceda el saldo (CF-06: rechazar A y B). Cumple RF-19 al pie de la letra, pero
viola RF-18 y deja sin aplicar un pago de 60 que cabia. Otra opcion, aplicar "la combinacion que mas
cubra", es un problema de mochila: no es explicable para Cartera ni auditable linea por linea.

### A-02. Facturas con centavos y pagos en pesos enteros (RF-04 vs RF-10) — CF-01

Una factura de 37.500.483,40 nunca puede quedar PAGADA con pagos enteros: siempre queda un residuo
de 0,40 o el pago excede el saldo y se rechaza (CF-02, por RF-09).

**Supuesto adoptado:** la regla se aplica tal cual. CF-01 queda PARCIAL con saldo 0,40. El sistema
no condona ni redondea.

**Alternativa descartada:** una tolerancia de redondeo (por ejemplo, residuo < 1 peso => PAGADA).
Resolveria el caso practico, pero es una decision contable (quien asume los centavos, como se
registra el ajuste) que no le corresponde a este sistema tomar sin que Cartera la defina. Queda
como pregunta abierta y en LIMITACIONES.

### A-03. "Pago aplicado despues de ese instante" (RF-13)

Puede leerse como la fecha del pago o como el instante en que el sistema aplica el pago.
**Supuesto:** la fecha del pago (`fechaPago`). CF-05 lo confirma: con la hora de procesamiento,
cualquier lote cargado al dia siguiente dejaria extemporaneas facturas pagadas a tiempo.

El vencimiento es el inicio del dia siguiente en America/Bogota (exclusivo), no 23:59:59: asi un
pago a las 23:59:59,500 no queda en un hueco. La zona es una constante del dominio
(`DueDatePolicy.OPERATION_ZONE`); nunca se usa la zona de la JVM, que en el contenedor es UTC.
CF-05 (23:30 -05:00 = 04:30 UTC del dia siguiente) queda PAGADA.

### A-04. Cual pago define PAGADA_EXTEMPORANEA

Si una factura recibe un abono tardio y luego otro, o abonos a tiempo y el ultimo tarde.
**Supuesto:** decide el pago que lleva el saldo a cero. Es el instante en que la factura "queda
pagada", que es lo que RF-13 califica.

### A-05. "El estado se calcula a partir de los pagos" (RF-14)

**Decision:** el estado se persiste como columna (para poder filtrar e indexar, RF-23 y PR-01), pero
nunca se asigna a mano: `InvoiceStatus.derive(total, saldo, cerroTarde)` lo recalcula cada vez que
se aplica un pago. Calcularlo en cada consulta a partir de la tabla de pagos haria imposible indexar
el filtro por estado sobre 500.000 facturas.

### A-06. Unicidad de la referencia (RF-12)

**Supuesto:** la referencia es unica en todo el sistema, no solo dentro del lote, y solo cuenta si
el pago se aplico. Un pago rechazado (por ejemplo por exceder el saldo) puede reenviarse con la misma
referencia en otro lote y aplicarse. Se garantiza tambien en la base con un indice unico parcial
(`ux_line_applied_reference ... WHERE outcome = 'APLICADO'`).

### A-07. Identidad de un reenvio (RF-20) — CF-03

El JSON trae `loteId`; el CSV no trae identificador.
**Decision:** el `loteId` es la llave de idempotencia. Para CSV sin `loteId`, el identificador es
`CSV-` + huella SHA-256 del contenido normalizado (sin BOM, sin espacios, sin fin de linea). El mismo
archivo subido dos veces, desde Windows o Linux, es el mismo lote.

- Mismo id y mismo contenido: se devuelve el resultado original (HTTP 200, `reenvio: true`) sin
  reaplicar nada.
- Mismo id y contenido distinto: 409. No se reprocesa ni se sobrescribe: es mas probable un error
  de Tesoreria que un lote legitimo.

Ademas, aunque llegara el mismo pago bajo otro id de lote, A-06 lo rechaza como REFERENCIA_DUPLICADA.

### A-08. Rango del tablero (RF-24)

"Sobre el rango de fechas filtrado" no dice que fecha. **Supuesto:** las facturas (total facturado,
saldo pendiente, top 10) se filtran por fecha de vencimiento, que es el filtro de RF-23; los pagos
(aplicado, rechazado) se filtran por fecha de pago en hora de Colombia. Un pago rechazado por
factura inexistente no tiene vencimiento, y filtrarlo por la factura lo haria desaparecer del tablero.
La pantalla lo explica en una linea bajo el filtro.

### A-09. Lineas sin fecha de pago (lote de abril)

Tres lineas del lote de abril llegan con `fecha_pago` vacia. **Decision:** se rechaza solo esa linea
con FECHA_INVALIDA. Sin fecha no se puede evaluar RF-13. Tambien se rechazan fechas sin offset
(`2026-03-15T10:00:00`): son ambiguas justamente en la frontera de RF-13.

### A-10. Pagos con fecha anterior a la emision de la factura

En los propios casos frontera, FV-0000100 se emite el 2026-03-16 y CF-06 la paga el 2026-03-13;
lo mismo con FV-0000300 en CF-03. **Supuesto:** no se valida, para no romper los casos publicados.
**(preguntar)** si debe ser un motivo de rechazo.

### A-11. Valores no enteros en el JSON

`"valor": "37500483.00"` es entero en magnitud pero no en forma. **Decision:** se acepta solo texto
de digitos o un numero JSON entero; cualquier decimal, signo o cero se rechaza con VALOR_INVALIDO.
RF-10 dice que Tesoreria no usa decimales; un decimal indica un error de origen que conviene ver.

### A-12. Usuario de la auditoria (RF-15)

Sin autenticacion (seccion 1.2), el usuario es fijo y configurable (`app.user`, por defecto `cartera`).

## 2. Decisiones de arquitectura

### D-01. Un lote = una transaccion, procesamiento sincrono

Registro del lote, bloqueo de facturas, aplicacion, auditoria y totales ocurren en una transaccion.
Si algo falla, no queda nada a medias y el reenvio de Tesoreria procesa el lote desde cero.

**Alternativa descartada:** procesamiento asincrono en segundo plano con estado del lote y avance
real (RF-26). Resuelve mejor el "mostrar avance" y no ocupa un hilo HTTP. Costaba un mecanismo de
trabajos, recuperacion de lotes que quedan a medias si el proceso muere, y lotes parcialmente
aplicados visibles. Con 50.000 lineas en ~18 s, el sincrono cabe holgado en el presupuesto y la
idempotencia queda trivialmente correcta. El costo esta en LIMITACIONES (avance indeterminado,
bloqueos durante el lote).

### D-02. Concurrencia: bloqueo pesimista ordenado — CF-04

`SELECT ... WHERE number = ANY(?) ORDER BY number FOR UPDATE`. PostgreSQL bloquea en el orden en que
entrega las filas, asi que dos lotes toman los bloqueos en el mismo orden y no se interbloquean. El
segundo lote espera y lee el saldo ya reducido. La restriccion `CHECK (balance >= 0)` es la red de
seguridad si algun dia otro camino escribe saldos.

**Alternativa descartada:** bloqueo optimista con columna de version. Evita esperar, pero ante un
conflicto hay que reintentar el lote entero; con lotes de 50.000 lineas que comparten facturas, el
reintento es caro y puede no converger. Probado en `cf04_concurrentBatchesNeverApplyMoreThanTheOriginalBalance`.

### D-03. Idempotencia en la base, no en memoria

`INSERT ... ON CONFLICT (id) DO NOTHING` sobre `payment_batch`. Si dos reenvios llegan a la vez, el
segundo queda esperando la llave del primero; cuando el primero confirma, el segundo ve el conflicto
y devuelve el resultado original. Si el primero falla, el segundo procesa. Sin tablas de bloqueo ni
cache. Probado con 4 envios simultaneos (`concurrentResendOfTheSameBatchAppliesOnlyOnce`).

### D-04. Una sola tabla para resultado, pagos y auditoria

`payment_line_result` guarda cada linea con resultado, motivo, saldo anterior y posterior, usuario
e instante. Un pago aplicado es una fila con `outcome = 'APLICADO'`.

**Alternativa descartada:** tablas separadas de pagos, auditoria y resultados. Es el modelo mas
"limpio", pero triplica las escrituras de un lote de 50.000 lineas (PR-02) y obliga a mantener tres
copias consistentes del mismo hecho. Lo que pide RF-15 (usuario, instante, saldo anterior y
posterior) es exactamente el resultado de la linea.

### D-05. Paginacion por cursor (keyset) — PR-03, CF-08

Orden por `(due_date, number)`, ambos inmutables; la pagina siguiente empieza despues de la ultima
llave vista. Con OFFSET, si se filtra por PENDIENTE y un lote cambia facturas a PARCIAL mientras se
navega, todas las filas se corren y se omiten facturas. Con cursor no se repite ni se omite ninguna
factura que siga cumpliendo el filtro (probado en `cf08_...`).

**Costo aceptado:** no hay "ir a la pagina N" ni total de resultados (un `COUNT(*)` con filtros
amplios sobre 500.000 filas compite con PR-01). Se pide una fila de mas para saber si hay siguiente.

### D-06. Dinero

- Dominio: `BigDecimal` con escala 2. Base: `NUMERIC(18,2)`.
- API: siempre texto decimal (`"37500483.40"`). Un numero JSON se lee como double en JavaScript.
- Frontend: formateo sobre el texto (`formatCop`), sin pasar por `Number` ni `Intl.NumberFormat`.
  Probado con un monto mayor a 2^53.

### D-07. JDBC en lugar de JPA

El dominio es pequeno y el rendimiento depende de operaciones por conjuntos: el lote actualiza
~41.000 saldos en una sola sentencia (`UPDATE ... FROM unnest(...)`) e inserta 50.000 lineas con
`reWriteBatchedInserts`. Con JPA esto exige salir del ORM igualmente, y el contexto de persistencia
con 41.000 entidades gestionadas es un costo sin beneficio. Costo: mapeo manual de filas.

### D-08. Semilla con COPY desde Flyway

`db/seed/V2__carga_facturas.sql` usa `COPY` del lado del servidor sobre el volumen `/seed`, que ya
montaba el compose semilla. Carga 500.000 facturas en ~1 minuto, sin codigo extra ni un servicio
adicional. Costo: depende de que el CSV este montado en el contenedor de la base; por eso esa
ubicacion de Flyway se excluye en las pruebas de integracion. Los indices se crean despues (V3),
lo que acelera la carga.

### D-09. Hexagonal sin ceremonia

- `domain`: sin dependencias externas (solo `java.*`).
- `application`: casos de uso y puertos de salida como interfaces; sin anotaciones de Spring. La
  transaccion se abstrae con el puerto `TransactionRunner`.
- `infrastructure`: JDBC, REST, CSV y `ApplicationConfig`, el unico lugar que cablea ambos mundos.

**Simplificacion consciente:** los servicios de aplicacion son el puerto de entrada; no se creo una
interfaz por caso de uso, porque tendria una sola implementacion y ningun consumidor alternativo.

### D-10. Modulo heredado (seccion 8)

**Defectos encontrados** (fijados en `ImportadorCsvLegacyCharacterizationTest`):

1. **BOM UTF-8.** El lote de marzo empieza con `EF BB BF`. `Files.lines` en UTF-8 entrega el BOM
   como caracter `﻿` pegado a `referencia`; `trim()` no lo quita (no es espacio), el encabezado
   no coincide y el archivo entero se rechaza como "Encabezado invalido".
2. **Campo final vacio.** `String.split(";")` descarta los campos vacios del final:
   `"TES-ABR-000013;FV-0378526;15917835;"` produce 3 elementos, `campos[3]` lanza
   `ArrayIndexOutOfBoundsException` y el `catch` generico rechaza el archivo entero.
3. **Una linea mala rechaza todo el archivo** (diseno, no accidente, pero es lo que amplifica 1 y 2).
4. **`Files.lines` nunca se cierra:** el descriptor queda abierto hasta que el GC lo recoja. En
   Windows eso impide mover o borrar el archivo mientras tanto. No explica el sintoma, pero es un
   defecto real.
5. Un archivo que no sea UTF-8 (por ejemplo Windows-1252 con una tilde) tambien se rechaza entero.

**Por que no se manifestaba en desarrollo.** Los archivos de desarrollo se generan con scripts
(como `lote-50k.csv`): ASCII puro, sin BOM y siempre con las 4 columnas llenas. Los de Tesoreria
pasan por personas y herramientas de escritorio: el de marzo se guardo con un editor que agrega
BOM (Excel "CSV UTF-8" o el Bloc de notas lo hacen), y el de abril trae celdas de fecha vacias, que
al exportar dejan el `;` final. Que "a veces entre y a veces no" depende de quien y con que
herramienta exporto ese dia, y de si el archivo reenviado se regenera o se corrige a mano. No tiene
que ver con la zona horaria ni con el volumen: `CRLF` si funcionaba, porque `trim()` quita el `\r`.

**Decision:** reemplazar en lugar de parchar. La semantica del heredado (todo o nada) es
incompatible con RF-21, que exige resultado linea por linea; parchar el BOM y el `split` dejaria el
modulo igual de fragil ante el siguiente dato raro. El reemplazo es `CsvBatchReader` (descarta BOM,
`split(";", -1)`, error por linea, lectura en streaming con cierre del recurso, error explicito si no
es UTF-8). Las pruebas de caracterizacion corren sobre una copia textual del modulo original en
`src/test/java/.../legacy/` (solo se agrego la linea `package`), y `CsvBatchReaderTest` repite los
mismos archivos con el comportamiento nuevo. El directorio `legacy/` se deja intacto como referencia.

### D-11. Frontend

- **Estado en la URL (RF-25):** filtros, cursor, pagina, factura abierta, lote y filtro de motivo
  viven solo en la URL (`lib/url.ts`, sobre `useSyncExternalStore` y la History API). No hay copia
  en el store que pueda desincronizarse. Se descarto React Router: para tres rutas y parametros de
  consulta, una dependencia mas no aportaba.
- **Redux Toolkit** como libreria de estado (la seccion 6.1 la deja libre). Se usa a traves de
  **RTK Query**, que viene incluido en Redux Toolkit: cache de consultas, estados de carga/error
  (RF-29) e invalidacion de facturas y tablero tras cargar un lote. No hay slices propios porque el
  unico estado global de la aplicacion es la URL (siguiente punto) o datos del servidor; duplicarlo
  en el store crearia dos fuentes de verdad.
  **Alternativa descartada:** TanStack Query resolvia lo mismo con una API equivalente, pero era
  una dependencia mas sin ventaja frente a RTK Query una vez elegido Redux.
- **RF-27 en tres capas:** guardia sincronica con `useRef` (un doble clic llega antes de que React
  re-renderice el boton deshabilitado), boton deshabilitado mientras se procesa, y la idempotencia
  del backend (D-03) para lo que llegue igual: recargar a mitad de envio, reintentos de red. Tras
  procesar, el lote queda en la URL (`?lote=ID`), asi que recargar muestra el resultado y no reenvia.
- **PR-04:** la tabla nunca carga mas de 50 filas (paginacion del servidor), asi que el filtro mas
  amplio (500.000 facturas) se comporta igual que uno estrecho.

### D-12. Identificadores en espanol para estados y motivos

La seccion 6.3 pide identificadores en ingles. Los estados (`PENDIENTE`, `PAGADA_EXTEMPORANEA`) y
motivos de rechazo (`EXCEDE_SALDO`) son vocabulario de negocio de Cartera, viajan por la API y se
persisten; ya venian asi en el esquema inicial. Traducirlos obligaba a mantener un mapeo en cada
frontera. Clases, metodos y variables estan en ingles.

### D-13. Herramientas de prueba

- **Testcontainers** para integracion: bloqueos de fila, el indice unico parcial y la espera sobre
  una llave en conflicto no se pueden probar con una base en memoria. Con Docker Engine 29,
  Testcontainers 1.21 no detecta Docker porque docker-java pide una version de API menor a la minima
  del motor; se fija `api.version=1.44` en `src/test/resources/docker-java.properties`.
- **happy-dom** en lugar de jsdom en Vitest: con jsdom, `fetchBaseQuery` de RTK falla al construir
  el `Request` (el `AbortSignal` de jsdom no es el de Node).

## 3. Casos frontera (Anexo B)

Verificados contra `docker compose up` con la base sembrada, subiendo los CSV de `seed/out/casos`.

| Caso | Resultado | Justificacion |
|---|---|---|
| CF-01 | Se aplica 37.500.483. Saldo 0,40, estado **PARCIAL**. | A-02: no hay regla de condonacion; el residuo queda visible. |
| CF-02 | **Rechazado** (EXCEDE_SALDO). Saldo intacto 37.500.483,40. | RF-09 y RF-11: excede por 0,60, se rechaza entero. |
| CF-03 | El segundo envio responde 200 con `reenvio: true` y el resultado original. FV-0000300 queda en 249.000 tras ambos envios. | A-07, D-03. |
| CF-04 | A (300.000) se aplica; B (280.000) se rechaza por EXCEDE_SALDO (saldo 200.000), o al reves segun quien bloquee primero. Nunca ambos. | D-02. La suma aplicada no supera 500.000. |
| CF-05 | **PAGADA**, no extemporanea. | A-03: 23:30 -05:00 es antes del fin del 15 en Colombia. |
| CF-06 | A (60) aplicado, saldo 40; B (70) rechazado por EXCEDE_SALDO. Factura PARCIAL. | A-01 y RF-09: B no cabe completo en el saldo que deja A. |
| CF-07 | 50.000 lineas en **17,8 s** (41.050 aplicadas, 6.443 exceden saldo, 2.507 a facturas inexistentes `FV-9xxxxxx`). | D-01, D-07. Presupuesto: 60 s. |
| CF-08 | Recorrido completo sin repetidos ni omitidos mientras se aplica un pago. | D-05. Prueba de integracion `cf08_...`. |

## 4. Presupuestos medidos

Medicion local: Docker Desktop en Windows 11, base sembrada (500.000 facturas) y despues de cargar
todos los lotes de `seed/out`. 20 peticiones secuenciales con `curl` contra el puerto 3000 (incluye
nginx), percentil 95.

| Consulta | p95 |
|---|---|
| Sin filtros | 64 ms |
| NIT | 99 ms |
| Estado PARCIAL | 55 ms |
| PENDIENTE + rango de vencimiento | 48 ms |
| Rango de saldo 1M-2M | 44 ms |
| NIT + PENDIENTE + saldo minimo | 58 ms |
| Vence desde 2027 + saldo >= 49M | 29 ms |
| Lote de 50.000 lineas | 17,8 s |
| Tablero sin rango | 514 ms (sin presupuesto definido) |
| Tablero de un mes | 140 ms |

PR-01 se cumple en las combinaciones medidas, pero no en "cualquier combinacion": no se midio de
forma exhaustiva ni con carga concurrente (ver LIMITACIONES).
