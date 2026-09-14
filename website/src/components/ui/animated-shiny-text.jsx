/*
 * Animated Shiny Text — ported from Magic UI, MIT.
 * Copyright (c) 2023 Dillion Verma.
 * https://github.com/magicuidesign/magicui
 *
 * Plain JSX instead of TypeScript, with only the dark-mode colors, since this
 * site has no light mode. The glare shows through because the text itself is
 * slightly transparent; give it an opaque color and the shine disappears. The
 * `shiny-text` keyframes live in tw.css.
 */
import { cn } from '../../lib/utils.js'

export function AnimatedShinyText({ children, className, shimmerWidth = 100, style, ...props }) {
  return (
    <span
      style={{ '--shiny-width': `${shimmerWidth}px`, ...style }}
      className={cn(
        'mx-auto max-w-md text-neutral-400/70',
        'bg-size-[var(--shiny-width)_100%] bg-clip-text bg-position-[0_0] bg-no-repeat [transition:background-position_1s_cubic-bezier(.6,.6,0,1)_infinite] motion-safe:animate-shiny-text',
        'bg-linear-to-r from-transparent via-50% via-white/80 to-transparent',
        className,
      )}
      {...props}
    >
      {children}
    </span>
  )
}
