import { useRef, useState, type FormEvent } from 'react'
import { errorMessage, useGetBatchLinesQuery, useGetBatchQuery, useUploadCsvMutation } from '../api/api'
import { QueryState } from '../components/QueryState'
import { StatusBadge } from '../components/StatusBadge'
import { formatCop } from '../lib/money'
import { useUrlParams } from '../lib/url'

const PAGE_SIZE = 100

/**
 * Pantalla de carga de lotes (RF-26, RF-27).
 * Cuando el lote termina, su id queda en la URL (?lote=ID): si recargas la pagina ves el
 * resultado, no se vuelve a enviar nada.
 */
export function BatchPage() {
  const [params, setParams] = useUrlParams()
  const batchId = params.get('lote')
  const page = Number(params.get('pagina') ?? '0')

  return (
    <div className="space-y-6">
      <UploadForm onUploaded={(id) => setParams({ lote: id, pagina: null })} />
      {batchId && <BatchResult id={batchId} page={page} onPageChange={(p) => setParams({ pagina: String(p) })} />}
    </div>
  )
}

function UploadForm({ onUploaded }: { onUploaded: (batchId: string) => void }) {
  const [file, setFile] = useState<File | null>(null)
  const [uploadCsv, { isLoading, error }] = useUploadCsvMutation()
  // RF-27: evita el doble envio. Un useRef cambia al instante; el estado de React (isLoading)
  // tarda un render, y un doble clic rapido alcanzaria a enviar dos veces.
  const sending = useRef(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!file || sending.current) {
      return
    }
    sending.current = true
    try {
      const batch = await uploadCsv(file).unwrap()
      onUploaded(batch.loteId)
    } catch {
      // El error se muestra abajo con errorMessage(error).
    } finally {
      sending.current = false
    }
  }

  return (
    <form onSubmit={submit} className="space-y-3 rounded border border-slate-200 bg-white p-4">
      <label className="block text-sm text-slate-700">
        Archivo CSV de Tesoreria
        <input
          type="file"
          accept=".csv"
          disabled={isLoading}
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          className="mt-1 block text-sm"
        />
      </label>
      <button type="submit" disabled={!file || isLoading} className="rounded bg-blue-700 px-4 py-1.5 text-sm font-medium text-white disabled:opacity-40">
        {isLoading ? 'Procesando…' : 'Procesar lote'}
      </button>
      {error && <p role="alert" className="text-sm text-red-700">{errorMessage(error)}</p>}
    </form>
  )
}

function BatchResult({ id, page, onPageChange }: { id: string; page: number; onPageChange: (page: number) => void }) {
  const batch = useGetBatchQuery(id)
  const lines = useGetBatchLinesQuery({ id, page })
  const totalPages = lines.data ? Math.max(1, Math.ceil(lines.data.total / PAGE_SIZE)) : 1

  return (
    <section className="space-y-4">
      <QueryState isLoading={batch.isFetching && !batch.data} error={errorMessage(batch.error)} isEmpty={false} onRetry={batch.refetch}>
        {batch.data && (
          <div className="rounded border border-slate-200 bg-white p-4">
            <h2 className="font-semibold">Lote {batch.data.loteId}</h2>
            <p className="mt-2 text-sm">
              {batch.data.totalLineas} lineas: {batch.data.lineasAplicadas} aplicadas ({formatCop(batch.data.valorAplicado)}) y{' '}
              {batch.data.lineasRechazadas} rechazadas ({formatCop(batch.data.valorRechazado)}).
            </p>
          </div>
        )}
      </QueryState>

      <QueryState
        isLoading={lines.isFetching && !lines.data}
        error={errorMessage(lines.error)}
        isEmpty={lines.data !== undefined && lines.data.lineas.length === 0}
        emptyMessage="El lote no tiene lineas."
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
              {lines.data?.lineas.map((line) => (
                <tr key={line.linea} className={line.resultado === 'RECHAZADO' ? 'border-t border-slate-100 bg-red-50' : 'border-t border-slate-100'}>
                  <td className="px-3 py-1.5">{line.linea}</td>
                  <td className="px-3 py-1.5 font-mono">{line.referencia}</td>
                  <td className="px-3 py-1.5 font-mono">{line.numeroFactura}</td>
                  <td className="px-3 py-1.5 text-right font-mono">{formatCop(line.valor)}</td>
                  <td className="px-3 py-1.5"><StatusBadge value={line.resultado} /></td>
                  <td className="px-3 py-1.5">
                    {line.descripcionMotivo}
                    {line.detalle && <div className="text-xs text-slate-500">{line.detalle}</div>}
                  </td>
                  <td className="px-3 py-1.5 text-right font-mono">{formatCop(line.saldoAnterior)}</td>
                  <td className="px-3 py-1.5 text-right font-mono">{formatCop(line.saldoPosterior)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <nav className="mt-2 flex items-center gap-3 text-sm">
          <button type="button" disabled={page === 0} onClick={() => onPageChange(page - 1)} className="rounded border border-slate-300 bg-white px-3 py-1 disabled:opacity-40">
            « Anterior
          </button>
          <span className="text-slate-600">Pagina {page + 1} de {totalPages}</span>
          <button type="button" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)} className="rounded border border-slate-300 bg-white px-3 py-1 disabled:opacity-40">
            Siguiente »
          </button>
        </nav>
      </QueryState>
    </section>
  )
}
