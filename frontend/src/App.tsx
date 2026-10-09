import { BatchPage } from './pages/BatchPage'
import { InvoicesPage } from './pages/InvoicesPage'

/** Dos pantallas: /facturas (por defecto) y /lotes. Los enlaces son normales: recargan la pagina. */
export default function App() {
  const isBatchPage = window.location.pathname === '/lotes'

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <header className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-xl font-semibold">Conciliacion de pagos</h1>
        <nav className="flex gap-1 text-sm">
          <a href="/facturas" className={isBatchPage ? 'rounded px-3 py-1.5 text-slate-700 hover:bg-slate-200' : 'rounded bg-blue-700 px-3 py-1.5 text-white'}>
            Facturas
          </a>
          <a href="/lotes" className={isBatchPage ? 'rounded bg-blue-700 px-3 py-1.5 text-white' : 'rounded px-3 py-1.5 text-slate-700 hover:bg-slate-200'}>
            Carga de lotes
          </a>
        </nav>
      </header>
      <main>{isBatchPage ? <BatchPage /> : <InvoicesPage />}</main>
    </div>
  )
}
