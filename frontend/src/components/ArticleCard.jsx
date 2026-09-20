import { useState } from 'react'

// Always dd/mm/yyyy, independent of the browser locale. UTC so the date matches NewsAPI's timestamp.
function formatDate(isoString) {
  const date = new Date(isoString)
  if (!isoString || Number.isNaN(date.getTime())) return null

  const dd = String(date.getUTCDate()).padStart(2, '0')
  const mm = String(date.getUTCMonth() + 1).padStart(2, '0')
  return `${dd}/${mm}/${date.getUTCFullYear()}`
}

function ArticleCard({ article }) {
  const [imageFailed, setImageFailed] = useState(false)

  // source.id can be null; author, description, urlToImage and publishedAt can all be null.
  const sourceName = article.source?.name || article.source?.id
  const publishedDate = formatDate(article.publishedAt)
  const showImage = article.urlToImage && !imageFailed

  return (
    <article className="article-card">
      {showImage && (
        <img
          className="article-image"
          src={article.urlToImage}
          alt=""
          loading="lazy"
          referrerPolicy="no-referrer"
          onError={() => setImageFailed(true)}
        />
      )}
      <div className="article-body">
        <h2>
          <a href={article.url} target="_blank" rel="noreferrer">
            {article.title}
          </a>
        </h2>
        <p className="article-meta">
          {[
            sourceName,
            article.author && `By ${article.author}`,
            publishedDate && <time dateTime={article.publishedAt}>{publishedDate}</time>,
          ]
            .filter(Boolean)
            .map((part, i) => (
              <span key={i}>{part}</span>
            ))}
        </p>
        {article.description && <p>{article.description}</p>}
      </div>
    </article>
  )
}

export default ArticleCard
