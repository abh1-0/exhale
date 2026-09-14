/*
 * A link card for the "Something else" doors.
 *
 * Magic UI's Shine Border, held back until hover: four cards all shimmering at
 * once is a slot machine, and the one under the pointer is the only one that
 * has earned it.
 */
import { ShineBorder } from './shine-border.jsx'

export const SHINE = ['var(--rose)', 'var(--iris)', 'var(--ember)']

export function DoorCard({ href, title, body }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noreferrer"
      className="group relative flex flex-col gap-1.5 overflow-hidden rounded-2xl border border-white/10 bg-white/[0.025] p-5 transition-[background-color,border-color,translate] duration-300 hover:-translate-y-0.5 hover:border-transparent hover:bg-white/[0.05]"
    >
      <ShineBorder
        duration={8}
        shineColor={SHINE}
        className="opacity-0 transition-opacity duration-300 group-hover:opacity-100 group-focus-visible:opacity-100"
      />
      <b className="font-semibold text-[15.5px] text-foreground">{title}</b>
      <span className="text-[13.5px] text-muted-foreground leading-snug">{body}</span>
    </a>
  )
}
