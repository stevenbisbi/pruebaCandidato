/** Un numero de pagina, o un hueco ("…") entre numeros que no se muestran. */
export type PageItem = number | 'gap'

/**
 * Los numeros de pagina a mostrar: siempre la primera y la ultima, y las paginas alrededor de la
 * actual. El resto se resume con un hueco. Ejemplo con 100 paginas y la actual en 50:
 * 1 … 48 49 50 51 52 … 100.
 *
 * @param around cuantas paginas mostrar a cada lado de la actual
 */
export function pageItems(current: number, total: number, around = 2): PageItem[] {
  if (total <= 1) {
    return [1]
  }
  const from = Math.max(2, current - around)
  const to = Math.min(total - 1, current + around)
  const items: PageItem[] = [1]
  if (from > 2) {
    items.push('gap')
  }
  for (let page = from; page <= to; page++) {
    items.push(page)
  }
  if (to < total - 1) {
    items.push('gap')
  }
  items.push(total)
  return items
}
