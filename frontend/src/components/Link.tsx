import type { AnchorHTMLAttributes, MouseEvent } from 'react'
import { navigate } from '../lib/router'

/**
 * Enlace interno: cambia de pantalla sin recargar. Es un <a> real, asi que abrir en otra pestana
 * (Ctrl+clic, clic con la rueda) y copiar el enlace siguen funcionando como en cualquier pagina.
 */
export function Link({ href, onClick, ...rest }: AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }) {
  function handleClick(event: MouseEvent<HTMLAnchorElement>) {
    onClick?.(event)
    const opensElsewhere = event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey
    if (event.defaultPrevented || opensElsewhere) {
      return
    }
    event.preventDefault()
    navigate(href)
  }

  return <a href={href} onClick={handleClick} {...rest} />
}
