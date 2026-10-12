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

**Interpretacion.** El contacto indico "redondee a los centavos". Se entiende como redondear los
centavos al peso entero mas cercano (`RoundingMode.HALF_UP`): sin otra indicacion, "redondear"
significa al valor mas cercano. La lectura literal, redondear a dos decimales, se descarta abajo
(Descartado 1).

**Decision:**
- La factura **guarda** su valor con dos decimales (RF-04, RF-05). El redondeo no modifica datos
  guardados: solo se usa para comparar y para decidir cuando la factura queda saldada.
- **Saldo a pagar** = saldo redondeado al peso con HALF_UP. 37.500.483,40 se paga como 37.500.483.
- Un pago se acepta si es menor o igual al saldo a pagar. Si es mayor, se rechaza completo (RF-09).
- Cuando el saldo a pagar llega a 0, la factura queda saldada: el saldo se deja en 0 y la
  diferencia de centavos se registra en la auditoria de la linea como ajuste por redondeo
  (positivo si se condona, negativo si se pago de mas).
- Un pago que llega con decimales se redondea al peso con la misma regla. La auditoria conserva el
  valor original recibido y el valor aplicado. Si al redondear queda en 0, se rechaza por valor
  invalido.

**Descartado 1:** redondear a dos decimales (lectura literal). Una factura con 0,40 de saldo no
podria saldarse nunca: un pago de 1 peso excede el saldo y RF-09 obliga a rechazarlo. Quedaria
PARCIAL para siempre.

**Descartado 2:** redondear siempre hacia arriba. La entidad nunca condona centavos, pero en CF-02
aceptaria un pago que excede el saldo, contradiciendo RF-09, que es justo lo que ese caso verifica.

**Costo aceptado:** se condonan hasta 0,49 pesos por factura y se pueden pagar hasta 0,50 de mas.
Ninguno de los dos se pierde: queda registrado en la auditoria.

### A-03. Que instante define el pago extemporaneo (RF-13) — CF-05

RF-13 dice "un pago *aplicado* despues de ese instante". Puede ser la fecha de pago que trae la
linea o el momento en que el sistema procesa el lote.

**Decision:** se usa la **fecha de pago** de la linea. La factura vence al terminar el dia de
vencimiento en hora de Colombia, con la zona fija en el dominio (`DueDatePolicy.OPERATION_ZONE`,
`America/Bogota`) y nunca la del servidor, que en el contenedor es UTC. Un pago con fecha
2026-03-15T23:30:00-05:00 (04:30 UTC del dia 16) queda a tiempo.

**Descartado:** el momento de procesamiento. Hacia fallar CF-05, porque el lote se procesa despues
del vencimiento aunque se pago a tiempo. Ademas el resultado dependeria de cuando se corre: el mismo
lote daria estados distintos hoy y manana, y un reenvio (RF-20) podria cambiarlos.

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
rechazadas pero no suman al total, porque no tienen monto.

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

## 2. Decisiones de arquitectura

### D-01. Persistencia con JDBC en lugar de JPA

Se usa `JdbcTemplate` con SQL escrito a mano. Cada adaptador de persistencia convierte las filas
directamente al modelo de dominio, sin entidades intermedias.

**Descartado:** JPA con Spring Data. Ahorraba el mapeo manual y validaba el mapeo al compilar, pero
los cuatro puntos dificiles de la prueba son SQL explicito: bloqueo de filas en orden fijo (CF-04),
insercion por lotes de 50.000 lineas (PR-02), filtros combinables con paginacion por cursor (RF-23,
PR-03) y agregaciones del tablero (RF-24). Con JPA se terminaria escribiendo SQL nativo de todas
formas, mas una entidad y un mapper por tabla.

**Costo aceptado:** un cambio de columna no falla al compilar sino al ejecutar; lo atrapan las
pruebas de integracion contra PostgreSQL.

