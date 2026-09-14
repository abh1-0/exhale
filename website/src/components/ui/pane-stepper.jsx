/*
 * The composer's sections, as Origin UI's "stepper with inline titles and
 * descriptions" (comp-524, MIT).
 *
 * Zero-based on the outside, because panes are an array index; the stepper
 * itself counts from one, because that is the number printed in the circle.
 */
import {
  Stepper,
  StepperDescription,
  StepperIndicator,
  StepperItem,
  StepperSeparator,
  StepperTitle,
  StepperTrigger,
} from './stepper.jsx'

export function PaneStepper({ steps, value, onChange, locked }) {
  return (
    <Stepper
      value={value + 1}
      onValueChange={(step) => onChange(step - 1)}
      role="group"
      aria-label="Sections"
      className="mb-7 sm:mb-9"
    >
      {steps.map(({ title, detail }, i) => {
        const step = i + 1
        return (
          <StepperItem
            key={step}
            step={step}
            disabled={locked && i > value}
            className="not-last:flex-1 max-md:items-start"
          >
            <StepperTrigger
              className="rounded-lg px-1 py-1 max-md:flex-col"
              aria-current={i === value ? 'step' : undefined}
            >
              <StepperIndicator />
              <div className="text-center md:text-left">
                <StepperTitle>{title}</StepperTitle>
                <StepperDescription className="text-[13px] max-sm:hidden">{detail}</StepperDescription>
              </div>
            </StepperTrigger>
            {step < steps.length ? <StepperSeparator className="max-md:mt-3.5 md:mx-4" /> : null}
          </StepperItem>
        )
      })}
    </Stepper>
  )
}
