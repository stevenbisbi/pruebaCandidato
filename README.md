# Repositorio semilla - Sistema de Conciliacion de Pagos

Punto de partida de la prueba tecnica. Trae la infraestructura y los datos; el codigo de
aplicacion lo escribes tu.

Lee primero el enunciado en Word. Este archivo solo explica que hay aca y como arrancar.

## Que incluye

| Ruta | Contenido |
|---|---|
| `docker-compose.yml` | PostgreSQL 16 con healthcheck. |
| `seed/out/` | Los datos de prueba, ya generados. |
| `legacy/` | El importador de CSV que hoy corre en produccion. Ver la seccion 8 del enunciado. |
| `backend/` | Vacio. Tu backend va aca. |
| `frontend/` | Vacio. Tu frontend va aca. |

## Los datos

Vienen listos en `seed/out/`. No hay que generarlos.

| Archivo | Contenido |
|---|---|
| `facturas.csv` | 500.000 facturas sobre 8.000 proveedores. ~41 MB. |
| `lote-50k.csv` | Lote de 50.000 lineas de pago, para el presupuesto PR-02. |
| `lote-tesoreria-marzo.csv` | Archivo real entregado por Tesoreria. |
| `lote-tesoreria-abril.csv` | Archivo real entregado por Tesoreria. |
| `casos/cf-*.csv` | Los casos frontera publicados en el Anexo B del enunciado. |

Son archivos de datos, no codigo: **versionalos junto con tu solucion.** La evaluacion parte de un
clon limpio de tu repositorio, y el requisito 6.2 del enunciado dice que ese clon debe quedar
operativo y con datos con un solo comando. Si prefieres no versionar los 41 MB, es una decision
valida, pero entonces documenta en tu README como obtenerlos.

## Puesta en marcha

**Levantar la base:**

```bash
docker compose up -d db
```

Queda en `localhost:5432`, base `conciliacion`, usuario y clave `conciliacion`. El contenido de
`seed/out/` queda montado dentro del contenedor en `/seed`.

**El resto es tuyo.** El esquema de base de datos, la carga de los CSV, el backend y el
frontend los disenas tu. Agrega los servicios que necesites a `docker-compose.yml`: al terminar,
un `docker compose up` sobre un clon limpio debe dejar el sistema operativo y con datos.

## Formato de los archivos

Facturas:

```
numero;nit;razon_social;fecha_emision;fecha_vencimiento;valor_total
FV-0000001;800000000-1;Suministros Andina S.A.S.;2026-01-15;2026-03-15;1250000.00
```

Pagos:

```
referencia;numero_factura;valor;fecha_pago
P-000001;FV-0042199;37500483;2026-03-15T23:30:00-05:00
```

## Notas

- El `docker-compose.yml` no fija zona horaria en ningun servicio: los contenedores corren en UTC,
  como en el ambiente real descrito en la seccion 4 del enunciado.
- No se entrega ningun esquema de base de datos. El modelo de datos lo disenas tu.
- Puedes reorganizar la estructura de directorios si tu solucion lo pide. Solo documentalo.
