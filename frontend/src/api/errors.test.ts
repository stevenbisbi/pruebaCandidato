import { describe, expect, it } from 'vitest'
import { errorMessage } from './errors'

/** Los errores llegan con la forma que les da RTK Query: { status, data } u { status: 'PARSING_ERROR', originalStatus }. */
describe('errorMessage', () => {
  it('usa el mensaje que manda el backend', () => {
    const error = { status: 409, data: { status: 409, error: 'Conflict', message: 'El lote L-1 ya existe con un contenido distinto' } }

    expect(errorMessage(error)).toBe('El lote L-1 ya existe con un contenido distinto')
  })

  it('explica un 502 de nginx, que no trae JSON, mientras el backend arranca', () => {
    const error = { status: 'PARSING_ERROR', originalStatus: 502, data: '<html>Bad Gateway</html>' }

    expect(errorMessage(error)).toMatch(/^El servidor todavia no esta disponible/)
  })

  it('explica que no hay conexion', () => {
    expect(errorMessage({ status: 'FETCH_ERROR', error: 'TypeError: Failed to fetch' })).toBe(
      'No fue posible conectar con el servidor.')
  })

  it('da un mensaje generico con el codigo cuando no hay nada mejor', () => {
    expect(errorMessage({ status: 500, data: null })).toBe('Ocurrio un error inesperado (500).')
  })

  it('no muestra nada si no hay error', () => {
    expect(errorMessage(undefined)).toBe('')
  })
})
