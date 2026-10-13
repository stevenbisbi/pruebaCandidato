# Decisiones tecnicas

Cada decision sigue el mismo formato: que se decidio, que alternativa se descarto, que ganaba esa
alternativa, que costaba y por que en este contexto no aplica.

## 1. Ambiguedades del enunciado

### A-01. RF-18 contradice a RF-19 — CF-06

RF-18 pide aplicar los pagos de una misma factura en el orden del lote. RF-19 pide que el resultado
no dependa del orden de las lineas. Con saldo 100 y pagos A=60 y B=70, no se pueden cumplir los
dos: si se aplica A primero, B se rechaza; al reves, A se rechaza.

**Decision:** manda RF-18. Los pagos se aplican en el orden en que aparecen en el lote y cada uno
ve el saldo que dejo el anterior. RF-19 se interpreta como "facturas distintas no se afectan entre
si": el resultado de una factura no depende de en que posicion aparecen las lineas de otras.

**Descartado:** rechazar todos los pagos de una factura cuando su suma excede el saldo. Cumplia
RF-19 al pie de la letra, pero viola RF-18 y deja sin aplicar un pago de 60 que si cabia.

### A-02. Facturas con centavos y pagos en pesos enteros (RF-04 vs RF-10) — CF-01, CF-02

La factura tiene centavos (RF-04) y Tesoreria paga en pesos enteros (RF-10). Una factura de
37.500.483,40 no puede quedar en cero con pagos enteros.

**Interpretacion.** El contacto indico "redondee a los centavos", sin decir hacia donde. Lo que
decidio la direccion fueron los datos: en el lote de 50.000 lineas, cuando Tesoreria salda una
factura con centavos paga la parte entera y descarta los centavos (30.202.062,72 se paga con
30.202.062). Se adopta esa misma regla: el saldo que se le cobra a Tesoreria es el saldo **sin los
centavos** (`RoundingMode.FLOOR`).

**Decision:**
- La factura **guarda** su valor con dos decimales (RF-04, RF-05). El redondeo no modifica datos
  guardados: solo se usa para comparar y para decidir cuando la factura queda saldada.
- **Saldo a pagar** = saldo sin centavos. 37.500.483,40 se paga como 37.500.483.
- Un pago se acepta si es menor o igual al saldo a pagar. Si es mayor, se rechaza completo (RF-09).
- Cuando queda menos de un peso, ya no hay nada que se pueda pagar en pesos enteros: la factura queda
  saldada, el saldo se deja en 0 y los centavos se registran en la auditoria de la linea como ajuste
  por redondeo (de 0,01 a 0,99).
- Un pago que llega con decimales se redondea al peso **mas cercano**: es dinero que Tesoreria si
  envio, y truncarlo le acreditaria menos de lo que pago. La auditoria conserva el valor recibido y
  el aplicado. Si al redondear queda en 0, se rechaza por valor invalido.
- La coma no se acepta como separador decimal (`100,5` se rechaza por valor invalido): el Anexo A
  usa punto.

**Descartado 1:** redondear el saldo al peso mas cercano (`HALF_UP`). Fue la primera regla adoptada,
y la medicion con el lote de 50.000 la descarto: de 16.418 pagos con los que Tesoreria salda una
factura con centavos, los 8.259 cuyos centavos eran 0,50 o mas dejaban la factura PARCIAL con un saldo
de 0,50 a 0,99, esperando un pago de un peso que Tesoreria no envia. Son justamente las diferencias que
el sistema debe evitar que aparezcan al cierre de mes. Ademas permitia pagar hasta 0,50 de mas.

**Descartado 2:** redondear a dos decimales y comparar exacto (lectura literal de la respuesta). Una
factura con 0,40 de saldo no podria saldarse nunca: un pago de 1 peso excede el saldo y RF-09 obliga a
rechazarlo.

**Descartado 3:** redondear siempre hacia arriba. La entidad nunca condonaria centavos, pero
aceptaria pagos mayores que el saldo (37.500.484 sobre 37.500.483,40), y la regla de negocio de RF-09
es que un pago que excede el saldo no se aplica.

**Costo aceptado:** se condonan hasta 0,99 pesos por factura, y nunca se cobra de mas. Lo condonado no
se pierde: queda registrado en la auditoria de la linea que saldo la factura.

### A-03. Que instante define el pago extemporaneo (RF-13) — CF-05

