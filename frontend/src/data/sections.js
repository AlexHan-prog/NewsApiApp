// Sections offered in the picker. `id` is what gets sent to the Guardian's `section` parameter (see its /sections
// endpoint for the full list); `name` is the label shown to the user. Add a line to offer another section.
// Note the Guardian's own naming: "Sport" is `sport`, and finance news lives under `business`.
export const SECTIONS = [
  { name: 'Tech', id: 'technology' },
  { name: 'Sport', id: 'sport' },
  { name: 'Finance', id: 'business' },
  { name: 'Politics', id: 'politics' },
  { name: 'Science', id: 'science' },
  { name: 'World', id: 'world' },
  { name: 'Environment', id: 'environment' },
  { name: 'Culture', id: 'culture' },
  { name: 'US news', id: 'us-news' },
  { name: 'UK news', id: 'uk-news' },
]
