import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import { API_BASE, withQuery } from './query'
import type { BatchSummary, CursorPage, Dashboard, Invoice, InvoiceDetail, LineResult, Page } from './types'

/**
 * Llamadas al backend con RTK Query (Redux Toolkit). Cada endpoint genera un hook, por ejemplo
 * useSearchInvoicesQuery, que devuelve { data, currentData, isLoading, isFetching, error, refetch }.
 *
 * Que aporta frente a llamar a fetch a mano:
 * - Cache por argumento: volver a una pagina o a una factura ya vista no repite la consulta.
 * - Invalidacion: procesar un lote cambia saldos y estados, asi que marca como viejas las facturas,
 *   sus detalles y el tablero; las pantallas que los muestran se vuelven a consultar solas.
 * - Si dos componentes piden lo mismo a la vez, se hace una sola peticion.
 *
 * Las consultas reciben el query string ya armado desde la URL (RF-25): la URL es el estado de la
 * pantalla y el store solo guarda la cache de las respuestas.
 */
export const api = createApi({
  reducerPath: 'api',
  baseQuery: fetchBaseQuery({ baseUrl: API_BASE }),
  tagTypes: ['Invoices', 'InvoiceDetail', 'Dashboard'],
  endpoints: (build) => ({
    // GET /invoices?nit=...&status=...&cursor=...
    searchInvoices: build.query<CursorPage<Invoice>, string>({
      query: (queryString) => withQuery('/invoices', queryString),
      providesTags: ['Invoices'],
    }),
    // GET /invoices/{numero}
    getInvoice: build.query<InvoiceDetail, string>({
      query: (number) => `/invoices/${encodeURIComponent(number)}`,
      providesTags: (_result, _error, number) => [{ type: 'InvoiceDetail', id: number }],
    }),
    // GET /batches/{id}. El resultado de un lote no cambia despues de procesarse: no se invalida.
    getBatch: build.query<BatchSummary, string>({
      query: (id) => `/batches/${encodeURIComponent(id)}`,
    }),
    // GET /batches/{id}/lines?outcome=...&reason=...&page=...&size=...
    getBatchLines: build.query<Page<LineResult>, { id: string; queryString: string }>({
      query: ({ id, queryString }) => withQuery(`/batches/${encodeURIComponent(id)}/lines`, queryString),
    }),
    // GET /conciliation?dueFrom=...&dueTo=...
    getDashboard: build.query<Dashboard, string>({
      query: (queryString) => withQuery('/conciliation', queryString),
      providesTags: ['Dashboard'],
    }),
    // POST /batches/file. 201 si se proceso ahora, 200 si era un reenvio (replayed).
    uploadCsv: build.mutation<BatchSummary, { file: File; id?: string }>({
      query: ({ file, id }) => {
        const form = new FormData()
        form.append('file', file)
        if (id && id.trim() !== '') {
          form.append('id', id.trim())
        }
        return { url: '/batches/file', method: 'POST', body: form }
      },
      invalidatesTags: ['Invoices', 'InvoiceDetail', 'Dashboard'],
    }),
    // POST /batches con el lote en el formato del Anexo A. RTK Query lo envia como JSON.
    submitJsonBatch: build.mutation<BatchSummary, unknown>({
      query: (batch) => ({ url: '/batches', method: 'POST', body: batch }),
      invalidatesTags: ['Invoices', 'InvoiceDetail', 'Dashboard'],
    }),
  }),
})

export const {
  useSearchInvoicesQuery,
  useGetInvoiceQuery,
  useGetBatchQuery,
  useGetBatchLinesQuery,
  useGetDashboardQuery,
  useUploadCsvMutation,
  useSubmitJsonBatchMutation,
} = api
