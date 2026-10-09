# Sistema de Conciliacion de Pagos

Recibe lotes de pago de Tesoreria (JSON o CSV), los aplica contra las facturas de proveedores,
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

## Como cargar datos

- **Facturas**: se cargan solas en el primer arranque desde `seed/out/facturas.csv` (migracion
  `db/seed/V2__carga_facturas.sql`, `COPY` del lado del servidor sobre el volumen `/seed`).
- **Lotes**: desde la pantalla *Carga de lotes* (subir CSV o pegar JSON), o por API:

```bash
curl -F "archivo=@seed/out/lote-50k.csv" http://localhost:3000/api/v1/lotes/archivo
```

```bash
curl -H "Content-Type: application/json" -d @lote.json http://localhost:3000/api/v1/lotes
```

Los casos del Anexo B estan en `seed/out/casos/`. Se pueden subir en orden desde la UI o con curl.

## API

| Metodo | Ruta | Descripcion |
|---|---|---|
| POST | `/api/v1/lotes` | Lote en JSON. 201 si se proceso ahora, 200 si es un reenvio, 409 si el id existe con otro contenido. |
| POST | `/api/v1/lotes/archivo` | Lote CSV (multipart, campo `archivo`; opcionales `loteId`, `origen`). |
| GET | `/api/v1/lotes/{id}` | Resumen del lote y rechazos por motivo. |
| GET | `/api/v1/lotes/{id}/lineas` | Resultado linea por linea, paginado (`resultado`, `motivo`, `pagina`, `tamano`). |
| GET | `/api/v1/lotes/{id}/resultado` | Descarga CSV del resultado linea por linea. |
| GET | `/api/v1/facturas` | Consulta paginada por cursor (`nit`, `estado` repetible, `venceDesde`, `venceHasta`, `saldoMin`, `saldoMax`, `cursor`, `tamano`). |
| GET | `/api/v1/facturas/{numero}` | Detalle de la factura y sus pagos (aplicados y rechazados). |
| GET | `/api/v1/conciliacion/tablero` | Totales y top 10 de proveedores (`desde`, `hasta`). |

Todos los montos viajan como texto decimal (`"37500483.40"`), nunca como numero JSON.

## Pruebas

Backend (JUnit 5; las de integracion usan Testcontainers y se omiten si no hay Docker):

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
| `BatchReconcilerTest` | CF-01, CF-02, CF-05, CF-06, frontera horaria, duplicados, lineas invalidas, RF-19. |
| `BatchProcessingIntegrationTest` | CF-03, CF-04, reenvio concurrente, CF-08 (paginacion estable), indice unico. |
| `money.test.ts` | Formato colombiano sin perdida de precision (incluye montos > 2^53). |
| `pages.test.tsx` | Vista reconstruida desde la URL, estados vacio/error, doble clic (RF-27). |

## Estructura

```
backend/pagos/        Spring Boot 3.5, Java 21 (hexagonal)
  domain/             Modelo y reglas. Sin Spring ni JDBC.
  application/        Casos de uso y puertos (interfaces). Sin Spring.
  infrastructure/     Adaptadores: JDBC, REST, CSV y el cableado de Spring.
frontend/             React 18 + TypeScript + Vite 6 + Tailwind 3 + RTK Query
legacy/               Importador heredado tal como se entrego (ver DECISIONS.md, D-10)
seed/out/             Datos de prueba
```
