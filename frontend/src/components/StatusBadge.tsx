import type { InvoiceStatus, LineOutcome } from '../api/types'
import { OUTCOME_LABELS, STATUS_LABELS } from '../lib/format'

const STYLES: Record<InvoiceStatus | LineOutcome, string> = {
  PENDIENTE: 'bg-slate-100 text-slate-700',
  PARCIAL: 'bg-amber-100 text-amber-800',
  PAGADA: 'bg-emerald-100 text-emerald-800',
  PAGADA_EXTEMPORANEA: 'bg-orange-100 text-orange-800',
  APLICADO: 'bg-emerald-100 text-emerald-800',
  RECHAZADO: 'bg-red-100 text-red-800',
}

const LABELS: Record<InvoiceStatus | LineOutcome, string> = { ...STATUS_LABELS, ...OUTCOME_LABELS }

export function StatusBadge({ value }: { value: InvoiceStatus | LineOutcome | null }) {
  if (value === null) {
    return <span className="text-slate-400">—</span>
  }
  return (
    <span className={`inline-block whitespace-nowrap rounded px-2 py-0.5 text-xs font-medium ${STYLES[value]}`}>
      {LABELS[value]}
    </span>
  )
}
