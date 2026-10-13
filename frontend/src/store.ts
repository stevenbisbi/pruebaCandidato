import { configureStore } from '@reduxjs/toolkit'
import { api } from './api/api'

/**
 * Store de Redux. Solo contiene la cache de RTK Query: los filtros y la pagina viven en la URL
 * (RF-25) y los formularios en el estado local de cada componente.
 *
 * createStore existe para que cada prueba arranque con un store limpio.
 */
export function createStore() {
  return configureStore({
    reducer: { [api.reducerPath]: api.reducer },
    middleware: (getDefault) => getDefault().concat(api.middleware),
  })
}

export const store = createStore()
