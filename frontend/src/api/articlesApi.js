// Relative URL: proxied to Spring Boot by Vite in dev, same origin when served from the JAR.
const ARTICLES_URL = '/api/articles'

export async function searchArticles(keyword, { signal } = {}) {
  const response = await fetch(`${ARTICLES_URL}?keyword=${encodeURIComponent(keyword)}`, { signal })

  if (!response.ok) {
    // Spring's error body is {status, error, message, path}; the body may not be JSON (e.g. proxy errors).
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || `Request failed (${response.status})`)
  }

  return response.json()
}
