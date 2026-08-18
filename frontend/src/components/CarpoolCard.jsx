import React from 'react'
import { getTagStyle } from '../data/tags'

const DAYS = ['일', '월', '화', '수', '목', '금', '토']
function fmtDate(d) {
  if (!d) return ''
  const dt = new Date(d)
  return `${dt.getMonth() + 1}/${dt.getDate()}(${DAYS[dt.getDay()]})`
}
function fmtPrice(p) {
  if (!p || p === '') return '무료'
  return Number(p).toLocaleString() + '원'
}

function TagPill({ tag, size = 'normal' }) {
  const t = getTagStyle(tag?.name)
  const fs = size === 'small' ? '0.66rem' : '0.72rem'
  const pad = size === 'small' ? '0.15rem 0.42rem' : '0.22rem 0.6rem'
  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: '0.25rem',
      fontSize: fs,
      fontWeight: 600,
      padding: pad,
      borderRadius: 100,
      whiteSpace: 'nowrap',
      background: t.bg,
      color: t.tc,
      border: `1.5px solid ${t.tc}22`,
    }}>
      {t.emoji} {tag?.name}
    </span>
  )
}

export { TagPill, fmtDate, fmtPrice }

export default function CarpoolCard({ post, onOpen, onDelete, onClose, showDelete, showClose }) {
  const avail = post.seats - post.filled
  const full = avail <= 0
  const isClosed = post.status === 'CLOSED'

  return (
    <div
      className="carpool-card"
      style={{
        ...styles.card,
        ...(isClosed ? { opacity: 0.8 } : {}),
      }}
      onClick={() => onOpen(post.id)}
    >
      <div className="carpool-card-topbar" style={styles.topBar} />
      <div style={styles.cardHeader}>
        <div style={styles.routeRow}>
          <span>{post.from}</span>
          <span style={{ color: 'var(--accent)' }}>→</span>
          <span>{post.to}</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
          <div style={{ ...styles.badge, ...(isClosed ? styles.badgeClosed : full ? styles.badgeFull : styles.badgeSeats) }}>
            {isClosed ? '마감됨' : full ? '마감' : `${avail}석 남음`}
          </div>
          {showClose && !isClosed && (
            <button
              style={styles.closeBtn}
              onClick={e => { e.stopPropagation(); onClose(post.id) }}
            >
              신청 마감
            </button>
          )}
          {showDelete && (
            <button
              style={styles.deleteBtn}
              onClick={e => { e.stopPropagation(); onDelete(post.id) }}
            >
              삭제
            </button>
          )}
        </div>
      </div>
      <div style={styles.meta}>
        <span style={styles.metaItem}>📅 {fmtDate(post.date)}</span>
        <span style={styles.metaItem}>⏰ {post.time}</span>
        <span style={styles.metaItem}>👥 {post.filled}/{post.seats}명</span>
      </div>
      {post.tags?.length > 0 && (
        <div style={styles.tags}>
          {post.tags.map(tag => <TagPill key={tag.id} tag={tag} />)}
        </div>
      )}
      {post.desc && <div style={styles.desc}>{post.desc}</div>}
      <div style={styles.footer}>
        <div style={styles.userInfo}>
          <div style={{ ...styles.avatar, background: `${post.color}22`, color: post.color }}>
            {(post.nickname || '?')[0]}
          </div>
          <div>
            <div style={styles.userName}>{post.nickname || '익명'}</div>
            <div style={styles.rating}>★ {post.rating} · {post.trips}회</div>
          </div>
        </div>
        <div style={styles.price}>
          {fmtPrice(post.price)}
          {!!post.price && <span style={styles.priceSub}>/인</span>}
        </div>
      </div>
    </div>
  )
}

const styles = {
  card: {
    background: 'var(--surface)',
    border: '1px solid var(--border)',
    borderRadius: 16,
    padding: '1.3rem',
    cursor: 'pointer',
    boxShadow: 'var(--card-glow)',
    position: 'relative',
    overflow: 'hidden',
    outline: 'none',
  },
  topBar: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    height: 3,
    background: 'var(--accent)',
    transition: 'opacity 0.25s',
  },
  cardHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: '0.9rem',
  },
  routeRow: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
    fontWeight: 700,
    fontSize: '0.98rem',
    color: 'var(--text)',
  },
  badge: {
    fontSize: '0.68rem',
    fontWeight: 700,
    padding: '0.22rem 0.55rem',
    borderRadius: 6,
    fontFamily: "'Space Mono', monospace",
    whiteSpace: 'nowrap',
  },
  badgeSeats: {
    background: 'var(--accent-pale)',
    color: 'var(--accent)',
  },
  badgeFull: {
    background: 'rgba(192,57,43,0.1)',
    color: 'var(--accent3)',
  },
  badgeClosed: {
    background: 'var(--surface2)',
    color: 'var(--text-muted)',
  },
  meta: {
    display: 'flex',
    gap: '0.9rem',
    marginBottom: '0.8rem',
    flexWrap: 'wrap',
  },
  metaItem: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.3rem',
    fontSize: '0.8rem',
    color: 'var(--text-muted)',
  },
  tags: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '0.35rem',
    marginBottom: '0.85rem',
  },
  desc: {
    fontSize: '0.83rem',
    color: 'var(--text-muted)',
    lineHeight: 1.55,
    marginBottom: '0.8rem',
    overflow: 'hidden',
    display: '-webkit-box',
    WebkitLineClamp: 2,
    WebkitBoxOrient: 'vertical',
  },
  footer: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: '0.85rem',
    borderTop: '1px solid var(--border)',
  },
  userInfo: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.45rem',
  },
  avatar: {
    width: 28,
    height: 28,
    borderRadius: '50%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '0.72rem',
    fontWeight: 700,
    flexShrink: 0,
  },
  userName: {
    fontSize: '0.8rem',
    fontWeight: 500,
    color: 'var(--text)',
  },
  rating: {
    fontSize: '0.7rem',
    color: '#b8860b',
  },
  price: {
    fontFamily: "'Space Mono', monospace",
    fontSize: '0.95rem',
    fontWeight: 700,
    color: 'var(--accent)',
  },
  priceSub: {
    fontSize: '0.68rem',
    color: 'var(--text-muted)',
    fontFamily: "'Noto Sans KR', sans-serif",
  },
  deleteBtn: {
    background: 'rgba(192,57,43,0.1)',
    color: 'var(--accent3)',
    border: 'none',
    borderRadius: 6,
    padding: '0.25rem 0.6rem',
    cursor: 'pointer',
    fontSize: '0.75rem',
    whiteSpace: 'nowrap',
  },
  closeBtn: {
    background: 'var(--accent-pale)',
    color: 'var(--accent)',
    border: '1px solid var(--accent)',
    borderRadius: 6,
    padding: '0.25rem 0.6rem',
    cursor: 'pointer',
    fontSize: '0.75rem',
    whiteSpace: 'nowrap',
    fontWeight: 700,
  },
}
