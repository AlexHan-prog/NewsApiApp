import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { OUTLETS } from '../data/outlets'

const MAX_OPTIONS = 8
const DOMAIN_PATTERN = /^[a-z0-9-]+(\.[a-z0-9-]+)+$/

function labelFor(domain) {
  return OUTLETS.find((outlet) => outlet.domain === domain)?.name ?? domain
}

// Multi-select: `selected` is an array of domains. Type to filter the static OUTLETS list, or type a
// domain that isn't listed and add it as-is.
function OutletPicker({ selected, onChange }) {
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [highlight, setHighlight] = useState(0)
  const containerRef = useRef(null)
  const listId = useId()

  const options = useMemo(() => {
    const q = query.trim().toLowerCase()
    const matches = OUTLETS.filter(
      (outlet) =>
        !selected.includes(outlet.domain) &&
        (outlet.name.toLowerCase().includes(q) || outlet.domain.includes(q)),
    )
      .slice(0, MAX_OPTIONS)
      .map((outlet) => ({
        key: outlet.domain,
        domain: outlet.domain,
        label: `${outlet.name} (${outlet.domain})`,
      }))

    const isListed = OUTLETS.some((outlet) => outlet.domain === q)
    if (DOMAIN_PATTERN.test(q) && !isListed && !selected.includes(q)) {
      matches.push({ key: `custom:${q}`, domain: q, label: `Add "${q}"` })
    }
    return matches
  }, [query, selected])

  const active = Math.min(highlight, options.length - 1)
  const listVisible = open && options.length > 0

  useEffect(() => {
    function handleMouseDown(event) {
      if (!containerRef.current?.contains(event.target)) setOpen(false)
    }
    document.addEventListener('mousedown', handleMouseDown)
    return () => document.removeEventListener('mousedown', handleMouseDown)
  }, [])

  function pick(domain) {
    onChange([...selected, domain])
    setQuery('')
    setHighlight(0)
  }

  function remove(domain) {
    onChange(selected.filter((d) => d !== domain))
  }

  function handleKeyDown(event) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setOpen(true)
      if (options.length > 0) setHighlight((active + 1) % options.length)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setOpen(true)
      if (options.length > 0) setHighlight((active - 1 + options.length) % options.length)
    } else if (event.key === 'Enter') {
      if (listVisible && options[active]) {
        event.preventDefault()
        pick(options[active].domain)
      }
    } else if (event.key === 'Escape') {
      setOpen(false)
    } else if (event.key === 'Backspace' && query === '' && selected.length > 0) {
      remove(selected[selected.length - 1])
    }
  }

  return (
    <div className="outlet-picker" ref={containerRef}>
      {selected.length > 0 && (
        <ul className="outlet-chips" aria-label="Selected outlets">
          {selected.map((domain) => (
            <li key={domain} className="outlet-chip">
              {labelFor(domain)}
              <button type="button" aria-label={`Remove ${labelFor(domain)}`} onClick={() => remove(domain)}>
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      <input
        type="text"
        role="combobox"
        aria-label="Filter by outlet"
        aria-autocomplete="list"
        aria-expanded={listVisible}
        aria-controls={listId}
        aria-activedescendant={listVisible ? `${listId}-${active}` : undefined}
        placeholder="Filter by outlet (optional)"
        value={query}
        onChange={(event) => {
          setQuery(event.target.value)
          setHighlight(0)
          setOpen(true)
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={handleKeyDown}
      />
      {listVisible && (
        <ul className="outlet-options" id={listId} role="listbox">
          {options.map((option, i) => (
            <li
              key={option.key}
              id={`${listId}-${i}`}
              role="option"
              aria-selected={i === active}
              className={i === active ? 'active' : undefined}
              // mousedown (not click) so the input keeps focus and the list doesn't close first.
              onMouseDown={(event) => {
                event.preventDefault()
                pick(option.domain)
              }}
              onMouseEnter={() => setHighlight(i)}
            >
              {option.label}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default OutletPicker
