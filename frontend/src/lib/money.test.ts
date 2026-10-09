import { describe, expect, it } from 'vitest'
import { formatCop } from './money'

describe('formatCop', () => {
  it('usa punto de miles y coma decimal', () => {
    expect(formatCop('37500483.40')).toBe('$ 37.500.483,40')
  })

  it('no pierde precision con montos por encima de 2^53', () => {
    // Como number, 9007199254740993.01 se redondea a 9007199254740992.
    expect(formatCop('9007199254740993.01')).toBe('$ 9.007.199.254.740.993,01')
  })

  it('completa los decimales y respeta montos pequenos y negativos', () => {
    expect(formatCop('0.4')).toBe('$ 0,40')
    expect(formatCop('100')).toBe('$ 100,00')
    expect(formatCop('-1250000.00')).toBe('-$ 1.250.000,00')
  })

  it('muestra un guion cuando no hay valor', () => {
    expect(formatCop(null)).toBe('—')
    expect(formatCop('')).toBe('—')
  })
})
