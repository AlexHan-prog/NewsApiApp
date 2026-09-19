## Stack:
- Frontend: React
- Backend: Java + Spring Boot

### News APIs:
1. NewsAPI (https://newsapi.org)
    Features:
    - rate limit: 100 reqs/day
    - Search articles and get live top article headings
    - Search articles up to a month old
    - Articles have 24 hr delay (Assuming this is the delay between when they're published to when they're available through API.)

    Search Criteria/Params:
    - Keyword or Phrase (e.g. Tesla, Apple, AI)
    - Date published
    - Source domain name: e.g. all articles from 'theTimes.com'
    - Language 'e.g. English'

    Sorting Results
    - Date published
    - Relevancy to search keyword
    - Popularity of source

    Authentication (use only one of these 2):
    - Via the X-Api-Key HTTP header.
    - Via the Authorization HTTP header. Including Bearer is optional, and be sure not to base 64 encode it like you may have seen in other authentication tutorials.
    - If you don't append your API key correctly, or your API key is invalid, you will receive a 401 - Unauthorized

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

2. 



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