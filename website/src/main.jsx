import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import Site from './Site.jsx'
import './styles.css'
// After styles.css on purpose: see the note at the top of tw.css.
import './tw.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <Site />
  </StrictMode>,
)
