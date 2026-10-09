import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import type { FetchBaseQueryError } from '@reduxjs/toolkit/query'
import type { SerializedError } from '@reduxjs/toolkit'
import type { ApiError, Batch, BatchLinePage, Dashboard, InvoiceDetail, InvoicePage } from './types'

export const api = createApi({
  reducerPath: 'api',
  baseQuery: fetchBaseQuery({ baseUrl: '/api/v1' }),
  tagTypes: ['Invoices', 'Dashboard'],
  endpoints: (build) => ({
    searchInvoices: build.query<InvoicePage, string>({
      query: (params) => `/facturas?${params}`,
      providesTags: ['Invoices'],
    }),
    getInvoice: build.query<InvoiceDetail, string>({
      query: (number) => `/facturas/${encodeURIComponent(number)}`,
      providesTags: ['Invoices'],
    }),
    getDashboard: build.query<Dashboard, string>({
      query: (params) => `/conciliacion/tablero?${params}`,
      providesTags: ['Dashboard'],
    }),
    getBatch: build.query<Batch, string>({
      query: (id) => `/lotes/${encodeURIComponent(id)}`,
    }),
    getBatchLines: build.query<BatchLinePage, { id: string; params: string }>({
      query: ({ id, params }) => `/lotes/${encodeURIComponent(id)}/lineas?${params}`,
    }),
    submitJsonBatch: build.mutation<Batch, unknown>({
      query: (body) => ({ url: '/lotes', method: 'POST', body }),
      invalidatesTags: ['Invoices', 'Dashboard'],
    }),
    submitCsvBatch: build.mutation<Batch, File>({
      query: (file) => {
        const form = new FormData()
        form.append('archivo', file)
        return { url: '/lotes/archivo', method: 'POST', body: form }
      },
      invalidatesTags: ['Invoices', 'Dashboard'],
    }),
  }),
})

export const {
  useSearchInvoicesQuery,
  useGetInvoiceQuery,
  useGetDashboardQuery,
  useGetBatchQuery,
  useGetBatchLinesQuery,
  useSubmitJsonBatchMutation,
  useSubmitCsvBatchMutation,
} = api

function isApiError(data: unknown): data is ApiError {
  return typeof data === 'object' && data !== null && 'mensaje' in data
}

/** Mensaje legible para cualquier error de RTK Query. */
export function errorMessage(error: FetchBaseQueryError | SerializedError | undefined): string {
  if (!error) {
    return ''
  }
  if ('status' in error) {
    if (isApiError(error.data)) {
      return error.data.mensaje
    }
    if (error.status === 'FETCH_ERROR') {
      return 'No fue posible conectar con el servidor.'
    }
    return `Error del servidor (${String(error.status)}).`
  }
  return error.message ?? 'Error inesperado.'
}
