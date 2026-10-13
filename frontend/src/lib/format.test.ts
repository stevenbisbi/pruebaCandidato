import { describe, expect, it } from 'vitest'
import { currentMonthRange, formatCount, formatDate, formatDateTime } from './format'
import { validateJson } from './batchJson'
import { applyParams } from './router'

describe('formatDate', () => {
  it('muestra una fecha de la API como dia/mes/ano sin correrla por la zona horaria', () => {
    expect(formatDate('2026-03-15')).toBe('15/03/2026')
    expect(formatDate(null)).toBe('—')
  })
})

describe('formatDateTime', () => {
  it('muestra los instantes en hora de Colombia, sin importar la zona del navegador', () => {
    // 04:30 UTC del 16 son las 23:30 del 15 en Colombia: el caso de CF-05.
    expect(formatDateTime('2026-03-16T04:30:00Z')).toBe('15/03/2026 23:30')
    expect(formatDateTime('2026-03-15T23:30:00-05:00')).toBe('15/03/2026 23:30')
    expect(formatDateTime(null)).toBe('—')
  })
})

describe('currentMonthRange', () => {
  it('usa el mes de Colombia aunque en UTC ya sea el mes siguiente', () => {
    // 31 de marzo a las 23:30 en Colombia = 1 de abril a las 04:30 UTC.
    expect(currentMonthRange(new Date('2026-04-01T04:30:00Z'))).toEqual({ from: '2026-03-01', to: '2026-03-31' })
  })

  it('calcula el ultimo dia de febrero', () => {
    expect(currentMonthRange(new Date('2028-02-10T12:00:00Z'))).toEqual({ from: '2028-02-01', to: '2028-02-29' })
  })
})

describe('formatCount', () => {
  it('usa punto como separador de miles', () => {
    expect(formatCount(50000)).toBe('50.000')
  })
})

describe('applyParams', () => {
  it('cambia, repite y borra parametros sin tocar los demas', () => {
    const params = applyParams('?nit=900&status=PARCIAL&cursor=FV-1', {
      status: ['PAGADA', 'PARCIAL'],
      cursor: null,
      dueFrom: '',
    })

    expect(params.get('nit')).toBe('900')
    expect(params.getAll('status')).toEqual(['PAGADA', 'PARCIAL'])
    expect(params.has('cursor')).toBe(false)
    expect(params.has('dueFrom')).toBe(false)
  })
})

describe('validateJson', () => {
  it('acepta un lote con pagos', () => {
    expect(validateJson('{"loteId":"L-1","pagos":[{"referencia":"P-1"}]}')).toBe('')
  })

  it('explica por que un texto no sirve', () => {
    expect(validateJson('{"loteId": ')).toMatch(/^El texto no es un JSON valido/)
    expect(validateJson('[1, 2]')).toMatch(/lista "pagos"/)
    expect(validateJson('{"pagos": []}')).toBe('El lote no contiene pagos.')
  })
})
