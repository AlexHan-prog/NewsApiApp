// Relative URL: proxied to Spring Boot by Vite in dev, same origin when served from the JAR.
const ARTICLES_URL = '/api/articles'

// Resolves to { articles, warnings }. `warnings` is [{ provider, message }], one per news API that failed while the
// others succeeded, so the caller can say the list is incomplete. If every API fails the promise rejects instead.
export async function searchArticles(keyword, { domains = [], sections = [], signal } = {}) {
  const params = new URLSearchParams()
  if (keyword) params.set('keyword', keyword)
  if (domains.length > 0) params.set('domains', domains.join(','))
  if (sections.length > 0) params.set('sections', sections.join(','))
  
  console.log(`Request url: ${ARTICLES_URL}?${params}`)
  const response = await fetch(`${ARTICLES_URL}?${params}`, { signal })

  if (!response.ok) {
    // Spring's error body is {status, error, message, path}; the body may not be JSON (e.g. proxy errors).
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || `Request failed (${response.status})`)
  }

  return response.json()
}
