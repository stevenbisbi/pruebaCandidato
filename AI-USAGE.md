# Uso de inteligencia artificial

Herramienta: Claude Code (modelo Claude Opus 5.5), en la aplicacion de escritorio.

## 1. Que se genero con asistencia de IA

### 1.1 Primera version del proyecto (reemplazada)

La primera version del backend, el frontend, la orquestacion (Dockerfiles, nginx, docker-compose) y
los documentos se generaron con el asistente. La estructura de carpetas del backend no me convencia
y la implementacion no la habia escrito yo, asi que en el commit `1ee3091` borre la implementacion
del backend y sus pruebas para rehacerlas por partes, entendiendo cada una. Del backend generado
solo se conservaron el dominio (`Invoice`, `InvoiceStatus`, `Batch`, `BatchReconciler`,
`DueDatePolicy`) y las migraciones, que se revisan y se reescriben en los commits siguientes.

La orquestacion sigue siendo la de la primera version; el frontend se rehizo despues (1.6).


### 1.2 Revision y decisiones

- Revision del backend y de los modelos de dominio: deteccion de los modelos faltantes, de las
  clases con nombres de JPA en un proyecto JDBC y de los casos de uso duplicados por canal.
- Analisis del enunciado: lista de ambiguedades y contradicciones (RF-18/RF-19, centavos,
  instante del vencimiento, identificador del CSV, entre otras).
- Redaccion de DECISIONS.md a partir de las decisiones que tome sobre cada ambiguedad. Mis decisiones
  iniciales no quedaron todas igual: el asistente me hizo ver que medir el pago extemporaneo por el
  momento en que se procesa el lote marcaba como tardios pagos hechos a tiempo (A-03), que componer
  el id del CSV con origen y fecha chocaba dos lotes del mismo dia (A-06), y que filtrar el tablero
  por todas las fechas complicaba PR-01 (A-08). En los tres cambie la decision.
- Diagnostico de un arranque fallido: Flyway rechazaba la migracion V2 porque el volumen de Docker
  tenia una base vieja donde V2 se habia aplicado vacia (0 facturas).

### 1.3 Modulo heredado (seccion 8)

El asistente ejecuto el proceso en el orden que pide la seccion 8:

1. **Diagnostico.** Leyendo el codigo se vieron dos puntos fragiles: la validacion exacta del
   encabezado y el `split(";")` seguido de `campos[3]`. Revisando los bytes de los archivos reales
   de Tesoreria aparecio el BOM (`EF BB BF`) al inicio del de marzo y tres lineas del de abril con
   el ultimo campo vacio.
2. **Caracterizacion, antes de modificar nada.** El modulo se copio sin cambios a
   `src/test/java/com/conciliacion/pagos/legacy` (solo se agrego la linea `package`) y se
   escribieron 16 pruebas en `ImportadorCsvLegacyCharacterizationTest` que fijan su comportamiento
   actual, incluidos los defectos. Se ejecutaron contra el modulo sin tocar y pasaron las 16.
3. **Correccion.** Se escribio `CsvBatchReader`, que reemplaza al modulo, con 13 pruebas en
   `CsvBatchReaderTest` sobre los mismos archivos reales: marzo se lee completo y abril entrega
   sus 100 lineas, con las 3 sin fecha listas para que el dominio las rechace una por una.
4. **Documentacion** en DECISIONS.md (D-04): defectos, por que no se veian en desarrollo, la
   decision de reemplazar y la alternativa descartada.

Los dos archivos de Tesoreria se copiaron a `src/test/resources/legacy`, y se agrego una regla en
`.gitattributes` para que Git no les cambie los finales de linea: los bytes exactos son parte del
caso de prueba.

### 1.4 Dominio y sus pruebas

- **`Invoice`.** Las validaciones del constructor (total positivo, saldo entre 0 y el total) y el
  campo `hadLatePayment` los empece yo; el asistente completo el resto: el saldo a pagar sin centavos
  (A-02, ver la correccion en la seccion 2), `apply()` devolviendo el ajuste por redondeo y la regla
  de pago tardio (A-04).
