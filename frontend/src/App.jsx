import { useState } from 'react'
import { searchArticles } from './api/articlesApi'
import ArticleCard from './components/ArticleCard'
import OutletPicker from './components/OutletPicker'
import Pagination from './components/Pagination'
import SearchBar from './components/SearchBar'
import masthead from './assets/Daily_Bugle_masthead.png'
import './App.css'

const PAGE_SIZE = 10

function App() {
  const [articles, setArticles] = useState([])
  const [page, setPage] = useState(1)
  const [domains, setDomains] = useState([])
  const [status, setStatus] = useState('idle') // idle | loading | error | done
  const [error, setError] = useState('')

  const totalPages = Math.ceil(articles.length / PAGE_SIZE)
  const visibleArticles = articles.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  async function handleSearch(keyword) {
  
    setStatus('loading')
    setError('')
    try {
      console.log(keyword)
      const results = await searchArticles(keyword, { domains })

      setArticles(results)
      setPage(1)
      setStatus('done')
    } catch (err) {
      setError(err.message)
      setStatus('error')
    }
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
      <SearchBar onSearch={handleSearch} disabled={status === 'loading'} allowEmpty={domains.length > 0} />
      <OutletPicker selected={domains} onChange={setDomains} />

      {status === 'loading' && <p className="status">Searching…</p>}
      {status === 'error' && (
        <p className="status status-error" role="alert">
          {error}
        </p>
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
