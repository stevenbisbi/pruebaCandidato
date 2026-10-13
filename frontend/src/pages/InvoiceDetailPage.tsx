import { useGetInvoiceQuery } from '../api/api'
import { errorMessage } from '../api/errors'
import { Link } from '../components/Link'
import { QueryState } from '../components/QueryState'
import { StatusBadge } from '../components/StatusBadge'
import { Card, TD_CLASS, TH_CLASS } from '../components/ui'
import { formatDate, formatDateTime, REASON_LABELS } from '../lib/format'
import { formatAdjustment, formatCop } from '../lib/money'

/** Una factura y todos los pagos que recibio, aplicados y rechazados, con su auditoria (RF-15). */
export function InvoiceDetailPage({ number }: { number: string }) {
  const result = useGetInvoiceQuery(number)
  // currentData es solo la respuesta de esta factura: nunca muestra la anterior mientras carga.
  const detail = result.currentData

  return (
    <div className="space-y-4">
      <Link href="/facturas" className="text-sm text-blue-700 hover:underline">« Volver a facturas</Link>

      <QueryState isLoading={result.isFetching && !detail} error={errorMessage(result.error)} isEmpty={false} onRetry={result.refetch}>
        {detail && (
          <>
            <Card title={`Factura ${detail.invoice.number}`} actions={<StatusBadge value={detail.invoice.status} />}>
              <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm md:grid-cols-4">
                <Field label="Proveedor" value={detail.invoice.supplierName} hint={detail.invoice.supplierNit} />
                <Field label="Emision" value={formatDate(detail.invoice.issueDate)} />
                <Field label="Vencimiento" value={formatDate(detail.invoice.dueDate)} />
                <Field label="Valor total" value={formatCop(detail.invoice.totalAmount)} mono />
                <Field label="Saldo pendiente" value={formatCop(detail.invoice.balance)} mono />
              </dl>
            </Card>

            <Card title="Pagos recibidos">
              {detail.payments.length === 0 ? (
                <p className="text-sm text-slate-500">Esta factura no ha recibido pagos.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="min-w-full text-sm">
                    <thead className="bg-slate-100 text-left text-xs uppercase text-slate-600">
                      <tr>
                        <th className={TH_CLASS}>Procesado</th>
                        <th className={TH_CLASS}>Lote</th>
                        <th className={TH_CLASS}>Referencia</th>
                        <th className={TH_CLASS}>Fecha de pago</th>
                        <th className={`${TH_CLASS} text-right`}>Valor</th>
                        <th className={TH_CLASS}>Resultado</th>
                        <th className={`${TH_CLASS} text-right`}>Saldo antes</th>
                        <th className={`${TH_CLASS} text-right`}>Saldo despues</th>
                        <th className={`${TH_CLASS} text-right`}>Ajuste</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detail.payments.map((payment) => (
                        <tr
                          key={`${payment.batchId}-${payment.lineNumber}`}
                          className={`border-t border-slate-100 ${payment.outcome === 'RECHAZADO' ? 'bg-red-50' : ''}`}
                        >
                          <td className={TD_CLASS}>
                            {formatDateTime(payment.processedAt)}
                            <div className="text-xs text-slate-500">{payment.processedBy}</div>
                          </td>
                          <td className={`${TD_CLASS} font-mono`}>
                            <Link href={`/lotes?lote=${encodeURIComponent(payment.batchId)}`} className="text-blue-700 hover:underline">
                              {payment.batchId}
                            </Link>
                            <div className="text-xs text-slate-500">linea {payment.lineNumber}</div>
                          </td>
                          <td className={`${TD_CLASS} font-mono`}>{payment.reference}</td>
                          <td className={TD_CLASS}>{formatDateTime(payment.paidAt)}</td>
                          <td className={`${TD_CLASS} text-right font-mono`}>{formatCop(payment.amount)}</td>
                          <td className={TD_CLASS}>
                            <StatusBadge value={payment.outcome} />
                            {payment.reason && <div className="text-xs text-red-700">{REASON_LABELS[payment.reason]}</div>}
                          </td>
                          <td className={`${TD_CLASS} text-right font-mono`}>{formatCop(payment.balanceBefore)}</td>
                          <td className={`${TD_CLASS} text-right font-mono`}>{formatCop(payment.balanceAfter)}</td>
                          <td className={`${TD_CLASS} text-right font-mono`}>{formatAdjustment(payment.roundingAdjustment)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>
          </>
        )}
      </QueryState>
    </div>
  )
}

function Field({ label, value, hint, mono = false }: { label: string; value: string; hint?: string; mono?: boolean }) {
  return (
    <div>
      <dt className="text-xs uppercase text-slate-500">{label}</dt>
      <dd className={mono ? 'font-mono' : ''}>{value}</dd>
      {hint && <dd className="text-xs text-slate-500">{hint}</dd>}
    </div>
  )
}
