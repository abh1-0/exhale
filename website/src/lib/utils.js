import { clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** The shadcn `cn`: join conditional classes, and let the later Tailwind class win a conflict. */
export function cn(...inputs) {
  return twMerge(clsx(inputs))
}
