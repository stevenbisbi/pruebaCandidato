import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { createStore } from '../store'
import { InvoicesPage } from './InvoicesPage'
import { BatchPage } from './BatchPage'

function renderWithStore(ui: React.ReactElement) {
  return render(<Provider store={createStore()}>{ui}</Provider>)
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function requestedUrl(call: unknown[]): string {
  const input = call[0]
  return input instanceof Request ? input.url : String(input)
}

describe('InvoicesPage', () => {
  beforeEach(() => {
    window.history.replaceState(null, '', '/facturas?nit=800404150-7&estado=PARCIAL&estado=PAGADA&cursor=abc')
  })
  afterEach(() => vi.restoreAllMocks())

  it('reconstruye la consulta desde la URL compartida (RF-25)', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ facturas: [], siguienteCursor: null }))

    renderWithStore(<InvoicesPage />)

    await waitFor(() => expect(fetchMock).toHaveBeenCalled())
    const url = new URL(requestedUrl(fetchMock.mock.calls[0]))
    expect(url.pathname).toBe('/api/v1/facturas')
    expect(url.searchParams.get('nit')).toBe('800404150-7')
    expect(url.searchParams.getAll('estado')).toEqual(['PARCIAL', 'PAGADA'])
    expect(url.searchParams.get('cursor')).toBe('abc')
    expect(screen.getByLabelText('NIT proveedor')).toHaveValue('800404150-7')
  })

  it('muestra el estado vacio y el de error de forma explicita (RF-29)', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({ facturas: [], siguienteCursor: null }))
    renderWithStore(<InvoicesPage />)
    expect(await screen.findByText('Ninguna factura cumple los filtros.')).toBeInTheDocument()
  })

  it('muestra el mensaje de error del servidor', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ codigo: 'SOLICITUD_INVALIDA', mensaje: 'Cursor de paginacion invalido' }, 400))
    renderWithStore(<InvoicesPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Cursor de paginacion invalido')
  })
})

describe('BatchPage', () => {
  beforeEach(() => window.history.replaceState(null, '', '/lotes'))
  afterEach(() => vi.restoreAllMocks())

  it('un doble clic envia el lote una sola vez (RF-27)', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise<Response>(() => {}))
    renderWithStore(<BatchPage />)

    fireEvent.click(screen.getByLabelText('Pegar JSON'))
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '{"loteId":"L-1","origen":"TESORERIA","pagos":[]}' } })
    const button = screen.getByRole('button', { name: 'Procesar lote' })
    fireEvent.click(button)
    fireEvent.click(button)

    await waitFor(() => expect(screen.getByRole('button', { name: 'Procesando…' })).toBeDisabled())
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
