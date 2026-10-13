import { useSyncExternalStore } from 'react'

/**
 * Navegacion sin libreria, con la History API del navegador. La URL (ruta y parametros) es la
 * fuente de verdad de la pantalla: un enlace compartido reproduce exactamente la misma vista (RF-25).
 */

/** Cambia la URL sin recargar y avisa a los componentes que la leen. */
export function navigate(to: string, options: { replace?: boolean } = {}) {
  if (options.replace) {
    window.history.replaceState(null, '', to)
  } else {
    window.history.pushState(null, '', to)
  }
  // pushState no dispara popstate por si solo: se emite para que useLocation se entere.
  window.dispatchEvent(new PopStateEvent('popstate'))
}

function subscribe(onChange: () => void) {
  window.addEventListener('popstate', onChange)
  return () => window.removeEventListener('popstate', onChange)
}

function currentLocation() {
  return window.location.pathname + window.location.search
}

/** La URL actual (ruta + parametros). Se actualiza al navegar y con los botones atras/adelante. */
export function useLocation(): { pathname: string; search: string } {
  const href = useSyncExternalStore(subscribe, currentLocation)
  const url = new URL(href, window.location.origin)
  return { pathname: url.pathname, search: url.search }
}

type ParamChanges = Record<string, string | string[] | null>

/** Aplica cambios a los parametros: un valor null o vacio borra el parametro; una lista lo repite. */
export function applyParams(search: string, changes: ParamChanges): URLSearchParams {
  const params = new URLSearchParams(search)
  for (const [key, value] of Object.entries(changes)) {
    params.delete(key)
    if (Array.isArray(value)) {
      value.filter((v) => v !== '').forEach((v) => params.append(key, v))
    } else if (value !== null && value !== '') {
      params.set(key, value)
    }
  }
  return params
}

/** Los parametros de la URL y una funcion para cambiarlos (queda en el historial: atras funciona). */
export function useUrlParams(): [URLSearchParams, (changes: ParamChanges) => void] {
  const { pathname, search } = useLocation()

  function setParams(changes: ParamChanges) {
    const params = applyParams(window.location.search, changes)
    const text = params.toString()
    navigate(pathname + (text === '' ? '' : `?${text}`))
  }

  return [new URLSearchParams(search), setParams]
}
