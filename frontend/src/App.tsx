import { BatchPage } from './pages/BatchPage'
import { DashboardPage } from './pages/DashboardPage'
import { InvoicesPage } from './pages/InvoicesPage'
import { navigate, useLocation } from './lib/url'

const ROUTES = [
  { path: '/facturas', label: 'Facturas', element: <InvoicesPage /> },
  { path: '/lotes', label: 'Carga de lotes', element: <BatchPage /> },
  { path: '/tablero', label: 'Tablero', element: <DashboardPage /> },
]

export default function App() {
  const { path } = useLocation()
  const route = ROUTES.find((r) => r.path === path) ?? ROUTES[0]

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <header className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-xl font-semibold">Conciliacion de pagos</h1>
        <nav className="flex gap-1 text-sm">
          {ROUTES.map((r) => (
            <a
              key={r.path}
              href={r.path}
              onClick={(e) => {
                e.preventDefault()
                navigate(r.path)
              }}
              className={`rounded px-3 py-1.5 ${r === route ? 'bg-blue-700 text-white' : 'text-slate-700 hover:bg-slate-200'}`}
            >
              {r.label}
            </a>
          ))}
        </nav>
      </header>
      <main>{route.element}</main>
    </div>
  )
}
