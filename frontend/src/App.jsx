import React, { useState, useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useIsMobile } from './hooks/useMobile'
import LoginPage from './components/LoginPage'
import Nav from './components/Nav'
import Hero from './components/Hero'
import SearchSection from './components/SearchSection'
import CarpoolCard from './components/CarpoolCard'
import MapView from './components/MapView'
import PostModal from './components/PostModal'
import DetailModal from './components/DetailModal'
import ProfilePage from './components/ProfilePage'
import RidePage from './components/RidePage'
import Toast from './components/Toast'
import { useCarpool } from './hooks/useCarpool'
import { logout } from './api/auth'
import { getMyApplications } from './api/applications'

const STATUS_MAP = {
  PENDING:  { label: '대기 중', color: '#b8860b',         bg: 'rgba(184,134,11,0.1)' },
  ACCEPTED: { label: '수락됨', color: 'var(--accent)',    bg: 'var(--accent-pale)' },
  REJECTED: { label: '거절됨', color: 'var(--accent3)',   bg: 'rgba(192,57,43,0.1)' },
}

function ApplicationCard({ app, post, onOpen }) {
  const s = STATUS_MAP[app.status] || STATUS_MAP.PENDING
  const [hovered, setHovered] = useState(false)
  return (
    <div
      style={{ ...appCardStyles.card, ...(hovered ? appCardStyles.cardHovered : {}) }}
      onClick={() => post && onOpen(post.id)}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <div style={{ ...appCardStyles.topBar, opacity: hovered ? 1 : 0 }} />
      <div style={appCardStyles.header}>
        {post ? (
          <div style={appCardStyles.route}>
            <span>{post.from}</span>
            <span style={{ color: 'var(--accent)' }}>→</span>
            <span>{post.to}</span>
          </div>
        ) : (
          <div style={appCardStyles.route}>게시글 #{app.postId}</div>
        )}
        <span style={{ ...appCardStyles.badge, color: s.color, background: s.bg }}>{s.label}</span>
      </div>
      {post && (
        <div style={appCardStyles.meta}>
          <span style={appCardStyles.metaItem}>📅 {post.date}</span>
          <span style={appCardStyles.metaItem}>⏰ {post.time}</span>
          <span style={appCardStyles.metaItem}>👥 {post.filled}/{post.seats}명</span>
        </div>
      )}
      <div style={appCardStyles.appliedDate}>
        신청일: {new Date(app.createdAt).toLocaleDateString('ko-KR')}
      </div>
    </div>
  )
}

const appCardStyles = {
  card: {
    background: 'var(--surface)',
    border: '1px solid var(--border)',
    borderRadius: 16,
    padding: '1.3rem',
    cursor: 'pointer',
    transition: 'box-shadow 0.25s, border-color 0.25s, transform 0.2s',
    boxShadow: 'var(--card-glow)',
    position: 'relative',
    overflow: 'hidden',
  },
  cardHovered: {
    borderColor: 'var(--accent)',
    boxShadow: '0 8px 32px rgba(107, 124, 63, 0.18)',
    transform: 'translateY(-2px)',
  },
  topBar: {
    position: 'absolute',
    top: 0, left: 0, right: 0,
    height: 3,
    background: 'var(--accent)',
    transition: 'opacity 0.25s',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: '0.9rem',
  },
  route: {
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
  appliedDate: {
    fontSize: '0.78rem',
    color: 'var(--text-muted)',
    paddingTop: '0.85rem',
    borderTop: '1px solid var(--border)',
  },
}

function parseMemberId(token) {
  try {
    const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(b64)).memberId
  } catch {
    return null
  }
}

function getInitialAuth() {
  const token = localStorage.getItem('accessToken')
  if (!token) return { token: null, memberId: null }
  const memberId = parseMemberId(token)
  return memberId ? { token, memberId } : { token: null, memberId: null }
}

