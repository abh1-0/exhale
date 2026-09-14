/*
 * Stepper — ported from Origin UI, MIT.
 * Copyright (c) 2025 coss.com, originally Copyright (c) 2025 Origin UI.
 * https://github.com/cosscom/coss/tree/main/apps/origin
 *
 * Changes from the original: plain JSX instead of TypeScript; the check is an
 * inline SVG rather than lucide-react; `asChild` and the loading state are gone,
 * along with the Radix Slot the first needed. And the trigger resets its own
 * button chrome, because this site has no preflight to do it.
 */
import { createContext, useCallback, useContext, useState } from 'react'
import { cn } from '../../lib/utils.js'

const StepperContext = createContext(undefined)
const StepItemContext = createContext(undefined)

const useStepper = () => {
  const context = useContext(StepperContext)
  if (!context) throw new Error('useStepper must be used within a Stepper')
  return context
}

const useStepItem = () => {
  const context = useContext(StepItemContext)
  if (!context) throw new Error('useStepItem must be used within a StepperItem')
  return context
}

export function Stepper({
  defaultValue = 0,
  value,
  onValueChange,
  orientation = 'horizontal',
  className,
  ...props
}) {
  const [activeStep, setInternalStep] = useState(defaultValue)

  const setActiveStep = useCallback(
    (step) => {
      if (value === undefined) setInternalStep(step)
      onValueChange?.(step)
    },
    [value, onValueChange],
  )

  return (
    <StepperContext.Provider value={{ activeStep: value ?? activeStep, orientation, setActiveStep }}>
      <div
        className={cn(
          'group/stepper inline-flex data-[orientation=horizontal]:w-full data-[orientation=horizontal]:flex-row data-[orientation=vertical]:flex-col',
          className,
        )}
        data-orientation={orientation}
        data-slot="stepper"
        {...props}
      />
    </StepperContext.Provider>
  )
}

export function StepperItem({ step, completed = false, disabled = false, className, children, ...props }) {
  const { activeStep } = useStepper()
  const state = completed || step < activeStep ? 'completed' : activeStep === step ? 'active' : 'inactive'

  return (
    <StepItemContext.Provider value={{ isDisabled: disabled, state, step }}>
      <div
        className={cn(
          'group/step flex items-center group-data-[orientation=horizontal]/stepper:flex-row group-data-[orientation=vertical]/stepper:flex-col',
          className,
        )}
        data-slot="stepper-item"
        data-state={state}
        {...props}
      >
        {children}
      </div>
    </StepItemContext.Provider>
  )
}

export function StepperTrigger({ className, children, ...props }) {
  const { setActiveStep } = useStepper()
  const { step, isDisabled } = useStepItem()

  return (
    <button
      className={cn(
        'inline-flex cursor-pointer items-center gap-3 rounded-full border-0 bg-transparent p-0 font-sans text-inherit outline-none focus-visible:z-10 focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:pointer-events-none disabled:opacity-50',
        className,
      )}
      data-slot="stepper-trigger"
      disabled={isDisabled}
      onClick={() => setActiveStep(step)}
      type="button"
      {...props}
    >
      {children}
    </button>
  )
}

export function StepperIndicator({ className, ...props }) {
  const { state, step } = useStepItem()

  return (
    <span
      className={cn(
        'relative flex size-6 shrink-0 items-center justify-center rounded-full bg-muted font-medium text-muted-foreground text-xs data-[state=active]:bg-primary data-[state=completed]:bg-primary data-[state=active]:text-primary-foreground data-[state=completed]:text-primary-foreground',
        className,
      )}
      data-slot="stepper-indicator"
      data-state={state}
      {...props}
    >
      <span className="transition-all group-data-[state=completed]/step:scale-0 group-data-[state=completed]/step:opacity-0">
        {step}
      </span>
      <svg
        aria-hidden="true"
        className="absolute scale-0 opacity-0 transition-all group-data-[state=completed]/step:scale-100 group-data-[state=completed]/step:opacity-100"
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="M20 6 9 17l-5-5" />
      </svg>
    </span>
  )
}

export function StepperTitle({ className, ...props }) {
  return <h3 className={cn('font-medium text-foreground text-sm', className)} data-slot="stepper-title" {...props} />
}

export function StepperDescription({ className, ...props }) {
  return <p className={cn('text-muted-foreground text-sm', className)} data-slot="stepper-description" {...props} />
}

export function StepperSeparator({ className, ...props }) {
  return (
    <div
      className={cn(
        'm-0.5 bg-muted group-data-[orientation=horizontal]/stepper:h-0.5 group-data-[orientation=vertical]/stepper:h-12 group-data-[orientation=horizontal]/stepper:w-full group-data-[orientation=vertical]/stepper:w-0.5 group-data-[orientation=horizontal]/stepper:flex-1 group-data-[state=completed]/step:bg-primary',
        className,
      )}
      data-slot="stepper-separator"
      {...props}
    />
  )
}
