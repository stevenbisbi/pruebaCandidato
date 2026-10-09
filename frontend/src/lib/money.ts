/**
 * Formato de montos en pesos colombianos (RF-28): "$ 37.500.483,40".
 *
 * La API entrega los montos como texto decimal ("37500483.40") y aqui se formatean operando sobre
 * el texto, sin convertir a number. Intl.NumberFormat recibe un number (double) y pierde precision
 * por encima de 2^53; con la escala de esta base (NUMERIC(18,2)) eso es alcanzable.
 */
export function formatCop(value: string | null | undefined): string {
  if (value === null || value === undefined || value.trim() === '') {
    return '—'
  }
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(value.trim())
  if (!match) {
    return value
  }
  const [, sign, integerPart, decimalPart = ''] = match
  const integer = integerPart.replace(/^0+(?=\d)/, '').replace(/\B(?=(\d{3})+(?!\d))/g, '.')
  const decimals = decimalPart.padEnd(2, '0')
  return `${sign}$ ${integer},${decimals}`
}

/** Valida un monto escrito por el usuario en un filtro: digitos con hasta dos decimales. */
export function isDecimalInput(value: string): boolean {
  return /^\d+(\.\d{1,2})?$/.test(value)
}
