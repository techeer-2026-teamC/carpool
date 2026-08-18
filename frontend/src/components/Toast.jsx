import React from 'react'
import { useIsMobile } from '../hooks/useMobile'

export default function Toast({ message }) {
  const isMobile = useIsMobile()
  if (!message) return null

  const bottom = isMobile ? 'calc(var(--bottom-nav-h) + 0.8rem)' : '2rem'

  return (
    <div style={{ ...styles.toast, bottom }}>
      <span style={styles.dot}>●</span>
      {message}
    </div>
  )
}

const styles = {
  toast: {
    position: 'fixed',
    left: '50%',
    transform: 'translateX(-50%)',
    background: 'var(--surface)',
    borderLeft: '3px solid var(--accent)',
    border: '1px solid var(--border)',
    borderLeftWidth: 3,
    borderLeftColor: 'var(--accent)',
    color: 'var(--text)',
    padding: '0.7rem 1.3rem 0.7rem 1rem',
    borderRadius: 12,
    fontSize: '0.86rem',
    fontWeight: 600,
    zIndex: 700,
    whiteSpace: 'nowrap',
    boxShadow: '0 4px 24px rgba(42,42,31,0.14)',
    animation: 'toastSlide 0.25s ease both',
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
  },
  dot: {
    fontSize: '0.45rem',
    color: 'var(--accent)',
    flexShrink: 0,
  },
}
