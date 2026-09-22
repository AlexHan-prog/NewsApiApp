import { useState } from 'react'

// Always dd/mm/yyyy, independent of the browser locale. UTC so the date matches NewsAPI's timestamp.
function formatDate(isoString) {
  const date = new Date(isoString)
  if (!isoString || Number.isNaN(date.getTime())) return null

  const dd = String(date.getUTCDate()).padStart(2, '0')
  const mm = String(date.getUTCMonth() + 1).padStart(2, '0')
  return `${dd}/${mm}/${date.getUTCFullYear()}`
}

const LEANING_NAMES = { LEFT: 'Left', CENTER: 'Center', RIGHT: 'Right' }

// leaning.score is Claude's own stated confidence in its label (0-1), shown as a percentage - its self-assessment,
// not a calibrated statistic. The text label is always shown, so the colour is never the only cue. The badge is a
// <details> summary: expanding it shows Claude's explanation and the quotes backing it up.
function LeaningBadge({ leaning }) {
  const name = LEANING_NAMES[leaning.label] ?? leaning.label
  return (
    <details className="leaning">
      <summary
        className={`leaning-badge leaning-${String(leaning.label).toLowerCase()}`}
        title="Claude's own assessment after reading the full article. The percentage is its stated confidence, not a calibrated statistic - expand for its reasoning and the quotes it points to."
      >
        {name} · {Math.round(leaning.score * 100)}%
      </summary>
      <div className="leaning-detail">
        {leaning.explanation && <p>{leaning.explanation}</p>}
        {leaning.excerpts?.length > 0 && (
          <ul className="leaning-excerpts">
            {leaning.excerpts.map((excerpt, i) => (
              <li key={i} className={`leaning-excerpt leaning-${String(excerpt.side).toLowerCase()}`}>
                <blockquote>“{excerpt.quote}”</blockquote>
                <p className="leaning-excerpt-reason">
                  {LEANING_NAMES[excerpt.side] ?? excerpt.side}: {excerpt.reason}
                </p>
              </li>
            ))}
          </ul>
        )}
      </div>
    </details>
  )
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
            article.section,
            article.author && `By ${article.author}`,
            publishedDate && <time dateTime={article.publishedAt}>{publishedDate}</time>,
          ]
            .filter(Boolean)
            .map((part, i) => (
              <span key={i}>{part}</span>
            ))}
        </p>
        {article.leaning && <LeaningBadge leaning={article.leaning} />}
        {article.description && <p>{article.description}</p>}
      </div>
    </article>
  )
}

export default ArticleCard