RF-13 dice "un pago *aplicado* despues de ese instante". Puede ser la fecha de pago que trae la
linea o el momento en que el sistema procesa el lote.

**Decision:** se usa la **fecha de pago** de la linea. La factura vence al terminar el dia de
vencimiento en hora de Colombia, con la zona fija en el dominio (`DueDatePolicy.OPERATION_ZONE`,
`America/Bogota`) y nunca la del servidor, que en el contenedor es UTC. Un pago con fecha
2026-03-15T23:30:00-05:00 (04:30 UTC del dia 16) queda a tiempo.

RF-13 dice "a las 23:59:59". Un pago a las 23:59:59,5 no es posterior a las 23:59:59 en segundos
enteros, pero si en milisegundos. Se toma como limite el inicio del dia siguiente (00:00:00 en
Colombia): todo lo anterior es a tiempo, y el primer instante tardio es la medianoche.

**Descartado:** el momento de procesamiento. Tesoreria entrega los pagos del dia en un lote, que
llega despues del cierre: un pago hecho a tiempo quedaria marcado como extemporaneo solo porque el
sistema lo proceso al dia siguiente. Ademas el resultado dependeria de cuando se corre: el mismo lote
daria estados distintos hoy y manana, y un reenvio (RF-20) podria cambiarlos.

### A-04. Factura saldada con algun pago tardio (RF-13)

Si un pago parcial llego despues del vencimiento y el que cierra la factura llego a tiempo (o al
reves), el enunciado no dice que estado corresponde.

**Decision:** si **cualquier** pago aplicado a la factura fue extemporaneo, la factura queda
PAGADA_EXTEMPORANEA al saldarse. Es coherente con RF-14: el estado se calcula a partir de los pagos
recibidos, no solo del ultimo.

**Descartado:** mirar solo el pago que cierra la factura. Es mas simple, pero esconde que la
factura tuvo pagos fuera de plazo.

### A-05. Una referencia rechazada puede volver a enviarse (RF-12)

RF-12 prohibe aplicar dos veces la misma referencia, pero no dice que pasa si la primera vez fue
rechazada.

**Decision:** una referencia rechazada se puede reenviar y aplicar. RF-12 se aplica solo a
referencias **aplicadas**, y la base lo garantiza con un indice unico parcial
(`WHERE outcome = 'APLICADO'`).

**Descartado:** bloquear toda referencia ya vista. Obligaria a Tesoreria a inventar una referencia
nueva para corregir un pago rechazado por un error de digitacion.

### A-06. Identificador de un lote CSV (RF-16 vs formato CSV) — CF-03

RF-16 dice que un lote tiene identificador, pero el formato del CSV (Anexo A) no lo trae.

**Decision:** el `loteId` se puede enviar como campo opcional junto al archivo. Si no viene, el id
se compone como `CSV-` mas la huella SHA-256 del contenido. El mismo archivo produce siempre el
mismo id, y asi un reenvio se reconoce solo (RF-20).

**Descartado:** componer el id con origen y fecha. Dos lotes legitimos del mismo dia tendrian el
mismo id y el segundo se rechazaria como conflicto (A-07).

### A-07. Mismo id de lote con contenido distinto (RF-20)

**Decision:** es un error. Se responde 409 Conflict y no se aplica nada. Mismo id con el mismo
contenido es un reenvio: se devuelve el resultado original sin reaplicar.

**Descartado:** tratarlo como una correccion del lote anterior. Exigiria revertir pagos ya
aplicados, algo que el enunciado no pide y que es peligroso hacer de forma automatica.

### A-08. Rango de fechas del tablero (RF-24)

RF-24 habla del "rango de fechas filtrado" sin decir que fecha.

**Decision:** es el mismo filtro de RF-23, por **fecha de vencimiento**. Total facturado, aplicado y
rechazado se calculan sobre las facturas con vencimiento en ese rango. El total rechazado incluye
las lineas rechazadas con un valor legible. Las lineas con valor ilegible cuentan como lineas
rechazadas pero no suman al total, porque no tienen monto. Los rechazos dirigidos a una factura que
no existe tampoco entran: no tienen fecha de vencimiento con la cual filtrarlos. Se ven en el
resultado de cada lote.

**Descartado:** filtrar tambien por fecha de emision y por fecha de pago. Daba mas vistas, pero cada
fecha necesita su propio indice y complica cumplir PR-01. Queda en LIMITACIONES.md.

