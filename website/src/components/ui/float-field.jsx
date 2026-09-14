/*
 * A field whose label lifts out of the way.
 *
 * Origin UI's "input with label animation" (comp-32) and "autogrowing textarea"
 * (comp-77), both MIT, reworked for glass. The original lifts the label onto
 * the border and paints a background chip behind it to cut the line. A frosted
 * sheet has no single background to paint, so the label lifts to the inside
 * top of the box instead.
 *
 * `:placeholder-shown` is how the label knows the box is empty, so a field with
 * no example text is given a blank one. The example text stays transparent until
 * focus: a resting form shows the questions, not a column of made-up answers.
 */
import { cn } from '../../lib/utils.js'
import { Input, Textarea } from './input.jsx'

export function FloatField({
  id,
  label,
  optional = false,
  hint,
  multiline = false,
  lead = false,
  rows,
  placeholder,
  className,
  ...props
}) {
  const Control = multiline ? Textarea : Input

  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <div className="relative">
        <Control
          id={id}
          rows={multiline ? rows : undefined}
          placeholder={placeholder || ' '}
          className={cn(
            'peer rounded-xl border-white/12 bg-white/[0.03] px-4 text-base tracking-[-0.01em] text-foreground shadow-none',
            'placeholder:text-transparent focus:placeholder:text-muted-foreground/60',
            'hover:border-white/20 focus-visible:bg-white/[0.05] focus-visible:ring-ring/25',
            multiline
              ? 'field-sizing-content max-h-80 min-h-28 resize-none pt-7 pb-3 leading-relaxed'
              : lead
                ? 'h-[4.5rem] pt-6 pb-2 text-xl font-semibold tracking-[-0.02em]'
                : 'h-14 pt-5 pb-1.5',
            optional && 'pe-24',
          )}
          {...props}
        />
        <label
          htmlFor={id}
          className={cn(
            'pointer-events-none absolute start-4 text-muted-foreground transition-all duration-200 ease-out',
            multiline ? 'top-4' : '-translate-y-1/2 top-1/2',
            lead ? 'text-lg' : 'text-[15px]',
            'peer-focus:top-2.5 peer-focus:translate-y-0 peer-focus:font-medium peer-focus:text-primary peer-focus:text-xs',
            'peer-not-placeholder-shown:top-2.5 peer-not-placeholder-shown:translate-y-0 peer-not-placeholder-shown:font-medium peer-not-placeholder-shown:text-xs',
          )}
        >
          {label}
        </label>
        {optional ? (
          <span className="pointer-events-none absolute end-3 top-3 rounded-full border border-white/10 px-2 py-0.5 font-medium text-[10px] text-muted-foreground/80 uppercase tracking-[0.06em]">
            Optional
          </span>
        ) : null}
      </div>
      {hint ? <p className="ps-1 text-muted-foreground text-xs">{hint}</p> : null}
    </div>
  )
}
