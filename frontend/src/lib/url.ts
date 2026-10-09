import { useCallback, useSyncExternalStore } from 'react'

/**
 * La URL es la unica fuente de verdad para filtros, paginacion y pantalla actual (RF-25): no hay
 * copia en el store, asi que pegar el enlace en otra pestana reproduce exactamente la misma vista.
 */
const listeners = new Set<() => void>()

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  window.addEventListener('popstate', listener)
  return () => {
    listeners.delete(listener)
    window.removeEventListener('popstate', listener)
  }
}

function notify(): void {
  listeners.forEach((l) => l())
}

function snapshot(): string {
  return window.location.pathname + window.location.search
}

export function navigate(path: string, params?: URLSearchParams, replace = false): void {
  const query = params && params.toString() ? `?${params.toString()}` : ''
  const url = `${path}${query}`
  if (url === snapshot()) {
    return
  }
  if (replace) {
    window.history.replaceState(null, '', url)
  } else {
    window.history.pushState(null, '', url)
  }
  notify()
}

export function useLocation(): { path: string; params: URLSearchParams } {
  const current = useSyncExternalStore(subscribe, snapshot)
  const [path, search = ''] = current.split('?')
  return { path, params: new URLSearchParams(search) }
}

/** Actualiza parametros de la URL actual. Un valor vacio o null elimina el parametro. */
export function useSearchParams(): [URLSearchParams, (changes: Record<string, string | string[] | null>, replace?: boolean) => void] {
  const { path, params } = useLocation()
  const update = useCallback(
    (changes: Record<string, string | string[] | null>, replace = false) => {
      const next = new URLSearchParams(window.location.search)
      Object.entries(changes).forEach(([key, value]) => {
        next.delete(key)
        if (Array.isArray(value)) {
          value.filter((v) => v !== '').forEach((v) => next.append(key, v))
        } else if (value !== null && value !== '') {
          next.set(key, value)
        }
      })
      navigate(path, next, replace)
    },
    [path],
  )
  return [params, update]
}
