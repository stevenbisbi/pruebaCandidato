import type { ReactNode } from 'react'
import { Link } from './components/Link'
import { useLocation } from './lib/router'
import { BatchPage } from './pages/BatchPage'
import { DashboardPage } from './pages/DashboardPage'
import { InvoiceDetailPage } from './pages/InvoiceDetailPage'
import { InvoicesPage } from './pages/InvoicesPage'

const SECTIONS = [
  { href: '/facturas', label: 'Facturas' },
  { href: '/lotes', label: 'Carga de lotes' },
  { href: '/tablero', label: 'Tablero' },
]

/**
 * Rutas de la aplicacion. nginx devuelve index.html para cualquiera de ellas (nginx.conf), asi que
 * un enlace compartido abre directamente la misma pantalla.
 */
function route(pathname: string): { section: string; page: ReactNode } {
  const detail = /^\/facturas\/(.+)$/.exec(pathname)
  if (detail) {
    const number = decodeURIComponent(detail[1])
    // key: al pasar de una factura a otra, la pantalla arranca de cero.
    return { section: '/facturas', page: <InvoiceDetailPage key={number} number={number} /> }
  }
  switch (pathname) {
    case '/':
    case '/facturas':
      return { section: '/facturas', page: <InvoicesPage /> }
    case '/lotes':
      return { section: '/lotes', page: <BatchPage /> }
    case '/tablero':
      return { section: '/tablero', page: <DashboardPage /> }
    default:
      return { section: '', page: <NotFound /> }
  }
}

export default function App() {
  const { pathname } = useLocation()
  const { section, page } = route(pathname)

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <header className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-xl font-semibold">Conciliacion de pagos</h1>
        <nav className="flex gap-1 text-sm">
          {SECTIONS.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              aria-current={section === item.href ? 'page' : undefined}
              className={section === item.href ? 'rounded bg-blue-700 px-3 py-1.5 text-white' : 'rounded px-3 py-1.5 text-slate-700 hover:bg-slate-200'}
            >
              {item.label}
            </Link>
          ))}
        </nav>
      </header>
      <main>{page}</main>
    </div>
  )
}

function NotFound() {
  return (
    <div className="rounded border border-slate-200 bg-white p-6 text-center text-slate-600">
      Esta pagina no existe. <Link href="/facturas" className="text-blue-700 hover:underline">Ir a facturas</Link>
    </div>
  )
}
