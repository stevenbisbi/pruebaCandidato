import { useState, type FormEvent } from 'react'
import { errorMessage, useGetDashboardQuery } from '../api/api'
import { QueryState } from '../components/QueryState'
import { formatCop } from '../lib/money'
import { useSearchParams } from '../lib/url'

/** Tablero de conciliacion (RF-24, RF-30). El rango vive en la URL. */
export function DashboardPage() {
  const [params, setParams] = useSearchParams()
  const from = params.get('desde') ?? ''
  const to = params.get('hasta') ?? ''
  const query = new URLSearchParams()
  if (from) query.set('desde', from)
  if (to) query.set('hasta', to)
  const { data, isFetching, error, refetch } = useGetDashboardQuery(query.toString())

  return (
    <div className="space-y-4">
      <RangeForm key={`${from}|${to}`} from={from} to={to} onApply={(d, h) => setParams({ desde: d, hasta: h })} />
      <p className="text-xs text-slate-500">
        El rango filtra las facturas por fecha de vencimiento y los pagos por fecha de pago (hora de Colombia).
      </p>
      <QueryState isLoading={isFetching && !data} error={errorMessage(error)} isEmpty={!!data && data.cantidadFacturas === 0 && data.cantidadAplicados === 0 && data.cantidadRechazados === 0} emptyMessage="No hay facturas ni pagos en el rango." onRetry={refetch}>
        {data && (
          <div className={`space-y-4 ${isFetching ? 'opacity-60' : ''}`}>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Card label="Total facturado" value={formatCop(data.totalFacturado)} hint={`${data.cantidadFacturas.toLocaleString('es-CO')} facturas`} />
              <Card label="Saldo pendiente" value={formatCop(data.saldoPendiente)} />
              <Card label="Total aplicado" value={formatCop(data.totalAplicado)} hint={`${data.cantidadAplicados.toLocaleString('es-CO')} pagos`} />
              <Card label="Total rechazado" value={formatCop(data.totalRechazado)} hint={`${data.cantidadRechazados.toLocaleString('es-CO')} pagos`} />
            </div>
            <section className="rounded border border-slate-200 bg-white">
              <h2 className="border-b border-slate-100 px-4 py-2 font-semibold">Diez proveedores con mayor saldo pendiente</h2>
              <table className="min-w-full text-sm">
                <tbody>
                  {data.topProveedores.map((p, i) => (
                    <tr key={p.nit} className="border-t border-slate-100">
                      <td className="px-4 py-1.5 text-slate-500">{i + 1}</td>
                      <td className="px-4 py-1.5">{p.razonSocial}</td>
                      <td className="px-4 py-1.5 text-slate-500">{p.nit}</td>
                      <td className="px-4 py-1.5 text-right font-mono">{formatCop(p.saldoPendiente)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          </div>
        )}
      </QueryState>
    </div>
  )
}

function RangeForm({ from, to, onApply }: { from: string; to: string; onApply: (from: string, to: string) => void }) {
  const [draftFrom, setDraftFrom] = useState(from)
  const [draftTo, setDraftTo] = useState(to)
  const invalid = draftFrom !== '' && draftTo !== '' && draftFrom > draftTo
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!invalid) onApply(draftFrom, draftTo)
  }
  return (
    <form onSubmit={submit} className="flex flex-wrap items-end gap-3 rounded border border-slate-200 bg-white p-4">
      <label className="text-xs text-slate-600">
        Desde
        <input type="date" value={draftFrom} onChange={(e) => setDraftFrom(e.target.value)} className="mt-1 block rounded border border-slate-300 px-2 py-1 text-sm" />
      </label>
      <label className="text-xs text-slate-600">
        Hasta
        <input type="date" value={draftTo} onChange={(e) => setDraftTo(e.target.value)} className="mt-1 block rounded border border-slate-300 px-2 py-1 text-sm" />
      </label>
      <button type="submit" disabled={invalid} className="rounded bg-blue-700 px-4 py-1.5 text-sm font-medium text-white disabled:opacity-40">
        Aplicar
      </button>
      {invalid && <p className="text-xs text-red-700">La fecha inicial es posterior a la final.</p>}
    </form>
  )
}

function Card({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded border border-slate-200 bg-white p-4">
      <div className="text-xs uppercase text-slate-500">{label}</div>
      <div className="mt-1 font-mono text-lg">{value}</div>
      {hint && <div className="text-xs text-slate-500">{hint}</div>}
    </div>
  )
}
