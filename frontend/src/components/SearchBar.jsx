import { useState } from 'react'

function SearchBar({ onSearch, disabled }) {
  const [keyword, setKeyword] = useState('')
  
  function handleSubmit(event) {
    event.preventDefault()
    const trimmed = keyword.trim()
    if (trimmed) {
      onSearch(trimmed)
    }
  }

  return (
    <form className="search-bar" onSubmit={handleSubmit} role="search">
      <input
        type="search"
        aria-label="Search articles"
        placeholder="Search news by keyword or phrase"
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
      />
      <button type="submit" disabled={disabled}>
        Search
      </button>
    </form>
  )
}

export default SearchBar
