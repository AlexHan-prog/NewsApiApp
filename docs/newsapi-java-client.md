# NewsAPI Java client (News-API-Java)

Third-party wrapper library for NewsAPI's REST endpoints. Use this instead of hand-rolling HTTP calls to newsapi.org for the backend integration.

Library: https://github.com/KwabenBerko/News-API-Java
Distributed via JitPack, not Maven Central.

## Gradle setup

`build.gradle` (root, or `backend/build.gradle` if the news client only lives in a submodule):

```groovy
allprojects {
    repositories {
        // existing repos (mavenCentral(), etc.) stay as they are
        maven { url 'https://jitpack.io' }
    }
}
```

Dependency:

```groovy
dependencies {
    implementation 'com.github.KwabenBerko:News-API-Java:1.0.0'
}
```

## Client setup

```java
NewsApiClient newsApiClient = new NewsApiClient("YOUR_API_KEY");
```

The API key should come from `application.properties` / `application-local.properties` (or an env var), never hardcoded. See the root `.env` / `.env.example` for where the key is expected to live in this project.

## Usage

The client is callback-based (`onSuccess` / `onFailure`), not synchronous and not a `CompletableFuture` — wrap it if the rest of the Spring service layer expects a blocking call or a reactive type.

### /v2/everything

```java
newsApiClient.getEverything(
    new EverythingRequest.Builder()
        .q("trump")
        .build(),
    new NewsApiClient.ArticlesResponseCallback() {
        @Override
        public void onSuccess(ArticleResponse response) {
            System.out.println(response.getArticles().get(0).getTitle());
        }

        @Override
        public void onFailure(Throwable throwable) {
            System.out.println(throwable.getMessage());
        }
    }
);
```

`EverythingRequest.Builder` mirrors the `/v2/everything` query params documented in `CLAUDE.md` (`q`, `searchIn`, `sources`, `domains`, `excludeDomains`, `from`, `to`, `language`, `sortBy`, `pageSize`, `page`) — check the builder's available methods in the library source/javadoc rather than assuming full 1:1 coverage, since third-party wrappers sometimes lag the official API.

### /v2/top-headlines

```java
newsApiClient.getTopHeadlines(
    new TopHeadlinesRequest.Builder()
        .q("bitcoin")
        .language("en")
        .build(),
    new NewsApiClient.ArticlesResponseCallback() {
        @Override
        public void onSuccess(ArticleResponse response) {
            System.out.println(response.getArticles().get(0).getTitle());
        }

        @Override
        public void onFailure(Throwable throwable) {
            System.out.println(throwable.getMessage());
        }
    }
);
```

### /v2/top-headlines/sources

```java
newsApiClient.getSources(
    new SourcesRequest.Builder()
        .language("en")
        .country("us")
        .build(),
    new NewsApiClient.SourcesCallback() {
        @Override
        public void onSuccess(SourcesResponse response) {
            System.out.println(response.getSources().get(0).getName());
        }

        @Override
        public void onFailure(Throwable throwable) {
            System.out.println(throwable.getMessage());
        }
    }
);
```

## Integration notes for this project

- **Bridging callback → Spring service**: the app's `NewsProvider` interface (see CLAUDE.md architecture notes) should expose a synchronous or `CompletableFuture`-returning method. Wrap the callback in a `CompletableFuture` (complete it in `onSuccess`, `completeExceptionally` in `onFailure`) so the rest of the service layer doesn't need to know this client is callback-based.
- **Error handling**: `onFailure(Throwable)` fires for both network failures and API error responses (e.g. rate limit, invalid key) — inspect the throwable's message/type to distinguish "API unavailable" from "bad request" from "rate limited" for the graceful-error-handling requirement in the brief. Don't assume every failure is a 5xx.
- **Version pinning**: `1.0.0` is the latest published version as of when this doc was written — worth a quick check on the GitHub releases page for anything newer before relying on it long-term.
- **Not officially maintained by NewsAPI** — this is a community wrapper, not NewsAPI's own SDK. If a builder method is missing for a param you need, either fall back to a raw HTTP call (e.g. Spring's `RestClient`/`WebClient`) for that one endpoint, or extend/fork the wrapper.
