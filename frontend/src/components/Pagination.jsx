// page is 1-based. At most ~10 pages (NewsAPI returns up to 100 articles), so every page number is shown.
function Pagination({ page, totalPages, onPageChange }) {
  if (totalPages <= 1) return null

  const pages = Array.from({ length: totalPages }, (_, i) => i + 1)

  return (
    <nav className="pagination" aria-label="Pagination">
      <button type="button" onClick={() => onPageChange(page - 1)} disabled={page === 1}>
        Previous
      </button>
      {pages.map((p) => (
        <button
          key={p}
          type="button"
          className={p === page ? 'active' : undefined}
          aria-current={p === page ? 'page' : undefined}
          aria-label={`Page ${p}`}
          onClick={() => onPageChange(p)}
        >
          {p}
        </button>
      ))}
      <button type="button" onClick={() => onPageChange(page + 1)} disabled={page === totalPages}>
        Next
      </button>
    </nav>
  )
}

export default Pagination
