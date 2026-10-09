import type { ReactNode } from 'react'

interface Props {
  isLoading: boolean
  error: string
  isEmpty: boolean
  emptyMessage?: string
  onRetry?: () => void
  children: ReactNode
}

/** Resuelve de forma explicita carga, error y vacio para toda vista que consulta datos (RF-29). */
export function QueryState({ isLoading, error, isEmpty, emptyMessage = 'No hay resultados.', onRetry, children }: Props) {
  if (isLoading) {
    return (
      <div role="status" className="rounded border border-slate-200 bg-white p-6 text-center text-slate-500">
        Cargando…
      </div>
    )
  }
  if (error) {
    return (
      <div role="alert" className="rounded border border-red-200 bg-red-50 p-4 text-red-800">
        <p>{error}</p>
        {onRetry && (
          <button type="button" onClick={onRetry} className="mt-2 text-sm font-medium underline">
            Reintentar
          </button>
        )}
      </div>
    )
  }
  if (isEmpty) {
    return <div className="rounded border border-slate-200 bg-white p-6 text-center text-slate-500">{emptyMessage}</div>
  }
  return <>{children}</>
}
