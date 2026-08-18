import React from 'react'

const STATS = [
  { num: (n) => n,     suffix: '',   label: '등록된 카풀', icon: '🚗' },
  { num: () => '2.8k', suffix: '',   label: '누적 매칭',   icon: '🤝' },
  { num: () => '4.8',  suffix: '★',  label: '평균 평점',   icon: '⭐' },
]

export default function Hero({ totalPosts }) {
  const values = [totalPosts, null, null]

  return (
    <div style={styles.hero}>
      {/* 그라디언트 오버레이 */}
      <div style={styles.gradientOverlay} />

      <div style={styles.inner}>
        <div style={styles.badge}>
          <span style={styles.badgeDot}>●</span>
          실시간 카풀 매칭
        </div>
        <h1 style={styles.h1}>
          이동을 <span style={{ color: 'var(--accent)' }}>함께</span>하면
          <br />
          더 저렴하고 빠릅니다
        </h1>
        <p style={styles.p}>
          목적지가 같은 사람들을 연결해 드립니다 · 연료비를 나누고, 교통체증도 줄이세요
        </p>

        <div style={styles.statsRow}>
          {STATS.map((s, i) => (
            <div key={i} style={styles.statCard}>
              <span style={styles.statIcon}>{s.icon}</span>
              <div style={styles.statNum}>
                {s.num(values[i])}{s.suffix}
              </div>
              <div style={styles.statLabel}>{s.label}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

const styles = {
  hero: {
    position: 'relative',
    zIndex: 1,
    overflow: 'hidden',
  },
  gradientOverlay: {
    position: 'absolute',
    inset: 0,
    background: 'linear-gradient(170deg, rgba(107,124,63,0.10) 0%, rgba(247,246,242,0) 65%)',
    pointerEvents: 'none',
  },
  inner: {
    position: 'relative',
    padding: 'clamp(2rem, 5vw, 4rem) 2rem 2rem',
    textAlign: 'center',
    maxWidth: 720,
    margin: '0 auto',
  },
  badge: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '0.4rem',
    background: 'var(--accent-pale)',
    border: '1px solid rgba(107,124,63,0.3)',
    color: 'var(--accent)',
    fontSize: '0.76rem',
    fontWeight: 600,
    padding: '0.3rem 0.9rem',
    borderRadius: 100,
    marginBottom: '1.2rem',
    letterSpacing: '0.3px',
  },
  badgeDot: {
    fontSize: '0.5rem',
    animation: 'pulse 2s infinite',
  },
  h1: {
    fontSize: 'clamp(1.8rem, 5vw, 3.2rem)',
    fontWeight: 900,
    lineHeight: 1.15,
    marginBottom: '0.8rem',
    letterSpacing: '-1.5px',
    color: 'var(--text)',
    wordBreak: 'keep-all',
  },
  p: {
    color: 'var(--text-muted)',
    fontSize: 'clamp(0.85rem, 2vw, 1rem)',
    marginBottom: '2rem',
    lineHeight: 1.7,
    wordBreak: 'keep-all',
  },
  statsRow: {
    display: 'flex',
    justifyContent: 'center',
    gap: '0.75rem',
    flexWrap: 'wrap',
  },
  statCard: {
    background: 'var(--surface)',
    border: '1px solid var(--border)',
    borderRadius: 16,
    padding: '0.9rem 1.4rem',
    textAlign: 'center',
    boxShadow: '0 2px 12px rgba(107,124,63,0.08)',
    minWidth: 90,
    flex: '1 1 90px',
    maxWidth: 130,
  },
  statIcon: {
    display: 'block',
    fontSize: '1.2rem',
    marginBottom: '0.3rem',
  },
  statNum: {
    fontFamily: "'Space Mono', monospace",
    fontSize: 'clamp(1.1rem, 3vw, 1.4rem)',
    fontWeight: 700,
    color: 'var(--accent)',
    lineHeight: 1,
    marginBottom: '0.25rem',
  },
  statLabel: {
    fontSize: '0.68rem',
    color: 'var(--text-muted)',
    fontWeight: 500,
    wordBreak: 'keep-all',
  },
}
