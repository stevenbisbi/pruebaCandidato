/** Contrato de la API. Los montos son texto decimal para no perder precision (RF-05). */
export type Money = string

export const INVOICE_STATUSES = ['PENDIENTE', 'PARCIAL', 'PAGADA', 'PAGADA_EXTEMPORANEA'] as const
export type InvoiceStatus = (typeof INVOICE_STATUSES)[number]

export type RejectionReason =
  | 'FORMATO_INVALIDO'
  | 'VALOR_INVALIDO'
  | 'FECHA_INVALIDA'
  | 'REFERENCIA_DUPLICADA'
  | 'FACTURA_INEXISTENTE'
  | 'EXCEDE_SALDO'

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

export interface Batch {
  loteId: string
  origen: string
  recibidoEn: string
  totalLineas: number
  lineasAplicadas: number
  lineasRechazadas: number
  valorAplicado: Money
  valorRechazado: Money
  reenvio: boolean
}

export interface BatchLinePage {
  lineas: BatchLine[]
  pagina: number
  tamano: number
  total: number
}

export interface ApiError {
  codigo: string
  mensaje: string
}
