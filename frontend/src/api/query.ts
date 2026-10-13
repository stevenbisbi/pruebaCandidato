/** Prefijo de la API. En Docker nginx lo redirige al backend; en desarrollo lo hace Vite (vite.config.ts). */
export const API_BASE = '/api/v1'

/** Filtros de la consulta de facturas (RF-23). Tienen el mismo nombre en la URL de la pantalla y en la API. */
export const INVOICE_FILTER_KEYS = ['nit', 'status', 'dueFrom', 'dueTo', 'minBalance', 'maxBalance'] as const

/** Los filtros mas la paginacion: tamano, cursor (pagina siguiente) y numero de pagina (salto directo). */
export const INVOICE_QUERY_KEYS = [...INVOICE_FILTER_KEYS, 'size', 'cursor', 'page'] as const

/** Copia de params solo los parametros que entiende un endpoint, para no enviar basura de la URL. */
export function pick(params: URLSearchParams, keys: readonly string[]): URLSearchParams {
  const result = new URLSearchParams()
  for (const key of keys) {
    for (const value of params.getAll(key)) {
      if (value.trim() !== '') {
        result.append(key, value.trim())
      }
    }
  }
  return result
}

/** Une una ruta con su query string, sin dejar un "?" suelto si no hay parametros. */
export function withQuery(path: string, query: string): string {
  return query === '' ? path : `${path}?${query}`
}

/** URL del CSV con el resultado completo: el navegador lo descarga por su cuenta (RF-22). */
export function batchDownloadUrl(id: string): string {
  return `${API_BASE}/batches/${encodeURIComponent(id)}/download`
}
