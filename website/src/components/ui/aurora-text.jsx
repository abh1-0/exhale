/*
 * Aurora Text — ported from Magic UI, MIT.
 * Copyright (c) 2023 Dillion Verma.
 * https://github.com/magicuidesign/magicui
 *
 * Plain JSX instead of TypeScript. The drift only runs for readers who have not
 * asked for less motion, and there is a little padding under the glyphs so the
 * text clip does not shave the bottom off a descender. The `aurora` keyframes
 * live in tw.css.
 */
import { memo } from 'react'
import { cn } from '../../lib/utils.js'

export const AuroraText = memo(function AuroraText({
  children,
  className,
  colors = ['#FF0080', '#7928CA', '#0070F3', '#38bdf8'],
  speed = 1,
}) {
  return (
    <span className={cn('relative inline-block', className)}>
      <span className="sr-only">{children}</span>
      <span
        aria-hidden="true"
        className="relative bg-size-[200%_auto] bg-clip-text pb-[0.12em] text-transparent motion-safe:animate-aurora"
        style={{
          backgroundImage: `linear-gradient(135deg, ${colors.join(', ')}, ${colors[0]})`,
          WebkitBackgroundClip: 'text',
          WebkitTextFillColor: 'transparent',
          animationDuration: `${10 / speed}s`,
        }}
      >
        {children}
      </span>
    </span>
  )
})
