import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { BatchSummary, CursorPage, Dashboard, Invoice, LineResult, Page } from '../api/types'
import { calledUrl, jsonResponse, mockFetch, openUrl, renderWithStore } from '../testUtils'
import { BatchPage } from './BatchPage'
import { DashboardPage } from './DashboardPage'
import { InvoicesPage } from './InvoicesPage'

afterEach(() => vi.restoreAllMocks())

const INVOICE: Invoice = {
  number: 'FV-0042199',
  supplierNit: '800404150-7',
  supplierName: 'Proveedor Uno',
  issueDate: '2026-02-13',
  dueDate: '2026-03-15',
  totalAmount: '37500483.40',
  balance: '37500483.40',
  status: 'PENDIENTE',
}

function invoicePage(items: Invoice[], nextCursor: string | null = null): CursorPage<Invoice> {
  return { items, size: items.length, nextCursor, hasNext: nextCursor !== null }
}

describe('InvoicesPage', () => {
  it('arma la consulta desde la URL compartida, con todos los filtros (RF-25)', async () => {
    openUrl('/facturas?nit=800404150-7&status=PARCIAL&status=PAGADA&dueFrom=2026-03-01&dueTo=2026-03-31&minBalance=1000&maxBalance=2000&cursor=FV-1&otro=x')
    const fetchSpy = mockFetch(() => jsonResponse(invoicePage([INVOICE])))

    renderWithStore(<InvoicesPage />)

    await screen.findByText('FV-0042199')
    const url = calledUrl(fetchSpy)
    expect(url.pathname).toBe('/api/v1/invoices')
    expect(url.searchParams.get('nit')).toBe('800404150-7')
    expect(url.searchParams.getAll('status')).toEqual(['PARCIAL', 'PAGADA'])
    expect(url.searchParams.get('dueFrom')).toBe('2026-03-01')
    expect(url.searchParams.get('maxBalance')).toBe('2000')
    expect(url.searchParams.get('cursor')).toBe('FV-1')
    // Un parametro que la API no entiende no se le envia.
    expect(url.searchParams.has('otro')).toBe(false)
    // El formulario muestra lo mismo que la URL.
    expect(screen.getByLabelText('NIT del proveedor')).toHaveValue('800404150-7')
    expect(screen.getByLabelText('Parcial')).toBeChecked()
  })

  it('muestra los montos en formato colombiano sin perder precision (RF-28)', async () => {
    openUrl('/facturas')
    mockFetch(() => jsonResponse(invoicePage([INVOICE])))

    renderWithStore(<InvoicesPage />)

    expect(await screen.findAllByText('$ 37.500.483,40')).toHaveLength(2)
    expect(screen.getByText('15/03/2026')).toBeInTheDocument()
  })

  it('al filtrar escribe los filtros en la URL y vuelve a la primera pagina', async () => {
    openUrl('/facturas?cursor=FV-9')
    const fetchSpy = mockFetch(() => jsonResponse(invoicePage([INVOICE])))
    renderWithStore(<InvoicesPage />)
    await screen.findByText('FV-0042199')

    fireEvent.change(screen.getByLabelText('NIT del proveedor'), { target: { value: '900123' } })
    fireEvent.click(screen.getByLabelText('Pendiente'))
    fireEvent.click(screen.getByRole('button', { name: 'Filtrar' }))

    const params = new URLSearchParams(window.location.search)
    expect(params.get('nit')).toBe('900123')
    expect(params.getAll('status')).toEqual(['PENDIENTE'])
    expect(params.has('cursor')).toBe(false)
    await waitFor(() => expect(calledUrl(fetchSpy, -1).searchParams.get('nit')).toBe('900123'))
  })

  it('"Siguiente" pide la pagina con el cursor que devolvio la API (PR-03)', async () => {
    openUrl('/facturas')
    const fetchSpy = mockFetch(() => jsonResponse(invoicePage([INVOICE], 'FV-0042199')))
    renderWithStore(<InvoicesPage />)
    await screen.findByText('FV-0042199')

    fireEvent.click(screen.getByRole('button', { name: 'Siguiente »' }))

    expect(new URLSearchParams(window.location.search).get('cursor')).toBe('FV-0042199')
    await waitFor(() => expect(calledUrl(fetchSpy, 1).searchParams.get('cursor')).toBe('FV-0042199'))
  })

  it('resuelve el estado vacio de forma explicita (RF-29)', async () => {
    openUrl('/facturas')
    mockFetch(() => jsonResponse(invoicePage([])))

    renderWithStore(<InvoicesPage />)

    expect(await screen.findByText('Ninguna factura cumple los filtros.')).toBeInTheDocument()
  })

  it('muestra el mensaje de error del backend (RF-29)', async () => {
    openUrl('/facturas?dueFrom=2026-03-31&dueTo=2026-03-01')
    mockFetch(() =>
      jsonResponse({ status: 400, error: 'Bad Request', message: 'El vencimiento desde no puede ser posterior al vencimiento hasta' }, 400))

    renderWithStore(<InvoicesPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('El vencimiento desde no puede ser posterior')
  })
})

const BATCH: BatchSummary = {
  id: 'CSV-abc',
  source: 'TESORERIA',
  channel: 'CSV',
  generatedOn: null,
  receivedAt: '2026-03-16T04:30:00Z',
  totalLines: 2,
  appliedLines: 1,
  rejectedLines: 1,
  appliedAmount: '37500483.00',
  rejectedAmount: '1000.00',
  replayed: false,
}

const LINES: Page<LineResult> = {
  items: [
    {
      lineNumber: 1, reference: 'P-1', invoiceNumber: 'FV-0042199', amount: '37500483.00', paidAt: '2026-03-16T04:30:00Z',
      outcome: 'APLICADO', reason: null, reasonDescription: null, detail: null, balanceBefore: '37500483.40',
      balanceAfter: '0.00', statusAfter: 'PAGADA', roundingAdjustment: '0.40',
    },
    {
      lineNumber: 2, reference: 'P-2', invoiceNumber: 'FV-404', amount: '1000.00', paidAt: '2026-03-16T04:30:00Z',
      outcome: 'RECHAZADO', reason: 'FACTURA_INEXISTENTE', reasonDescription: 'La factura destino no existe', detail: null,
      balanceBefore: null, balanceAfter: null, statusAfter: null, roundingAdjustment: null,
    },
  ],
  page: 0,
  size: 100,
  totalItems: 2,
  hasNext: false,
}

/** Responde como el backend segun la ruta pedida. */
function batchApi(receipt: BatchSummary = BATCH) {
  return mockFetch((url, method) => {
    if (method === 'POST') {
      return jsonResponse(receipt, receipt.replayed ? 200 : 201)
    }
    if (url.pathname.endsWith('/lines')) {
      return jsonResponse(LINES)
    }
    return jsonResponse(receipt)
  })
}

function chooseCsv() {
  const csv = new File(['referencia;numero_factura;valor;fecha_pago\n'], 'lote.csv', { type: 'text/csv' })
  fireEvent.change(screen.getByLabelText('Archivo CSV de Tesoreria'), { target: { files: [csv] } })
}

describe('BatchPage', () => {
  it('un doble clic envia el lote una sola vez (RF-27)', async () => {
    openUrl('/lotes')
    const fetchSpy = mockFetch(() => new Promise<Response>(() => {}))
    renderWithStore(<BatchPage />)

    chooseCsv()
    const button = screen.getByRole('button', { name: 'Procesar lote' })
    fireEvent.click(button)
    fireEvent.click(button)

    await waitFor(() => expect(screen.getByRole('button', { name: 'Procesando…' })).toBeDisabled())
    expect(fetchSpy).toHaveBeenCalledTimes(1)
  })

  it('al terminar deja el id del lote en la URL y muestra el resultado linea por linea (RF-26)', async () => {
    openUrl('/lotes')
    batchApi()
    renderWithStore(<BatchPage />)

    chooseCsv()
    fireEvent.click(screen.getByRole('button', { name: 'Procesar lote' }))

    expect(await screen.findByText(/Lote procesado/)).toBeInTheDocument()
    // Si se recarga la pagina, se ve el resultado y no se reenvia nada (RF-27).
    expect(new URLSearchParams(window.location.search).get('lote')).toBe('CSV-abc')
    const rejected = (await screen.findByText('La factura destino no existe')).closest('tr')
    expect(rejected).toHaveClass('bg-red-50')
    expect(within(rejected as HTMLElement).getByText('FV-404')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Descargar resultado (CSV)' })).toHaveAttribute('href', '/api/v1/batches/CSV-abc/download')
  })

  it('avisa cuando el lote era un reenvio (RF-20)', async () => {
    openUrl('/lotes')
    batchApi({ ...BATCH, replayed: true })
    renderWithStore(<BatchPage />)

    chooseCsv()
    fireEvent.click(screen.getByRole('button', { name: 'Procesar lote' }))

    expect(await screen.findByText(/ya se habia procesado/)).toBeInTheDocument()
  })

  it('al procesar un lote, las facturas en pantalla se vuelven a consultar (invalidacion de RTK Query)', async () => {
    openUrl('/facturas')
    const fetchSpy = mockFetch((url, method) => {
      if (url.pathname === '/api/v1/invoices') {
        return jsonResponse(invoicePage([INVOICE]))
      }
      if (method === 'POST') {
        return jsonResponse(BATCH, 201)
      }
      return url.pathname.endsWith('/lines') ? jsonResponse(LINES) : jsonResponse(BATCH)
    })
    const invoiceRequests = () => fetchSpy.mock.calls.filter((_, i) => calledUrl(fetchSpy, i).pathname === '/api/v1/invoices').length
    renderWithStore(
      <>
        <InvoicesPage />
        <BatchPage />
      </>,
    )
    await screen.findByText('FV-0042199')
    expect(invoiceRequests()).toBe(1)

    chooseCsv()
    fireEvent.click(screen.getByRole('button', { name: 'Procesar lote' }))

    await screen.findByText(/Lote procesado/)
    await waitFor(() => expect(invoiceRequests()).toBe(2))
  })

  it('no envia un JSON mal escrito y explica el error', () => {
    openUrl('/lotes')
    const fetchSpy = mockFetch(() => jsonResponse(BATCH))
    renderWithStore(<BatchPage />)

    fireEvent.click(screen.getByRole('tab', { name: 'Pegar JSON' }))
    fireEvent.change(screen.getByLabelText('Lote en JSON (formato del Anexo A)'), { target: { value: '{"pagos": [' } })
    fireEvent.click(screen.getByRole('button', { name: 'Procesar lote' }))

    expect(screen.getByRole('alert')).toHaveTextContent('El texto no es un JSON valido')
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('al elegir un motivo de rechazo filtra solo los rechazos con ese motivo (RF-26)', async () => {
    openUrl('/lotes?lote=CSV-abc')
    const fetchSpy = batchApi()
    renderWithStore(<BatchPage />)
    await screen.findByText('La factura destino no existe')

    fireEvent.change(screen.getByLabelText('Motivo de rechazo'), { target: { value: 'FACTURA_INEXISTENTE' } })

    const params = new URLSearchParams(window.location.search)
    expect(params.get('outcome')).toBe('RECHAZADO')
    expect(params.get('reason')).toBe('FACTURA_INEXISTENTE')
    await waitFor(() => {
      const last = calledUrl(fetchSpy, -1)
      expect(last.searchParams.get('reason')).toBe('FACTURA_INEXISTENTE')
    })
  })
})

const DASHBOARD: Dashboard = {
  dueFrom: '2026-03-01',
  dueTo: '2026-03-31',
  invoiceCount: 21281,
  totalInvoiced: '845417309077.34',
  totalPending: '796156025111.65',
  appliedCount: 1771,
  totalApplied: '49261283874.00',
  rejectedCount: 248,
  totalRejected: '8750993209.00',
  topSuppliers: [{ nit: '800932696-3', name: 'Servicios Nacional S.A.S.', pendingBalance: '568872268.46', invoiceCount: 10 }],
}

describe('DashboardPage', () => {
  it('muestra los totales del rango y lleva a las facturas del proveedor (RF-24, RF-30)', async () => {
    openUrl('/tablero?dueFrom=2026-03-01&dueTo=2026-03-31')
    const fetchSpy = mockFetch(() => jsonResponse(DASHBOARD))

    renderWithStore(<DashboardPage />)

    expect(await screen.findByText('$ 845.417.309.077,34')).toBeInTheDocument()
    expect(screen.getByText('21.281 facturas')).toBeInTheDocument()
    expect(calledUrl(fetchSpy).searchParams.get('dueFrom')).toBe('2026-03-01')
    const supplier = new URL(screen.getByRole('link', { name: 'Servicios Nacional S.A.S.' }).getAttribute('href') ?? '', 'http://localhost')
    expect(supplier.pathname).toBe('/facturas')
    expect(supplier.searchParams.get('nit')).toBe('800932696-3')
    expect(supplier.searchParams.getAll('status')).toEqual(['PENDIENTE', 'PARCIAL'])
    expect(supplier.searchParams.get('dueTo')).toBe('2026-03-31')
  })

  it('sin rango en la URL abre con el mes actual, que queda escrito en la URL (D-07)', async () => {
    openUrl('/tablero')
    const fetchSpy = mockFetch(() => jsonResponse(DASHBOARD))

    renderWithStore(<DashboardPage />)

    await screen.findByText('$ 845.417.309.077,34')
    const params = new URLSearchParams(window.location.search)
    expect(params.get('dueFrom')).toMatch(/^\d{4}-\d{2}-01$/)
    // Solo se consulta una vez, ya con el rango: nunca se piden las 500.000 facturas sin querer.
    expect(fetchSpy).toHaveBeenCalledTimes(1)
    expect(calledUrl(fetchSpy).searchParams.get('dueFrom')).toBe(params.get('dueFrom'))
  })

  it('"Todas" consulta sin rango de forma explicita', async () => {
    openUrl('/tablero?range=all')
    const fetchSpy = mockFetch(() => jsonResponse(DASHBOARD))

    renderWithStore(<DashboardPage />)

    await screen.findByText('Todas las facturas')
    expect(calledUrl(fetchSpy).searchParams.toString()).toBe('')
  })
})
