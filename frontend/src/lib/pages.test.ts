import { describe, expect, it } from 'vitest'
import { pageItems } from './pages'

describe('pageItems', () => {
  it('muestra todas las paginas cuando son pocas', () => {
    expect(pageItems(1, 1)).toEqual([1])
    expect(pageItems(2, 5)).toEqual([1, 2, 3, 4, 5])
  })

  it('resume con huecos y siempre deja ir a la primera y a la ultima', () => {
    expect(pageItems(50, 10000)).toEqual([1, 'gap', 48, 49, 50, 51, 52, 'gap', 10000])
  })

  it('no pone hueco cuando la ventana toca un extremo', () => {
    expect(pageItems(1, 10000)).toEqual([1, 2, 3, 'gap', 10000])
    expect(pageItems(3, 10000)).toEqual([1, 2, 3, 4, 5, 'gap', 10000])
    expect(pageItems(10000, 10000)).toEqual([1, 'gap', 9998, 9999, 10000])
  })
})
