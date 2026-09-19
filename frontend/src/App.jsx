import { useState } from 'react'
import { searchArticles } from './api/articlesApi'
import ArticleCard from './components/ArticleCard'
import SearchBar from './components/SearchBar'
import './App.css'

function App() {
  const [articles, setArticles] = useState([])
  const [status, setStatus] = useState('idle') // idle | loading | error | done
  const [error, setError] = useState('')

  async function handleSearch(keyword) {
  
    setStatus('loading')
    setError('')
    try {
      const results = await searchArticles(keyword)
      setArticles(results)
      setStatus('done')
    } catch (err) {
      setError(err.message)
      setStatus('error')
    }
  }

  return (
    <main className="app">
      <h1>News Search</h1>
      <SearchBar onSearch={handleSearch} disabled={status === 'loading'} />

      {status === 'loading' && <p className="status">Searching…</p>}
      {status === 'error' && (
        <p className="status status-error" role="alert">
          {error}
        </p>
      )}
      {status === 'done' && articles.length === 0 && <p className="status">No articles found.</p>}
      {status === 'done' && (
        <div className="article-list">
          {articles.map((article) => (
            <ArticleCard key={article.url} article={article} />
          ))}
        </div>
      )}
    </main>
  )
}

export default App
