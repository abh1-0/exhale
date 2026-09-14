/*
 * Input and Textarea — ported from Origin UI, MIT.
 * Copyright (c) 2025 coss.com, originally Copyright (c) 2025 Origin UI.
 * https://github.com/cosscom/coss/tree/main/apps/origin
 *
 * Plain JSX instead of TypeScript, the file-input variants dropped, and
 * `font-sans` added to both: with no preflight on this site, a form control
 * does not inherit the page's font on its own.
 */
import { cn } from '../../lib/utils.js'

export function Input({ className, type, ...props }) {
  return (
    <input
      className={cn(
        'flex h-9 w-full min-w-0 rounded-md border border-input bg-transparent px-3 py-1 font-sans text-sm shadow-xs outline-none transition-[color,box-shadow] placeholder:text-muted-foreground/70 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50',
        'focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50',
        'aria-invalid:border-destructive aria-invalid:ring-destructive/20',
        className,
      )}
      data-slot="input"
      type={type}
      {...props}
    />
  )
}

export function Textarea({ className, ...props }) {
  return (
    <textarea
      className={cn(
        'flex min-h-19.5 w-full rounded-md border border-input bg-transparent px-3 py-2 font-sans text-sm shadow-xs outline-none transition-[color,box-shadow] placeholder:text-muted-foreground/70 focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-destructive/20',
        className,
      )}
      data-slot="textarea"
      {...props}
    />
  )
}
