import type { ButtonHTMLAttributes, ReactNode } from 'react'

/** Piezas visuales repetidas en varias pantallas. Una interfaz sobria y consistente basta (seccion 1.2). */

export function Card({ title, children, actions }: { title?: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="rounded border border-slate-200 bg-white p-4">
      {(title || actions) && (
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          {title && <h2 className="font-semibold">{title}</h2>}
          {actions}
        </div>
      )}
      {children}
    </section>
  )
}

export function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded border border-slate-200 bg-white p-4">
      <div className="text-xs uppercase text-slate-500">{label}</div>
      <div className="mt-1 font-mono text-lg">{value}</div>
      {hint && <div className="mt-1 text-xs text-slate-500">{hint}</div>}
    </div>
  )
}

type ButtonVariant = 'primary' | 'secondary'

const BUTTON_STYLES: Record<ButtonVariant, string> = {
  primary: 'bg-blue-700 text-white hover:bg-blue-800',
  secondary: 'border border-slate-300 bg-white text-slate-800 hover:bg-slate-50',
}

export function Button({ variant = 'secondary', className = '', ...rest }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant }) {
  return (
    <button
      type="button"
      className={`rounded px-3 py-1.5 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-40 ${BUTTON_STYLES[variant]} ${className}`}
      {...rest}
    />
  )
}

export const INPUT_CLASS = 'mt-1 block rounded border border-slate-300 px-2 py-1 text-sm text-slate-900'
export const LABEL_CLASS = 'text-xs text-slate-600'
export const TH_CLASS = 'px-3 py-2'
export const TD_CLASS = 'px-3 py-1.5'
