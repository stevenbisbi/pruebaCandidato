import { formatCount } from '../lib/format'
import { pageItems } from '../lib/pages'
import { Button } from './ui'

interface Props {
  /** Pagina actual, desde 1. */
  page: number
  totalPages: number
  totalItems: number
  /** Nombre de lo que se cuenta, en plural: "facturas", "lineas". */
  itemLabel: string
  onPageChange: (page: number) => void
  /** Si se da, "Siguiente" la usa en lugar de saltar a page + 1 (por ejemplo, para seguir un cursor). */
  onNext?: () => void
  disabled?: boolean
}

/** Paginacion numerada: primera, anterior, numeros con huecos, siguiente y ultima. */
export function Pagination({ page, totalPages, totalItems, itemLabel, onPageChange, onNext, disabled = false }: Props) {
  const isFirst = page <= 1
  const isLast = page >= totalPages

  return (
    <nav aria-label="Paginacion" className="flex flex-wrap items-center gap-1 text-sm">
      <Button aria-label="Primera pagina" disabled={disabled || isFirst} onClick={() => onPageChange(1)}>«</Button>
      <Button aria-label="Pagina anterior" disabled={disabled || isFirst} onClick={() => onPageChange(page - 1)}>‹ Anterior</Button>
      {pageItems(page, totalPages).map((item, index) =>
        item === 'gap' ? (
          <span key={`gap-${index}`} className="px-1 text-slate-400">…</span>
        ) : (
          <Button
            key={item}
            aria-label={`Pagina ${item}`}
            aria-current={item === page ? 'page' : undefined}
            disabled={disabled}
            variant={item === page ? 'primary' : 'secondary'}
            onClick={() => item !== page && onPageChange(item)}
          >
            {formatCount(item)}
          </Button>
        ),
      )}
      <Button aria-label="Pagina siguiente" disabled={disabled || isLast} onClick={() => (onNext ? onNext() : onPageChange(page + 1))}>
        Siguiente ›
      </Button>
      <Button aria-label="Ultima pagina" disabled={disabled || isLast} onClick={() => onPageChange(totalPages)}>»</Button>
      <span className="ml-2 text-slate-600">
        Pagina {formatCount(page)} de {formatCount(totalPages)} · {formatCount(totalItems)} {itemLabel}
      </span>
    </nav>
  )
}
