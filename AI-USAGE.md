# Uso de inteligencia artificial

> **Nota para el candidato:** el apartado 1 y la primera parte del 2 describen lo que ocurrio en la
> sesion con el asistente. Los marcados con `TODO` solo los puedes escribir tu. Un AI-USAGE generico
> resta (seccion 2.1): completalo con lo que de verdad revisaste y cambiaste antes de entregar.

## 1. Que se genero con asistencia de IA

Herramienta: Claude Code (modelo Claude Opus 5.5), en la aplicacion de escritorio.

- Lectura del enunciado y analisis de los datos semilla (deteccion del BOM en el lote de marzo, de
  las lineas sin fecha en el de abril y de los pagos a facturas inexistentes en el lote de 50.000).
- Backend: dominio, casos de uso, adaptadores JDBC y REST, migracion V3 y la carga de semilla V2.
- Pruebas: caracterizacion del heredado, dominio, lector CSV e integracion con Testcontainers.
- Frontend: las tres pantallas, el manejo de URL, el formato de montos y sus pruebas.
- Dockerfiles, nginx, docker-compose y los documentos README, DECISIONS y LIMITACIONES.

Partes que ya existian antes de usar el asistente: el proyecto Spring Boot inicial, el esquema
`V1__schema.sql` (tablas `supplier` e `invoice` con sus restricciones) y el proyecto Vite con
Redux Toolkit.

## 2. Que se le corrigio a la salida

Correcciones que surgieron al revisar y ejecutar lo generado durante la sesion:

- **Truncamiento silencioso de referencias.** El adaptador de persistencia recortaba la referencia
  a 80 caracteres para que cupiera en la columna. Dos referencias largas distintas con el mismo
  prefijo habrian quedado iguales, y la segunda se habria rechazado como duplicada (o, al reves, el
  indice unico no habria reflejado la referencia real). Se cambio por una validacion en el dominio:
  una referencia demasiado larga se rechaza como FORMATO_INVALIDO.
- **Encabezado vacio.** La verificacion del BOM hacia `header.charAt(0)` sin comprobar que la linea
  tuviera contenido; un archivo cuya primera linea esta vacia habria lanzado una excepcion en vez de
  un 400 con mensaje. Se agrego la guarda.
- **Entorno de pruebas del frontend.** La configuracion propuesta usaba jsdom; las pruebas de las
  pantallas fallaban porque `fetchBaseQuery` construye un `Request` de Node con un `AbortSignal` de
  jsdom. Se cambio a happy-dom.
- **Testcontainers no detectaba Docker 29.** Las pruebas de integracion se saltaban en silencio
  (`disabledWithoutDocker`), lo que habria dado un falso verde. Se fijo la version de la API de
  docker-java y se confirmo que las 7 pruebas corren contra PostgreSQL real.
- **Prueba de contexto por defecto.** `PagosApplicationTests` necesitaba una base levantada y
  fallaba sin ella; se reemplazo por la prueba de integracion con Testcontainers.

TODO (candidato): agrega aqui lo que tu revisaste y cambiaste. Un ejemplo util es concreto: que
propuso la herramienta, por que estaba mal, que pusiste en su lugar.

## 3. Que se escribio sin asistencia y por que

TODO (candidato): por ejemplo el esquema inicial `V1__schema.sql` y las restricciones de saldo y
estado, si las escribiste tu, y por que preferiste hacerlo sin asistencia.
