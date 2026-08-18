import React, { useEffect, useRef, useState, useCallback } from 'react'
import { fmtDate, fmtPrice } from './CarpoolCard'

const KAKAO_JS_KEY = import.meta.env.VITE_KAKAO_JS_KEY

let sdkPromise = null
function loadSDK() {
  if (sdkPromise) return sdkPromise
  sdkPromise = new Promise((resolve, reject) => {
    if (window.kakao?.maps?.Map) { resolve(); return }
    const script = document.createElement('script')
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${KAKAO_JS_KEY}&autoload=false`
    script.onload = () => window.kakao.maps.load(resolve)
    script.onerror = () => reject(new Error('카카오맵 SDK 로드 실패'))
    document.head.appendChild(script)
  })
  return sdkPromise
}

function makeDepMarkerEl(post, selected) {
  const avail = post.seats - post.filled
  const full = avail <= 0
  const col = selected ? '#3d5a1a' : (full ? '#c0392b' : (post.color || '#6b7c3f'))
  const size = selected ? 50 : 42
  const label = full ? '✕' : avail + '석'
  const div = document.createElement('div')
  div.innerHTML = `
    <div style="
      width:${size}px;height:${size}px;
      border-radius:50% 50% 50% 5px;
      transform:rotate(-45deg);
      background:${col};
      border:${selected ? '3px' : '2.5px'} solid white;
      box-shadow:${selected ? '0 4px 20px rgba(0,0,0,0.4)' : '0 3px 14px rgba(0,0,0,0.25)'};
      display:flex;align-items:center;justify-content:center;cursor:pointer;
      transition:all 0.2s;
    ">
      <span style="transform:rotate(45deg);font-size:0.55rem;font-weight:900;color:white;font-family:'Space Mono',monospace;text-align:center;line-height:1.1;">
        ${label}
      </span>
    </div>
  `
  return div
}

function makeDestMarkerEl() {
  const div = document.createElement('div')
  div.innerHTML = `
    <div style="display:flex;flex-direction:column;align-items:center;gap:2px;">
      <div style="
        width:36px;height:36px;
        border-radius:50% 50% 50% 5px;
        transform:rotate(-45deg);
        background:#e67e22;
        border:2.5px solid white;
        box-shadow:0 3px 14px rgba(0,0,0,0.3);
        display:flex;align-items:center;justify-content:center;
      ">
        <span style="transform:rotate(45deg);font-size:0.8rem;">도</span>
      </div>
    </div>
  `
  return div
}

export default function MapView({ posts, onOpenDetail }) {
  const mapRef = useRef(null)
  const mapInstanceRef = useRef(null)
  const depOverlaysRef = useRef([])   // 출발지 마커들
  const destOverlayRef = useRef(null) // 선택된 도착지 마커
  const routeLineRef = useRef(null)   // 경로 라인
  const [sdkReady, setSdkReady] = useState(false)
  const [error, setError] = useState(null)
  const [selectedId, setSelectedId] = useState(null)
  const [sidebarOpen, setSidebarOpen] = useState(true)

  const selectedPost = posts.find(p => p.id === selectedId) || null

  // SDK 로드
  useEffect(() => {
    loadSDK()
      .then(() => setSdkReady(true))
      .catch(e => setError(e.message))
  }, [])

  // 지도 초기화
  useEffect(() => {
    if (!sdkReady || !mapRef.current) return
    const kakao = window.kakao
    const map = new kakao.maps.Map(mapRef.current, {
      center: new kakao.maps.LatLng(37.5665, 126.9780),
      level: 8,       // 서울 전체 보기 (줌인 가능)
      maxLevel: 12,   // 최대 줌아웃
    })
    mapInstanceRef.current = map
    return () => { mapInstanceRef.current = null }
  }, [sdkReady])

  // 출발지 마커 렌더링 (posts 또는 selectedId 변경 시)
  useEffect(() => {
    const map = mapInstanceRef.current
    if (!map || !sdkReady) return
    const kakao = window.kakao

    depOverlaysRef.current.forEach(o => o.setMap(null))
    depOverlaysRef.current = []

    posts.forEach(p => {
      if (p.departureLat == null || p.departureLng == null) return
      const el = makeDepMarkerEl(p, p.id === selectedId)
      el.addEventListener('click', () => setSelectedId(prev => prev === p.id ? null : p.id))
      const overlay = new kakao.maps.CustomOverlay({
        position: new kakao.maps.LatLng(p.departureLat, p.departureLng),
        content: el,
        yAnchor: 1,
        zIndex: p.id === selectedId ? 5 : 3,
      })
      overlay.setMap(map)
      depOverlaysRef.current.push(overlay)
    })
  }, [sdkReady, posts, selectedId])

  // 경로 라인 + 도착지 마커 (selectedId 변경 시)
  useEffect(() => {
    const map = mapInstanceRef.current
    if (!map || !sdkReady) return
    const kakao = window.kakao

    // 기존 제거
    if (routeLineRef.current) { routeLineRef.current.setMap(null); routeLineRef.current = null }
    if (destOverlayRef.current) { destOverlayRef.current.setMap(null); destOverlayRef.current = null }

    if (!selectedPost) return
    const { departureLat, departureLng, destinationLat, destinationLng } = selectedPost
    if (departureLat == null || destinationLat == null) return

    // 도착지 마커
    const destEl = makeDestMarkerEl()
    const destOverlay = new kakao.maps.CustomOverlay({
      position: new kakao.maps.LatLng(destinationLat, destinationLng),
      content: destEl,
      yAnchor: 1,
      zIndex: 4,
    })
    destOverlay.setMap(map)
    destOverlayRef.current = destOverlay

    // 경로 전체가 보이도록 bounds 설정
    const bounds = new kakao.maps.LatLngBounds()
    bounds.extend(new kakao.maps.LatLng(departureLat, departureLng))
    bounds.extend(new kakao.maps.LatLng(destinationLat, destinationLng))

    // OSRM으로 실제 도로 경로 요청 → 실패 시 직선 fallback
    const url = `https://router.project-osrm.org/route/v1/driving/` +
      `${departureLng},${departureLat};${destinationLng},${destinationLat}` +
      `?geometries=geojson&overview=full`

    fetch(url, { signal: AbortSignal.timeout(5000) })
      .then(r => r.json())
      .then(data => {
        const coords = data?.routes?.[0]?.geometry?.coordinates
        if (!coords?.length) throw new Error('no coords')
        const path = coords.map(([lng, lat]) => new kakao.maps.LatLng(lat, lng))
        const line = new kakao.maps.Polyline({
          path,
          strokeWeight: 5,
          strokeColor: '#6b7c3f',
          strokeOpacity: 0.9,
          strokeStyle: 'solid',
        })
        line.setMap(map)
        routeLineRef.current = line
        path.forEach(p => bounds.extend(p))
        map.setBounds(bounds, 60)
      })
      .catch(() => {
        // OSRM 실패 시 직선 fallback
        const line = new kakao.maps.Polyline({
          path: [
            new kakao.maps.LatLng(departureLat, departureLng),
            new kakao.maps.LatLng(destinationLat, destinationLng),
          ],
          strokeWeight: 4,
          strokeColor: '#6b7c3f',
          strokeOpacity: 0.85,
          strokeStyle: 'dashed',
        })
        line.setMap(map)
        routeLineRef.current = line
        map.setBounds(bounds, 60)
      })
  }, [sdkReady, selectedId, selectedPost])

  const handleSelectFromSidebar = useCallback((id) => {
    setSelectedId(prev => prev === id ? null : id)
  }, [])

  return (
    <div style={styles.container}>
      <div ref={mapRef} style={styles.map}>
        {!sdkReady && !error && <div style={styles.loadingOverlay}>지도 불러오는 중...</div>}
        {error && <div style={styles.loadingOverlay}>⚠️ {error}</div>}
      </div>

      {/* 우측 사이드바 */}
      <div style={{ ...styles.sidebar, width: sidebarOpen ? 280 : 0 }}>
        <button
          style={{ ...styles.sidebarToggle, right: sidebarOpen ? 288 : 8 }}
          onClick={() => setSidebarOpen(o => !o)}
          title={sidebarOpen ? '목록 접기' : '목록 펼치기'}
        >
          {sidebarOpen ? '▶' : '◀'}<br />
          <span style={{ fontSize: '0.6rem', letterSpacing: 0 }}>목록</span>
        </button>
        <div style={{ ...styles.sidebarInner, opacity: sidebarOpen ? 1 : 0, pointerEvents: sidebarOpen ? 'auto' : 'none', transition: 'opacity 0.2s' }}>
          <div style={styles.sidebarHeader}>카풀 목록 ({posts.length})</div>
          {posts.map(p => {
            const avail = p.seats - p.filled
            const isSel = p.id === selectedId
            return (
              <div
                key={p.id}
                style={{ ...styles.mapCard, ...(isSel ? styles.mapCardSelected : {}) }}
                onClick={() => handleSelectFromSidebar(p.id)}
              >
                <div style={styles.mapRoute}>
                  <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {p.from}
                    <span style={{ color: 'var(--accent)', margin: '0 4px' }}>→</span>
                    {p.to}
                  </span>
                  <span style={{ ...styles.badge, ...(avail <= 0 ? styles.badgeFull : styles.badgeSeats) }}>
                    {avail <= 0 ? '마감' : `${avail}석`}
                  </span>
                </div>
                <div style={styles.mapMeta}>
                  <span>📅 {fmtDate(p.date)}</span>
                  <span>⏰ {p.time}</span>
                  <span style={styles.mapPrice}>{fmtPrice(p.price)}</span>
                </div>
              </div>
            )
          })}
        </div>
      </div>

      {/* 하단 플로팅 카드 (선택된 게시글) */}
      {selectedPost && (
        <div style={{ ...styles.infoCard, right: sidebarOpen ? 308 : 16 }}>
          <div style={styles.infoCardInner}>
            <div style={styles.infoRoute}>
              <div style={styles.infoRoutePoint}>
                <div style={{ ...styles.routeDot, background: 'var(--accent)' }} />
                <span style={styles.infoRouteText}>{selectedPost.from}</span>
              </div>
              <div style={styles.routeLine} />
              <div style={styles.infoRoutePoint}>
                <div style={{ ...styles.routeDot, background: '#e67e22' }} />
                <span style={styles.infoRouteText}>{selectedPost.to}</span>
              </div>
            </div>
            <div style={styles.infoDivider} />
            <div style={styles.infoMeta}>
              <span style={styles.infoMetaItem}>📅 {selectedPost.date}</span>
              <span style={styles.infoMetaItem}>⏰ {selectedPost.time}</span>
              <span style={styles.infoMetaItem}>👥 {selectedPost.filled}/{selectedPost.seats}명</span>
              <span style={{ ...styles.infoMetaItem, fontFamily: "'Space Mono',monospace", fontWeight: 700, color: 'var(--accent)' }}>
                {fmtPrice(selectedPost.price)}
              </span>
            </div>
            <div style={styles.infoActions}>
              <div style={styles.infoNickname}>
                <div style={{ ...styles.infoAvatar, background: `${selectedPost.color}22`, color: selectedPost.color }}>
                  {(selectedPost.nickname || '?')[0]}
                </div>
                <span style={styles.infoNicknameText}>{selectedPost.nickname}</span>
              </div>
              <div style={styles.infoBtns}>
                <button style={styles.btnClose} onClick={() => setSelectedId(null)}>✕</button>
                <button style={styles.btnDetail} onClick={() => onOpenDetail(selectedPost.id)}>
                  상세보기
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 범례 */}
      {!selectedPost && (
        <div style={styles.legend}>
          <div style={styles.legendItem}>
            <div style={{ width: 12, height: 12, borderRadius: '50% 50% 50% 3px', transform: 'rotate(-45deg)', background: 'var(--accent)', border: '2px solid white', boxShadow: '0 1px 4px rgba(0,0,0,0.2)' }} />
            출발지
          </div>
          <div style={styles.legendItem}>
            <div style={{ width: 12, height: 12, borderRadius: '50% 50% 50% 3px', transform: 'rotate(-45deg)', background: '#e67e22', border: '2px solid white', boxShadow: '0 1px 4px rgba(0,0,0,0.2)' }} />
            도착지
          </div>
        </div>
      )}
    </div>
  )
}

const styles = {
  container: {
    position: 'relative',
    borderRadius: 20,
    overflow: 'hidden',
    border: '1px solid var(--border)',
    boxShadow: '0 4px 24px rgba(107,124,63,0.1)',
    marginBottom: '3rem',
  },
  map: {
    height: 660,
    width: '100%',
    background: '#f5f5f0',
  },
  loadingOverlay: {
    position: 'absolute',
    inset: 0,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '0.9rem',
    color: 'var(--text-muted)',
    background: '#f5f5f0',
    zIndex: 10,
  },
  sidebar: {
    position: 'absolute',
    top: 12,
    right: 12,
    bottom: 12,
    zIndex: 400,
    transition: 'width 0.25s cubic-bezier(0.4,0,0.2,1)',
    overflow: 'visible',
  },
  sidebarToggle: {
    position: 'absolute',
    top: '50%',
    transform: 'translateY(-50%)',
    zIndex: 410,
    background: 'rgba(255,255,255,0.96)',
    border: '1px solid var(--border)',
    borderRadius: 10,
    width: 28,
    padding: '0.5rem 0.2rem',
    cursor: 'pointer',
    fontSize: '0.65rem',
    fontWeight: 700,
    color: 'var(--accent)',
    boxShadow: '0 2px 10px rgba(0,0,0,0.12)',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    lineHeight: 1.4,
    transition: 'right 0.25s cubic-bezier(0.4,0,0.2,1)',
  },
  sidebarInner: {
    background: 'rgba(255,255,255,0.96)',
    backdropFilter: 'blur(12px)',
    border: '1px solid var(--border)',
    borderRadius: 16,
    overflowY: 'auto',
    height: '100%',
    boxShadow: '0 4px 24px rgba(107,124,63,0.15)',
  },
  sidebarHeader: {
    padding: '0.9rem 1.1rem 0.7rem',
    borderBottom: '1px solid var(--border)',
    fontSize: '0.75rem',
    fontWeight: 700,
    color: 'var(--text-muted)',
    textTransform: 'uppercase',
    letterSpacing: '0.5px',
    position: 'sticky',
    top: 0,
    background: 'rgba(255,255,255,0.96)',
    zIndex: 1,
    borderRadius: '16px 16px 0 0',
  },
  mapCard: {
    padding: '0.85rem 1.1rem',
    borderBottom: '1px solid var(--border)',
    cursor: 'pointer',
    transition: 'background 0.15s',
  },
  mapCardSelected: {
    background: 'var(--accent-pale)',
    borderLeft: '3px solid var(--accent)',
  },
  mapRoute: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.35rem',
    fontWeight: 700,
    fontSize: '0.84rem',
    color: 'var(--text)',
    marginBottom: '0.35rem',
  },
  mapMeta: {
    display: 'flex',
    gap: '0.6rem',
    fontSize: '0.73rem',
    color: 'var(--text-muted)',
    alignItems: 'center',
    flexWrap: 'wrap',
  },
  mapPrice: {
    fontFamily: "'Space Mono', monospace",
    fontSize: '0.78rem',
    fontWeight: 700,
    color: 'var(--accent)',
    marginLeft: 'auto',
  },
  badge: {
    fontSize: '0.65rem',
    fontWeight: 700,
    padding: '0.18rem 0.48rem',
    borderRadius: 6,
    fontFamily: "'Space Mono', monospace",
    flexShrink: 0,
  },
  badgeSeats: {
    background: 'var(--accent-pale)',
    color: 'var(--accent)',
  },
  badgeFull: {
    background: 'rgba(192,57,43,0.1)',
    color: 'var(--accent3)',
  },

  /* 하단 플로팅 카드 */
  infoCard: {
    position: 'absolute',
    bottom: 16,
    left: 16,
    right: 308,
    zIndex: 450,
    animation: 'cardUp 0.25s cubic-bezier(0.34,1.2,0.64,1) both',
  },
  infoCardInner: {
    background: 'rgba(255,255,255,0.97)',
    backdropFilter: 'blur(16px)',
    border: '1px solid var(--border)',
    borderRadius: 18,
    padding: '1rem 1.2rem',
    boxShadow: '0 8px 32px rgba(0,0,0,0.15)',
  },
  infoRoute: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.6rem',
    marginBottom: '0.75rem',
  },
  infoRoutePoint: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.4rem',
    flex: 1,
    minWidth: 0,
  },
  routeDot: {
    width: 10,
    height: 10,
    borderRadius: '50%',
    flexShrink: 0,
  },
  infoRouteText: {
    fontSize: '0.9rem',
    fontWeight: 700,
    color: 'var(--text)',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  routeLine: {
    flex: '0 0 24px',
    height: 2,
    background: 'linear-gradient(to right, var(--accent), #e67e22)',
    borderRadius: 2,
  },
  infoDivider: {
    height: 1,
    background: 'var(--border)',
    margin: '0.65rem 0',
  },
  infoMeta: {
    display: 'flex',
    gap: '0.9rem',
    flexWrap: 'wrap',
    marginBottom: '0.75rem',
  },
  infoMetaItem: {
    fontSize: '0.8rem',
    color: 'var(--text-muted)',
  },
  infoActions: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  infoNickname: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.45rem',
  },
  infoAvatar: {
    width: 26,
    height: 26,
    borderRadius: '50%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '0.7rem',
    fontWeight: 700,
  },
  infoNicknameText: {
    fontSize: '0.83rem',
    fontWeight: 500,
    color: 'var(--text)',
  },
  infoBtns: {
    display: 'flex',
    gap: '0.5rem',
    alignItems: 'center',
  },
  btnClose: {
    background: 'var(--surface2)',
    border: '1px solid var(--border)',
    borderRadius: 8,
    padding: '0.4rem 0.65rem',
    cursor: 'pointer',
    fontSize: '0.8rem',
    color: 'var(--text-muted)',
  },
  btnDetail: {
    background: 'var(--accent)',
    color: '#fff',
    border: 'none',
    borderRadius: 8,
    padding: '0.4rem 1rem',
    cursor: 'pointer',
    fontSize: '0.85rem',
    fontWeight: 700,
  },

  /* 범례 */
  legend: {
    position: 'absolute',
    bottom: 20,
    left: 16,
    zIndex: 400,
    background: 'rgba(255,255,255,0.93)',
    backdropFilter: 'blur(8px)',
    border: '1px solid var(--border)',
    borderRadius: 10,
    padding: '0.6rem 0.9rem',
    fontSize: '0.73rem',
    color: 'var(--text-muted)',
    pointerEvents: 'none',
    display: 'flex',
    flexDirection: 'column',
    gap: '0.35rem',
  },
  legendItem: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
  },
}
