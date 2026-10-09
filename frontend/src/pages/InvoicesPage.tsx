import type { FormEvent } from 'react'
import { errorMessage, useSearchInvoicesQuery } from '../api/api'
import { INVOICE_STATUSES } from '../api/types'
import { QueryState } from '../components/QueryState'
import { StatusBadge } from '../components/StatusBadge'
import { formatCop } from '../lib/money'
import { useUrlParams } from '../lib/url'

/**
 * Pantalla de facturas (RF-23, RF-25). Los filtros y la pagina se guardan en la URL, y la
 * consulta al backend usa exactamente esos mismos parametros.
 */
export function InvoicesPage() {
  const [params, setParams] = useUrlParams()
  const { data, isFetching, error, refetch } = useSearchInvoicesQuery(params.toString())
  const selectedStatuses = params.getAll('estado')

  // Al pulsar "Filtrar" se leen los campos del formulario y se escriben en la URL.
  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setParams({
      nit: String(form.get('nit') ?? ''),
      estado: form.getAll('estado').map(String),
      cursor: null,
    })
  }

  return (
    <div className="space-y-4">
      {/* key: si la URL cambia (por ejemplo con el boton atras), el formulario muestra los valores nuevos */}
      <form key={params.toString()} onSubmit={applyFilters} className="flex flex-wrap items-end gap-4 rounded border border-slate-200 bg-white p-4">
        <label className="text-xs text-slate-600">
          NIT proveedor
          <input
            name="nit"
            defaultValue={params.get('nit') ?? ''}
            className="mt-1 block w-48 rounded border border-slate-300 px-2 py-1 text-sm text-slate-900"
          />
        </label>
        <fieldset className="text-xs text-slate-600">
          <legend>Estado</legend>
          <div className="mt-1 flex flex-wrap gap-3">
            {INVOICE_STATUSES.map((status) => (
              <label key={status} className="flex items-center gap-1 text-sm text-slate-800">
                <input type="checkbox" name="estado" value={status} defaultChecked={selectedStatuses.includes(status)} />
                {status.replace('_', ' ')}
              </label>
            ))}
          </div>
        </fieldset>
        <button type="submit" className="rounded bg-blue-700 px-4 py-1.5 text-sm font-medium text-white">
          Filtrar
        </button>
      </form>

      <QueryState
        isLoading={isFetching && !data}
        error={errorMessage(error)}
        isEmpty={data !== undefined && data.facturas.length === 0}
        emptyMessage="Ninguna factura cumple los filtros."
        onRetry={refetch}
      >
        <div className="overflow-x-auto rounded border border-slate-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-100 text-left text-xs uppercase text-slate-600">
              <tr>
                <th className="px-3 py-2">Numero</th>
                <th className="px-3 py-2">Proveedor</th>
                <th className="px-3 py-2">Vencimiento</th>
                <th className="px-3 py-2 text-right">Valor total</th>
                <th className="px-3 py-2 text-right">Saldo pendiente</th>
                <th className="px-3 py-2">Estado</th>
              </tr>
            </thead>
            <tbody>
              {data?.facturas.map((invoice) => (
                <tr key={invoice.numero} className="border-t border-slate-100">
                  <td className="px-3 py-2 font-mono">{invoice.numero}</td>
                  <td className="px-3 py-2">
                    <div>{invoice.razonSocial}</div>
                    <div className="text-xs text-slate-500">{invoice.nit}</div>
                  </td>
                  <td className="px-3 py-2">{invoice.fechaVencimiento}</td>
                  <td className="px-3 py-2 text-right font-mono">{formatCop(invoice.valorTotal)}</td>
                  <td className="px-3 py-2 text-right font-mono">{formatCop(invoice.saldoPendiente)}</td>
                  <td className="px-3 py-2"><StatusBadge value={invoice.estado} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </QueryState>

      {/* Paginacion por cursor: "Siguiente" pide las facturas despues de la ultima que se ve (PR-03). */}
      <nav className="flex gap-3 text-sm">
        <button
          type="button"
          disabled={!params.get('cursor')}
          onClick={() => setParams({ cursor: null })}
          className="rounded border border-slate-300 bg-white px-3 py-1 disabled:opacity-40"
        >
          « Primera pagina
        </button>
        <button
          type="button"
          disabled={!data?.siguienteCursor || isFetching}
          onClick={() => setParams({ cursor: data?.siguienteCursor ?? null })}
          className="rounded border border-slate-300 bg-white px-3 py-1 disabled:opacity-40"
        >
          Siguiente »
        </button>
      </nav>
    </div>
  )
}
