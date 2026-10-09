import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import type { Batch, BatchLinePage, InvoicePage } from './types'

/**
 * Llamadas al backend con RTK Query (Redux Toolkit). Cada endpoint genera un hook, por ejemplo
 * useSearchInvoicesQuery, que devuelve { data, isFetching, error } y guarda el resultado en cache.
 */
export const api = createApi({
  reducerPath: 'api',
  baseQuery: fetchBaseQuery({ baseUrl: '/api/v1' }),
  tagTypes: ['Invoices'],
  endpoints: (build) => ({
    // GET /facturas?nit=...&estado=...&cursor=...
    searchInvoices: build.query<InvoicePage, string>({
      query: (params) => `/facturas?${params}`,
      providesTags: ['Invoices'],
    }),
    // GET /lotes/{id}
    getBatch: build.query<Batch, string>({
      query: (id) => `/lotes/${encodeURIComponent(id)}`,
    }),
    // GET /lotes/{id}/lineas?pagina=...
    getBatchLines: build.query<BatchLinePage, { id: string; page: number }>({
      query: ({ id, page }) => `/lotes/${encodeURIComponent(id)}/lineas?pagina=${page}&tamano=100`,
    }),
    // POST /lotes/archivo. Al terminar, las facturas en cache quedan viejas y se vuelven a pedir.
    uploadCsv: build.mutation<Batch, File>({
      query: (file) => {
        const form = new FormData()
        form.append('archivo', file)
        return { url: '/lotes/archivo', method: 'POST', body: form }
      },
      invalidatesTags: ['Invoices'],
    }),
  }),
})

export const { useSearchInvoicesQuery, useGetBatchQuery, useGetBatchLinesQuery, useUploadCsvMutation } = api

/** Convierte el error de una llamada en un texto para mostrar al usuario. */
export function errorMessage(error: unknown): string {
  if (!error) {
    return ''
  }
  // El backend responde los errores como { codigo, mensaje }.
  const e = error as { status?: unknown; data?: { mensaje?: string } }
  if (e.data?.mensaje) {
    return e.data.mensaje
  }
  if (e.status === 'FETCH_ERROR') {
    return 'No fue posible conectar con el servidor.'
  }
  return 'Ocurrio un error inesperado.'
}
