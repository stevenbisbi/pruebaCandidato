import { configureStore } from '@reduxjs/toolkit'
import { api } from './api/api'

export function createStore() {
  return configureStore({
    reducer: { [api.reducerPath]: api.reducer },
    middleware: (getDefault) => getDefault().concat(api.middleware),
  })
}

export const store = createStore()