### A-09. Formato de descarga del resultado (RF-22)

**Decision:** CSV con el mismo separador que usa Tesoreria (`;`), una fila por linea del lote con
su resultado y motivo. Los montos van con punto decimal y sin separador de miles (`37500483.40`):
el formato colombiano (RF-28) es solo de presentacion en pantalla.

### A-10. Lineas mal formadas

El lote de abril trae tres lineas sin fecha.

**Decision:** se rechaza solo esa linea, con su motivo (RF-21). El resto del lote se procesa.

**Descartado:** rechazar el lote completo, como hace el importador heredado. Es justamente el
defecto que reporta Cartera (seccion 8).

### A-11. Estado de la factura: calculado y guardado (RF-03, RF-14)

RF-14 dice que el estado "se calcula a partir de los pagos recibidos". Puede leerse como calcularlo
cada vez que se consulta, sumando los pagos, o como derivarlo de los pagos cada vez que llega uno.

**Decision:** el estado se deriva en el dominio cada vez que se aplica un pago (`InvoiceStatus.derive`,
a partir del saldo y de si hubo algun pago tardio) y se guarda en la factura. Nunca se asigna a mano.

**Descartado:** calcularlo en cada consulta sumando los pagos. Es la lectura mas literal, pero el
filtro por estado de RF-23 no podria usar un indice: habria que sumar los pagos de las 500.000
facturas para saber cuales estan PARCIAL, y PR-01 (300 ms) no se cumpliria.

### A-12. Que significa que la tabla se mantenga "utilizable" (PR-04)

PR-04 pide que la tabla de facturas siga siendo utilizable "con el filtro mas amplio posible", es
decir, sin filtros: 500.000 facturas.

**Decision:** la tabla nunca recibe mas de una pagina: 50 facturas por defecto, hasta 200. La
consulta sin filtros responde en menos de 15 ms (D-06); la paginacion muestra el total, los numeros
de pagina y deja ir a la ultima (D-03).

**Descartado:** traer mas filas y mostrarlas con desplazamiento virtual. Daria una tabla continua,
pero obliga a transferir y guardar en el navegador miles de filas que nadie va a mirar.

## 2. Decisiones de arquitectura

### D-01. Persistencia con JDBC en lugar de JPA

Se usa `JdbcTemplate` con SQL escrito a mano. Cada adaptador de persistencia convierte las filas
directamente al modelo de dominio, sin entidades intermedias.

**Descartado:** JPA con Spring Data. Ahorraba el mapeo manual y validaba el mapeo al compilar, pero
las partes criticas de este sistema dependen de SQL explicito: bloqueo de filas en orden fijo (CF-04),
insercion por lotes de 50.000 lineas (PR-02), filtros combinables con paginacion por cursor (RF-23,
PR-03) y agregaciones del tablero (RF-24). Con JPA se terminaria escribiendo SQL nativo de todas
formas, mas una entidad y un mapper por tabla.

**Costo aceptado:** un cambio de columna no falla al compilar sino al ejecutar; lo atrapan las
pruebas de integracion contra PostgreSQL.

### D-02. Concurrencia entre lotes — CF-04

Al procesar un lote, sus facturas se bloquean con `SELECT ... FOR UPDATE`, siempre ordenadas por
numero. Si dos lotes tocan la misma factura, el segundo espera y ve el saldo ya reducido. El orden
fijo evita que dos lotes se esperen mutuamente.

**Descartado:** bloqueo optimista con columna de version. Ante un conflicto obliga a reintentar el
lote completo, caro con lotes de 50.000 lineas.

**Referencia repetida en lotes concurrentes (RF-12).** Si dos lotes van a la misma factura, el
bloqueo los ordena y el segundo ve la referencia ya aplicada. Si van a facturas distintas, ninguno
ve la referencia del otro hasta que confirma, y el indice unico de V3 rechaza al segundo: su
transaccion se deshace entera y la API responde 409 pidiendo reenviarlo; al reenviarlo, esa linea se
rechaza como duplicada y el resto se aplica. La prueba de integracion lo repite diez veces: en las
diez se dio la carrera y la referencia se aplico una sola vez.

**Descartado:** bloquear tambien las referencias del lote antes de procesar (por ejemplo con un
`advisory lock` por referencia). Evitaba el reenvio, pero suma 50.000 bloqueos por lote para un caso
que exige que Tesoreria mande la misma referencia en dos lotes distintos casi al mismo tiempo.

