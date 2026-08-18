import React, { useState, useEffect } from 'react'
import { getTagStyle } from '../data/tags'
import { fetchTags } from '../api/tags'
import { useIsMobile } from '../hooks/useMobile'

export default function SearchSection({ onSearch, onClear, selectedTagFilters, onToggleTag, onClearTags }) {
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [date, setDate] = useState('')
  const [allTags, setAllTags] = useState([])
  const isMobile = useIsMobile()

  useEffect(() => {
    fetchTags().then(setAllTags).catch(() => {})
  }, [])

  function handleSearch() {
    onSearch({ from, to, date })
  }

  function handleClear() {
    setFrom('')
    setTo('')
    setDate('')
    onClear()
  }

  return (
    <div style={styles.section}>
      {/* 검색 영역 */}
      <div style={{ ...styles.searchTop, flexDirection: isMobile ? 'column' : 'row' }}>
        {/* 출발지 + 목적지 (모바일: 2열 grid) */}
        <div style={{ ...styles.inputGroup, gridTemplateColumns: isMobile ? '1fr 1fr' : undefined, display: isMobile ? 'grid' : 'contents', gap: '0.6rem' }}>
          <div style={styles.field}>
            <label style={styles.label}>출발지</label>
            <input
              style={styles.input}
              value={from}
              onChange={e => setFrom(e.target.value)}
              placeholder="예: 강남역"
              onKeyDown={e => e.key === 'Enter' && handleSearch()}
            />
          </div>
          <div style={styles.field}>
            <label style={styles.label}>목적지</label>
            <input
              style={styles.input}
              value={to}
              onChange={e => setTo(e.target.value)}
              placeholder="예: 판교역"
              onKeyDown={e => e.key === 'Enter' && handleSearch()}
            />
          </div>
        </div>

        {/* 날짜 + 버튼 (모바일: 한 줄) */}
        <div style={{ ...styles.actionRow, flex: isMobile ? undefined : 'none' }}>
          <div style={{ ...styles.field, minWidth: isMobile ? 0 : 130, flex: isMobile ? 1 : undefined }}>
            <label style={styles.label}>날짜</label>
            <input
              style={styles.input}
              type="date"
              value={date}
              onChange={e => setDate(e.target.value)}
            />
          </div>
          <button style={{ ...styles.searchBtn, flex: isMobile ? 1 : undefined }} onClick={handleSearch}>
            🔍 검색
          </button>
          <button style={styles.resetBtn} onClick={handleClear}>초기화</button>
        </div>
      </div>

      {/* 태그 필터 — 가로 스크롤 */}
      <div style={styles.tagStripWrapper}>
        <span style={styles.tagLabel}>태그</span>
        <div className="tag-scroll" style={styles.tagScroll}>
          {allTags.map(tag => {
            const t = getTagStyle(tag.name)
            const active = selectedTagFilters.has(tag.id)
            return (
              <button
                key={tag.id}
                onClick={() => onToggleTag(tag.id)}
                style={{
                  ...styles.tagBtn,
                  ...(active ? { background: t.bg, color: t.tc, border: `1.5px solid ${t.tc}`, fontWeight: 700 } : {}),
                }}
              >
                {t.emoji} {tag.name}
              </button>
            )
          })}
        </div>
        {selectedTagFilters.size > 0 && (
          <button style={styles.clearBtn} onClick={onClearTags}>✕</button>
        )}
      </div>
    </div>
  )
}

const styles = {
  section: {
    background: 'var(--surface)',
    border: '1px solid var(--border)',
    borderRadius: 16,
    padding: '1rem 1.2rem 0.9rem',
    marginBottom: '0.8rem',
    boxShadow: '0 2px 12px rgba(107,124,63,0.06)',
  },
  searchTop: {
    display: 'flex',
    gap: '0.6rem',
    alignItems: 'flex-end',
    marginBottom: '0.85rem',
  },
  inputGroup: {
    flex: 1,
    display: 'contents',
  },
  actionRow: {
    display: 'flex',
    gap: '0.5rem',
    alignItems: 'flex-end',
  },
  field: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    gap: '0.28rem',
    minWidth: 0,
  },
  label: {
    fontSize: '0.68rem',
    color: 'var(--text-muted)',
    fontWeight: 700,
    letterSpacing: '0.4px',
    textTransform: 'uppercase',
  },
  input: {
    background: 'var(--surface2)',
    border: '1.5px solid var(--border)',
    borderRadius: 10,
    padding: '0.62rem 0.85rem',
    color: 'var(--text)',
    fontSize: '0.88rem',
    outline: 'none',
    transition: 'border-color 0.2s',
    width: '100%',
    minHeight: 42,
  },
  searchBtn: {
    background: 'var(--accent)',
    color: '#fff',
    border: 'none',
    cursor: 'pointer',
    fontWeight: 700,
    fontSize: '0.88rem',
    padding: '0 1.2rem',
    borderRadius: 10,
    whiteSpace: 'nowrap',
    alignSelf: 'flex-end',
    height: 42,
    transition: 'filter 0.15s',
  },
  resetBtn: {
    background: 'var(--surface2)',
    color: 'var(--text-muted)',
    border: '1.5px solid var(--border)',
    cursor: 'pointer',
    fontWeight: 600,
    fontSize: '0.85rem',
    padding: '0 0.9rem',
    borderRadius: 10,
    whiteSpace: 'nowrap',
    alignSelf: 'flex-end',
    height: 42,
    transition: 'filter 0.15s',
  },
  tagStripWrapper: {
    borderTop: '1px solid var(--border)',
    paddingTop: '0.75rem',
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
  },
  tagLabel: {
    fontSize: '0.65rem',
    fontWeight: 700,
    color: 'var(--text-dim)',
    textTransform: 'uppercase',
    letterSpacing: '0.5px',
    whiteSpace: 'nowrap',
    flexShrink: 0,
  },
  tagScroll: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.4rem',
    flex: 1,
    paddingBottom: '0.1rem',
  },
  tagBtn: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '0.25rem',
    border: '1.5px solid var(--border)',
    background: 'none',
    color: 'var(--text-muted)',
    fontSize: '0.76rem',
    fontWeight: 500,
    padding: '0.28rem 0.65rem',
    borderRadius: 100,
    cursor: 'pointer',
    whiteSpace: 'nowrap',
    transition: 'all 0.15s',
    flexShrink: 0,
  },
  clearBtn: {
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    fontSize: '0.78rem',
    color: 'var(--text-dim)',
    padding: '0.2rem 0.3rem',
    borderRadius: 6,
    flexShrink: 0,
  },
}