- **`BatchReconciler`, `LineResult`, `RejectionReason`.** El asistente adapto el reconciliador para
  aceptar valores con decimales y redondearlos al peso (A-02), registrar el valor recibido y pasar
  el ajuste de la factura al resultado de la linea, que gano el campo `roundingAdjustment`.
- **Pruebas.** Nunca habia escrito pruebas unitarias; el asistente me explico la estructura
  (preparar, ejecutar, verificar) y escribi la primera, la de CF-01 en `InvoiceTest`. Las demas las
  genero el asistente: el resto de `InvoiceTest` y `BatchReconcilerTest`, que cubren CF-01,
  CF-02, CF-05, CF-06 en los dos ordenes, A-04, A-05, valores y fechas invalidos y referencias
  duplicadas. Todas pasan con la JVM en UTC (`-Duser.timezone=UTC`), como en el contenedor.

### 1.5 Procesamiento de lotes y API

- **Procesar un lote** (`ReconcileBatchService`, `IncomingBatch`, los adaptadores JDBC y la
  migracion V5): el asistente propuso el diseno y el codigo, y yo lo fui escribiendo y armando paso
  a paso con su guia, corrigiendo los errores de compilacion que aparecian (imports con paquetes
  equivocados, una clase sin la linea `package`, un `record` con un componente de mas).
- **Endpoints de entrada** (`POST /batches` y `POST /batches/file`): los escribi con la guia del
  asistente. Al probarlos aparecio un `StackOverflowError`: el metodo `respond` se llamaba a si mismo
  en lugar de armar la respuesta. Lo encontramos leyendo los logs del contenedor.
- **Desde la consulta de lotes** (`GET /batches/{id}` y `/lines`, con filtros por resultado y motivo)
  la implementacion la hace el asistente y yo reviso, pruebo en Postman y hago el commit de cada paso.
  Tambien escribio `BatchResultServiceTest` y el manejo uniforme de errores (`ApiExceptionHandler`).
- **Consulta de facturas** (`GET /invoices`): la implemento el asistente y midio el rendimiento. Encontro
  que un rango de saldo angosto no cumplia PR-01 con diez clientes en paralelo (435 ms) y que el indice
  por saldo no bastaba por el plan generico de las consultas preparadas; lo diagnostico con
  `EXPLAIN ANALYZE` (D-06). Tambien escribio `InvoiceSearchServiceTest`.
- **Medicion de PR-02:** la hice yo, sobre una base recien sembrada: el lote de 50.000 lineas tardo 7 s.
- **Pruebas de integracion, descarga del resultado, detalle de factura, tablero y LIMITACIONES.md:**
  los hizo el asistente. En las pruebas de integracion descarto la opcion `disabledWithoutDocker`
  de la primera version, para que sin Docker fallen en lugar de saltarse en silencio. Al revisar la
  limitacion de "una referencia repetida en dos lotes concurrentes" encontro que el caso respondia 500
  y lo cambio por un 409 con un mensaje que dice que hacer; la prueba que lo cubre se ejecuto con un
  contador temporal para confirmar que la carrera realmente ocurria (diez de diez rondas). Tambien
  midio el tablero (D-07) y CF-08 recorriendo las 500.000 facturas mientras se aplicaba un lote.

### 1.6 Frontend

Le pedi al asistente que rehiciera el frontend y lo acoplara a la API actual, con una condicion: no
instalar ni sugerir librerias de terceros sin mi aprobacion, y preferir las APIs nativas del
navegador con funciones escritas desde cero. El asistente:

- Escribio las cuatro pantallas (facturas, detalle de factura, carga de lotes por CSV o JSON y
  tablero), la navegacion con la History API, los formatos de montos y fechas con `Intl`, y las
  pruebas con Vitest y Testing Library. Las probo en el navegador contra la base sembrada.
- En una primera version quito Redux Toolkit y lo reemplazo por `fetch` y un hook propio. Decidi
  mantener Redux Toolkit: el asistente me recordo que el enunciado deja libre la libreria de estado,
  y aun asi lo conservamos, porque RTK Query aporta la cache y la invalidacion (procesar un lote
  refresca solas las facturas y el tablero). Volvio a la misma version que ya tenia el proyecto, sin
  agregar nada nuevo, y la decision quedo en D-08 con el hook propio como alternativa descartada.
