// Relative URL: proxied to Spring Boot by Vite in dev, same origin when served from the JAR.
const ARTICLES_URL = '/api/articles'
const LEANING_DEMO_URL = '/api/leaning-demo'

// Resolves to { articles, warnings }. `warnings` is [{ provider, message }], one per news API that failed while the
// others succeeded, so the caller can say the list is incomplete. If every API fails the promise rejects instead.
export async function searchArticles(keyword, { domains = [], sections = [], signal } = {}) {
  const params = new URLSearchParams()
  if (keyword) params.set('keyword', keyword)
  if (domains.length > 0) params.set('domains', domains.join(','))
  if (sections.length > 0) params.set('sections', sections.join(','))
  
  console.log(`Request url: ${ARTICLES_URL}?${params}`)
  return getJson(`${ARTICLES_URL}?${params}`, signal)
}

// A fixed demo: 5 Guardian Politics articles Claude has read in full and assessed for political leaning. Same
// { articles, warnings } shape as searchArticles. Independent of any search - never triggered by one.
export function fetchLeaningDemo({ signal } = {}) {
  return getJson(LEANING_DEMO_URL, signal)
}

async function getJson(url, signal) {
  const response = await fetch(url, { signal })

  if (!response.ok) {
    // Spring's error body is {status, error, message, path}; the body may not be JSON (e.g. proxy errors).
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || `Request failed (${response.status})`)
  }

  return response.json()
}