export default function App() {
  const [auth, setAuth] = useState(getInitialAuth)
  const location = useLocation()
  const navigate = useNavigate()
  const isMobile = useIsMobile()

  const currentPage = {
    '/':        'list',
    '/my':      'my',
    '/rides':   'rides',
    '/profile': 'profile',
  }[location.pathname] || 'list'

  function handleLogin(token) {
    const memberId = parseMemberId(token)
    localStorage.setItem('accessToken', token)
    setAuth({ token, memberId })
  }

  async function handleLogout() {
    try { await logout() } catch {}
    localStorage.removeItem('accessToken')
    setAuth({ token: null, memberId: null })
    navigate('/')
  }

  useEffect(() => {
    const onAuthExpired = () => {
      setAuth({ token: null, memberId: null })
      navigate('/')
    }
    window.addEventListener('auth:logout', onAuthExpired)
    return () => window.removeEventListener('auth:logout', onAuthExpired)
  }, [navigate])

  const {
    posts,
    filteredPosts,
    myPosts,
    filteredMyPosts,
    loading,
    currentView, setCurrentView,
    currentFilter, setCurrentFilter,
    searchQuery, setSearchQuery,
    selectedTagFilters,
    toggleTagFilter,
    clearTagFilters,
    toast,
    addPost,
    deletePost,
    joinCarpool,
    closePost,
  } = useCarpool(auth.memberId)

  const [showPostModal, setShowPostModal] = useState(false)
  const [selectedPostId, setSelectedPostId] = useState(null)
  const [myTab, setMyTab] = useState('registered')
  const [myApplications, setMyApplications] = useState([])
  const [appLoading, setAppLoading] = useState(false)

  useEffect(() => {
    if (myTab !== 'applied' || !auth.token) return
    setAppLoading(true)
    getMyApplications()
      .then(data => setMyApplications(Array.isArray(data) ? data : []))
      .catch(() => setMyApplications([]))
      .finally(() => setAppLoading(false))
  }, [myTab, auth.token])

  const selectedPost = posts.find(p => p.id === selectedPostId) || null

  function handleOpenDetail(id) {
    setSelectedPostId(id)
  }

  function handleJoin(id) {
    joinCarpool(id)
    setSelectedPostId(null)
  }

  if (!auth.token) {
    return <LoginPage onLogin={handleLogin} />
  }

  return (
    <div>
      <Nav
        onOpenPost={() => setShowPostModal(true)}
        onLogout={handleLogout}
      />

      {currentPage === 'list' && (
        <Hero totalPosts={posts.length} />
      )}

      <div style={{ ...styles.main, paddingBottom: isMobile ? 'calc(var(--bottom-nav-h) + 1.5rem)' : '2rem' }}>
        {(currentPage === 'list' || currentPage === 'my') && (
          <SearchSection
            onSearch={setSearchQuery}
            onClear={() => setSearchQuery({ from: '', to: '', date: '' })}
            selectedTagFilters={selectedTagFilters}
            onToggleTag={toggleTagFilter}
            onClearTags={clearTagFilters}
          />
        )}

        {currentPage === 'list' && (
          <>
            <div style={styles.toolbar}>
              <div style={styles.toolbarLeft}>
                <div style={styles.sectionTitle}>
                  <div style={styles.dot} />
                  <span>
                    카풀 목록 ({filteredPosts.length})
                    {selectedTagFilters.size > 0 && (
                      <span style={styles.tagCount}>{selectedTagFilters.size}</span>
                    )}
                  </span>
                </div>
                <div style={styles.filterRow}>
                  {[
                    { key: 'all', label: '전체' },
                    { key: 'today', label: '오늘' },
                    { key: 'seats', label: '자리있음' },
                    { key: 'cheap', label: '저렴순' },
                  ].map(f => (
                    <button
                      key={f.key}
                      style={{
                        ...styles.filterChip,
                        borderColor: currentFilter === f.key && f.key !== 'all' ? 'var(--accent)' : 'var(--border)',
                        color: currentFilter === f.key && f.key !== 'all' ? 'var(--accent)' : 'var(--text-muted)',
                        background: currentFilter === f.key && f.key !== 'all' ? 'var(--accent-pale)' : 'none',
                      }}
                      onClick={() => setCurrentFilter(f.key === 'all' || currentFilter === f.key ? 'all' : f.key)}
                    >
                      {f.label}
                    </button>
                  ))}
                </div>
              </div>
              <div style={styles.viewToggle}>
                <button
                  style={{ ...styles.viewBtn, ...(currentView === 'card' ? styles.viewBtnActive : {}) }}
                  onClick={() => setCurrentView('card')}
                >
                  ⊞ 카드 보기
                </button>
                <button
                  style={{ ...styles.viewBtn, ...(currentView === 'map' ? styles.viewBtnActive : {}) }}
                  onClick={() => setCurrentView('map')}
                >
                  📍 지도 보기
                </button>
              </div>
            </div>

            {loading ? (
              <div style={styles.emptyState}>
                <div style={{ fontSize: '2.5rem', marginBottom: '1rem', opacity: 0.4 }}>🚗</div>
                <p>불러오는 중...</p>
              </div>
            ) : currentView === 'card' ? (
              <div style={styles.cardsGrid}>
                {filteredPosts.length === 0 ? (
                  <div style={styles.emptyState}>
                    <div style={{ fontSize: '2.5rem', marginBottom: '1rem', opacity: 0.4 }}>🔍</div>
                    {(searchQuery.from || searchQuery.to || searchQuery.date || selectedTagFilters.size > 0) ? (
                      <>
                        <p style={{ fontWeight: 700, marginBottom: '0.4rem' }}>검색 조건에 맞는 카풀이 없어요</p>
                        <p style={{ fontSize: '0.83rem', color: 'var(--text-muted)', marginBottom: '1rem' }}>
                          다른 조건으로 검색해보거나 조건을 초기화해보세요
                        </p>
                        <button
                          style={{ background: 'var(--accent)', color: '#fff', border: 'none', borderRadius: 8, padding: '0.5rem 1.2rem', fontSize: '0.85rem', fontWeight: 700, cursor: 'pointer' }}
                          onClick={() => { setSearchQuery({ from: '', to: '', date: '' }); clearTagFilters() }}
                        >
                          조건 초기화
                        </button>
                      </>
                    ) : (
                      <>
                        <p style={{ fontWeight: 700 }}>아직 등록된 카풀이 없어요</p>
                        <p style={{ fontSize: '0.83rem', color: 'var(--text-muted)', marginTop: '0.3rem' }}>첫 카풀을 등록해보세요!</p>
                      </>
                    )}
                  </div>
                ) : (
                  filteredPosts.map(p => (
                    <CarpoolCard key={p.id} post={p} onOpen={handleOpenDetail} />
                  ))
                )}
              </div>
            ) : (
              <MapView posts={filteredPosts} onOpenDetail={handleOpenDetail} />
            )}
          </>
        )}

        {currentPage === 'my' && (
          <>
            <div style={styles.toolbar}>
              <div style={styles.tabRow}>
                <button
                  style={{ ...styles.tab, ...(myTab === 'registered' ? styles.tabActive : {}) }}
                  onClick={() => setMyTab('registered')}
                >
                  내가 등록한 카풀
                </button>
                <button
                  style={{ ...styles.tab, ...(myTab === 'applied' ? styles.tabActive : {}) }}
                  onClick={() => setMyTab('applied')}
                >
                  내가 신청한 카풀
                </button>
              </div>
            </div>

            {myTab === 'registered' && (
              <div style={styles.cardsGrid}>
                {filteredMyPosts.length === 0 ? (
                  <div style={styles.emptyState}>
                    <div style={{ fontSize: '2.5rem', marginBottom: '1rem', opacity: 0.4 }}>📝</div>
                    <p>{myPosts.length === 0 ? '등록한 카풀이 없습니다' : '조건에 맞는 카풀이 없습니다'}</p>
                  </div>
                ) : (
                  filteredMyPosts.map(p => (
                    <CarpoolCard
                      key={p.id}
                      post={p}
                      onOpen={handleOpenDetail}
                      onDelete={deletePost}
                      onClose={async (id) => {
                        const success = await closePost(id)
                        if (success) navigate('/rides')
                      }}
                      showDelete
                      showClose={p.status === 'OPEN'}
                    />
                  ))
                )}
              </div>
            )}

            {myTab === 'applied' && (
              <div style={styles.cardsGrid}>
                {appLoading ? (
                  <div style={styles.emptyState}>
                    <div style={{ fontSize: '2.5rem', marginBottom: '1rem', opacity: 0.4 }}>🚗</div>
                    <p>불러오는 중...</p>
                  </div>
                ) : myApplications.length === 0 ? (
                  <div style={styles.emptyState}>
                    <div style={{ fontSize: '2.5rem', marginBottom: '1rem', opacity: 0.4 }}>📋</div>
                    <p>신청한 카풀이 없습니다</p>
                  </div>
                ) : (
                  myApplications.map(app => (
                    <ApplicationCard
                      key={app.id}
                      app={app}
                      post={posts.find(p => p.id === app.postId) || null}
                      onOpen={handleOpenDetail}
                    />
                  ))
                )}
              </div>
            )}
          </>
        )}

        {currentPage === 'profile' && (
          <ProfilePage onLogout={handleLogout} />
        )}

        {currentPage === 'rides' && (
          <RidePage memberId={auth.memberId} />
        )}
      </div>

      {showPostModal && (
        <PostModal
          onClose={() => setShowPostModal(false)}
          onSubmit={addPost}
        />
      )}
      {selectedPost && (
        <DetailModal
          post={selectedPost}
          onClose={() => setSelectedPostId(null)}
          onJoin={handleJoin}
          currentMemberId={auth.memberId}
        />
      )}

      <Toast message={toast} />
    </div>
  )
}

