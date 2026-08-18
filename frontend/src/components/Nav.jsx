import React from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useIsMobile } from '../hooks/useMobile'

const NAV_TABS = [
  { path: '/',        label: '카풀 찾기', icon: '🔍' },
  { path: '/my',      label: '내 카풀',   icon: '📋' },
  { path: '/rides',   label: '내 운행',   icon: '🚗' },
  { path: '/profile', label: '프로필',    icon: '👤' },
]

export default function Nav({ onOpenPost, onLogout }) {
  const navigate = useNavigate()
  const isMobile = useIsMobile()

  return (
    <>
      <nav style={styles.nav}>
        <div style={styles.logo} onClick={() => navigate('/')}>
          같이<span style={{ color: 'var(--text)' }}>타</span>
        </div>

        {/* 데스크탑: 탭 + 버튼 */}
        {!isMobile && (
          <div style={styles.navLinks}>
            {NAV_TABS.map(t => (
              <NavLink
                key={t.path}
                to={t.path}
                end={t.path === '/'}
                style={({ isActive }) => ({ ...styles.navTab, ...(isActive ? styles.navTabActive : {}) })}
              >
                {t.label}
              </NavLink>
            ))}
            <button style={styles.btnPrimary} onClick={onOpenPost}>
              + 게시글 등록
            </button>
            <button style={styles.btnLogout} onClick={onLogout}>
              로그아웃
            </button>
          </div>
        )}

        {/* 모바일: 로그아웃만 */}
        {isMobile && (
          <button style={styles.btnLogout} onClick={onLogout}>
            로그아웃
          </button>
        )}
      </nav>

      {/* 모바일: 하단 탭 바 */}
      {isMobile && (
        <div style={styles.bottomNav}>
          {NAV_TABS.map(t => (
            <NavLink
              key={t.path}
              to={t.path}
              end={t.path === '/'}
              style={({ isActive }) => ({
                ...styles.bottomTab,
                ...(isActive ? styles.bottomTabActive : {}),
              })}
            >
              <span style={styles.bottomIcon}>{t.icon}</span>
              <span style={styles.bottomLabel}>{t.label}</span>
            </NavLink>
          ))}
        </div>
      )}

      {/* 모바일: FAB */}
      {isMobile && (
        <button style={styles.fab} onClick={onOpenPost} aria-label="게시글 등록">
          +
        </button>
      )}
    </>
  )
}

const styles = {
  nav: {
    position: 'sticky',
    top: 0,
    zIndex: 500,
    background: 'rgba(247,246,242,0.95)',
    backdropFilter: 'blur(16px)',
    WebkitBackdropFilter: 'blur(16px)',
    borderBottom: '1px solid var(--border)',
    padding: '0 1.5rem',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    height: 56,
  },
  logo: {
    fontFamily: "'Space Mono', monospace",
    fontSize: '1.25rem',
    fontWeight: 700,
    color: 'var(--accent)',
    letterSpacing: '-0.5px',
    cursor: 'pointer',
    userSelect: 'none',
  },
  navLinks: {
    display: 'flex',
    gap: '0.3rem',
    alignItems: 'center',
  },
  navTab: {
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    color: 'var(--text-muted)',
    fontSize: '0.88rem',
    padding: '0.45rem 0.85rem',
    borderRadius: 8,
    transition: 'all 0.15s',
    textDecoration: 'none',
    display: 'inline-block',
    fontWeight: 500,
  },
  navTabActive: {
    background: 'var(--accent-pale)',
    color: 'var(--accent)',
    fontWeight: 700,
  },
  btnPrimary: {
    background: 'var(--accent)',
    color: '#fff',
    border: 'none',
    cursor: 'pointer',
    fontWeight: 700,
    fontSize: '0.88rem',
    padding: '0.5rem 1.1rem',
    borderRadius: 8,
    transition: 'all 0.15s',
    marginLeft: '0.4rem',
  },
  btnLogout: {
    background: 'none',
    border: '1px solid var(--border)',
    cursor: 'pointer',
    color: 'var(--text-muted)',
    fontSize: '0.82rem',
    padding: '0.42rem 0.8rem',
    borderRadius: 8,
    transition: 'all 0.15s',
  },
  /* 하단 탭 바 */
  bottomNav: {
    position: 'fixed',
    bottom: 0,
    left: 0,
    right: 0,
    height: 'var(--bottom-nav-h)',
    background: 'rgba(247,246,242,0.97)',
    backdropFilter: 'blur(16px)',
    WebkitBackdropFilter: 'blur(16px)',
    borderTop: '1px solid var(--border)',
    display: 'flex',
    zIndex: 500,
    paddingBottom: 'env(safe-area-inset-bottom)',
  },
  bottomTab: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '0.2rem',
    textDecoration: 'none',
    color: 'var(--text-dim)',
    transition: 'color 0.15s',
    paddingTop: '0.3rem',
  },
  bottomTabActive: {
    color: 'var(--accent)',
  },
  bottomIcon: {
    fontSize: '1.2rem',
    lineHeight: 1,
  },
  bottomLabel: {
    fontSize: '0.62rem',
    fontWeight: 600,
    letterSpacing: '-0.3px',
  },
  /* FAB */
  fab: {
    position: 'fixed',
    bottom: 'calc(var(--bottom-nav-h) + 1rem)',
    right: '1.2rem',
    width: 52,
    height: 52,
    borderRadius: '50%',
    background: 'var(--accent)',
    color: '#fff',
    border: 'none',
    fontSize: '1.6rem',
    fontWeight: 300,
    lineHeight: 1,
    boxShadow: '0 4px 20px rgba(107,124,63,0.4)',
    zIndex: 490,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    cursor: 'pointer',
    transition: 'transform 0.15s, box-shadow 0.15s',
  },
}
