import { useState, type FormEvent } from 'react'
import { errorMessage, useGetInvoiceQuery, useSearchInvoicesQuery } from '../api/api'
import { INVOICE_STATUSES, type InvoiceStatus } from '../api/types'
import { QueryState } from '../components/QueryState'
import { StatusBadge } from '../components/StatusBadge'
import { formatCop, isDecimalInput } from '../lib/money'
import { useSearchParams } from '../lib/url'

const FILTER_KEYS = ['nit', 'venceDesde', 'venceHasta', 'saldoMin', 'saldoMax'] as const
type FilterKey = (typeof FILTER_KEYS)[number]
type Draft = Record<FilterKey, string> & { estado: InvoiceStatus[] }

const LABELS: Record<FilterKey, string> = {
  nit: 'NIT proveedor',
  venceDesde: 'Vence desde',
  venceHasta: 'Vence hasta',
  saldoMin: 'Saldo minimo',
  saldoMax: 'Saldo maximo',
}

function draftFrom(params: URLSearchParams): Draft {
  const draft = { estado: params.getAll('estado') as InvoiceStatus[] } as Draft
  FILTER_KEYS.forEach((k) => (draft[k] = params.get(k) ?? ''))
  return draft
}

/** Pantalla de facturas (RF-25). Filtros, cursor y factura abierta viven en la URL. */
export function InvoicesPage() {
  const [params, setParams] = useSearchParams()
  const apiParams = new URLSearchParams(params)
  apiParams.delete('factura')
  apiParams.delete('pagina')
  const page = Number(params.get('pagina') ?? '1')
  const { data, isFetching, error, refetch } = useSearchInvoicesQuery(apiParams.toString())
  const selected = params.get('factura')

  return (
    <div className="space-y-4">
      <Filters key={params.toString()} initial={draftFrom(params)} onApply={(d) => setParams({ ...d, cursor: null, pagina: null, factura: null })} />

      <QueryState
        isLoading={isFetching && !data}
        error={errorMessage(error)}
        isEmpty={!!data && data.facturas.length === 0}
        emptyMessage="Ninguna factura cumple los filtros."
        onRetry={refetch}
      >
        <div className={`overflow-x-auto rounded border border-slate-200 bg-white ${isFetching ? 'opacity-60' : ''}`}>
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
              {data?.facturas.map((f) => (
                <tr key={f.numero} className="border-t border-slate-100 hover:bg-slate-50">
                  <td className="px-3 py-2">
                    <button type="button" className="font-mono text-blue-700 underline" onClick={() => setParams({ factura: f.numero })}>
                      {f.numero}
                    </button>
                  </td>
                  <td className="px-3 py-2">
                    <div>{f.razonSocial}</div>
                    <div className="text-xs text-slate-500">{f.nit}</div>
                  </td>
                  <td className="px-3 py-2">{f.fechaVencimiento}</td>
                  <td className="px-3 py-2 text-right font-mono">{formatCop(f.valorTotal)}</td>
                  <td className="px-3 py-2 text-right font-mono">{formatCop(f.saldoPendiente)}</td>
                  <td className="px-3 py-2"><StatusBadge value={f.estado} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </QueryState>

      <nav className="flex items-center gap-3 text-sm">
        <button
          type="button"
          disabled={!params.get('cursor')}
          onClick={() => setParams({ cursor: null, pagina: null })}
          className="rounded border border-slate-300 bg-white px-3 py-1 disabled:opacity-40"
        >
          « Primera
        </button>
        <span className="text-slate-600">Pagina {page}</span>
        <button
          type="button"
          disabled={!data?.siguienteCursor || isFetching}
          onClick={() => setParams({ cursor: data?.siguienteCursor ?? null, pagina: String(page + 1) })}
          className="rounded border border-slate-300 bg-white px-3 py-1 disabled:opacity-40"
        >
          Siguiente »
        </button>
      </nav>

      {selected && <InvoiceDetailPanel number={selected} onClose={() => setParams({ factura: null })} />}
    </div>
  )
}

function Filters({ initial, onApply }: { initial: Draft; onApply: (d: Draft) => void }) {
  const [draft, setDraft] = useState<Draft>(initial)
  const invalidAmount = [draft.saldoMin, draft.saldoMax].some((v) => v !== '' && !isDecimalInput(v))

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!invalidAmount) {
      onApply(draft)
    }
  }

  const toggleStatus = (s: InvoiceStatus) =>
    setDraft((d) => ({ ...d, estado: d.estado.includes(s) ? d.estado.filter((x) => x !== s) : [...d.estado, s] }))

  return (
    <form onSubmit={submit} className="grid gap-3 rounded border border-slate-200 bg-white p-4 sm:grid-cols-3 lg:grid-cols-6">
      {FILTER_KEYS.map((k) => (
        <label key={k} className="text-xs text-slate-600">
          {LABELS[k]}
          <input
            type={k.startsWith('vence') ? 'date' : 'text'}
            inputMode={k.startsWith('saldo') ? 'decimal' : undefined}
            value={draft[k]}
            onChange={(e) => setDraft((d) => ({ ...d, [k]: e.target.value }))}
            className="mt-1 block w-full rounded border border-slate-300 px-2 py-1 text-sm text-slate-900"
          />
        </label>
      ))}
      <fieldset className="text-xs text-slate-600 sm:col-span-3 lg:col-span-5">
        <legend>Estado</legend>
        <div className="mt-1 flex flex-wrap gap-3">
          {INVOICE_STATUSES.map((s) => (
            <label key={s} className="flex items-center gap-1 text-sm text-slate-800">
              <input type="checkbox" checked={draft.estado.includes(s)} onChange={() => toggleStatus(s)} />
              {s.replace('_', ' ')}
            </label>
          ))}
        </div>
      </fieldset>
      <div className="flex items-end gap-2">
        <button type="submit" disabled={invalidAmount} className="rounded bg-blue-700 px-4 py-1.5 text-sm font-medium text-white disabled:opacity-40">
          Filtrar
        </button>
      </div>
      {invalidAmount && <p className="text-xs text-red-700 sm:col-span-3 lg:col-span-6">Los saldos usan punto decimal y hasta dos decimales, por ejemplo 1500000.50</p>}
    </form>
  )
}

function InvoiceDetailPanel({ number, onClose }: { number: string; onClose: () => void }) {
  const { data, isFetching, error, refetch } = useGetInvoiceQuery(number)
  return (
    <section className="rounded border border-slate-200 bg-white p-4">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-semibold">Factura {number}</h2>
        <button type="button" onClick={onClose} className="text-sm text-slate-600 underline">Cerrar</button>
      </div>
      <QueryState isLoading={isFetching && !data} error={errorMessage(error)} isEmpty={false} onRetry={refetch}>
        {data && (
          <>
            <dl className="mb-3 grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
              <div><dt className="text-slate-500">Emision</dt><dd>{data.factura.fechaEmision}</dd></div>
              <div><dt className="text-slate-500">Vencimiento</dt><dd>{data.factura.fechaVencimiento}</dd></div>
              <div><dt className="text-slate-500">Valor total</dt><dd className="font-mono">{formatCop(data.factura.valorTotal)}</dd></div>
              <div><dt className="text-slate-500">Saldo</dt><dd className="font-mono">{formatCop(data.factura.saldoPendiente)}</dd></div>
            </dl>
            {data.pagos.length === 0 ? (
              <p className="text-sm text-slate-500">Sin pagos registrados.</p>
            ) : (
              <table className="min-w-full text-sm">
                <thead className="text-left text-xs uppercase text-slate-500">
                  <tr><th className="py-1">Referencia</th><th>Fecha pago</th><th className="text-right">Valor</th><th>Resultado</th><th>Motivo</th><th className="text-right">Saldo posterior</th></tr>
                </thead>
                <tbody>
                  {data.pagos.map((p) => (
                    <tr key={`${p.referencia}-${p.linea}`} className="border-t border-slate-100">
                      <td className="py-1 font-mono">{p.referencia}</td>
                      <td>{p.fechaPago ?? '—'}</td>
                      <td className="text-right font-mono">{formatCop(p.valor)}</td>
                      <td><StatusBadge value={p.resultado} /></td>
                      <td>{p.descripcionMotivo ?? ''}</td>
                      <td className="text-right font-mono">{formatCop(p.saldoPosterior)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </>
        )}
      </QueryState>
    </section>
  )
}
