import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Importador de archivos de pago entregados por Tesoreria.
 *
 * En produccion desde 2022. Formato acordado con Tesoreria:
 *   referencia;numero_factura;valor;fecha_pago
 *
 * Historial de cambios:
 *   2022-04  version inicial
 *   2022-11  se agrega validacion de encabezado tras un incidente de formato
 *   2023-06  se sube el limite de lineas de 10.000 a 100.000
 */
public class ImportadorCsvLegacy {

    private static final String SEPARADOR = ";";
    private static final int COLUMNAS = 4;
    private static final int MAX_LINEAS = 100_000;

    private static final String[] ENCABEZADO_ESPERADO = {
        "referencia", "numero_factura", "valor", "fecha_pago"
    };

    /**
     * Lee el archivo completo y devuelve las lineas de pago que contiene.
     * Si el archivo no cumple el formato acordado, se rechaza entero.
     */
    public ResultadoImportacion importar(Path archivo) {
        List<LineaPago> pagos = new ArrayList<LineaPago>();
        try {
            Stream<String> flujo = Files.lines(archivo, StandardCharsets.UTF_8);
            List<String> lineas = flujo.collect(Collectors.toList());

            if (lineas.isEmpty()) {
                return ResultadoImportacion.rechazado("El archivo esta vacio");
            }
            if (lineas.size() - 1 > MAX_LINEAS) {
                return ResultadoImportacion.rechazado(
                    "El archivo supera el limite de " + MAX_LINEAS + " lineas");
            }

            String[] encabezado = lineas.get(0).split(SEPARADOR);
            if (!validarEncabezado(encabezado)) {
                return ResultadoImportacion.rechazado(
                    "Encabezado invalido. Se esperaba: " + String.join(SEPARADOR, ENCABEZADO_ESPERADO));
            }

            for (int i = 1; i < lineas.size(); i++) {
                String linea = lineas.get(i);
                if (linea.trim().isEmpty()) {
                    continue;
                }
                String[] campos = linea.split(SEPARADOR);
                LineaPago pago = new LineaPago(
                    campos[0].trim(),
                    campos[1].trim(),
                    Long.parseLong(campos[2].trim()),
                    OffsetDateTime.parse(campos[3].trim()));
                pagos.add(pago);
            }
        } catch (IOException e) {
            return ResultadoImportacion.rechazado("No se pudo leer el archivo: " + e.getMessage());
        } catch (RuntimeException e) {
            return ResultadoImportacion.rechazado(
                "Error procesando el archivo: " + e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : " - " + e.getMessage()));
        }
        return ResultadoImportacion.aceptado(pagos);
    }

    private boolean validarEncabezado(String[] encabezado) {
        if (encabezado.length != COLUMNAS) {
            return false;
        }
        for (int i = 0; i < COLUMNAS; i++) {
            if (!encabezado[i].trim().equals(ENCABEZADO_ESPERADO[i])) {
                return false;
            }
        }
        return true;
    }
}
