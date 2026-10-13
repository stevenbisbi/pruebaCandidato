# Sistema de Conciliacion de Pagos

Recibe lotes de pago de Tesoreria en CSV o JSON, los aplica contra las facturas de proveedores,
rechaza linea por linea lo que no cuadra y muestra el estado de la conciliacion.

- Decisiones, ambiguedades y casos frontera: [DECISIONS.md](DECISIONS.md)
- Que quedo afuera y que esta mal: [LIMITACIONES.md](LIMITACIONES.md)
- Uso de IA: [AI-USAGE.md](AI-USAGE.md)

## Puesta en marcha

Requisito unico: Docker (con Docker Compose v2).

```bash
docker compose up
```

El primer arranque compila backend y frontend dentro de los contenedores y siembra las 500.000
facturas (la migracion de semilla tarda alrededor de 1 a 2 minutos). Mientras el backend no termina
de arrancar, el frontend responde, pero las consultas devuelven error 502. Arranques posteriores
reutilizan el volumen `db-data` y son inmediatos.

Para empezar de cero (borra la base): `docker compose down -v`.

| Servicio | Puerto | URL |
|---|---|---|
| Frontend (nginx) | 3000 | **http://localhost:3000** (URL de entrada) |
| Backend (Spring Boot) | 8080 | http://localhost:8080/api/v1 |
| PostgreSQL 16 | 5432 | base `conciliacion`, usuario y clave `conciliacion` |

El frontend hace de proxy de `/api` hacia el backend, asi que basta con el puerto 3000.

## Pantallas

Todas las rutas se pueden abrir, recargar o compartir directamente: filtros, pagina y lote viven en
la URL (RF-25).

| Ruta | Que muestra |
|---|---|
| `/facturas` | Consulta de facturas con filtros por NIT, estado, rango de vencimiento y rango de saldo (RF-23), paginada por cursor. |
| `/facturas/{numero}` | Detalle de una factura y todos los pagos que recibio, con su auditoria. |
| `/lotes` | Carga de un lote por archivo CSV o pegando el JSON (RF-26), y su resultado linea por linea filtrable por resultado y motivo, con descarga en CSV. |
| `/tablero` | Tablero de conciliacion (RF-24, RF-30). Abre con el mes actual; "Todas" cubre todas las facturas. |

## Como cargar datos

- **Facturas**: se cargan solas en el primer arranque desde `seed/out/facturas.csv` (migracion
  `db/seed/V2__carga_facturas.sql`, `COPY` del lado del servidor sobre el volumen `/seed`).
- **Lotes**: desde la pantalla *Carga de lotes* (subir el CSV o pegar el JSON), o por API:

```bash
curl -F "file=@seed/out/lote-50k.csv" http://localhost:8080/api/v1/batches/file
```

Los casos del Anexo B estan en `seed/out/casos/`. Se pueden subir en orden desde la UI o con curl.

## API

Las rutas van en ingles y se apartan de las sugeridas en el Anexo A; la equivalencia y el motivo
estan en [DECISIONS.md](DECISIONS.md), D-05. Los campos del JSON de entrada y las columnas del CSV
si son los del Anexo A.

| Metodo | Ruta | Descripcion | Estado |
|---|---|---|---|
| POST | `/api/v1/batches` | Lote en JSON (formato del Anexo A). 201 si se proceso, 200 si es un reenvio, 400 si el JSON no es valido o no trae pagos, 409 si el id existe con otro contenido. | Implementado |
| POST | `/api/v1/batches/file` | Lote CSV (multipart, campo `file`; `id` y `source` opcionales). Mismas respuestas que el JSON. | Implementado |
| GET | `/api/v1/batches/{id}` | Resumen del lote. 404 si no existe. | Implementado |
| GET | `/api/v1/batches/{id}/lines` | Resultado linea por linea, paginado (`page` desde 0, `size` hasta 500) y filtrable por `outcome` (`APLICADO`, `RECHAZADO`) y `reason` (motivo de rechazo). | Implementado |
| GET | `/api/v1/batches/{id}/download` | Resultado completo en CSV con separador `;` (RF-22). 404 si el lote no existe. | Implementado |
| GET | `/api/v1/invoices` | Consulta de facturas (RF-23). Filtros combinables: `nit`, `status` (repetible), `dueFrom` y `dueTo` (AAAA-MM-DD), `minBalance` y `maxBalance`. Paginacion: `size` (50 por defecto, hasta 200), `cursor` (el `nextCursor` de la pagina anterior) o `page` (desde 1, para saltar a una pagina). | Implementado |
| GET | `/api/v1/invoices/count` | Cuantas facturas cumplen los mismos filtros, para numerar las paginas. | Implementado |
| GET | `/api/v1/invoices/{id}` | Una factura y todos los pagos que recibio, aplicados y rechazados, con su auditoria. 404 si no existe. | Implementado |
| GET | `/api/v1/conciliation` | Tablero de conciliacion (RF-24): totales del rango de vencimiento (`dueFrom`, `dueTo`, opcionales) y los diez proveedores con mayor saldo pendiente. | Implementado |

