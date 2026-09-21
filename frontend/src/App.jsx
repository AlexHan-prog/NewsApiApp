import { useState } from 'react'
import { searchArticles } from './api/articlesApi'
import ArticleCard from './components/ArticleCard'
import OutletPicker from './components/OutletPicker'
import Pagination from './components/Pagination'
import SearchBar from './components/SearchBar'
import SectionPicker from './components/SectionPicker'
import { GUARDIAN_DOMAIN } from './data/sections'
import masthead from './assets/Daily_Bugle_masthead.png'
import './App.css'

const PAGE_SIZE = 10

function App() {
  const [articles, setArticles] = useState([])
  const [warnings, setWarnings] = useState([]) // news APIs that failed while others answered
  const [page, setPage] = useState(1)
  const [domains, setDomains] = useState([])
  const [sections, setSections] = useState([])
  const [status, setStatus] = useState('idle') // idle | loading | error | done
  const [error, setError] = useState('')

  const totalPages = Math.ceil(articles.length / PAGE_SIZE)
  const visibleArticles = articles.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  async function handleSearch(keyword) {
  
    setStatus('loading')
    setError('')
    try {
      console.log(keyword)
      const results = await searchArticles(keyword, { domains, sections })

      setArticles(results.articles)
      setWarnings(results.warnings)
      setPage(1)
      setStatus('done')
    } catch (err) {
      setError(err.message)
      setStatus('error')
    }
  }

  // Sections only exist on The Guardian, so picking one replaces the outlet filter with The Guardian (dropping any
  // other outlets) and OutletPicker is locked; clearing the sections leaves no outlets selected.
  function handleSectionsChange(nextSections) {
    setSections(nextSections)
    setDomains(nextSections.length > 0 ? [GUARDIAN_DOMAIN] : [])
  }

  function handlePageChange(nextPage) {
    setPage(nextPage)
    window.scrollTo({ top: 0 })
  }

  return (
    <main className="app">
      <header className="masthead">
        <h1>
          <img className="masthead-image" src={masthead} alt="Daily Bugle" />
        </h1>
      </header>
      <SearchBar
        onSearch={handleSearch}
        disabled={status === 'loading'}
        allowEmpty={domains.length > 0 || sections.length > 0}
      />
      <OutletPicker selected={domains} onChange={setDomains} locked={sections.length > 0} />
      <SectionPicker selected={sections} onChange={handleSectionsChange} />

      {status === 'loading' && <p className="status">Searching…</p>}
      {status === 'error' && (
        <p className="status status-error" role="alert">
          {error}
        </p>
      )}
      {status === 'done' && warnings.length > 0 && (
        <div className="status status-warning" role="status">
          <p>Some results may be missing:</p>
          <ul>
            {warnings.map((warning) => (
              <li key={warning.provider}>
                <strong>{warning.provider}</strong>: {warning.message}
              </li>
            ))}
          </ul>
        </div>
      )}
      {status === 'done' && articles.length === 0 && <p className="status">No articles found.</p>}
      {status === 'done' && (
        <>
          <div className="article-list">
            {visibleArticles.map((article) => (
              <ArticleCard key={article.url} article={article} />
            ))}
          </div>
          <Pagination page={page} totalPages={totalPages} onPageChange={handlePageChange} />
        </>
      )}
    </main>
  )
}

export default App