### D-03. Paginacion por cursor (PR-03) — CF-08

La pagina siguiente pide las facturas con numero mayor al ultimo visto (`WHERE number > ?`). El
numero de una factura nunca cambia.

**Que garantiza:** cada factura aparece una sola vez y en orden, aunque un lote cambie saldos y
estados mientras se navega. Con un filtro por estado o por saldo, una factura que deja de cumplirlo
a mitad del recorrido ya no aparece en las paginas siguientes: es el estado actual, no una omision.

**Como se verifico (CF-08):** se recorrieron las 500.000 facturas, de 200 en 200, mientras se
procesaba un lote de 50.000 pagos sobre facturas pendientes. Durante 15 s los dos corrieron a la vez:
salieron las 500.000, sin repetidas y en orden. El mismo recorrido filtrando PENDIENTE tampoco
repitio ninguna.

**Descartado:** `LIMIT/OFFSET`. Si mientras se navega un lote cambia el estado de una factura y sale
del filtro, las siguientes se corren una posicion y se omite una.

**Numeracion y salto a una pagina.** La pantalla muestra los numeros de pagina y deja ir a la ultima.
Para eso hay un conteo aparte (`GET /invoices/count`), que el frontend pide una vez por combinacion de
filtros y no en cada pagina, y un parametro `page` para saltar: el backend se posiciona con OFFSET
desde el extremo mas cercano, asi que una pagina de la segunda mitad, la ultima incluida, se lee al
reves desde el final. "Siguiente" sigue siendo por cursor, asi que recorrer pagina a pagina mantiene
la garantia de PR-03; un salto se posiciona por el conteo del momento, y si un lote cambia facturas
justo entonces, la pagina puede correrse unas filas.

**Medido** con las 500.000 facturas: conteo, 26 a 64 ms; primera, segunda y ultima pagina, menos de
45 ms; una pagina del medio (la 5.000 de 10.000), 151 ms con un cliente y 607 ms en el p95 con diez
en paralelo. OFFSET recorre todas las filas anteriores a la pagina, y en el medio son 250.000.

**Costo aceptado:** saltar a una pagina del medio con muchos usuarios a la vez pasa de 300 ms. La
consulta que pide PR-01 (filtrar y avanzar) no cambia: sigue en menos de 20 ms.

### D-04. Modulo heredado (seccion 8)

**Defectos encontrados:**
1. **BOM UTF-8:** el archivo de marzo empieza con los bytes `EF BB BF`. Java los lee como un
   caracter invisible pegado a `referencia`, el encabezado no coincide y se rechaza el archivo.
2. **Campo final vacio:** `"a;b;c;".split(";")` devuelve 3 campos, no 4. Las lineas de abril sin
   fecha lanzan `ArrayIndexOutOfBoundsException` y se rechaza el archivo completo.

**Por que no se manifestaba en desarrollo:** los archivos de desarrollo se generan con un programa,
sin BOM y con todas las columnas llenas. Los de Tesoreria dependen de como se exportaron ese dia:
guardados con Excel llevan BOM, y si a un pago le falto la fecha queda el campo vacio. Por eso "a
veces entra y a veces no" al reenviarlo.

**Otros defectos fijados por las pruebas de caracterizacion:** acepta valores 0 y negativos; un
valor con decimales o una fecha sin zona horaria rechazan el archivo completo; el limite de 100.000
lineas cuenta tambien las lineas en blanco; el `Stream` de `Files.lines` nunca se cierra.

**Proceso:** primero se copio el modulo sin cambios a `src/test/.../legacy` (solo se agrego la linea
`package`) y se fijo su comportamiento en `ImportadorCsvLegacyCharacterizationTest`, incluidos los
defectos. Los pares "marzo con y sin BOM" y "abril con y sin las lineas sin fecha" prueban la causa,
no solo el sintoma. Despues se escribio el reemplazo.

**Decision:** reemplazar el modulo por `CsvBatchReader`. Quita el BOM, usa `split(";", -1)`, lee
por streaming, y una linea mala se marca sola (`BatchLine` con `formatError`) mientras el resto del
lote sigue (RF-21, A-10). Los valores y fechas se entregan como texto y los valida el dominio, asi
un valor con decimales se redondea segun A-02 en lugar de tumbar el archivo. Solo un archivo vacio,
un encabezado distinto o mas de 100.000 pagos rechazan el archivo completo. Las pruebas de
caracterizacion se conservan sobre la copia del modulo original.

