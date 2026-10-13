import { useEffect, type FormEvent } from 'react'
import { useGetDashboardQuery } from '../api/api'
import { errorMessage } from '../api/errors'
import { pick } from '../api/query'
import { Link } from '../components/Link'
import { QueryState } from '../components/QueryState'
import { Button, Card, INPUT_CLASS, LABEL_CLASS, Stat, TD_CLASS, TH_CLASS } from '../components/ui'
import { currentMonthRange, formatCount, formatDate } from '../lib/format'
import { formatCop } from '../lib/money'
import { navigate, useUrlParams } from '../lib/router'

/**
 * Tablero de conciliacion (RF-24, RF-30). El rango es de fechas de vencimiento, el mismo filtro de la
 * consulta de facturas (A-08), y vive en la URL como el resto de los filtros.
 */
export function DashboardPage() {
  const [params, setParams] = useUrlParams()
  const dueFrom = params.get('dueFrom') ?? ''
  const dueTo = params.get('dueTo') ?? ''
  // Sin rango en la URL se abre con el mes actual (D-07): sumar las 500.000 facturas es lo mas lento,
  // asi que "todas" se pide de forma explicita con ?range=all.
  const needsDefaultRange = !dueFrom && !dueTo && params.get('range') !== 'all'

  useEffect(() => {
    if (needsDefaultRange) {
      const { from, to } = currentMonthRange()
      // replace: el atras del navegador no debe volver a la URL sin rango.
      navigate(`/tablero?dueFrom=${from}&dueTo=${to}`, { replace: true })
    }
  }, [needsDefaultRange])

  // skip: mientras se completa el rango por defecto no se consulta, para no pedir las 500.000 facturas sin querer.
  const result = useGetDashboardQuery(pick(params, ['dueFrom', 'dueTo']).toString(), { skip: needsDefaultRange })

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const from = String(form.get('dueFrom') ?? '')
    const to = String(form.get('dueTo') ?? '')
    setParams({ dueFrom: from, dueTo: to, range: from || to ? null : 'all' })
  }

  function thisMonth() {
    const { from, to } = currentMonthRange()
    setParams({ dueFrom: from, dueTo: to, range: null })
  }

  /** Lleva a la consulta de facturas con saldo del proveedor, en el mismo rango. */
  function supplierLink(nit: string): string {
    const target = new URLSearchParams({ nit })
    target.append('status', 'PENDIENTE')
    target.append('status', 'PARCIAL')
    if (dueFrom) {
      target.set('dueFrom', dueFrom)
    }
    if (dueTo) {
      target.set('dueTo', dueTo)
    }
    return `/facturas?${target.toString()}`
  }

  const d = result.data
  const rangeParts = [dueFrom && `desde ${formatDate(dueFrom)}`, dueTo && `hasta ${formatDate(dueTo)}`].filter(Boolean)
  const rangeText = rangeParts.length === 0 ? 'Todas las facturas' : `Facturas con vencimiento ${rangeParts.join(' ')}`

  return (
    <div className="space-y-4">
      <form key={params.toString()} onSubmit={apply} className="flex flex-wrap items-end gap-4 rounded border border-slate-200 bg-white p-4">
        <label className={LABEL_CLASS}>
          Vence desde
          <input type="date" name="dueFrom" defaultValue={dueFrom} className={INPUT_CLASS} />
        </label>
        <label className={LABEL_CLASS}>
          Vence hasta
          <input type="date" name="dueTo" defaultValue={dueTo} className={INPUT_CLASS} />
        </label>
        <Button type="submit" variant="primary">Aplicar</Button>
        <Button onClick={thisMonth}>Este mes</Button>
        <Button onClick={() => setParams({ dueFrom: null, dueTo: null, range: 'all' })} disabled={!dueFrom && !dueTo}>
          Todas
        </Button>
      </form>

      <QueryState isLoading={result.isLoading || needsDefaultRange} error={errorMessage(result.error)} isEmpty={false} onRetry={result.refetch} loadingMessage="Calculando el tablero…">
        {d && (
          <div className={`space-y-4 ${result.isFetching ? 'opacity-60' : ''}`}>
            <p className="text-sm text-slate-600">{rangeText}</p>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Stat label="Total facturado" value={formatCop(d.totalInvoiced)} hint={`${formatCount(d.invoiceCount)} facturas`} />
              <Stat label="Total aplicado" value={formatCop(d.totalApplied)} hint={`${formatCount(d.appliedCount)} pagos`} />
              <Stat
                label="Total rechazado"
                value={formatCop(d.totalRejected)}
                hint={`${formatCount(d.rejectedCount)} pagos (sin contar los dirigidos a facturas inexistentes)`}
              />
              <Stat label="Saldo pendiente" value={formatCop(d.totalPending)} />
            </div>

            <Card title="Diez proveedores con mayor saldo pendiente">
              {d.topSuppliers.length === 0 ? (
                <p className="text-sm text-slate-500">Ningun proveedor tiene saldo pendiente en este rango.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="min-w-full text-sm">
                    <thead className="bg-slate-100 text-left text-xs uppercase text-slate-600">
                      <tr>
                        <th className={TH_CLASS}>#</th>
                        <th className={TH_CLASS}>Proveedor</th>
                        <th className={`${TH_CLASS} text-right`}>Facturas</th>
                        <th className={`${TH_CLASS} text-right`}>Saldo pendiente</th>
                      </tr>
                    </thead>
                    <tbody>
                      {d.topSuppliers.map((supplier, index) => (
                        <tr key={supplier.nit} className="border-t border-slate-100">
                          <td className={TD_CLASS}>{index + 1}</td>
                          <td className={TD_CLASS}>
                            <Link href={supplierLink(supplier.nit)} className="text-blue-700 hover:underline">
                              {supplier.name}
                            </Link>
                            <div className="text-xs text-slate-500">{supplier.nit}</div>
                          </td>
                          <td className={`${TD_CLASS} text-right`}>{formatCount(supplier.invoiceCount)}</td>
                          <td className={`${TD_CLASS} text-right font-mono`}>{formatCop(supplier.pendingBalance)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>
          </div>
        )}
      </QueryState>
    </div>
  )
}
