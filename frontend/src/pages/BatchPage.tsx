import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useGetBatchLinesQuery, useGetBatchQuery, useSubmitJsonBatchMutation, useUploadCsvMutation } from '../api/api'
import { errorMessage } from '../api/errors'
import { batchDownloadUrl } from '../api/query'
import { LINE_OUTCOMES, REJECTION_REASONS, type BatchSummary } from '../api/types'
import { Link } from '../components/Link'
import { Pagination } from '../components/Pagination'
import { QueryState } from '../components/QueryState'
import { StatusBadge } from '../components/StatusBadge'
import { Button, Card, INPUT_CLASS, LABEL_CLASS, Stat, TD_CLASS, TH_CLASS } from '../components/ui'
import { formatCount, formatDate, formatDateTime, OUTCOME_LABELS, REASON_LABELS } from '../lib/format'
import { formatAdjustment, formatCop } from '../lib/money'
import { validateJson } from '../lib/batchJson'
import { useUrlParams } from '../lib/router'

const PAGE_SIZE = 100

const JSON_EXAMPLE = JSON.stringify(
  {
    loteId: 'TES-2026-03-15-001',
    origen: 'TESORERIA',
    fechaGeneracion: '2026-03-15',
    pagos: [{ referencia: 'P-000001', numeroFactura: 'FV-0042199', valor: '37500483', fechaPago: '2026-03-15T23:30:00-05:00' }],
  },
  null,
  2,
)

/**
 * Carga de lotes (RF-26, RF-27). Cuando el lote termina, su id queda en la URL (?lote=ID): al
 * recargar la pagina se ve el resultado y no se vuelve a enviar nada. Ademas el backend reconoce un
 * reenvio del mismo lote y no lo aplica dos veces (RF-20).
 */
export function BatchPage() {
  const [params, setParams] = useUrlParams()
  const batchId = params.get('lote')
  const [lastReceipt, setLastReceipt] = useState<BatchSummary | null>(null)

  function onProcessed(receipt: BatchSummary) {
    setLastReceipt(receipt)
    setParams({ lote: receipt.id, outcome: null, reason: null, page: null })
  }

  return (
    <div className="space-y-6">
      <UploadForm onProcessed={onProcessed} />
      {batchId && (
        <>
          {lastReceipt?.id === batchId && <ReceiptBanner receipt={lastReceipt} />}
          <BatchResult id={batchId} />
        </>
      )}
    </div>
  )
}

type Mode = 'csv' | 'json'

function UploadForm({ onProcessed }: { onProcessed: (receipt: BatchSummary) => void }) {
  const [mode, setMode] = useState<Mode>('csv')
  const [file, setFile] = useState<File | null>(null)
  const [csvId, setCsvId] = useState('')
  const [json, setJson] = useState('')
  const [error, setError] = useState('')
  const [uploadCsv, csvState] = useUploadCsvMutation()
  const [submitJsonBatch, jsonState] = useSubmitJsonBatchMutation()
  const sending = csvState.isLoading || jsonState.isLoading
  const elapsed = useElapsedSeconds(sending)
  // RF-27: un ref cambia en el acto; el estado de React (sending) se ve recien en el siguiente
  // render, y un doble clic rapido alcanzaria a enviar dos veces.
  const inFlight = useRef(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (inFlight.current) {
      return
    }
    setError('')
    if (mode === 'json') {
      const problem = validateJson(json)
      if (problem) {
        setError(problem)
        return
      }
    } else if (!file) {
      return
    }
    inFlight.current = true
    try {
      // unwrap: devuelve la respuesta o lanza el error, en lugar de guardarlo en el estado del hook.
      const receipt =
        mode === 'csv' && file
          ? await uploadCsv({ file, id: csvId }).unwrap()
          : await submitJsonBatch(JSON.parse(json)).unwrap()
      onProcessed(receipt)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      inFlight.current = false
    }
  }

  const canSubmit = !sending && (mode === 'csv' ? file !== null : json.trim() !== '')

  return (
    <Card title="Cargar un lote de Tesoreria">
      <div role="tablist" className="mb-4 flex gap-1 text-sm">
        {(['csv', 'json'] as const).map((option) => (
          <button
            key={option}
            type="button"
            role="tab"
            aria-selected={mode === option}
            disabled={sending}
            onClick={() => {
              setMode(option)
              setError('')
            }}
            className={`rounded px-3 py-1.5 ${mode === option ? 'bg-slate-800 text-white' : 'text-slate-700 hover:bg-slate-100'}`}
          >
            {option === 'csv' ? 'Archivo CSV' : 'Pegar JSON'}
          </button>
        ))}
      </div>

      <form onSubmit={submit} className="space-y-3">
        {mode === 'csv' ? (
          <div className="flex flex-wrap items-end gap-4">
            <label className="block text-sm text-slate-700">
              Archivo CSV de Tesoreria
              <input
                type="file"
                accept=".csv,text/csv"
                disabled={sending}
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                className="mt-1 block text-sm"
              />
            </label>
            <label className={LABEL_CLASS}>
              Id del lote (opcional)
              <input
                value={csvId}
                onChange={(e) => setCsvId(e.target.value)}
                disabled={sending}
                placeholder="Se calcula del contenido"
                className={`${INPUT_CLASS} w-64`}
              />
            </label>
          </div>
        ) : (
          <div>
            <label className="block text-sm text-slate-700">
              Lote en JSON (formato del Anexo A)
              <textarea
                value={json}
                onChange={(e) => setJson(e.target.value)}
                disabled={sending}
                rows={10}
                spellCheck={false}
                placeholder={JSON_EXAMPLE}
                className="mt-1 block w-full rounded border border-slate-300 p-2 font-mono text-xs"
              />
            </label>
            <button type="button" onClick={() => setJson(JSON_EXAMPLE)} disabled={sending} className="mt-1 text-xs text-blue-700 underline">
              Pegar un ejemplo
            </button>
          </div>
        )}

        <div className="flex items-center gap-3">
          <Button type="submit" variant="primary" disabled={!canSubmit}>
            {sending ? 'Procesando…' : 'Procesar lote'}
          </Button>
          {sending && <SendingProgress seconds={elapsed} />}
        </div>
        {error && (
          <p role="alert" className="text-sm text-red-700">
            {error}
          </p>
        )}
      </form>
    </Card>
  )
}