### D-02. Un solo caso de uso para las dos vias de entrada (RF-17)

El lote llega en CSV o en JSON, pero se procesa igual. Hay un solo caso de uso
(`ReconcileBatchUseCase`) y dos adaptadores de entrada (`BatchFileController`, `BatchPayController`)
que lo invocan con el mismo lote.

**Descartado:** un caso de uso por canal. Duplicaba la regla de negocio y metia en la aplicacion
una distincion que solo le importa al adaptador.

### D-03. Arquitectura hexagonal

- `domain`: modelo, reglas y puertos de salida (`repository`). No depende de Spring ni de JDBC.
- `application`: puertos de entrada (`usecase`) y su implementacion.
- `adapters/inbound`: REST y lectura de CSV. Los DTO y mappers HTTP viven aqui, no en la aplicacion.
- `adapters/outbound`: persistencia JDBC.

### D-04. Dinero sin perdida de precision (RF-05)

`BigDecimal` en Java, `NUMERIC(18,2)` en la base y montos como texto en la API (`"37500483.40"`),
para que ningun cliente los convierta a punto flotante.

### D-05. Concurrencia entre lotes — CF-04

Al procesar un lote, sus facturas se bloquean con `SELECT ... FOR UPDATE`, siempre ordenadas por
numero. Si dos lotes tocan la misma factura, el segundo espera y ve el saldo ya reducido. El orden
fijo evita que dos lotes se esperen mutuamente.

**Descartado:** bloqueo optimista con columna de version. Ante un conflicto obliga a reintentar el
lote completo, caro con lotes de 50.000 lineas.

### D-06. Paginacion por cursor (PR-03) — CF-08

La pagina siguiente pide las facturas con numero mayor al ultimo visto (`WHERE number > ?`). El
numero de una factura nunca cambia.

**Descartado:** `LIMIT/OFFSET`. Si mientras se navega un lote cambia el estado de una factura y sale
del filtro, las siguientes se corren una posicion y se omite una.

**Costo aceptado:** no hay total de resultados ni salto directo a la pagina N.

### D-07. Modulo heredado (seccion 8)

**Defectos encontrados:**
1. **BOM UTF-8:** el archivo de marzo empieza con los bytes `EF BB BF`. Java los lee como un
   caracter invisible pegado a `referencia`, el encabezado no coincide y se rechaza el archivo.
2. **Campo final vacio:** `"a;b;c;".split(";")` devuelve 3 campos, no 4. Las lineas de abril sin
   fecha lanzan `ArrayIndexOutOfBoundsException` y se rechaza el archivo completo.

**Por que no se manifestaba en desarrollo:** los archivos de desarrollo se generan con un programa,
sin BOM y con todas las columnas llenas. Los de Tesoreria dependen de como se exportaron ese dia:
guardados con Excel llevan BOM, y si a un pago le falto la fecha queda el campo vacio. Por eso "a
veces entra y a veces no" al reenviarlo.

## 3. Casos frontera (Anexo B)

| Caso | Resultado esperado | Por que |
|---|---|---|
| CF-01 | Se aplica. El saldo a pagar queda en 0: PAGADA, con ajuste por redondeo de 0,40 en la auditoria. | A-02 |
| CF-02 | Se rechaza completo por exceder el saldo a pagar (37.500.483). El saldo no cambia (RF-11). | A-02 |
| CF-03 | El segundo envio devuelve el resultado original; los saldos quedan identicos. | A-06, A-07 |
| CF-04 | Los lotes se serializan sobre la factura; lo aplicado nunca supera el saldo original. | D-05 |
| CF-05 | PAGADA, no extemporanea: 23:30 en Colombia es antes del fin del dia de vencimiento. | A-03 |
| CF-06 | A se aplica (saldo 40); B se rechaza por exceder el saldo. | A-01 |
| CF-07 | Pendiente de medir. | — |
| CF-08 | No se repiten ni se omiten facturas. | D-06 |
