/*
 * Search over the answers, in the hero.
 *
 * The field is Origin UI's search input with an icon and a <kbd> hint (comp-25
 * and comp-26, MIT). The results are this page's own: the FAQ and the four ways
 * in, matched on every word typed, with a hit in a question ranked above a hit
 * in an answer or its keywords (see support.js). Most people arriving here have
 * a question that is already answered further down, and a box that finds it
 * beats scrolling for it.
 *
 * `/` focuses it from anywhere on the page, the way it does on GitHub.
 */
import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { cn } from '../../lib/utils.js'
import { Input } from './input.jsx'

const words = (text) =>
  text
    .toLowerCase()
    .split(/[^a-z0-9]+/)
    .filter((w) => w.length > 1)

/** A short piece of the answer around the first match, so the hit reads in context. */
function excerpt(paragraphs, terms) {
  const hit = paragraphs.find((p) => terms.some((t) => p.toLowerCase().includes(t))) || paragraphs[0]
  const lower = hit.toLowerCase()
  const term = terms.find((t) => lower.includes(t))
  const at = term ? lower.indexOf(term) : 0
  const start = at > 50 ? lower.indexOf(' ', at - 50) + 1 : 0
  const end = start + 130
  return `${start ? '…' : ''}${hit.slice(start, end).trim()}${end < hit.length ? '…' : ''}`
}

export function searchHelp(query, faq, paths) {
  const terms = words(query)
  if (!terms.length) return []

  const rank = (title, body) => {
    const t = title.toLowerCase()
    const b = body.toLowerCase()
    if (!terms.every((term) => t.includes(term) || b.includes(term))) return 0
    return terms.reduce((score, term) => score + (t.includes(term) ? 3 : 1), 0)
  }

  const results = []
  for (const item of faq) {
    const score = rank(item.q, `${item.a.join(' ')} ${item.keywords || ''}`)
    if (score) {
      results.push({ kind: 'answer', id: item.id, title: item.q, detail: excerpt(item.a, terms), score })
    }
  }
  for (const path of paths) {
    const score = rank(path.title, `${path.body} ${path.keywords || ''}`)
    if (score) results.push({ kind: 'path', id: path.id, title: path.title, detail: path.body, score })
  }

  return results.sort((a, b) => b.score - a.score).slice(0, 6)
}

export function HelpSearch({ faq, paths, onAnswer, onPath, className }) {
  const id = useId()
  const listId = `${id}-results`
  const inputRef = useRef(null)
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)

  const results = useMemo(() => searchHelp(query, faq, paths), [query, faq, paths])
  const showing = open && words(query).length > 0

  useEffect(() => {
    const onKey = (event) => {
      if (event.key !== '/' || event.metaKey || event.ctrlKey || event.altKey) return
      if (event.target.closest?.('input, textarea, select, [contenteditable="true"]')) return
      event.preventDefault()
      inputRef.current?.focus()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const choose = (result) => {
    setOpen(false)
    inputRef.current?.blur()
    if (result.kind === 'path') onPath(result.id)
    else onAnswer(result.id)
  }

  const onKeyDown = (event) => {
    if (event.key === 'ArrowDown' && results.length) {
      event.preventDefault()
      setOpen(true)
      setActive((i) => (i + 1) % results.length)
    } else if (event.key === 'ArrowUp' && results.length) {
      event.preventDefault()
      setActive((i) => (i - 1 + results.length) % results.length)
    } else if (event.key === 'Enter') {
      event.preventDefault()
      if (results[active]) choose(results[active])
    } else if (event.key === 'Escape') {
      if (query) setQuery('')
      else inputRef.current?.blur()
      setOpen(false)
    }
  }

  return (
    <div className={cn('relative', className)}>
      <label htmlFor={id} className="sr-only">
        Search the answers
      </label>

      <div className="relative">
        <Input
          ref={inputRef}
          id={id}
          type="text"
          inputMode="search"
          enterKeyHint="search"
          autoComplete="off"
          spellCheck={false}
          role="combobox"
          aria-expanded={showing}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={showing && results[active] ? `${id}-r${active}` : undefined}
          placeholder="Search the answers"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value)
            setActive(0)
            setOpen(true)
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setOpen(false)}
          onKeyDown={onKeyDown}
          className="peer h-14 rounded-2xl border-white/12 bg-white/[0.045] ps-12 pe-14 text-base text-foreground shadow-[0_24px_70px_-30px_rgba(0,0,0,0.95)] backdrop-blur-xl placeholder:text-muted-foreground/75 hover:border-white/20 focus-visible:bg-white/[0.07] focus-visible:ring-ring/25"
        />
        <div className="pointer-events-none absolute inset-y-0 start-0 flex items-center ps-4 text-muted-foreground/80 peer-focus:text-foreground">
          <svg
            aria-hidden="true"
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.3-4.3" />
          </svg>
        </div>
        <div className="pointer-events-none absolute inset-y-0 end-0 flex items-center pe-4 max-sm:hidden">
          <kbd className="inline-flex h-6 items-center rounded-md border border-white/12 px-2 font-medium font-sans text-[11px] text-muted-foreground/80">
            /
          </kbd>
        </div>
      </div>

      {showing ? (
        <div className="absolute inset-x-0 top-full z-30 mt-2 overflow-hidden rounded-2xl border border-white/10 bg-[#0b0b0e]/95 text-left shadow-[0_30px_80px_-20px_rgba(0,0,0,0.95)] backdrop-blur-xl">
          {results.length ? (
            <ul
              id={listId}
              role="listbox"
              aria-label="Results"
              className="m-0 max-h-[min(60vh,420px)] list-none overflow-y-auto p-1.5"
            >
              {results.map((result, i) => (
                <li
                  key={`${result.kind}-${result.id}`}
                  id={`${id}-r${i}`}
                  role="option"
                  aria-selected={i === active}
                  // Keep focus in the input so a click does not blur it shut first.
                  onMouseDown={(event) => event.preventDefault()}
                  onMouseEnter={() => setActive(i)}
                  onClick={() => choose(result)}
                  className="flex cursor-pointer items-start gap-3 rounded-xl px-3 py-2.5 aria-selected:bg-white/[0.07]"
                >
                  <span
                    className={cn(
                      'mt-0.5 shrink-0 rounded-full border px-2 py-0.5 font-medium text-[10px] uppercase tracking-[0.06em]',
                      result.kind === 'path'
                        ? 'border-primary/40 text-primary'
                        : 'border-white/12 text-muted-foreground',
                    )}
                  >
                    {result.kind === 'path' ? 'Contact' : 'Answer'}
                  </span>
                  <span className="flex min-w-0 flex-col gap-0.5">
                    <span className="font-medium text-[15px] text-foreground leading-snug">{result.title}</span>
                    <span className="text-[13px] text-muted-foreground leading-snug">{result.detail}</span>
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p id={listId} className="m-0 px-4 py-3.5 text-[14px] text-muted-foreground">
              Nothing here matches that. Pick the closest of the four below and it goes to GitHub.
            </p>
          )}
        </div>
      ) : null}
    </div>
  )
}
