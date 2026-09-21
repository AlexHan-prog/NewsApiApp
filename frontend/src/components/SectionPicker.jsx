import { SECTIONS } from '../data/sections'

// Multi-select: `selected` is an array of Guardian section ids. Sections are a Guardian feature (NewsAPI has no
// equivalent), so choosing any narrows the search to The Guardian.
function SectionPicker({ selected, onChange }) {
  function toggle(id) {
    onChange(selected.includes(id) ? selected.filter((s) => s !== id) : [...selected, id])
  }

  return (
    <div className="section-picker" role="group" aria-labelledby="section-picker-label">
      <p className="section-picker-label" id="section-picker-label">
        Sections
      </p>
      <ul className="section-options">
        {SECTIONS.map((section) => (
          <li key={section.id}>
            <button
              type="button"
              aria-pressed={selected.includes(section.id)}
              onClick={() => toggle(section.id)}
            >
              {section.name}
            </button>
          </li>
        ))}
      </ul>
      {selected.length > 0 && <p className="section-hint">Sections search The Guardian only.</p>}
    </div>
  )
}

export default SectionPicker