/**
 * El lote se procesa dentro de la peticion: el backend no informa avance linea a linea
 * (LIMITACIONES.md). Se muestra el tiempo transcurrido y una barra indeterminada.
 */
function SendingProgress({ seconds }: { seconds: number }) {
  return (
    <div role="status" className="flex items-center gap-2 text-sm text-slate-600">
      <span className="relative block h-1.5 w-32 overflow-hidden rounded bg-slate-200">
        <span className="absolute inset-y-0 w-1/3 animate-pulse rounded bg-blue-600" />
      </span>
      Procesando el lote… {seconds} s
    </div>
  )
}

/** Segundos transcurridos mientras activo es true; vuelve a 0 cuando se apaga. */
function useElapsedSeconds(active: boolean): number {
  const [seconds, setSeconds] = useState(0)
  useEffect(() => {
    if (!active) {
      setSeconds(0)
      return
    }
    const startedAt = Date.now()
    const timer = window.setInterval(() => setSeconds(Math.floor((Date.now() - startedAt) / 1000)), 500)
    return () => window.clearInterval(timer)
  }, [active])
  return seconds
}

function ReceiptBanner({ receipt }: { receipt: BatchSummary }) {
  if (receipt.replayed) {
    return (
      <p role="status" className="rounded border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
        Este lote ya se habia procesado: no se aplico nada de nuevo y se muestra el resultado original.
      </p>
    )
  }
  return (
    <p role="status" className="rounded border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">
      Lote procesado: {formatCount(receipt.appliedLines)} pagos aplicados y {formatCount(receipt.rejectedLines)} rechazados.
    </p>
  )
}

