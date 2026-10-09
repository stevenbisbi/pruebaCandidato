import { useEffect, useState } from 'react'

type ParamChanges = Record<string, string | string[] | null>

/**
 * Lee y cambia los parametros de la URL (?nit=...&estado=...).
 *
 * La URL es el unico lugar donde viven los filtros y la pagina (RF-25): si copias el enlace y lo
 * pegas en otra pestana, la pantalla se reconstruye igual.
 */
export function useUrlParams(): [URLSearchParams, (changes: ParamChanges) => void] {
  const [search, setSearch] = useState(window.location.search)

  // Si el usuario usa los botones atras/adelante del navegador, se vuelve a leer la URL.
  useEffect(() => {
    const onBackOrForward = () => setSearch(window.location.search)
    window.addEventListener('popstate', onBackOrForward)
    return () => window.removeEventListener('popstate', onBackOrForward)
  }, [])

  // Cambia algunos parametros y deja los demas igual. Un valor null o vacio borra el parametro.
  function setParams(changes: ParamChanges) {
    const params = new URLSearchParams(window.location.search)
    for (const key of Object.keys(changes)) {
      const value = changes[key]
      params.delete(key)
      if (Array.isArray(value)) {
        value.forEach((v) => params.append(key, v))
      } else if (value) {
        params.set(key, value)
      }
    }
    const newSearch = params.toString() ? '?' + params.toString() : ''
    window.history.pushState(null, '', window.location.pathname + newSearch)
    setSearch(newSearch)
  }

  return [new URLSearchParams(search), setParams]
}
