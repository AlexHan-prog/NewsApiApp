import { useEffect, useState } from 'react'
import { fetchLeaningDemo, searchArticles } from './api/articlesApi'
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

  // A fixed demo shown only before the user's first search (see the render below); loaded once on mount, kept
  // separate from the search state above so the two never clobber each other.
  const [demo, setDemo] = useState({ status: 'loading', articles: [], warnings: [] })

  useEffect(() => {
    let cancelled = false
    fetchLeaningDemo()
      .then((results) => {
        if (!cancelled) setDemo({ status: 'done', articles: results.articles, warnings: results.warnings })
      })
      .catch((err) => {
        if (!cancelled) setDemo({ status: 'error', articles: [], warnings: [], error: err.message })
      })
    return () => {
      cancelled = true
    }
  }, [])

  const totalPages = Math.ceil(articles.length / PAGE_SIZE)
  const visibleArticles = articles.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  // Runs a request that resolves to { articles, warnings } and shows the outcome.
  async function load(request) {
    setStatus('loading')
    setError('')
    try {
      const results = await request()

      setArticles(results.articles)
      setWarnings(results.warnings)
      setPage(1)
      setStatus('done')
    } catch (err) {
      setError(err.message)
      setStatus('error')
    }
  }

  function handleSearch(keyword) {
    console.log(keyword)
    return load(() => searchArticles(keyword, { domains, sections }))
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

      {status === 'idle' && demo.articles.length > 0 && (
        <section className="leaning-demo">
          <h2>AI political leaning — demo</h2>
          <p className="leaning-demo-intro">
            {demo.articles.length} of The Guardian's latest Politics articles, each read in full and assessed by
            Claude for political leaning. This is a fixed demonstration of the feature, not part of your search.
          </p>
          {demo.warnings.length > 0 && (
            <div className="status status-warning" role="status">
              <p>Some demo articles may be missing an assessment:</p>
              <ul>
                {demo.warnings.map((warning) => (
                  <li key={warning.provider}>
                    <strong>{warning.provider}</strong>: {warning.message}
                  </li>
                ))}
              </ul>
            </div>
          )}
          <div className="article-list">
            {demo.articles.map((article) => (
              <ArticleCard key={article.url} article={article} />
            ))}
          </div>
        </section>
      )}

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
