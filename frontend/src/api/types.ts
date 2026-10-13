/**
 * Contrato de la API del backend (ver README, seccion API). Los montos llegan como texto decimal
 * ("37500483.40") y se mantienen como texto: convertirlos a number perderia precision (RF-05, RF-28).
 */
export type Money = string

export const INVOICE_STATUSES = ['PENDIENTE', 'PARCIAL', 'PAGADA', 'PAGADA_EXTEMPORANEA'] as const
export type InvoiceStatus = (typeof INVOICE_STATUSES)[number]

export const LINE_OUTCOMES = ['APLICADO', 'RECHAZADO'] as const
export type LineOutcome = (typeof LINE_OUTCOMES)[number]

export const REJECTION_REASONS = [
  'FORMATO_INVALIDO',
  'VALOR_INVALIDO',
  'FECHA_INVALIDA',
  'REFERENCIA_DUPLICADA',
  'FACTURA_INEXISTENTE',
  'EXCEDE_SALDO',
] as const
export type RejectionReason = (typeof REJECTION_REASONS)[number]

/** GET /invoices (cada elemento) */
export interface Invoice {
  number: string
  supplierNit: string
  supplierName: string
  issueDate: string
  dueDate: string
  totalAmount: Money
  balance: Money
  status: InvoiceStatus
}

/** Pagina por cursor (D-03): sin total; la siguiente se pide con nextCursor. */
export interface CursorPage<T> {
  items: T[]
  size: number
  nextCursor: string | null
  hasNext: boolean
}

/** Pagina numerada: se usa para el resultado de un lote, que no cambia despues de procesarse. */
export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  hasNext: boolean
}

/** GET /invoices/{id} (cada pago) */
export interface InvoicePayment {
  batchId: string
  lineNumber: number
  reference: string | null
  amount: Money | null
  paidAt: string | null
  outcome: LineOutcome
  reason: RejectionReason | null
  balanceBefore: Money | null
  balanceAfter: Money | null
  statusAfter: InvoiceStatus | null
  roundingAdjustment: Money | null
  processedBy: string
  processedAt: string
}

/** GET /invoices/{id} */
export interface InvoiceDetail {
  invoice: Invoice
  payments: InvoicePayment[]
}

/** POST /batches, POST /batches/file y GET /batches/{id} */
export interface BatchSummary {
  id: string
  source: string
  channel: 'JSON' | 'CSV'
  generatedOn: string | null
  receivedAt: string
  totalLines: number
  appliedLines: number
  rejectedLines: number
  appliedAmount: Money
  rejectedAmount: Money
  replayed: boolean
}

/** GET /batches/{id}/lines (cada linea) */
export interface LineResult {
  lineNumber: number
  reference: string | null
  invoiceNumber: string | null
  amount: Money | null
  paidAt: string | null
  outcome: LineOutcome
  reason: RejectionReason | null
  reasonDescription: string | null
  detail: string | null
  balanceBefore: Money | null
  balanceAfter: Money | null
  statusAfter: InvoiceStatus | null
  roundingAdjustment: Money | null
}

/** GET /conciliation */
export interface Dashboard {
  dueFrom: string | null
  dueTo: string | null
  invoiceCount: number
  totalInvoiced: Money
  totalPending: Money
  appliedCount: number
  totalApplied: Money
  rejectedCount: number
  totalRejected: Money
  topSuppliers: SupplierBalance[]
}

export interface SupplierBalance {
  nit: string
  name: string
  pendingBalance: Money
  invoiceCount: number
}

/** Cuerpo de todos los errores de la API (ApiExceptionHandler). */
export interface ApiErrorBody {
  status: number
  error: string
  message: string
}
