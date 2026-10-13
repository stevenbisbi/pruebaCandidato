import type { FormEvent } from 'react'
import { useSearchInvoicesQuery } from '../api/api'
import { errorMessage } from '../api/errors'
import { INVOICE_QUERY_KEYS, pick } from '../api/query'
import { INVOICE_STATUSES } from '../api/types'
import { Link } from '../components/Link'
import { QueryState } from '../components/QueryState'
import { StatusBadge } from '../components/StatusBadge'
import { Button, INPUT_CLASS, LABEL_CLASS, TD_CLASS, TH_CLASS } from '../components/ui'
import { formatDate, STATUS_LABELS } from '../lib/format'
import { formatCop } from '../lib/money'
import { useUrlParams } from '../lib/router'

const PAGE_SIZES = ['50', '100', '200']
const FILTER_KEYS = ['nit', 'status', 'dueFrom', 'dueTo', 'minBalance', 'maxBalance']
const AMOUNT_PATTERN = '\\d+(\\.\\d{1,2})?'

/**
 * Consulta de facturas (RF-23, RF-25). Los filtros, el tamano de pagina y el cursor viven en la URL
 * con los mismos nombres que usa la API: la consulta se arma directamente desde la URL, asi que
 * pegar el enlace en otra pestana reproduce exactamente la misma vista.
 */
