// Outlets offered in the picker. `domain` is what gets sent to NewsAPI's `domains` parameter:
// lower-case, no protocol, no "www.". Add a line to offer another outlet.
// The exception is theguardian.com: NewsAPI's free plan has no coverage of it, so the backend answers that outlet
// from the Guardian API instead.
//
// Every entry below was checked against NewsAPI on 2026-09-20 (GET /v2/everything?domains=<domain>): it returned
// results and the articles' hosts belonged to that domain. These were tried and REMOVED because they returned no
// articles on the free plan, so don't re-add them without re-testing:
//   no results:               nytimes.com, abcnews.go.com, telegraph.co.uk, news.sky.com,
//                             france24.com, ft.com, economist.com, arstechnica.com
//   count but zero articles:  reuters.com, independent.co.uk, engadget.com
// Coverage changes over time, and the free plan only reaches back about a month, so re-check occasionally.
export const OUTLETS = [
  // General news
  { name: 'BBC News', domain: 'bbc.co.uk' },
  { name: 'BBC (international)', domain: 'bbc.com' },
  { name: 'The Guardian', domain: 'theguardian.com' },
  { name: 'CNN', domain: 'cnn.com' },
  { name: 'Associated Press', domain: 'apnews.com' },
  { name: 'The Washington Post', domain: 'washingtonpost.com' },
  { name: 'USA Today', domain: 'usatoday.com' },
  { name: 'NBC News', domain: 'nbcnews.com' },
  { name: 'CBS News', domain: 'cbsnews.com' },
  { name: 'Fox News', domain: 'foxnews.com' },
  { name: 'NPR', domain: 'npr.org' },
  { name: 'Al Jazeera', domain: 'aljazeera.com' },
  { name: 'DW', domain: 'dw.com' },
  { name: 'TIME', domain: 'time.com' },
  { name: 'Newsweek', domain: 'newsweek.com' },
  { name: 'HuffPost', domain: 'huffpost.com' },

  // Politics
  { name: 'Politico', domain: 'politico.com' },
  { name: 'Axios', domain: 'axios.com' },
  { name: 'The Hill', domain: 'thehill.com' },

  // Business
  { name: 'Bloomberg', domain: 'bloomberg.com' },
  { name: 'The Wall Street Journal', domain: 'wsj.com' },
  { name: 'CNBC', domain: 'cnbc.com' },
  { name: 'Forbes', domain: 'forbes.com' },
  { name: 'Business Insider', domain: 'businessinsider.com' },

  // Technology and science
  { name: 'TechCrunch', domain: 'techcrunch.com' },
  { name: 'The Verge', domain: 'theverge.com' },
  { name: 'Wired', domain: 'wired.com' },
  { name: 'Gizmodo', domain: 'gizmodo.com' },
  { name: 'Mashable', domain: 'mashable.com' },
  { name: 'Nature', domain: 'nature.com' },
  { name: 'Scientific American', domain: 'scientificamerican.com' },

  // Sport and entertainment
  { name: 'ESPN', domain: 'espn.com' },
  { name: 'Variety', domain: 'variety.com' },
  { name: 'The Hollywood Reporter', domain: 'hollywoodreporter.com' },
]
