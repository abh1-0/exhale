import { useEffect } from 'react'
import App from './App.jsx'
import Release from './pages/Release.jsx'
import Support from './pages/Support.jsx'
import { RELEASE } from './release.js'
import { useRoute } from './router.jsx'

/**
 * Three pages, two string comparisons.
 *
 * `/release` without a version resolves to the current one, so the short URL
 * keeps working after the next release rather than rotting into a 404 — and any
 * path that is neither a release nor support is the home page, which is what a
 * marketing site should do with a typo.
 */
const isRelease = (path) => path === '/release' || path.startsWith('/release/')
const isSupport = (path) => path === '/support' || path.startsWith('/support/')

/**
 * The title and the canonical link are the two things a crawler and a preview
 * card read, and neither follows a client-side navigation on its own.
 */
const HEAD = {
  home: {
    title: 'Exhale — a music player that breathes',
    description:
      'A fast, open-source music player for Android. Live liquid glass, lyrics on the beat, and color that follows your album art.',
    path: '/',
  },
  release: {
    title: `Exhale ${RELEASE.version} — release notes`,
    description: RELEASE.dek,
    path: `/release/${RELEASE.version}`,
  },
  support: {
    title: 'Exhale — support',
    description:
      'Report a bug, suggest an idea, or find the answer outright. Background playback, audio quality, lyrics, backups and updates, answered.',
    path: '/support',
  },
}

export default function Site() {
  const path = useRoute()
  const page = isRelease(path) ? 'release' : isSupport(path) ? 'support' : 'home'

  useEffect(() => {
    const head = HEAD[page]
    document.title = head.title

    const set = (selector, attribute, value) => {
      const node = document.head.querySelector(selector)
      if (node) node.setAttribute(attribute, value)
    }

    set('meta[name="description"]', 'content', head.description)
    set('meta[property="og:title"]', 'content', head.title)
    set('meta[property="og:description"]', 'content', head.description)
    set('meta[property="og:url"]', 'content', `https://exhale.ozyern.me${head.path}`)
    set('link[rel="canonical"]', 'href', `https://exhale.ozyern.me${head.path}`)
  }, [page])

  if (page === 'release') return <Release />
  if (page === 'support') return <Support />
  return <App />
}