export function InvoicesPage() {
  const [params, setParams] = useUrlParams()
  // Mientras llega la pagina nueva, RTK Query deja en data la anterior: la tabla no salta al paginar.
  const result = useSearchInvoicesQuery(pick(params, INVOICE_QUERY_KEYS).toString())
  const selectedStatuses = params.getAll('status')
  const filtered = FILTER_KEYS.some((filterKey) => params.has(filterKey))

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const text = (name: string) => String(form.get(name) ?? '').trim()
    setParams({
      nit: text('nit'),
      status: form.getAll('status').map(String),
      dueFrom: text('dueFrom'),
      dueTo: text('dueTo'),
      minBalance: text('minBalance'),
      maxBalance: text('maxBalance'),
      size: text('size') === '50' ? null : text('size'),
      cursor: null,
    })
  }

  function clearFilters() {
    setParams(Object.fromEntries([...FILTER_KEYS, 'cursor'].map((filterKey) => [filterKey, null])))
  }

  const invoices = result.data?.items ?? []

  return (
    <div className="space-y-4">
      {/* key: si la URL cambia (atras, adelante, un enlace), el formulario muestra los valores nuevos. */}
      <form key={params.toString()} onSubmit={applyFilters} className="rounded border border-slate-200 bg-white p-4">
        <div className="flex flex-wrap items-end gap-4">
          <label className={LABEL_CLASS}>
            NIT del proveedor
            <input name="nit" defaultValue={params.get('nit') ?? ''} className={`${INPUT_CLASS} w-44`} />
          </label>
          <label className={LABEL_CLASS}>
            Vence desde
            <input type="date" name="dueFrom" defaultValue={params.get('dueFrom') ?? ''} className={INPUT_CLASS} />
          </label>
          <label className={LABEL_CLASS}>
            Vence hasta
            <input type="date" name="dueTo" defaultValue={params.get('dueTo') ?? ''} className={INPUT_CLASS} />
          </label>
          <label className={LABEL_CLASS}>
            Saldo desde
            <input
              name="minBalance"
              inputMode="decimal"
              pattern={AMOUNT_PATTERN}
              title="Un numero con punto decimal, por ejemplo 1000000.50"
              placeholder="0"
              defaultValue={params.get('minBalance') ?? ''}
              className={`${INPUT_CLASS} w-36 font-mono`}
            />
          </label>
          <label className={LABEL_CLASS}>
            Saldo hasta
            <input
              name="maxBalance"
              inputMode="decimal"
              pattern={AMOUNT_PATTERN}
              title="Un numero con punto decimal, por ejemplo 1000000.50"
              defaultValue={params.get('maxBalance') ?? ''}
              className={`${INPUT_CLASS} w-36 font-mono`}
            />
          </label>
          <label className={LABEL_CLASS}>
            Por pagina
            <select name="size" defaultValue={params.get('size') ?? '50'} className={INPUT_CLASS}>
              {PAGE_SIZES.map((size) => (
                <option key={size} value={size}>{size}</option>
              ))}
            </select>
          </label>
        </div>
        <fieldset className={`${LABEL_CLASS} mt-3`}>
          <legend>Estado</legend>
          <div className="mt-1 flex flex-wrap gap-4">
            {INVOICE_STATUSES.map((status) => (
              <label key={status} className="flex items-center gap-1 text-sm text-slate-800">
                <input type="checkbox" name="status" value={status} defaultChecked={selectedStatuses.includes(status)} />
                {STATUS_LABELS[status]}
              </label>
            ))}
          </div>
        </fieldset>
        <div className="mt-4 flex gap-2">
          <Button type="submit" variant="primary">Filtrar</Button>
          <Button onClick={clearFilters} disabled={!filtered}>Limpiar filtros</Button>
        </div>
      </form>

      <QueryState
        isLoading={result.isLoading}
        error={errorMessage(result.error)}
        isEmpty={result.data !== undefined && invoices.length === 0}
        emptyMessage="Ninguna factura cumple los filtros."
        onRetry={result.refetch}
      >
        <div className={`overflow-x-auto rounded border border-slate-200 bg-white ${result.isFetching ? 'opacity-60' : ''}`}>
          <table className="min-w-full text-sm">
            <thead className="bg-slate-100 text-left text-xs uppercase text-slate-600">
              <tr>
                <th className={TH_CLASS}>Numero</th>
                <th className={TH_CLASS}>Proveedor</th>
                <th className={TH_CLASS}>Emision</th>
                <th className={TH_CLASS}>Vencimiento</th>
                <th className={`${TH_CLASS} text-right`}>Valor total</th>
                <th className={`${TH_CLASS} text-right`}>Saldo pendiente</th>
                <th className={TH_CLASS}>Estado</th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((invoice) => (
                <tr key={invoice.number} className="border-t border-slate-100 hover:bg-slate-50">
                  <td className={`${TD_CLASS} font-mono`}>
                    <Link href={`/facturas/${encodeURIComponent(invoice.number)}`} className="text-blue-700 hover:underline">
                      {invoice.number}
                    </Link>
                  </td>
                  <td className={TD_CLASS}>
                    <div>{invoice.supplierName}</div>
                    <div className="text-xs text-slate-500">{invoice.supplierNit}</div>
                  </td>
                  <td className={TD_CLASS}>{formatDate(invoice.issueDate)}</td>
                  <td className={TD_CLASS}>{formatDate(invoice.dueDate)}</td>
                  <td className={`${TD_CLASS} text-right font-mono`}>{formatCop(invoice.totalAmount)}</td>
                  <td className={`${TD_CLASS} text-right font-mono`}>{formatCop(invoice.balance)}</td>
                  <td className={TD_CLASS}><StatusBadge value={invoice.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </QueryState>

      {/*
        Paginacion por cursor (D-03): "Siguiente" pide las facturas despues de la ultima que se ve.
        No hay total ni salto a la pagina N; el boton atras del navegador vuelve a la pagina anterior.
      */}
      <nav className="flex flex-wrap items-center gap-3 text-sm">
        <Button disabled={!params.get('cursor')} onClick={() => setParams({ cursor: null })}>« Primera pagina</Button>
        <Button
          disabled={!result.data?.hasNext || result.isFetching}
          onClick={() => setParams({ cursor: result.data?.nextCursor ?? null })}
        >
          Siguiente »
        </Button>
        {result.data && invoices.length > 0 && (
          <span className="text-slate-600">
            {invoices[0].number} a {invoices[invoices.length - 1].number}
          </span>
        )}
      </nav>
    </div>
  )
}
