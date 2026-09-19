function ArticleCard({ article }) {
  return (
    <article className="article-card">
      <h2>
        <a href={article.url} target="_blank" rel="noreferrer">
          {article.title}
        </a>
      </h2>
      {/* NewsAPI can send a null description. */}
      {article.description && <p>{article.description}</p>}
    </article>
  )
}

export default ArticleCard
