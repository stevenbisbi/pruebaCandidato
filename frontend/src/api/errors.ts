import type { ApiErrorBody } from './types'

/**
 * Convierte el error de una llamada de RTK Query en un texto para mostrar al usuario.
 *
 * RTK Query entrega { status, data }: status es el codigo HTTP, o 'FETCH_ERROR' si no hubo conexion,
 * y data es el cuerpo de la respuesta. El backend responde los errores como { status, error, message }.
 */
export function errorMessage(error: unknown): string {
  if (!error) {
    return ''
  }
  const { status: rawStatus, originalStatus, data } = error as { status?: unknown; originalStatus?: unknown; data?: unknown }
  const message = (data as Partial<ApiErrorBody> | undefined)?.message
  if (typeof message === 'string' && message !== '') {
    return message
  }
  if (rawStatus === 'FETCH_ERROR') {
    return 'No fue posible conectar con el servidor.'
  }
  // Si el cuerpo no era JSON, RTK Query responde 'PARSING_ERROR' y guarda el codigo real en originalStatus.
  const status = rawStatus === 'PARSING_ERROR' ? originalStatus : rawStatus
  // nginx responde 502 con una pagina HTML mientras el backend arranca (la siembra tarda 1-2 minutos).
  if (status === 502 || status === 503) {
    return 'El servidor todavia no esta disponible. Si el sistema acaba de arrancar, espere uno o dos minutos.'
  }
  if (status === 404) {
    return 'No se encontro lo que se buscaba.'
  }
  return typeof status === 'number' ? `Ocurrio un error inesperado (${status}).` : 'Ocurrio un error inesperado.'
}