**Descartado:** parchar el heredado (quitar el BOM y usar `split(";", -1)`). Eran dos lineas y
corregian los dos archivos de Tesoreria, pero el modulo seguiria siendo todo o nada: la proxima
linea mala, por ejemplo un valor con decimales, volveria a rechazar el lote entero, que es
justamente el sintoma que reporta Cartera.

**Cambio de comportamiento consciente:** un archivo con solo el encabezado era un lote vacio
aceptado; ahora se rechaza, porque un lote sin pagos no tiene nada que conciliar.

### D-05. Contrato de la API: rutas en ingles (se aparta del Anexo A)

El Anexo A sugiere rutas en espanol (`/api/v1/lotes`, `/lotes/archivo`, `/facturas`,
`/conciliacion/tablero`) y aclara que es una sugerencia: se puede usar otro diseno si se justifica
y se documenta.

**Decision:** las rutas y los parametros de consulta van en ingles y los recursos en plural, como
pide la convencion REST:

| Anexo A | Este proyecto |
|---|---|
| `POST /api/v1/lotes` | `POST /api/v1/batches` |
| `POST /api/v1/lotes/archivo` | `POST /api/v1/batches/file` (campo `file`, `id` opcional) |
| `GET /api/v1/lotes/{id}` | `GET /api/v1/batches/{id}` |
| `GET /api/v1/lotes/{id}/resultado` | `GET /api/v1/batches/{id}/lines` (paginado: `page`, `size`) y `GET /api/v1/batches/{id}/download` (CSV) |
| `GET /api/v1/facturas` | `GET /api/v1/invoices` |
| `GET /api/v1/facturas/{numero}` | `GET /api/v1/invoices/{id}` |
| `GET /api/v1/conciliacion/tablero` | `GET /api/v1/conciliation` |

El motivo es la consistencia: la seccion 6.3 pide identificadores en ingles, y con rutas en espanol
la API quedaria en un idioma y el codigo que la atiende en otro (`/lotes` atendido por
`BatchController`). El Anexo A trae una sola ruta para el resultado; aqui son dos porque la pantalla
lo necesita paginado (un lote puede tener 50.000 lineas) y la descarga lo necesita completo (RF-22).

**Lo que no cambia:** los campos del JSON de entrada (`loteId`, `origen`, `fechaGeneracion`,
`pagos`, `referencia`, `numeroFactura`, `valor`, `fechaPago`) y las columnas del CSV
(`referencia;numero_factura;valor;fecha_pago`) se conservan tal como los define el Anexo A. Son el
formato que envia Tesoreria, un sistema externo, y cambiarlos le exigiria modificar su integracion.

**Descartado:** usar las rutas del Anexo A tal cual. Cualquier cliente escrito contra el contrato
sugerido funcionaria sin cambios, pero la API mezclaria idiomas con el codigo. Como el contrato es
una sugerencia y el cliente de este proyecto es el propio frontend, se prefirio la consistencia.

**Costo aceptado:** quien pruebe la API con las rutas del Anexo A recibe 404. Por eso el contrato
completo esta en el README.

### D-06. Rendimiento de la consulta de facturas (PR-01)

**Como se midio:** base sembrada (500.000 facturas) despues de procesar el lote de 50.000, en Docker
local. Veinte peticiones por combinacion de filtros con `curl`, y las mas costosas tambien con diez
clientes en paralelo (100 peticiones).

**Lo que aparecio:** con los indices por NIT y por estado (V3), casi todas las combinaciones
respondian en menos de 20 ms. La excepcion fue un rango de saldo angosto sin otros filtros
(`minBalance=1000000&maxBalance=1001000`, 7 facturas): 144 ms con un cliente y **435 ms en el p95
con diez en paralelo**, que no cumple PR-01. Sin indice por saldo, PostgreSQL recorre las 500.000
facturas en orden de numero buscando las pocas que cumplen.