- Encontro una contradiccion entre su propio codigo y DECISIONS.md: D-07 dice que el tablero se abre
  con un rango, pero la pantalla abria sin rango (sumando las 500.000 facturas). Cambio la pantalla
  para que abra con el mes actual.
- **Un tropiezo que conviene declarar:** al correr las pruebas por primera vez uso `npx vitest`, y como
  vitest no estaba instalado en `node_modules`, npx lo descargo a su cache temporal, fuera del
  proyecto. No agrego nada al proyecto (vitest ya estaba declarado en `package.json`), pero no debio
  ejecutarlo sin revisar antes. Desde ahi verifico en un contenedor desechable con la misma imagen del
  Dockerfile (`npm ci` instala solo lo declarado), sin tocar mi maquina.

## 2. Que se le corrigio a la salida

Correcciones que hice yo sobre lo que propuso el asistente:

- **Redondeo de centavos.** La primera version redondeaba el saldo hacia arriba al peso
  (`RoundingMode.CEILING`), y con eso CF-02 aceptaba un pago de 37.500.484 sobre un saldo de
  37.500.483,40, aunque RF-09 dice que un pago que excede el saldo se rechaza completo. Le pregunte al
  contacto que hacer con los centavos y respondio "redondee a los centavos". Con esa respuesta se paso
  a redondear al peso mas cercano (HALF_UP): CF-02 se rechaza y CF-01 deja la factura saldada.
- **Facturas colgadas por centavos.** Despues de cargar el lote de 50.000 note que habia miles de
  facturas con saldo menor a un peso y pregunte por que. El asistente lo rastreo: eran 8.259, todas de
  ese lote, todas entre 0,50 y 0,99. Tesoreria salda las facturas pagando la parte entera, y con
  HALF_UP unos centavos de 0,50 o mas se cobraban como un peso entero, asi que la factura quedaba
  PARCIAL esperando un pago que nunca llega. Se cambio a cobrar el saldo sin centavos (FLOOR): CF-01 y
  CF-02 dan lo mismo y ninguna factura queda colgada (A-02).
- **Estructura de carpetas.** No me convencia la estructura de la primera version: un caso de uso por
  canal de entrada (CSV y JSON), clases con nombres de JPA sin JPA en el proyecto, el lector de CSV
  como adaptador de salida y los DTO HTTP en la capa de aplicacion. La reorganice; despues, revisando
  con el asistente, se unifico el caso de uso, se eliminaron las clases de JPA (la persistencia es
  JDBC, D-01) y los DTO y el lector se movieron a `adapters/inbound`.
- **Decisiones que no eran decisiones.** El asistente habia registrado en DECISIONS.md la arquitectura
  hexagonal, "un caso de uso por recurso" y "usar BigDecimal" como decisiones de arquitectura. Le pedi
  quitarlas: la hexagonal la exige el enunciado y las otras dos no tenian una alternativa real que
  justificar.
- **Rutas de la API.** El asistente recomendo usar las rutas del Anexo A, en espanol. Preferi rutas en
  ingles y en plural (`/api/v1/batches`, `/api/v1/invoices`), y le pedi justificar en DECISIONS.md
  por que nos apartamos del contrato sugerido (D-05).
- **Redux Toolkit.** Al rehacer el frontend, el asistente quito Redux Toolkit y lo reemplazo por un hook
  propio sobre `fetch`. Le pedi mantenerlo. El asistente me recordo que el enunciado deja libre la
  libreria de estado; aun asi lo conservamos por la cache y la invalidacion de RTK Query (D-08).
- **Sin librerias nuevas.** Le prohibi instalar o sugerir dependencias de terceros sin mi aprobacion.
  Lo incumplio una vez con `npx` (seccion 1.6) y lo declaro.
