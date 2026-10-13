/** Revisa el JSON antes de enviarlo, para dar un mensaje claro sin ir al servidor. */
export function validateJson(text: string): string {
  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch (e) {
    return `El texto no es un JSON valido: ${(e as Error).message}`
  }
  const pagos = (parsed as { pagos?: unknown } | null)?.pagos
  if (typeof parsed !== 'object' || parsed === null || !Array.isArray(pagos)) {
    return 'El JSON debe ser un objeto con la lista "pagos" (ver el ejemplo del Anexo A).'
  }
  if (pagos.length === 0) {
    return 'El lote no contiene pagos.'
  }
  return ''
}