**Primer intento, insuficiente:** un indice por saldo. Mejoro las combinaciones con estado, pero el
caso principal siguio en ~116 ms. `EXPLAIN ANALYZE` mostro la causa: la misma consulta tardaba 0,18 ms
con el plan armado para sus valores y 109 ms con el plan generico. El driver JDBC reutiliza las
consultas preparadas, y desde la sexta ejecucion PostgreSQL usa un plan generico que no mira los
valores del filtro; sin ellos no sabe que el rango es angosto y elige recorrer la tabla.

**Decision:** el indice por saldo (V6) mas `plan_cache_mode = force_custom_plan` en cada conexion
(`application.properties`), para que el plan siempre se arme con los valores reales.

| Combinacion | Antes (p95) | Despues (p95) |
|---|---|---|
| Sin filtros, NIT, estado, vencimiento (1 dia y 1 mes) | 8-15 ms | 7-15 ms |
| Rango de saldo angosto | 144 ms | 10 ms |
| Estado + rango de saldo angosto | 108-128 ms | 8-10 ms |
| Un dia de vencimiento + saldo angosto | 85 ms | 7 ms |
| Rango imposible (sin resultados) | 102 ms | 7 ms |
| Rango de saldo angosto, 10 clientes en paralelo | **435 ms** | **20 ms** |

**Descartado 1:** forzar planes especificos con `prepareThreshold=0` en la URL de conexion. Logra lo
mismo, pero la URL se sobrescribe con la variable `DB_URL` del docker-compose y habria que mantenerla
en dos lugares.

**Descartado 2:** indice por fecha de vencimiento. Las consultas por vencimiento ya respondian en
menos de 20 ms: los vencimientos estan repartidos en 24 meses, asi que recorriendo por numero se
juntan 50 resultados enseguida. Seria un indice mas que mantener sin una mejora medible.

**Costo aceptado:** cada pago aplicado actualiza tambien el indice por saldo, lo que hace un poco mas
lento el lote de 50.000. Medido sobre una base recien sembrada y con el indice: 7 s, contra un limite
de 60 s (PR-02). Con dos recorridos completos de la paginacion compitiendo al mismo tiempo, 15 s.

### D-07. Tablero de conciliacion sin indice por vencimiento (RF-24)

El tablero suma las facturas del rango de vencimiento y los pagos dirigidos a ellas, en una sola
transaccion REPEATABLE READ para que las tres cifras salgan de la misma foto de la base aunque un
lote se este aplicando.

**Medido** con las 500.000 facturas: un mes, 105 ms (p50) y 123 ms (p95); un ano, 174 y 224 ms; sin
rango, 229 y 409 ms. Sin rango recorre la tabla entera, porque suma todas las facturas.

**Decision:** dejarlo asi. PR-01 se refiere a la consulta de facturas (RF-23), no al tablero, y la
pantalla del tablero se abre con un rango.

**Descartado:** un indice por fecha de vencimiento. Bajaria el caso de un mes, pero cuando un pago
cambia el estado de una factura PostgreSQL actualiza todos sus indices, asi que hace mas lento el lote
de 50.000 (PR-02), que si es un presupuesto. La alternativa para un tablero mas rapido seria guardar
los totales por mes y actualizarlos al aplicar cada lote; queda en LIMITACIONES.md.

### D-08. Frontend: Redux Toolkit para los datos del servidor, la URL para el estado de pantalla

El enunciado deja libre la libreria de estado. Se usa Redux Toolkit con RTK Query, y solo para lo
que llega de la API; lo demas esta escrito con las APIs nativas del navegador:

- **Datos del servidor con RTK Query:** cada endpoint es un hook con carga, error y reintento
  (RF-29). Guarda una cache por argumento, junta peticiones repetidas, y procesar un lote invalida
  las facturas, sus detalles y el tablero: las pantallas que los muestran se vuelven a consultar
  solas, sin que cada una tenga que saber que otra cambio los datos.
- **Estado de la pantalla en la URL (RF-25):** filtros, cursor, pagina y lote son parametros de la
  URL con los mismos nombres que usa la API, y no van al store. La navegacion usa la History API
  (`pushState` y `useSyncExternalStore`), sin libreria de rutas. Pegar un enlace reproduce la misma
  vista, y atras y adelante funcionan.
- **Montos (RF-28):** se formatean sobre el texto que manda la API, sin convertir a `number`, para no
  perder precision por encima de 2^53 (RF-05).
- **Fechas:** las de vencimiento se muestran desde el texto (`2026-03-15` como `15/03/2026`); los
  instantes, con `Intl.DateTimeFormat` en `America/Bogota`, sin importar la zona del navegador.