- **Mediciones que ya no eran ciertas.** El DECISIONS.md de la primera version decia que el lote de
  50.000 lineas tardaba ~23 s y que las consultas respondian en menos de 100 ms. Eran mediciones de la
  implementacion que borre, asi que las quite: una medicion que no corresponde al codigo actual es un
  dato falso sobre el sistema. Las volvi a medir sobre el codigo nuevo (CF-07: 7 s; D-06).
- **Las pruebas de caracterizacion de la primera version.** Le pregunte al asistente si el defecto del
  modulo heredado lo habian descubierto esas pruebas. La respuesta fue que no: quien las escribio ya
  conocia el defecto. Las borre, y se rehicieron en orden: primero se reprodujo el comportamiento
  contra los archivos reales de Tesoreria y despues se escribio el reemplazo (seccion 1.3).

Correcciones registradas al generar la primera version, que siguen vigentes:

- **Entorno de pruebas del frontend.** La configuracion propuesta usaba jsdom; las pruebas de las
  pantallas fallaban porque `fetchBaseQuery` construye un `Request` de Node con un `AbortSignal` de
  jsdom. Se cambio a happy-dom.
- **Testcontainers no detectaba Docker 29.** Las pruebas de integracion se saltaban en silencio
  (`disabledWithoutDocker`), lo que habria dado un falso verde. Se fijo la version de la API de
  docker-java (`docker-java.properties`), y en las pruebas de integracion actuales se quito
  `disabledWithoutDocker`: sin Docker fallan.

## 3. Que se escribio sin asistencia y por que

**Sin asistencia:**

- **Las decisiones sobre las ambiguedades que se mantuvieron como las tome:** que los pagos se aplican
  en el orden del lote (A-01), que un pago tardio deja la factura extemporanea (A-04), que una
  referencia rechazada se puede reenviar (A-05), que un id repetido con otro contenido es un error
  (A-07) y que la descarga es en CSV (A-09). El asistente las redacto con el formato de DECISIONS.md.
  A-02 salio de la respuesta del contacto (seccion 2), y A-03, A-06 y A-08 las cambie despues de la
  revision del asistente (seccion 1.2).
- **La medicion de PR-02** sobre una base recien sembrada (7 s) y **las pruebas manuales en Postman**
  de los endpoints con los casos frontera (CF-01, CF-02, CF-06) y con dos lotes JSON de prueba.

Por que: son decisiones de negocio, no de codigo. Como se redondea un saldo, cuando un pago cuenta
como tardio o que se hace con un lote repetido definen como se comporta el sistema con el dinero de
los proveedores, y deben salir de entender la operacion de Cartera y Tesoreria, no de una herramienta.
Lo mismo con las mediciones: un numero de rendimiento solo sirve si quien lo toma sabe en que
condiciones lo hizo y puede repetirlo.

**Escrito por mi con guia del asistente** (el asistente explicaba la pieza y daba el codigo de
referencia; yo lo escribia, compilaba y corregia los errores que aparecian):

- La reorganizacion de carpetas y los controladores REST con sus rutas.
- Las validaciones del constructor de `Invoice` y el campo `hadLatePayment`.
- Mi primera prueba unitaria, la de CF-01 en `InvoiceTest`.
- La migracion V5.
- Los modelos y puertos del procesamiento de lotes (`BatchChannel`, `Batch.received` y
  `Batch.withResults`, `BatchRepository`, `PaymentLedger`), `IncomingBatch`, `ReconcileBatchService`
  y los adaptadores JDBC.
- Los endpoints de entrada (CSV y JSON), `BatchPayResponseDto`, `ErrorResponse` y el primer
  `ApiExceptionHandler`.

Por que con guia y no generado: el procesamiento de un lote es el nucleo del sistema. Ahi se mueve el
dinero, se bloquean las facturas y se garantiza que un reenvio no se aplique dos veces, y es el codigo
que mas probablemente haya que mantener, depurar o cambiar cuando cambie una regla de negocio. Codigo
que no se entiende no se puede mantener. Escribirlo yo, aunque fuera con guia, me obligo a entender
cada pieza: los imports, la linea `package`, el constructor de un `record` y la recursion de `respond`
fueron errores mios que tuve que entender para corregirlos.
