import { useEffect, useRef, useState, type FormEvent } from 'react'
import { errorMessage, useGetBatchLinesQuery, useGetBatchQuery, useSubmitCsvBatchMutation, useSubmitJsonBatchMutation } from '../api/api'
import { REJECTION_REASONS, type Batch } from '../api/types'
import { QueryState } from '../components/QueryState'
import { StatusBadge } from '../components/StatusBadge'
import { formatCop } from '../lib/money'
import { useSearchParams } from '../lib/url'

const PAGE_SIZE = 100

/**
 * Carga de lotes (RF-26, RF-27). El lote procesado queda en la URL (?lote=ID): recargar la pagina
 * muestra el resultado en lugar de reenviar, y el backend es idempotente si aun asi llega dos veces.
 */
export function BatchPage() {
  const [params, setParams] = useSearchParams()
  const batchId = params.get('lote')

  return (
    <div className="space-y-6">
      <UploadForm onProcessed={(b) => setParams({ lote: b.loteId, resultado: null, motivo: null, pagina: null })} />
      {batchId && <BatchResult id={batchId} />}
    </div>
  )
}

function UploadForm({ onProcessed }: { onProcessed: (b: Batch) => void }) {
  const [mode, setMode] = useState<'csv' | 'json'>('csv')
  const [file, setFile] = useState<File | null>(null)
  const [json, setJson] = useState('')
  const [localError, setLocalError] = useState('')
  const [submitCsv, csvState] = useSubmitCsvBatchMutation()
  const [submitJson, jsonState] = useSubmitJsonBatchMutation()
  // Guardia sincronica: el estado de React se actualiza despues del render, un doble clic rapido
  // alcanza a disparar dos envios antes de que el boton quede deshabilitado.
  const inFlight = useRef(false)
  const pending = csvState.isLoading || jsonState.isLoading
  const elapsed = useElapsedSeconds(pending)
  const error = localError || errorMessage(mode === 'csv' ? csvState.error : jsonState.error)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (inFlight.current) {
      return
    }
    setLocalError('')
    let body: unknown = null
    if (mode === 'json') {
      try {
        body = JSON.parse(json)
      } catch {
        setLocalError('El texto no es un JSON valido.')
        return
      }
    } else if (!file) {
      setLocalError('Seleccione un archivo CSV.')
      return
    }
    inFlight.current = true
    try {
      const result = mode === 'csv' && file ? await submitCsv(file).unwrap() : await submitJson(body).unwrap()
      onProcessed(result)
    } catch {
      // El error queda en el estado de la mutacion y se muestra abajo.
    } finally {
      inFlight.current = false
    }
  }

  return (
    <form onSubmit={submit} className="space-y-3 rounded border border-slate-200 bg-white p-4">
      <div className="flex gap-4 text-sm">
        {(['csv', 'json'] as const).map((m) => (
          <label key={m} className="flex items-center gap-1">
            <input type="radio" name="mode" checked={mode === m} onChange={() => setMode(m)} disabled={pending} />
            {m === 'csv' ? 'Archivo CSV' : 'Pegar JSON'}
          </label>
        ))}
      </div>
      {mode === 'csv' ? (
        <input type="file" accept=".csv,text/csv" disabled={pending} onChange={(e) => setFile(e.target.files?.[0] ?? null)} className="block text-sm" />
      ) : (
        <textarea
          value={json}
          disabled={pending}
          onChange={(e) => setJson(e.target.value)}
          rows={8}
          placeholder='{"loteId": "TES-2026-03-15-001", "origen": "TESORERIA", "fechaGeneracion": "2026-03-15", "pagos": [...]}'
          className="block w-full rounded border border-slate-300 p-2 font-mono text-xs"
        />
      )}
      <div className="flex items-center gap-3">
        <button type="submit" disabled={pending} className="rounded bg-blue-700 px-4 py-1.5 text-sm font-medium text-white disabled:opacity-40">
          {pending ? 'Procesando…' : 'Procesar lote'}
        </button>
        {pending && (
          <span role="status" className="flex items-center gap-2 text-sm text-slate-600">
            <span className="h-3 w-3 animate-spin rounded-full border-2 border-blue-700 border-t-transparent" />
            Aplicando pagos… {elapsed} s
          </span>
        )}
      </div>
      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
    </form>
  )
}

