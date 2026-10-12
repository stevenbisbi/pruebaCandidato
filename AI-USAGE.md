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

El frontend y la orquestacion siguen siendo los de la primera version.


### 1.2 Revision y decisiones

- Revision del backend y de los modelos de dominio: deteccion de los modelos faltantes, de las
  clases con nombres de JPA en un proyecto JDBC y de los casos de uso duplicados por canal.
- Analisis del enunciado: lista de ambiguedades y contradicciones (RF-18/RF-19, centavos,
  instante del vencimiento, identificador del CSV, entre otras).
- Redaccion de DECISIONS.md a partir de las decisiones que tome sobre cada ambiguedad.
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

## 2. Que se le corrigio a la salida

- **Redondeo de centavos hacia arriba.** La primera version redondeaba el saldo hacia arriba al
  peso (`RoundingMode.CEILING`). Con eso CF-02 aceptaba un pago de 37.500.484 sobre un saldo de
  37.500.483,40, aunque RF-09 dice que un pago que excede el saldo se rechaza completo. Le pregunte
  al contacto y respondio "redondee a los centavos". Lo interprete como redondear al peso mas
  cercano (HALF_UP): CF-02 se rechaza, como pide RF-09, y CF-01 deja la factura saldada (A-02).
- **Mediciones que ya no eran ciertas.** El DECISIONS.md generado decia que el lote de 50.000
  lineas tardaba ~23 s y que las consultas respondian en menos de 100 ms. Eran mediciones de la
  implementacion que borre, asi que las quite: no las podria reproducir en la defensa. CF-07 queda
  pendiente de medir sobre el codigo nuevo.
- **Estructura del backend.** La primera version tenia un caso de uso por canal de entrada (CSV y
  JSON), clases con nombres de JPA (`*Entity`, `Spring*Repository`) sin JPA en el proyecto, el
  lector de CSV como adaptador de salida y los DTO HTTP en la capa de aplicacion. Se unifico el caso
  de uso, se eliminaron las clases de JPA (la persistencia es JDBC, D-01) y los DTO y el lector se
  movieron a `adapters/inbound`.

Correcciones registradas al generar la primera version (frontend y pruebas de esa version):

- **Entorno de pruebas del frontend.** La configuracion propuesta usaba jsdom; las pruebas de las
  pantallas fallaban porque `fetchBaseQuery` construye un `Request` de Node con un `AbortSignal` de
  jsdom. Se cambio a happy-dom.
- **Testcontainers no detectaba Docker 29.** Las pruebas de integracion se saltaban en silencio
  (`disabledWithoutDocker`), lo que habria dado un falso verde. Se fijo la version de la API de
  docker-java. Ese archivo se borro junto con las pruebas en `1ee3091` y habra que reponerlo cuando
  vuelvan las pruebas de integracion.

TODO (candidato): agrega aqui lo que tu revisaste y cambiaste. Un ejemplo util es concreto: que
propuso la herramienta, por que estaba mal, que pusiste en su lugar.

## 3. Que se escribio sin asistencia y por que