function BatchResult({ id }: { id: string }) {
  const [params, setParams] = useUrlParams()
  const outcome = params.get('outcome')
  const reason = params.get('reason')
  // En la URL la pagina empieza en 1, como en facturas; la API cuenta desde 0.
  const page = Math.max(1, Number(params.get('page')) || 1)

  const summary = useGetBatchQuery(id)

  const lineQuery = new URLSearchParams({ page: String(page - 1), size: String(PAGE_SIZE) })
  if (outcome) {
    lineQuery.set('outcome', outcome)
  }
  if (reason) {
    lineQuery.set('reason', reason)
  }
  // Al paginar o filtrar, data conserva la pagina anterior mientras llega la nueva: la tabla no salta.
  const lines = useGetBatchLinesQuery({ id, queryString: lineQuery.toString() })
  const totalPages = lines.data ? Math.max(1, Math.ceil(lines.data.totalItems / PAGE_SIZE)) : 1

  function changeFilter(next: { outcome?: string; reason?: string }) {
    const nextReason = next.reason ?? reason ?? ''
    // Elegir un motivo de rechazo implica ver solo rechazos.
    const nextOutcome = nextReason !== '' ? 'RECHAZADO' : (next.outcome ?? outcome ?? '')
    setParams({ outcome: nextOutcome, reason: nextOutcome === 'APLICADO' ? null : nextReason, page: null })
  }

  // currentData: al cambiar de lote no se muestra el resumen del anterior mientras carga.
  const batch = summary.currentData

  return (
    <section className="space-y-4">
      <QueryState isLoading={summary.isFetching && !batch} error={errorMessage(summary.error)} isEmpty={false} onRetry={summary.refetch}>
        {batch && (
          <Card
            title={`Lote ${batch.id}`}
            actions={
              <a href={batchDownloadUrl(batch.id)} download className="text-sm text-blue-700 hover:underline">
                Descargar resultado (CSV)
              </a>
            }
          >
            <p className="mb-3 text-xs text-slate-500">
              {batch.source} · {batch.channel} · recibido {formatDateTime(batch.receivedAt)}
              {batch.generatedOn && <> · generado {formatDate(batch.generatedOn)}</>}
            </p>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <Stat label="Lineas" value={formatCount(batch.totalLines)} />
              <Stat label="Aplicadas" value={formatCount(batch.appliedLines)} hint={formatCop(batch.appliedAmount)} />
              <Stat label="Rechazadas" value={formatCount(batch.rejectedLines)} hint={formatCop(batch.rejectedAmount)} />
            </div>
          </Card>
        )}
      </QueryState>

      {batch && (
        <Card title="Resultado linea por linea">
          <div className="mb-3 flex flex-wrap items-end gap-4">
            <label className={LABEL_CLASS}>
              Resultado
              <select value={outcome ?? ''} onChange={(e) => changeFilter({ outcome: e.target.value, reason: '' })} className={INPUT_CLASS}>
                <option value="">Todos</option>
                {LINE_OUTCOMES.map((value) => (
                  <option key={value} value={value}>{OUTCOME_LABELS[value]}</option>
                ))}
              </select>
            </label>
            <label className={LABEL_CLASS}>
              Motivo de rechazo
              <select value={reason ?? ''} onChange={(e) => changeFilter({ reason: e.target.value })} className={INPUT_CLASS}>
                <option value="">Todos</option>
                {REJECTION_REASONS.map((value) => (
                  <option key={value} value={value}>{REASON_LABELS[value]}</option>
                ))}
              </select>
            </label>
          </div>

          <QueryState
            isLoading={lines.isLoading}
            error={errorMessage(lines.error)}
            isEmpty={lines.data !== undefined && lines.data.items.length === 0}
            emptyMessage="Ninguna linea cumple el filtro."
            onRetry={lines.refetch}
          >
            <div className={`overflow-x-auto ${lines.isFetching ? 'opacity-60' : ''}`}>
              <table className="min-w-full text-sm">
                <thead className="bg-slate-100 text-left text-xs uppercase text-slate-600">
                  <tr>
                    <th className={TH_CLASS}>Linea</th>
                    <th className={TH_CLASS}>Referencia</th>
                    <th className={TH_CLASS}>Factura</th>
                    <th className={TH_CLASS}>Fecha de pago</th>
                    <th className={`${TH_CLASS} text-right`}>Valor</th>
                    <th className={TH_CLASS}>Resultado</th>
                    <th className={TH_CLASS}>Motivo</th>
                    <th className={`${TH_CLASS} text-right`}>Saldo antes</th>
                    <th className={`${TH_CLASS} text-right`}>Saldo despues</th>
                    <th className={`${TH_CLASS} text-right`}>Ajuste</th>
                  </tr>
                </thead>
                <tbody>
                  {lines.data?.items.map((line) => (
                    <tr key={line.lineNumber} className={`border-t border-slate-100 ${line.outcome === 'RECHAZADO' ? 'bg-red-50' : ''}`}>
                      <td className={TD_CLASS}>{line.lineNumber}</td>
                      <td className={`${TD_CLASS} font-mono`}>{line.reference}</td>
                      <td className={`${TD_CLASS} font-mono`}>
                        {line.invoiceNumber && line.reason !== 'FACTURA_INEXISTENTE' ? (
                          <Link href={`/facturas/${encodeURIComponent(line.invoiceNumber)}`} className="text-blue-700 hover:underline">
                            {line.invoiceNumber}
                          </Link>
                        ) : (
                          line.invoiceNumber
                        )}
                      </td>
                      <td className={TD_CLASS}>{formatDateTime(line.paidAt)}</td>
                      <td className={`${TD_CLASS} text-right font-mono`}>{formatCop(line.amount)}</td>
                      <td className={TD_CLASS}><StatusBadge value={line.outcome} /></td>
                      <td className={TD_CLASS}>
                        {line.reason && <div className="text-red-800">{line.reasonDescription ?? REASON_LABELS[line.reason]}</div>}
                        {line.detail && <div className="text-xs text-slate-500">{line.detail}</div>}
                      </td>
                      <td className={`${TD_CLASS} text-right font-mono`}>{formatCop(line.balanceBefore)}</td>
                      <td className={`${TD_CLASS} text-right font-mono`}>{formatCop(line.balanceAfter)}</td>
                      <td className={`${TD_CLASS} text-right font-mono`}>{formatAdjustment(line.roundingAdjustment)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="mt-3">
              <Pagination
                page={Math.min(page, totalPages)}
                totalPages={totalPages}
                totalItems={lines.data?.totalItems ?? 0}
                itemLabel="lineas"
                onPageChange={(target) => setParams({ page: target <= 1 ? null : String(target) })}
                disabled={lines.isFetching}
              />
            </div>
          </QueryState>
        </Card>
      )}
    </section>
  )
}
