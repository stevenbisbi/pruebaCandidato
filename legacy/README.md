# Modulo heredado: importador de CSV

Este es el importador que hoy corre en produccion. Se entrega tal como esta.

## Contexto

Escrito en 2022, con dos modificaciones posteriores registradas en el encabezado del archivo.
Su autor ya no esta en la organizacion y el modulo nunca tuvo pruebas automatizadas.

## Sintoma reportado por Cartera

Cada cierto tiempo el importador rechaza un archivo completo que Tesoreria asegura haber generado
correctamente. El operador vuelve a pedir el archivo, Tesoreria lo reenvia y a veces entra y a
veces no. Nadie ha logrado reproducirlo en el ambiente de desarrollo: los archivos de prueba que
se usan alli siempre entran sin problema.

Los dos archivos `lote-tesoreria-*.csv` que trae `seed/out/` son entregas reales de Tesoreria.

## Que se te pide

Esta descrito en la seccion 8 del enunciado. En resumen, y en este orden:

1. Pruebas de caracterizacion que fijen el comportamiento actual, **antes** de modificar nada.
2. La correccion.
3. La explicacion de por que el defecto no se manifestaba en desarrollo, en `DECISIONS.md`.

## Compilar y probar el modulo tal como esta

Las clases estan en el paquete por defecto para que puedas ejecutarlas sin montar un proyecto.
Muevelas al paquete que corresponda cuando las integres.

```bash
cd legacy
javac -encoding UTF-8 *.java
```

Para verlo trabajar sobre un archivo, escribe un `main` corto que llame a
`new ImportadorCsvLegacy().importar(Paths.get("../seed/out/lote-50k.csv"))`.
