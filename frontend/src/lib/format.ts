import type { InvoiceStatus, LineOutcome, RejectionReason } from '../api/types'

/** La operacion de Tesoreria ocurre en hora de Colombia (seccion 4), sin importar donde este el navegador. */
export const OPERATION_TIME_ZONE = 'America/Bogota'

const dateTimeFormat = new Intl.DateTimeFormat('es-CO', {
  timeZone: OPERATION_TIME_ZONE,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

const countFormat = new Intl.NumberFormat('es-CO')

/**
 * Una fecha sin hora de la API ("2026-03-15") como "15/03/2026". Se arma desde el texto: convertirla
 * a Date la interpretaria como medianoche UTC y en Colombia se veria el dia anterior.
 */
export function formatDate(value: string | null | undefined): string {
  const match = value ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(value) : null
  if (!match) {
    return value ?? '—'
  }
  const [, year, month, day] = match
  return `${day}/${month}/${year}`
}

/**
 * Un instante de la API ("2026-03-16T04:30:00Z") en hora de Colombia: "15/03/2026 23:30". La base
 * guarda instantes, asi que un pago enviado como 23:30-05:00 vuelve en UTC (LIMITACIONES, punto 6).
 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  // es-CO separa fecha y hora con coma: "15/03/2026, 23:30".
  return dateTimeFormat.format(date).replace(', ', ' ')
}

const isoDateInOperationZone = new Intl.DateTimeFormat('en-CA', {
  timeZone: OPERATION_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

/** El mes calendario de hoy en hora de Colombia, como fechas "AAAA-MM-DD" (primer y ultimo dia). */
export function currentMonthRange(now: Date = new Date()): { from: string; to: string } {
  // en-CA formatea como AAAA-MM-DD, que es el formato de los <input type="date"> y de la API.
  const today = isoDateInOperationZone.format(now)
  const [year, month] = today.split('-').map(Number)
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate()
  const mm = String(month).padStart(2, '0')
  return { from: `${year}-${mm}-01`, to: `${year}-${mm}-${String(lastDay).padStart(2, '0')}` }
}

/** Una cantidad con separador de miles colombiano: 50000 -> "50.000". */
export function formatCount(value: number): string {
  return countFormat.format(value)
}

export const STATUS_LABELS: Record<InvoiceStatus, string> = {
  PENDIENTE: 'Pendiente',
  PARCIAL: 'Parcial',
  PAGADA: 'Pagada',
  PAGADA_EXTEMPORANEA: 'Pagada extemporanea',
}

export const OUTCOME_LABELS: Record<LineOutcome, string> = {
  APLICADO: 'Aplicado',
  RECHAZADO: 'Rechazado',
}

export const REASON_LABELS: Record<RejectionReason, string> = {
  FORMATO_INVALIDO: 'Formato invalido',
  VALOR_INVALIDO: 'Valor invalido',
  FECHA_INVALIDA: 'Fecha invalida',
  REFERENCIA_DUPLICADA: 'Referencia duplicada',
  FACTURA_INEXISTENTE: 'Factura inexistente',
  EXCEDE_SALDO: 'Excede el saldo',
}
