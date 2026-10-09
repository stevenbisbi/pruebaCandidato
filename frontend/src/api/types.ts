/** Contrato de la API. Los montos son texto decimal para no perder precision (RF-05). */
export type Money = string

export const INVOICE_STATUSES = ['PENDIENTE', 'PARCIAL', 'PAGADA', 'PAGADA_EXTEMPORANEA'] as const
export type InvoiceStatus = (typeof INVOICE_STATUSES)[number]

export const REJECTION_REASONS = [
  'FORMATO_INVALIDO',
  'VALOR_INVALIDO',
  'FECHA_INVALIDA',
  'REFERENCIA_DUPLICADA',
  'FACTURA_INEXISTENTE',
  'EXCEDE_SALDO',
] as const
export type RejectionReason = (typeof REJECTION_REASONS)[number]

export type LineOutcome = 'APLICADO' | 'RECHAZADO'

export interface Invoice {
  numero: string
  nit: string
  razonSocial: string
  fechaEmision: string
  fechaVencimiento: string
  valorTotal: Money
  saldoPendiente: Money
  estado: InvoiceStatus
}

export interface InvoicePage {
  facturas: Invoice[]
  siguienteCursor: string | null
}

export interface BatchLine {
  linea: number
  referencia: string | null
  numeroFactura: string | null
  valor: Money | null
  fechaPago: string | null
  resultado: LineOutcome
  motivo: RejectionReason | null
  descripcionMotivo: string | null
  detalle: string | null
  saldoAnterior: Money | null
  saldoPosterior: Money | null
  estadoFactura: InvoiceStatus | null
}

export interface InvoiceDetail {
  factura: Invoice
  pagos: BatchLine[]
}

export interface Batch {
  loteId: string
  origen: string
  canal: 'JSON' | 'CSV'
  fechaGeneracion: string | null
  recibidoEn: string
  totalLineas: number
  lineasAplicadas: number
  lineasRechazadas: number
  valorAplicado: Money
  valorRechazado: Money
  rechazosPorMotivo: Partial<Record<RejectionReason, number>>
  reenvio: boolean
}

export interface BatchLinePage {
  lineas: BatchLine[]
  pagina: number
  tamano: number
  total: number
}

export interface Dashboard {
  totalFacturado: Money
  cantidadFacturas: number
  saldoPendiente: Money
  totalAplicado: Money
  cantidadAplicados: number
  totalRechazado: Money
  cantidadRechazados: number
  topProveedores: { nit: string; razonSocial: string; saldoPendiente: Money }[]
}

export interface ApiError {
  codigo: string
  mensaje: string
}