- **Doble envio (RF-27):** un `useRef` bloquea el segundo clic en el mismo instante (el estado de la
  mutacion recien se ve en el render siguiente); al terminar, el id del lote queda en la URL, asi que
  recargar muestra el resultado sin reenviar. El backend, ademas, reconoce el reenvio (RF-20).

**Descartado 1:** guardar tambien los filtros en el store de Redux. Habria que sincronizar el store con
la URL en los dos sentidos (al filtrar y al usar atras o pegar un enlace); con la URL como unica fuente
no hay nada que sincronizar.

**Descartado 2:** un hook propio sobre `fetch`, sin Redux. Se probo: hace lo mismo en unas 70 lineas y
quita dos dependencias, pero sin cache ni invalidacion; despues de procesar un lote, cada pantalla
tendria que saber que debe volver a consultar.

**Costo aceptado:** dos dependencias y un store global cuyo unico contenido es la cache de la API.

### D-09. Un lote, una transaccion, dentro de la peticion (RF-20, RF-26)

Registrar el lote, bloquear sus facturas, aplicar los pagos, guardar saldos, resultado por linea y
totales ocurre en una sola transaccion (`ReconcileBatchService`, `@Transactional`), mientras la
peticion HTTP espera. Si algo falla a mitad de camino no queda nada aplicado, y el reenvio de
Tesoreria procesa el lote desde cero.

**Descartado:** procesar el lote en segundo plano y que la pantalla consulte el avance. Mostraria un
avance real (RF-26), pero exige guardar lotes a medio procesar y decidir que pasa con ellos si el
servidor se cae. Con 7 s para el lote mas grande (CF-07), la espera dentro de la peticion es
razonable.

**Costo aceptado:** no hay avance linea a linea (la pantalla muestra el tiempo transcurrido), y las
facturas del lote quedan bloqueadas mientras dura (LIMITACIONES.md).

### D-10. La auditoria vive en el resultado de cada linea (RF-15)

RF-15 pide que toda aplicacion o rechazo quede auditado con usuario, instante, saldo anterior y saldo
posterior. Cada fila de `payment_line_result` tiene todo eso: es a la vez el resultado del lote
(RF-21) y la auditoria. Una fila con resultado APLICADO es un pago aplicado; no hay otra tabla de pagos.

**Descartado:** una tabla de auditoria aparte, escrita por el codigo o por un trigger. Separaba "lo que
paso" de "como se audito", pero duplicaba cada linea y abria la posibilidad de que las dos tablas no
coincidieran. Aqui el resultado es el registro auditado.

**Costo aceptado:** la tabla crece con cada lote (50.000 filas por carga grande); las consultas por
lote usan la llave primaria y las consultas por factura, el indice de V7.

## 3. Casos frontera (Anexo B)

| Caso | Resultado esperado | Por que |
|---|---|---|
| CF-01 | Se aplica. El saldo a pagar queda en 0: PAGADA, con ajuste por redondeo de 0,40 en la auditoria. | A-02 |
| CF-02 | Se rechaza completo por exceder el saldo a pagar (37.500.483). El saldo no cambia (RF-11). | A-02 |
| CF-03 | El segundo envio devuelve el resultado original (200, `replayed`) y los saldos quedan identicos. Probado tambien con cuatro reenvios simultaneos: se aplica una sola vez (`BatchProcessingIntegrationTest`). | A-06, A-07 |
| CF-04 | Los lotes se serializan sobre cada factura; lo aplicado nunca supera el saldo original. Probado con dos lotes concurrentes sobre 30 facturas en orden inverso: sin bloqueos mutuos y un solo pago aplicado por factura (`BatchProcessingIntegrationTest`). | D-02 |
| CF-05 | PAGADA, no extemporanea: 23:30 en Colombia es antes del fin del dia de vencimiento. | A-03 |
| CF-06 | A se aplica (saldo 40); B se rechaza por exceder el saldo. | A-01 |
| CF-07 | 50.000 lineas en 7 s sobre una base recien sembrada (limite PR-02: 60 s): 41.067 aplicadas y 8.933 rechazadas, cada una con su motivo. | D-06 |
| CF-08 | No se repiten ni se omiten facturas. Verificado recorriendo las 500.000 mientras se aplicaba un lote de 50.000 pagos. | D-03 |
