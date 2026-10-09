import java.util.Collections;
import java.util.List;

/** Resultado de leer un archivo de pagos: o se acepta entero, o se rechaza entero. */
public class ResultadoImportacion {

    private final boolean aceptado;
    private final String motivoRechazo;
    private final List<LineaPago> pagos;

    private ResultadoImportacion(boolean aceptado, String motivoRechazo, List<LineaPago> pagos) {
        this.aceptado = aceptado;
        this.motivoRechazo = motivoRechazo;
        this.pagos = pagos;
    }

    public static ResultadoImportacion aceptado(List<LineaPago> pagos) {
        return new ResultadoImportacion(true, null, pagos);
    }

    public static ResultadoImportacion rechazado(String motivo) {
        return new ResultadoImportacion(false, motivo, Collections.<LineaPago>emptyList());
    }

    public boolean fueAceptado() {
        return aceptado;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public List<LineaPago> getPagos() {
        return pagos;
    }
}
