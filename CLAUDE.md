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

3. Guardian Open Platform (https://open-platform.theguardian.com)
    Features:
    - Free tier ("developer" key): rate-limited: up to 500 calls/day, up to 1 call/second and access to over 1.9 million articles.
    - Endpoints: `/search` (content), `/tags`, `/sections`, `/editions`, single item (path-based)
    - Results are paginated, 10 per page by default (`page-size` param, 1-50)
    - Base URL: `https://content.guardianapis.com/` (HTTPS supported/encouraged, incl. client-side use)
    - No official Java client library — call it directly with Spring's `RestClient`, same pattern as NewsAPI/GNews

    Authentication:
    - `api-key` query param on every request, e.g. `?api-key=YOUR_KEY` (sign up at https://open-platform.theguardian.com/access)

    Query operators (supported in `q`):
    - `AND`, `OR` (default/implicit between terms), `NOT` — AND has higher precedence than OR, use parentheses to override
      e.g. `q=debate AND (economy OR immigration)`, `q=debate AND NOT immigration`
    - Exact phrase search with double quotes, e.g. `q="mitochondrial donation"`
    - Filter params (not `q`) support their own boolean syntax: AND = `,`, OR = `|`, NOT = `-`, grouped with `()`

    **GET /search** — all content, filtered/searched
    Params:
    - `q` — free text search, supports operators above
    - `query-fields` — which indexed fields `q` searches, e.g. `body`, `body,thumbnail`
    - `section` — restrict to section(s), e.g. `football`
    - `tag` — restrict to tag(s), e.g. `technology/apple`
    - `reference` / `reference-type` — restrict by reference, e.g. `isbn/9780718178949` / `isbn`
    - `ids` — restrict to specific content IDs
    - `rights` — `syndicatable` | `subscription-databases`
    - `production-office` — e.g. `aus`
    - `lang` — ISO language code, e.g. `en`, `fr`
    - `star-rating` — 1-5
    - `from-date` / `to-date` — `YYYY-MM-DD`
    - `use-date` — which date `from-date`/`to-date` filter on: `published` (default), `first-publication`, `newspaper-edition`, `last-modified`
    - `order-by` — `newest` (default), `oldest`, `relevance` (default when `q` is set)
    - `order-date` — which date to sort by: `published` (default), `newspaper-edition`, `last-modified`
    - `page` — default 1
    - `page-size` — default 10, max 50
    - `show-fields` — comma-separated extra fields to include, e.g. `trailText,headline,thumbnail,body,byline,wordcount` (see field list below; `all` for everything)
    - `show-tags` — comma-separated tag types to include: `blog,contributor,keyword,newspaper-book,newspaper-book-section,publication,series,tone,type` (or `all`)
    - `show-section` — `true`/`false`, include section metadata
    - `show-blocks`, `show-elements`, `show-references`, `show-rights` — see Guardian docs for sub-options (blocks/live-blog content, media elements, ISBN/IMDB-style references, rights)
    - `format` — `json` (default) | `xml`
    - `callback` — JSONP callback name for cross-origin requests

    `show-fields` values worth knowing for the mapper: `trailText` (HTML summary), `headline` (HTML), `body` (full HTML article body), `standfirst`, `byline`, `thumbnail`, `wordcount`, `lastModified`, `shortUrl`, `starRating`. All are strings (HTML where noted); booleans/integers still come back as strings.

    Deep pagination: `page`/`page-size` only reliably work down to a few thousand results. Beyond that, use the `/content/{id}/next` endpoint — take the `id` of the last result seen and call `https://content.guardianapis.com/content/{that id}/next?<same q/page-size/order-by params>`; repeat until a response returns fewer than `page-size` results, which signals the end.

    **GET /tags** — list/search the ~50,000+ categorisation tags (types: `keyword`, `series`, `contributor`, `tone`, `type`, `blog`)
    Params: `q` (tag contains this text), `web-title` (tag starts with this text), `type`, `section`, `reference`, `reference-type`, `page`, `page-size`, `show-references`

    **GET /sections** — list content sections (used to logically group content, e.g. `technology`, `football`)
    Params: `q` (filter by section name)

    **GET /editions** — list regionalised front pages (UK, US, Australia, Europe)
    Params: `q` (filter by edition name)

    **Single item** — replace `theguardian.com` with `content.guardianapis.com` in any Guardian web URL (or use `id`/`apiUrl` values from other endpoints) to fetch the API representation of that content, tag, or section. Supports the same filter/date/paging/`show-*` params as `/search`.

    Response shape (`/search`):
    ```json
    {
      "response": {
        "status": "ok",
        "userTier": "developer",
        "total": 5857,
        "startIndex": 1,
        "pageSize": 10,
        "currentPage": 1,
        "pages": 586,
        "orderBy": "relevance",
        "results": [
          {
            "id": "world/2022/oct/21/russia-ukraine-war-latest-what-we-know-on-day-240-of-the-invasion",
            "type": "article",
            "sectionId": "world",
            "sectionName": "World news",
            "webPublicationDate": "2022-10-21T14:06:14Z",
            "webTitle": "Russia-Ukraine war latest: what we know on day 240 of the invasion",
            "webUrl": "string",
            "apiUrl": "string",
            "isHosted": false,
            "pillarId": "pillar/news",
            "pillarName": "News"
          }
        ]
      }
    }
    ```
    Everything is nested under a top-level `response` object (unlike NewsAPI/GNews, which are flat) — the mapper needs to unwrap `response.results` rather than a top-level `articles` array. No `status: error` example is published for this API; treat non-`ok` `status` or a non-2xx HTTP code as an error and surface the HTTP status/body.

    Notes for the mapping layer:
    - Base response has minimal fields (`id`, `type`, `sectionId`, `sectionName`, `webPublicationDate`, `webTitle`, `webUrl`, `apiUrl`, `isHosted`, `pillarId`, `pillarName`) — no summary, author, or image unless requested via `show-fields`/`show-tags`/`show-elements`. Must set `show-fields=trailText,byline,thumbnail,body` (or similar) to get comparable data to NewsAPI/GNews (summary, author, image).
    - No dedicated `author` field — author comes back as `byline` (via `show-fields=byline`) or as a `contributor`-type tag (via `show-tags=contributor`), needs its own extraction logic per provider.
    - `webPublicationDate` is consistently ISO 8601 UTC with `Z` suffix, more consistent than the other two providers.
    - No description/summary field by default — closest equivalent is `trailText` (HTML, via `show-fields`), which may contain markup to strip for plain-text display.
    - `total`/`pages`/`currentPage` are at `response` level (not per-article) — useful for building pagination controls directly.

    Search criteria only the Guardian can honour (NewsAPI has no equivalent):
    - Section (`section`), e.g. Tech = `technology`, Sport = `sport`, Finance = `business`. Exposed in the UI as section chips.
    - Tag (`tag`), e.g. `technology/apple`. Accepted by the backend (`tags` param) but not in the UI yet.

### How the APIs are combined
- The backend (`ArticleSearchService`) sends each search to every provider that can honour all of its filters, merges the results newest-first and drops duplicate URLs. If one provider fails the others' articles are still returned.
- Sections/tags -> Guardian only. Outlet (`domains`) filter -> NewsAPI, except `theguardian.com`, which the Guardian API answers (NewsAPI's free plan has no coverage of it). No filters -> both.
- Because of that, the UI selects The Guardian and locks the outlet picker (dropping any other outlets) while a section is chosen, and the API rejects sections/tags combined with any other outlet with a 400.
- `/api/articles` returns `{ "articles": [...], "warnings": [{ "provider", "message" }] }`. A warning is added for each provider that failed (budget used up, upstream error, timeout) while another succeeded; the UI shows them above the results. If every provider fails the request itself fails.
- Only the Guardian's summary (`trailText`), byline and thumbnail are fetched, never the article body, so Guardian articles have no `content`.
- Calls to both APIs have a 5s connect / 10s read timeout (`spring.http.client.*`), and one search waits at most `search.provider-timeout` (15s) for any provider.
- Each provider has its own daily call budget and cache. GNews is documented above but not integrated.

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
