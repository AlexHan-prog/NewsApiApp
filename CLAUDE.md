## Stack:
- Frontend: React
- Backend: Java + Spring Boot

### News APIs:
1. NewsAPI (https://newsapi.org)
    Features:
    - Rate limit: 100 reqs/day (verify against your account dashboard, not shown in the endpoint docs)
    - Search articles and get live top headlines
    - Free tier: /v2/everything restricted to ~1 month of history (verify against account)
    - Articles have ~24hr publish delay on free tier (verify against account)
    - No Java client library: call it directly with Spring's `RestClient` (`NewsClientConfig` bean, base URL `https://newsapi.org/v2`, `X-Api-Key` header)

    Authentication (use only ONE of these):
    - `apiKey` query param, e.g. `?apiKey=YOUR_KEY`
    - `X-Api-Key` HTTP header
    - `Authorization` HTTP header (Bearer prefix optional; do NOT base64-encode the key)
    - Missing/invalid key → 401, response body: `{"status":"error","code":"apiKeyMissing","message":"..."}`

    Endpoints:

    **GET /v2/everything** — search all articles (millions of sources, last ~5 years per NewsAPI, though free tier caps history to ~1 month)
    Params:
    - `q` — keyword/phrase. Supports quoted exact match `"..."`, required `+word`, excluded `-word`, and `AND`/`OR`/`NOT` with parentheses. Max 500 chars, must be URL-encoded.
    - `searchIn` — restrict `q` to `title`, `description`, and/or `content` (comma-separated). Default: all fields.
    - `sources` — comma-separated source identifiers, max 20.
    - `domains` / `excludeDomains` — comma-separated domains to include/exclude, e.g. `bbc.co.uk,techcrunch.com`.
    - `from` / `to` — ISO 8601 date or datetime, e.g. `2026-09-19` or `2026-09-19T13:29:39`.
    - `language` — 2-letter ISO-639-1 code (`ar,de,en,es,fr,he,it,nl,no,pt,ru,sv,ud,zh`).
    - `sortBy` — `relevancy` | `popularity` | `publishedAt` (default).
    - `pageSize` — default 100, max 100.
    - `page` — default 1.

    **GET /v2/top-headlines** — breaking headlines by country, category, source, or keyword
    Params:
    - `country` — 2-letter ISO 3166-1 code (e.g. `us`). Cannot combine with `sources`.
    - `category` — `business|entertainment|general|health|science|sports|technology`. Cannot combine with `sources`.
    - `sources` — comma-separated source identifiers. Cannot combine with `country` or `category`.
    - `q` — keyword/phrase search.
    - `pageSize` — default 20, max 100.
    - `page` — default 1.

    **GET /v2/top-headlines/sources** — lists available sources (id, name, description, category, language, country) for use with the `sources` param above.

    Response shape (both /everything and /top-headlines):
    ```json
    {
      "status": "ok",
      "totalResults": 36,
      "articles": [
        {
          "source": { "id": "bbc-news", "name": "BBC News" },
          "author": "string | null",
          "title": "string",
          "description": "string | null",
          "url": "string",
          "urlToImage": "string | null",
          "publishedAt": "ISO 8601 UTC, e.g. 2026-09-19T11:01:34Z",
          "content": "string | null, truncated to 200 chars + [+N chars] suffix"
        }
      ]
    }
    ```
    Error shape: `{ "status": "error", "code": "...", "message": "..." }`

    Search criteria this app will expose to the user:
    - Keyword/phrase (`q`)
    - Date range (`from`/`to`)
    - Source domain (`domains`)
    - Language (`language`)

    Sorting options this app will expose:
    - Date published (`publishedAt`)
    - Relevancy (`relevancy`)
    - Popularity (`popularity`)

    Notes for the mapping layer:
    - `source.id` can be `null` (e.g. Fox Business, NPR) — don't rely on it for grouping/display, fall back to `source.name`.
    - `author` and `description` can be `null` — handle in the Article model and frontend rendering.
    - `content` is truncated on the free plan (`[+N chars]` suffix) — don't treat it as the full article body, it's a preview only; link out via `url` for the rest.
    - `publishedAt` format varies slightly by source (`Z` suffix vs `+00:00` vs fractional seconds) — parse with a tolerant ISO 8601 parser (e.g. `OffsetDateTime.parse`), a strict pattern may reject some variants.

2. GNews (https://gnews.io)
    Features:
    - Free tier: 100 requests/day, 1 request/sec, max 10 articles per request, non-commercial use only
    - ~12hr publish delay on free tier; ~30 days of historical data
    - Endpoint shapes closely mirror NewsAPI (`/search`, `/top-headlines`), but the JSON field names differ, still needs its own mapper
    - Base URL: `https://gnews.io/api/v4/`
    - No official Java client library (their docs show JS/Python/C#/PHP/Bash only) — call it directly with Spring's `RestClient`/`WebClient`, same as NewsAPI

    Authentication:
    - `apikey` query param on every request, e.g. `?apikey=YOUR_KEY`

    **GET /search** — keyword search across articles
    Params:
    - `q` — keywords, max 200 chars. Supports `AND`/`OR`/`NOT` and quoted phrases, same style as NewsAPI.
    - `lang` — 2-letter language code (`en`, `fr`, `de`, `es`, etc.)
    - `country` — 2-letter country code (`us`, `gb`, `au`, `de`, `fr`, etc.)
    - `from` / `to` — ISO 8601 datetime, e.g. `2026-09-19T13:29:39.000Z`
    - `sortby` — `publishedAt` (default) | `relevance`
    - `in` — which fields `q` searches: `title`, `description`, `content` (comma-separated), default `title,description`
    - `nullable` — comma-separated fields allowed to come back `null` instead of omitted/empty: `description`, `content`, `image`
    - `max` — results per page, default 10, max 100 (free tier effectively capped at 10)
    - `page` — pagination, up to 1000 articles deep

    **GET /top-headlines** — breaking headlines by category/country
    Params:
    - `category` — `general|world|nation|business|technology|entertainment|sports|science|health`
    - `lang`, `country`, `max`, `nullable`, `from`, `to`, `page`, `q` — same as `/search`

    Response shape (both endpoints) — verify exact field names against a live test call, GNews's own docs page doesn't render a full example, but this is the commonly documented shape:
    ```json
    {
      "totalArticles": 36,
      "articles": [
        {
          "title": "string",
          "description": "string | null",
          "content": "string | null",
          "url": "string",
          "image": "string | null",
          "publishedAt": "ISO 8601 UTC",
          "source": { "name": "string", "url": "string" }
        }
      ]
    }
    ```
    Note the differences from NewsAPI worth handling in the mapper: field is `image` not `urlToImage`; `source` has no `id`, only `name`/`url`; top-level key is `totalArticles` not `totalResults`; no `author` field at all (GNews doesn't expose one) — the app's `ArticleDto.author` will always be `null` for GNews-sourced articles, worth noting in the UI or README rather than treating as a bug.

    Error shape: `{"errors": ["message"]}` or `{"errors": {"paramName": "message"}}` for param-specific errors (different from NewsAPI's `{status, code, message}` shape — the mapping/error-handling layer needs to branch per provider). HTTP codes: 400 malformed request, 401 missing/invalid key, 403 daily quota exceeded (resets 00:00 UTC), 429 rate limit (1 req/sec) exceeded, 500/503 provider-side failure.

## Project structure
- React app setup with Vite. React App calls the Spring Boot API and renders results. 

- Spring Boot exposes an API that calls the news APIs and returns JSON.

### Submission:
- npm run build on the React app, copy output to Spring Boot's src/main/resources/static, so whole thing runs in one JAR with mvn spring-boot:run or java -jar

### Additional feature(s):
1. Political bias/leaniancy of articles.
    - Display whether an article is left, right or center leaning
    - Display whether news sources contain particular biases
    - May need to integrate with a fine-tuned LLM for political text