const styles = {
  main: {
    position: 'relative',
    zIndex: 1,
    maxWidth: 1300,
    margin: '0 auto',
    padding: '1.5rem 2rem 2rem',
  },
  toolbar: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '1rem',
    flexWrap: 'wrap',
    gap: '0.8rem',
    marginTop: '1rem',
  },
  toolbarLeft: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.8rem',
    flexWrap: 'wrap',
  },
  sectionTitle: {
    fontSize: '1rem',
    fontWeight: 700,
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
    color: 'var(--text)',
  },
  dot: {
    width: 7,
    height: 7,
    background: 'var(--accent)',
    borderRadius: '50%',
  },
  tagCount: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: 18,
    height: 18,
    borderRadius: '50%',
    background: 'var(--accent)',
    color: '#fff',
    fontSize: '0.65rem',
    fontWeight: 700,
    marginLeft: '0.3rem',
  },
  filterRow: {
    display: 'flex',
    gap: '0.35rem',
    flexWrap: 'wrap',
  },
  filterChip: {
    background: 'none',
    borderWidth: '1px',
    borderStyle: 'solid',
    borderColor: 'var(--border)',
    color: 'var(--text-muted)',
    fontSize: '0.76rem',
    padding: '0.28rem 0.75rem',
    borderRadius: 100,
    cursor: 'pointer',
    transition: 'all 0.2s',
    outline: 'none',
  },
  viewToggle: {
    display: 'flex',
    background: 'var(--surface2)',
    border: '1px solid var(--border)',
    borderRadius: 10,
    padding: 3,
    gap: 2,
  },
  viewBtn: {
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    padding: '0.4rem 1rem',
    borderRadius: 7,
    fontSize: '0.82rem',
    fontWeight: 600,
    color: 'var(--text-muted)',
    transition: 'all 0.2s',
  },
  viewBtnActive: {
    background: 'var(--surface)',
    color: 'var(--accent)',
    boxShadow: '0 1px 4px rgba(0,0,0,0.1)',
  },
  tabRow: {
    display: 'flex',
    gap: '0.35rem',
    background: 'var(--surface2)',
    border: '1px solid var(--border)',
    borderRadius: 10,
    padding: 3,
  },
  tab: {
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    padding: '0.45rem 1.1rem',
    borderRadius: 7,
    fontSize: '0.85rem',
    fontWeight: 600,
    color: 'var(--text-muted)',
    transition: 'all 0.2s',
    whiteSpace: 'nowrap',
  },
  tabActive: {
    background: 'var(--surface)',
    color: 'var(--accent)',
    boxShadow: '0 1px 4px rgba(0,0,0,0.1)',
  },
  cardsGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(330px, 1fr))',
    gap: '1rem',
    marginBottom: '3rem',
  },
  emptyState: {
    textAlign: 'center',
    padding: '4rem 2rem',
    color: 'var(--text-muted)',
    gridColumn: '1 / -1',
  },
}