Todos los montos viajan como texto decimal (`"37500483.40"`), nunca como numero JSON.

## Pruebas

Backend (JUnit 5). Las pruebas de integracion levantan un PostgreSQL 16 con Testcontainers, asi que
necesitan Docker corriendo: sin Docker fallan, no se saltan en silencio.

```bash
cd backend/pagos && ./mvnw test
```

Frontend (Vitest + Testing Library):

```bash
cd frontend && npm ci && npm test
```

| Prueba | Que fija |
|---|---|
| `ImportadorCsvLegacyCharacterizationTest` | Comportamiento actual del importador heredado, defectos incluidos. |
| `CsvBatchReaderTest` | Los mismos archivos con el lector que lo reemplaza. |
| `InvoiceTest` | Saldo a pagar sin centavos (CF-01, CF-02, el caso real de 0,72 del lote de 50.000), pago tardio (CF-05, A-04), reglas del constructor. |
| `BatchReconcilerTest` | Aplicacion linea por linea: CF-06 en los dos ordenes, decimales, fechas, referencias duplicadas, textos largos. |
| `BatchProcessingIntegrationTest` | Contra PostgreSQL real: CF-03 (con reenvios simultaneos), CF-04 (lotes concurrentes), conflicto A-07, referencia repetida en lotes concurrentes, auditoria. |
| `DashboardIntegrationTest` | Cifras del tablero (RF-24, A-08) contra PostgreSQL real. |
| `InvoiceSearchIntegrationTest` | Saltar a cualquier pagina da las mismas facturas que avanzar con el cursor; ultima pagina y conteo con filtros. |
| `*ServiceTest`, `BatchResultCsvWriterTest` | Validaciones de los casos de uso y formato del CSV de descarga. |
| `money.test.ts` | Formato colombiano sin perdida de precision (incluye montos > 2^53). |
| `format.test.ts` | Fechas en hora de Colombia sin importar la zona del navegador, mes actual, parametros de la URL, validacion del JSON. |
| `errors.test.ts` | Mensajes de error del backend, de nginx (502) y sin conexion. |
| `pages.test.ts` | Numeros de pagina a mostrar: primera, ultima y las cercanas a la actual, con huecos. |
| `pages.test.tsx` | Consulta reconstruida desde la URL (RF-25), formato de montos (RF-28), estados vacio y error (RF-29), doble clic (RF-27), resultado filtrable por motivo (RF-26), tablero (RF-24), facturas que se vuelven a consultar al procesar un lote. |

## Estructura

```
backend/pagos/        Spring Boot 3.5, Java 21 (hexagonal)
  domain/
    model/            Entidades y modelos de lectura. Sin Spring ni JDBC.
    repository/       Puertos de salida (interfaces).
    service/          Reglas puras: aplicacion de pagos y vencimiento.
  application/
    dto/              Comando y resultado del lote.
    usecase/          Puertos de entrada: interfaces de los casos de uso.
    service/          Implementacion de los casos de uso (@Service).
  adapters/
    inbound/rest/     Controladores REST y respuestas JSON.
    inbound/csv/      Lector del CSV de Tesoreria.
    outbound/persistence/  Implementacion JDBC de los puertos (@Repository).
frontend/src/         React 18 + TypeScript + Vite 6 + Tailwind 3 + Redux Toolkit (D-08)
  api/                Contrato de la API y endpoints con RTK Query.
  store.ts            Store de Redux: solo la cache de RTK Query.
  lib/                Navegacion con la History API, formato de montos y fechas.
  components/         Piezas compartidas (estados de carga/error/vacio, enlaces, insignias).
  pages/              Una pantalla por ruta.
legacy/               Importador heredado tal como se entrego (ver DECISIONS.md, D-04)
seed/out/             Datos de prueba
```
