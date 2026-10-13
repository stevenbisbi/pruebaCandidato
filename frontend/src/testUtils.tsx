import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { Provider } from 'react-redux'
import { vi } from 'vitest'
import { createStore } from './store'

/** Utilidades de prueba: respuestas falsas de la API sin levantar el backend. */

/** Renderiza con un store nuevo: cada prueba arranca sin la cache de RTK Query de la anterior. */
export function renderWithStore(ui: ReactElement) {
  return render(<Provider store={createStore()}>{ui}</Provider>)
}

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/** RTK Query llama a fetch con un Request; otras llamadas, con la URL como texto. */
function urlOf(input: unknown): URL {
  return new URL(input instanceof Request ? input.url : String(input), 'http://localhost')
}

function methodOf(input: unknown, init?: RequestInit): string {
  return (init?.method ?? (input instanceof Request ? input.method : 'GET')).toUpperCase()
}

/** Reemplaza fetch y devuelve el espia, para revisar que URL se pidio. */
export function mockFetch(implementation: (url: URL, method: string) => Promise<Response> | Response) {
  return vi
    .spyOn(globalThis, 'fetch')
    .mockImplementation(async (input, init) => implementation(urlOf(input), methodOf(input, init)))
}

/** La URL de la llamada numero n a fetch (0 = la primera; -1 = la ultima). */
export function calledUrl(spy: ReturnType<typeof mockFetch>, n = 0): URL {
  const calls = spy.mock.calls
  return urlOf(calls[n < 0 ? calls.length + n : n][0])
}

/** La URL de la ultima llamada a fetch con exactamente esa ruta, o undefined si no hubo ninguna. */
export function lastCallTo(spy: ReturnType<typeof mockFetch>, pathname: string): URL | undefined {
  return spy.mock.calls.map((call) => urlOf(call[0])).filter((url) => url.pathname === pathname).at(-1)
}

/** Pone la URL del navegador como si el usuario hubiera abierto o pegado ese enlace. */
export function openUrl(path: string) {
  window.history.replaceState(null, '', path)
}
