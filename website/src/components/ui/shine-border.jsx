/*
 * Shine Border — ported from Magic UI, MIT.
 * Copyright (c) 2023 Dillion Verma.
 * https://github.com/magicuidesign/magicui
 *
 * Plain JSX instead of TypeScript; otherwise as published. The `shine`
 * keyframes live in tw.css. Pass CSS variables as colors and it retints with
 * the album art like the rest of the page.
 */
import { cn } from '../../lib/utils.js'

export function ShineBorder({ borderWidth = 1, duration = 14, shineColor = '#000000', className, style, ...props }) {
  return (
    <div
      style={{
        '--border-width': `${borderWidth}px`,
        '--duration': `${duration}s`,
        backgroundImage: `radial-gradient(transparent,transparent, ${
          Array.isArray(shineColor) ? shineColor.join(',') : shineColor
        },transparent,transparent)`,
        backgroundSize: '300% 300%',
        mask: 'linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)',
        WebkitMask: 'linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)',
        WebkitMaskComposite: 'xor',
        maskComposite: 'exclude',
        padding: 'var(--border-width)',
        ...style,
      }}
      className={cn(
        'motion-safe:animate-shine pointer-events-none absolute inset-0 size-full rounded-[inherit] will-change-[background-position]',
        className,
      )}
      {...props}
    />
  )
}
