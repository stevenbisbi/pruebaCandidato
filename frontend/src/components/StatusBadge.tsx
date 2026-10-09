import type { InvoiceStatus, LineOutcome } from '../api/types'

const STYLES: Record<InvoiceStatus | LineOutcome, string> = {
  PENDIENTE: 'bg-slate-100 text-slate-700',
  PARCIAL: 'bg-amber-100 text-amber-800',
  PAGADA: 'bg-emerald-100 text-emerald-800',
  PAGADA_EXTEMPORANEA: 'bg-orange-100 text-orange-800',
  APLICADO: 'bg-emerald-100 text-emerald-800',
  RECHAZADO: 'bg-red-100 text-red-800',
}

export function StatusBadge({ value }: { value: InvoiceStatus | LineOutcome }) {
  return (
    <span className={`inline-block rounded px-2 py-0.5 text-xs font-medium ${STYLES[value]}`}>
      {value.replace('_', ' ')}
    </span>
  )
}