function BatchResult({ id }: { id: string }) {
  const [params, setParams] = useSearchParams()
  const outcome = params.get('resultado') ?? ''
  const reason = params.get('motivo') ?? ''
  const page = Number(params.get('pagina') ?? '0')
  const batch = useGetBatchQuery(id)
  const lineParams = new URLSearchParams({ pagina: String(page), tamano: String(PAGE_SIZE) })
  if (outcome) lineParams.set('resultado', outcome)
  if (reason) lineParams.set('motivo', reason)
  const lines = useGetBatchLinesQuery({ id, params: lineParams.toString() })
  const totalPages = lines.data ? Math.max(1, Math.ceil(lines.data.total / PAGE_SIZE)) : 1

  return (
    <section className="space-y-4">
      <QueryState isLoading={batch.isFetching && !batch.data} error={errorMessage(batch.error)} isEmpty={false} onRetry={batch.refetch}>
        {batch.data && (
          <div className="rounded border border-slate-200 bg-white p-4">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h2 className="font-semibold">Lote {batch.data.loteId}</h2>
              <a href={`/api/v1/lotes/${encodeURIComponent(id)}/resultado`} className="text-sm text-blue-700 underline">
                Descargar resultado (CSV)
              </a>
            </div>
            <dl className="mt-3 grid grid-cols-2 gap-3 text-sm sm:grid-cols-5">
              <Stat label="Lineas" value={String(batch.data.totalLineas)} />
              <Stat label="Aplicadas" value={String(batch.data.lineasAplicadas)} />
              <Stat label="Rechazadas" value={String(batch.data.lineasRechazadas)} />
              <Stat label="Valor aplicado" value={formatCop(batch.data.valorAplicado)} />
              <Stat label="Valor rechazado" value={formatCop(batch.data.valorRechazado)} />
            </dl>
            <p className="mt-2 text-xs text-slate-500">
              Recibido {new Date(batch.data.recibidoEn).toLocaleString('es-CO', { timeZone: 'America/Bogota' })} · canal {batch.data.canal}
            </p>
          </div>
        )}
      </QueryState>

      <div className="flex flex-wrap items-end gap-3 text-sm">
        <label className="text-xs text-slate-600">
          Resultado
          <select value={outcome} onChange={(e) => setParams({ resultado: e.target.value, motivo: null, pagina: null })} className="mt-1 block rounded border border-slate-300 px-2 py-1 text-sm">
            <option value="">Todos</option>
            <option value="APLICADO">Aplicados</option>
            <option value="RECHAZADO">Rechazados</option>
          </select>
        </label>
        <label className="text-xs text-slate-600">
          Motivo de rechazo
          <select value={reason} onChange={(e) => setParams({ motivo: e.target.value, resultado: e.target.value ? 'RECHAZADO' : null, pagina: null })} className="mt-1 block rounded border border-slate-300 px-2 py-1 text-sm">
            <option value="">Todos</option>
            {REJECTION_REASONS.map((r) => (
              <option key={r} value={r}>
                {r.replaceAll('_', ' ')} {batch.data?.rechazosPorMotivo[r] ? `(${batch.data.rechazosPorMotivo[r]})` : ''}
              </option>
            ))}
          </select>
        </label>
      </div>

      <QueryState
        isLoading={lines.isFetching && !lines.data}
        error={errorMessage(lines.error)}
        isEmpty={!!lines.data && lines.data.lineas.length === 0}
        emptyMessage="No hay lineas con ese filtro."
        onRetry={lines.refetch}
      >
        <div className="overflow-x-auto rounded border border-slate-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-100 text-left text-xs uppercase text-slate-600">
              <tr>
                <th className="px-3 py-2">Linea</th>
                <th className="px-3 py-2">Referencia</th>
                <th className="px-3 py-2">Factura</th>
                <th className="px-3 py-2 text-right">Valor</th>
                <th className="px-3 py-2">Resultado</th>
                <th className="px-3 py-2">Motivo</th>
                <th className="px-3 py-2 text-right">Saldo anterior</th>
                <th className="px-3 py-2 text-right">Saldo posterior</th>
              </tr>
            </thead>
            <tbody>
              {lines.data?.lineas.map((l) => (
                <tr key={l.linea} className={`border-t border-slate-100 ${l.resultado === 'RECHAZADO' ? 'bg-red-50' : ''}`}>
                  <td className="px-3 py-1.5">{l.linea}</td>
                  <td className="px-3 py-1.5 font-mono">{l.referencia}</td>
                  <td className="px-3 py-1.5 font-mono">{l.numeroFactura}</td>
                  <td className="px-3 py-1.5 text-right font-mono">{formatCop(l.valor)}</td>
                  <td className="px-3 py-1.5"><StatusBadge value={l.resultado} /></td>
                  <td className="px-3 py-1.5">
                    {l.descripcionMotivo}
                    {l.detalle && <div className="text-xs text-slate-500">{l.detalle}</div>}
                  </td>
                  <td className="px-3 py-1.5 text-right font-mono">{formatCop(l.saldoAnterior)}</td>
                  <td className="px-3 py-1.5 text-right font-mono">{formatCop(l.saldoPosterior)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <nav className="mt-2 flex items-center gap-3 text-sm">
          <button type="button" disabled={page === 0} onClick={() => setParams({ pagina: String(page - 1) })} className="rounded border border-slate-300 bg-white px-3 py-1 disabled:opacity-40">
            « Anterior
          </button>
          <span className="text-slate-600">Pagina {page + 1} de {totalPages}</span>
          <button type="button" disabled={page + 1 >= totalPages} onClick={() => setParams({ pagina: String(page + 1) })} className="rounded border border-slate-300 bg-white px-3 py-1 disabled:opacity-40">
            Siguiente »
          </button>
        </nav>
      </QueryState>
    </section>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="font-mono">{value}</dd>
    </div>
  )
}

function useElapsedSeconds(active: boolean): number {
  const [seconds, setSeconds] = useState(0)
  useEffect(() => {
    if (!active) {
      return
    }
    setSeconds(0)
    const started = Date.now()
    const timer = window.setInterval(() => setSeconds(Math.floor((Date.now() - started) / 1000)), 500)
    return () => window.clearInterval(timer)
  }, [active])
  return seconds
}
