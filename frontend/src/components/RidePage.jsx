import React, { useEffect, useRef, useState, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import {
  getMyRidesAsDriver, getMyRidesAsPassenger, startRide, completeRide,
  getPassengers, boardPassenger, dropOffPassenger, getLocation,
} from '../api/rides'
import { getMyReviewForRide } from '../api/reviews'
import ReviewModal from './ReviewModal'

const KAKAO_JS_KEY = import.meta.env.VITE_KAKAO_JS_KEY

let sdkPromise = null
function loadKakaoSDK() {
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

function makeDepEl(label) {
  const d = document.createElement('div')
  d.innerHTML = `<div style="width:38px;height:38px;border-radius:50% 50% 50% 5px;transform:rotate(-45deg);background:#6b7c3f;border:2.5px solid #fff;box-shadow:0 3px 10px rgba(0,0,0,0.25);display:flex;align-items:center;justify-content:center;">
    <span style="transform:rotate(45deg);font-size:0.55rem;font-weight:900;color:#fff;text-align:center;line-height:1.1;">${label}</span>
  </div>`
  return d
}
function makeDestEl(label) {
  const d = document.createElement('div')
  d.innerHTML = `<div style="width:38px;height:38px;border-radius:50% 50% 50% 5px;transform:rotate(-45deg);background:#c0392b;border:2.5px solid #fff;box-shadow:0 3px 10px rgba(0,0,0,0.25);display:flex;align-items:center;justify-content:center;">
    <span style="transform:rotate(45deg);font-size:0.55rem;font-weight:900;color:#fff;text-align:center;line-height:1.1;">${label}</span>
  </div>`
  return d
}
function makeDriverEl() {
  const d = document.createElement('div')
  d.innerHTML = `<div style="width:40px;height:40px;background:#27ae60;border-radius:50%;border:3px solid #fff;box-shadow:0 2px 10px rgba(0,0,0,0.35);display:flex;align-items:center;justify-content:center;font-size:18px;">🚗</div>`
  return d
}

function makePassengerEl(nickname) {
  const d = document.createElement('div')
  d.innerHTML = `
    <div style="display:flex;flex-direction:column;align-items:center;gap:2px;">
      <div style="width:34px;height:34px;background:#3498db;border-radius:50%;border:3px solid #fff;box-shadow:0 2px 10px rgba(0,0,0,0.35);display:flex;align-items:center;justify-content:center;font-size:13px;">🧍</div>
      <div style="background:rgba(0,0,0,0.65);color:#fff;font-size:0.6rem;font-weight:700;padding:1px 5px;border-radius:4px;white-space:nowrap;max-width:60px;overflow:hidden;text-overflow:ellipsis;">${nickname}</div>
    </div>`
  return d
}

function makeMyPosEl() {
  const d = document.createElement('div')
  d.innerHTML = `
    <div style="display:flex;flex-direction:column;align-items:center;gap:2px;">
      <div style="width:36px;height:36px;background:#e74c3c;border-radius:50%;border:3px solid #fff;box-shadow:0 2px 10px rgba(0,0,0,0.35);display:flex;align-items:center;justify-content:center;font-size:16px;" title="내 위치">📍</div>
      <div style="background:rgba(0,0,0,0.65);color:#fff;font-size:0.6rem;font-weight:700;padding:1px 5px;border-radius:4px;white-space:nowrap;">내 위치</div>
    </div>`
  return d
}

function RideMap({ ride, driverPos, passengerPositions, focusPos, large, myPos }) {
  const mapRef = useRef(null)
  const mapInstanceRef = useRef(null)
  const driverOverlayRef = useRef(null)       // 드라이버 마커 (위치만 업데이트)
  const passengerOverlaysRef = useRef([])     // 탑승자 마커들
  const myPosOverlayRef = useRef(null)        // 내 위치 마커 (승객 전용)
  const [sdkReady, setSdkReady] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    loadKakaoSDK().then(() => setSdkReady(true)).catch(e => setError(e.message))
  }, [])

  // 지도 + 정적 마커(출발/도착) 초기화 — 한 번만
  useEffect(() => {
    if (!sdkReady || !mapRef.current) return
    const kakao = window.kakao
    const initLat = ride.departureLat || 37.5665
    const initLng = ride.departureLng || 126.9780
    const map = new kakao.maps.Map(mapRef.current, {
      center: new kakao.maps.LatLng(initLat, initLng),
      level: 3,       // 더 가깝게 (동네 수준)
      maxLevel: 10,
    })
    mapInstanceRef.current = map

    // 출발지/도착지 정적 마커
    if (ride.departureLat && ride.departureLng) {
      new kakao.maps.CustomOverlay({
        position: new kakao.maps.LatLng(ride.departureLat, ride.departureLng),
        content: makeDepEl('출발'), yAnchor: 1.1, zIndex: 3, map,
      })
    }
    if (ride.destinationLat && ride.destinationLng) {
      new kakao.maps.CustomOverlay({
        position: new kakao.maps.LatLng(ride.destinationLat, ride.destinationLng),
        content: makeDestEl('도착'), yAnchor: 1.1, zIndex: 3, map,
      })
    }
    return () => { mapInstanceRef.current = null }
  }, [sdkReady])  // ride 변경 시 재초기화 안 함

  // 드라이버 마커 — 위치만 업데이트 (마커 새로 만들지 않음)
  useEffect(() => {
    const map = mapInstanceRef.current
    if (!map || !sdkReady) return
    const kakao = window.kakao
    const pos = driverPos ? new kakao.maps.LatLng(driverPos[0], driverPos[1]) : null

    if (!pos) {
      if (driverOverlayRef.current) { driverOverlayRef.current.setMap(null); driverOverlayRef.current = null }
      return
    }
    if (driverOverlayRef.current) {
      driverOverlayRef.current.setPosition(pos)  // 기존 마커 위치만 이동 (깜빡임 없음)
    } else {
      driverOverlayRef.current = new kakao.maps.CustomOverlay({
        position: pos, content: makeDriverEl(), yAnchor: 0.5, zIndex: 5, map,
      })
    }
    // 지도가 드라이버 따라가도록 (네비 방식)
    map.setCenter(pos)
    map.setLevel(3)
  }, [sdkReady, driverPos])

  // 탑승자 마커들 — 위치 바뀔 때마다 업데이트
  useEffect(() => {
    const map = mapInstanceRef.current
    if (!map || !sdkReady) return
    const kakao = window.kakao

    passengerOverlaysRef.current.forEach(o => o.setMap(null))
    passengerOverlaysRef.current = []

    if (passengerPositions && passengerPositions.size > 0) {
      passengerPositions.forEach(({ pos, nickname }) => {
        const [lat, lng] = pos
        const ov = new kakao.maps.CustomOverlay({
          position: new kakao.maps.LatLng(lat, lng),
          content: makePassengerEl(nickname || '승객'), yAnchor: 1.1, zIndex: 4, map,
        })
        passengerOverlaysRef.current.push(ov)
      })
    }
  }, [sdkReady, passengerPositions])

  // 내 위치 마커 (승객 전용) — 지도 중심 이동 없이 마커만 표시
  useEffect(() => {
    const map = mapInstanceRef.current
    if (!map || !sdkReady) return
    const kakao = window.kakao
    const pos = myPos ? new kakao.maps.LatLng(myPos[0], myPos[1]) : null

    if (!pos) {
      if (myPosOverlayRef.current) { myPosOverlayRef.current.setMap(null); myPosOverlayRef.current = null }
      return
    }
    if (myPosOverlayRef.current) {
      myPosOverlayRef.current.setPosition(pos)
    } else {
      myPosOverlayRef.current = new kakao.maps.CustomOverlay({
        position: pos, content: makeMyPosEl(), yAnchor: 1.1, zIndex: 6, map,
      })
    }
  }, [sdkReady, myPos])

  // 탑승자 클릭 시 해당 위치로 지도 이동
  useEffect(() => {
    const map = mapInstanceRef.current
    if (!map || !sdkReady || !focusPos) return
    map.setCenter(new window.kakao.maps.LatLng(focusPos[0], focusPos[1]))
    map.setLevel(3)
  }, [sdkReady, focusPos])

  return (
    <div style={{ height: large ? 480 : 260, borderRadius: 12, overflow: 'hidden', border: '1px solid var(--border)', background: '#f5f5f0', position: 'relative' }}>
      <div ref={mapRef} style={{ height: '100%', width: '100%' }} />
      {!sdkReady && !error && (
        <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
          지도 불러오는 중...
        </div>
      )}
      {error && (
        <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
          지도를 불러올 수 없습니다
        </div>
      )}
    </div>
  )
}

function useCountdown(targetDate) {
  const [remaining, setRemaining] = useState(null)
  useEffect(() => {
    if (!targetDate) return
    const calc = () => {
      const diff = new Date(targetDate) - Date.now()
      setRemaining(diff)
    }
    calc()
    const t = setInterval(calc, 10000)
    return () => clearInterval(t)
  }, [targetDate])
  return remaining
}

function PreDepartureInfo({ ride, remainingMs }) {
  if (remainingMs === null) return null
  const mins = Math.floor(remainingMs / 60000)
  if (remainingMs <= 0) return null
  return (
    <div style={styles.countdownBox}>
      <span style={{ fontSize: '1.1rem' }}>⏱</span>
      <span style={{ fontSize: '0.85rem', fontWeight: 700, color: 'var(--text)' }}>
        출발까지 {mins > 60 ? `${Math.floor(mins / 60)}시간 ${mins % 60}분` : `${mins}분`}
      </span>
      {remainingMs <= 30 * 60 * 1000 && (
        <span style={{ fontSize: '0.72rem', color: '#27ae60', fontWeight: 700 }}>● 위치 공유 중</span>
      )}
    </div>
  )
}

const PASSENGER_STATUS_MAP = {
  PENDING:    { label: '탑승 대기', color: 'var(--text-muted)' },
  BOARDED:    { label: '탑승 중',   color: '#27ae60' },
  DROPPED_OFF:{ label: '하차 완료', color: 'var(--accent)' },
}

function PassengerRow({ passenger, onBoard, onDropOff, hasPos, onFocus }) {
  const { label, color } = PASSENGER_STATUS_MAP[passenger.status] || { label: passenger.status, color: 'var(--text-muted)' }
  return (
    <div
      style={{ ...styles.passengerRow, cursor: hasPos ? 'pointer' : 'default' }}
      onClick={() => hasPos && onFocus && onFocus(passenger.passengerId)}
      title={hasPos ? '클릭하면 탑승자 위치로 지도 이동' : ''}
    >
      <div style={{ flex: 1 }}>
        <div style={styles.passengerId}>
          {passenger.nickname || `승객 #${passenger.passengerId}`}
          {hasPos && <span style={{ fontSize: '0.65rem', color: '#3498db', marginLeft: 6 }}>📍 위치 확인</span>}
        </div>
        <span style={{ fontSize: '0.75rem', color, fontWeight: 700 }}>{label}</span>
      </div>
      <div style={{ display: 'flex', gap: '0.4rem' }}>
        {passenger.status === 'PENDING' && (
          <button style={styles.smallBtn} onClick={e => { e.stopPropagation(); onBoard(passenger.applicationId) }}>탑승 확인</button>
        )}
        {passenger.status === 'BOARDED' && (
          <button style={{ ...styles.smallBtn, ...styles.smallBtnDanger }} onClick={e => { e.stopPropagation(); onDropOff(passenger.applicationId) }}>하차 확인</button>
        )}
      </div>
    </div>
  )
}

function ActiveRidePanel({ ride, isDriver, large, memberId }) {
  const [passengers, setPassengers] = useState([])
  const [driverPos, setDriverPos] = useState(null)
  const [passengerPositions, setPassengerPositions] = useState(new Map()) // passengerId → { pos: [lat,lng], nickname }
  const [myPos, setMyPos] = useState(null)   // 내 위치 (승객 전용)
  const [focusPos, setFocusPos] = useState(null)
  const [connected, setConnected] = useState(false)
  const [err, setErr] = useState('')
  const stompRef = useRef(null)
  const geoIntervalRef = useRef(null)
  const remainingMs = useCountdown(ride.departureTime)
  const withinWindow = remainingMs !== null && remainingMs <= 30 * 60 * 1000
  // 테스트 모드: 브라우저 GPS 전송을 끄고 k6가 보낸 mock 위치만 사용
  // (콘솔에서 localStorage.setItem('rideTestMode','1') 후 새로고침)
  const testMode = typeof window !== 'undefined' && localStorage.getItem('rideTestMode') === '1'

  useEffect(() => {
    if (isDriver) {
      getPassengers(ride.id)
        .then(data => setPassengers(data || []))
        .catch(() => setErr('탑승자 목록을 불러오지 못했습니다.'))
    } else {
      getLocation(ride.id)
        .then(loc => { if (loc?.latitude && loc?.longitude) setDriverPos([loc.latitude, loc.longitude]) })
        .catch(() => {})
    }
  }, [ride.id, isDriver])

  // 운행 중 탑승자 상태 3초마다 자동 갱신 (K6 탑승/하차 처리 즉시 반영)
  useEffect(() => {
    if (!isDriver || ride.status !== 'IN_PROGRESS') return
    const interval = setInterval(() => {
      getPassengers(ride.id).then(data => setPassengers(data || [])).catch(() => {})
    }, 3000)
    return () => clearInterval(interval)
  }, [ride.id, ride.status, isDriver])

  useEffect(() => {
    const isActive = ride.status === 'IN_PROGRESS'
    const shouldConnect = isActive || (ride.status === 'SCHEDULED' && withinWindow)
    if (!shouldConnect) return

    const token = localStorage.getItem('accessToken')
    const wsProto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    // VITE_WS_BASE 미설정 시(로컬 dev) 현재 호스트 → Vite proxy 사용.
    // Vercel 배포 시 VITE_WS_BASE=wss://<백엔드도메인> 으로 EC2 직접 지정.
    const wsBase = import.meta.env.VITE_WS_BASE || `${wsProto}//${window.location.host}`
    const client = new Client({
      brokerURL: `${wsBase}/ws`,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true)
        if (isDriver) {
          // 드라이버: 내 위치를 탑승자들에게 전송 (실제 GPS)
          // 테스트 모드에서는 전송하지 않고 k6가 보낸 위치를 아래 구독으로 수신
          const sendLocation = () => {
            if (!navigator.geolocation || !client.connected) return
            navigator.geolocation.getCurrentPosition(pos => {
              const { latitude, longitude } = pos.coords
              client.publish({
                destination: `/app/ride/${ride.id}/location`,
                body: JSON.stringify({ latitude, longitude }),
              })
            })
          }
          if (!testMode) {
            sendLocation()
            geoIntervalRef.current = setInterval(sendLocation, 5000)
          }

          // 드라이버: 서버에서 브로드캐스트된 내 위치 수신 → 지도에 표시 (K6 시뮬레이션 포함)
          client.subscribe(`/topic/ride/${ride.id}`, msg => {
            const loc = JSON.parse(msg.body)
            setDriverPos([loc.latitude, loc.longitude])
          })

          // 드라이버: 탑승자 위치 구독 (닉네임 포함)
          client.subscribe(`/topic/ride/${ride.id}/passengers`, msg => {
            const loc = JSON.parse(msg.body)
            const nickname = passengers.find(p => p.passengerId === loc.passengerId)?.nickname || '승객'
            setPassengerPositions(prev => {
              const next = new Map(prev)
              next.set(loc.passengerId, { pos: [loc.latitude, loc.longitude], nickname })
              return next
            })
          })
        } else {
          // 탑승자: 드라이버 위치 구독
          client.subscribe(`/topic/ride/${ride.id}`, msg => {
            const loc = JSON.parse(msg.body)
            setDriverPos([loc.latitude, loc.longitude])
          })

          // 탑승자: 내 위치 수신 (k6 등 외부에서 전송된 위치를 서버가 브로드캐스트한 것)
          client.subscribe(`/topic/ride/${ride.id}/passengers`, msg => {
            const loc = JSON.parse(msg.body)
            if (memberId && loc.passengerId === memberId) {
              setMyPos([loc.latitude, loc.longitude])
            }
          })

          // 탑승자: 내 위치를 드라이버에게 전송 + 지도에 표시 (실제 GPS)
          // 테스트 모드에서는 전송하지 않고 k6가 보낸 내 위치를 위 구독으로 수신
          const sendPassengerLocation = () => {
            if (!client.connected || !navigator.geolocation) return
            navigator.geolocation.getCurrentPosition(
              pos => {
                const { latitude, longitude } = pos.coords
                client.publish({
                  destination: `/app/ride/${ride.id}/passenger-location`,
                  body: JSON.stringify({ latitude, longitude }),
                })
                setMyPos([latitude, longitude])
              },
              () => { /* GPS 실패 시 무시 — WebSocket으로 수신한 위치로 대체됨 */ },
              { enableHighAccuracy: false, timeout: 8000, maximumAge: 10000 }
            )
          }
          if (!testMode) {
            sendPassengerLocation()
            geoIntervalRef.current = setInterval(sendPassengerLocation, 5000)
          }
        }
      },
      onDisconnect: () => { setConnected(false) },
      onStompError: () => setErr('위치 연결에 실패했습니다.'),
    })
    client.activate()
    stompRef.current = client

    return () => {
      clearInterval(geoIntervalRef.current)
      client.deactivate()
    }
  }, [ride.id, ride.status, isDriver, withinWindow, testMode])

  async function handleBoard(applicationId) {
    try {
      const updated = await boardPassenger(ride.id, applicationId)
      setPassengers(prev => prev.map(p => p.applicationId === applicationId ? updated : p))
    } catch (e) {
      setErr(e.message || '탑승 확인에 실패했습니다.')
    }
  }

  async function handleDropOff(applicationId) {
    try {
      const updated = await dropOffPassenger(ride.id, applicationId)
      setPassengers(prev => prev.map(p => p.applicationId === applicationId ? updated : p))
    } catch (e) {
      setErr(e.message || '하차 확인에 실패했습니다.')
    }
  }

  return (
    <section style={{ ...styles.card, border: '2px solid #27ae60' }}>
      <div style={styles.cardHeader}>
        <div style={{ ...styles.dot, background: '#27ae60' }} />
        <h2 style={styles.cardTitle}>
          {ride.status === 'SCHEDULED' ? '운행 예정' : '운행 중'} — {isDriver ? '드라이버' : '승객'}
        </h2>
        <span style={{ ...styles.activeBadge, marginLeft: 'auto' }}>#{ride.id}</span>
        {isDriver && connected && (
          <span style={{ fontSize: '0.72rem', color: '#27ae60', fontWeight: 700 }}>● 위치 전송 중</span>
        )}
      </div>

      {ride.departureLocation && (
        <div style={styles.routeBox}>
          <span style={styles.routeFrom}>{ride.departureLocation}</span>
          <span style={{ color: 'var(--accent)', fontWeight: 700 }}>→</span>
          <span style={styles.routeTo}>{ride.destinationLocation}</span>
        </div>
      )}

      {ride.status === 'SCHEDULED' && <PreDepartureInfo ride={ride} remainingMs={remainingMs} />}

      {err && <div style={styles.errorBox}>{err}</div>}

      <RideMap ride={ride} driverPos={driverPos} passengerPositions={isDriver ? passengerPositions : null} focusPos={focusPos} large={large} myPos={isDriver ? null : myPos} />

      {isDriver && (
        <div style={{ marginTop: '1rem' }}>
          <div style={{ fontSize: '0.82rem', fontWeight: 700, color: 'var(--text-muted)', marginBottom: '0.5rem' }}>
            탑승자 관리
          </div>
          {passengers.length === 0 ? (
            <p style={styles.emptyText}>탑승자가 없습니다</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              {passengers.map(p => (
                <PassengerRow
                  key={p.id}
                  passenger={p}
                  onBoard={handleBoard}
                  onDropOff={handleDropOff}
                  hasPos={passengerPositions.has(p.passengerId)}
                  onFocus={pid => { const entry = passengerPositions.get(pid); if (entry) setFocusPos([...entry.pos]) }}
                />
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  )
}

function ReviewSection({ ride }) {
  const [reviewStatus, setReviewStatus] = useState('loading')
  const [showModal, setShowModal] = useState(false)

  useEffect(() => {
    getMyReviewForRide(ride.id)
      .then(data => setReviewStatus(data ? 'done' : 'pending'))
      .catch(() => setReviewStatus('pending'))
  }, [ride.id])

  if (reviewStatus === 'loading') return null

  return (
    <div style={styles.reviewSection}>
      {reviewStatus === 'done' ? (
        <div style={styles.reviewDone}>평가 완료</div>
      ) : (
        <button style={styles.reviewBtn} onClick={() => setShowModal(true)}>
          ★ 드라이버 평가하기
        </button>
      )}
      {showModal && (
        <ReviewModal
          ride={ride}
          onClose={() => setShowModal(false)}
          onSubmitted={() => setReviewStatus('done')}
        />
      )}
    </div>
  )
}

function RideStatusBadge({ status }) {
  const map = {
    SCHEDULED:   { label: '예정', bg: 'var(--accent-pale)', color: 'var(--accent)' },
    IN_PROGRESS: { label: '운행 중', bg: 'rgba(39,174,96,0.1)', color: '#27ae60' },
    COMPLETED:   { label: '완료', bg: 'var(--surface2)', color: 'var(--text-muted)' },
  }
  const s = map[status] || { label: status, bg: 'var(--surface2)', color: 'var(--text-muted)' }
  return <span style={{ ...styles.badge, background: s.bg, color: s.color }}>{s.label}</span>
}

function RideCard({ ride, isDriver, onStart, onComplete }) {
  const remainingMs = useCountdown(ride.departureTime)

  return (
    <div style={styles.rideCard}>
      <div style={styles.rideTop}>
        <RideStatusBadge status={ride.status} />
        <span style={styles.rideId}>운행 #{ride.id}</span>
      </div>

      {ride.departureLocation && (
        <div style={{ ...styles.rideMeta, display: 'flex', alignItems: 'center', gap: '0.4rem', marginBottom: '0.5rem' }}>
          <span style={{ fontWeight: 600, color: 'var(--text)' }}>{ride.departureLocation}</span>
          <span style={{ color: 'var(--accent)' }}>→</span>
          <span style={{ fontWeight: 600, color: 'var(--text)' }}>{ride.destinationLocation}</span>
        </div>
      )}

      {ride.departureTime && (
        <div style={styles.rideMeta}>
          출발: {new Date(ride.departureTime).toLocaleString('ko-KR')}
          {ride.status === 'SCHEDULED' && remainingMs !== null && remainingMs > 0 && (
            <span style={{ marginLeft: '0.5rem', color: remainingMs <= 30 * 60 * 1000 ? '#27ae60' : 'var(--text-muted)', fontSize: '0.75rem' }}>
              ({Math.floor(remainingMs / 60000)}분 후)
            </span>
          )}
        </div>
      )}
      {ride.startedAt && (
        <div style={styles.rideMeta}>시작: {new Date(ride.startedAt).toLocaleString('ko-KR')}</div>
      )}
      {ride.completedAt && (
        <div style={styles.rideMeta}>완료: {new Date(ride.completedAt).toLocaleString('ko-KR')}</div>
      )}

      {isDriver && ride.status === 'SCHEDULED' && (
        <button style={styles.actionBtn} onClick={() => onStart(ride.id)}>운행 시작</button>
      )}
      {isDriver && ride.status === 'IN_PROGRESS' && (
        <button style={{ ...styles.actionBtn, ...styles.actionBtnDanger }} onClick={() => onComplete(ride.id)}>
          운행 종료
        </button>
      )}

      {!isDriver && ride.status === 'COMPLETED' && (
        <ReviewSection ride={ride} />
      )}
    </div>
  )
}

export default function RidePage({ memberId }) {
  const [driverRides, setDriverRides] = useState([])
  const [passengerRides, setPassengerRides] = useState([])
  const [loadingDriver, setLoadingDriver] = useState(true)
  const [loadingPassenger, setLoadingPassenger] = useState(true)
  const [error, setError] = useState('')
  const [tab, setTab] = useState('map') // 'map' | 'history'

  const activeDriverRide = driverRides.find(r => r.status === 'IN_PROGRESS' || r.status === 'SCHEDULED') ?? null
  const activePassengerRide = passengerRides.find(r => r.status === 'IN_PROGRESS' || r.status === 'SCHEDULED') ?? null

  const loadRides = useCallback(async () => {
    setLoadingDriver(true)
    setLoadingPassenger(true)
    getMyRidesAsDriver()
      .then(data => setDriverRides(data || []))
      .catch(() => setError('운행 목록을 불러오지 못했습니다.'))
      .finally(() => setLoadingDriver(false))
    getMyRidesAsPassenger()
      .then(data => setPassengerRides(data || []))
      .catch(() => {})
      .finally(() => setLoadingPassenger(false))
  }, [])

  useEffect(() => { loadRides() }, [loadRides])

  // SCHEDULED 운행이 있으면 5초마다 자동 폴링 → IN_PROGRESS 전환 시 새로고침 없이 지도 표시
  useEffect(() => {
    const hasScheduled = [...driverRides, ...passengerRides].some(r => r.status === 'SCHEDULED')
    if (!hasScheduled) return
    const interval = setInterval(loadRides, 5000)
    return () => clearInterval(interval)
  }, [driverRides, passengerRides, loadRides])

  async function handleStart(rideId) {
    try {
      const updated = await startRide(rideId)
      setDriverRides(prev => prev.map(r => r.id === rideId ? updated : r))
    } catch (e) {
      setError(e.message || '운행 시작에 실패했습니다.')
    }
  }

  async function handleComplete(rideId) {
    try {
      const updated = await completeRide(rideId)
      setDriverRides(prev => prev.map(r => r.id === rideId ? updated : r))
    } catch (e) {
      setError(e.message || '운행 종료에 실패했습니다.')
    }
  }

  const hasActive = activeDriverRide || activePassengerRide

  return (
    <div style={styles.page}>
      {error && <div style={styles.errorBox}>{error}</div>}

      {/* 탭 헤더 */}
      <div style={styles.tabBar}>
        <button
          style={{ ...styles.tabBtn, ...(tab === 'map' ? styles.tabBtnActive : {}) }}
          onClick={() => setTab('map')}
        >
          📍 실시간 운행
          {hasActive && <span style={styles.tabDot} />}
        </button>
        <button
          style={{ ...styles.tabBtn, ...(tab === 'history' ? styles.tabBtnActive : {}) }}
          onClick={() => setTab('history')}
        >
          📋 운행 이력
        </button>
      </div>

      {/* 지도 운행 탭 */}
      {tab === 'map' && (
        <div>
          {activeDriverRide && (
            <ActiveRidePanel ride={activeDriverRide} isDriver large />
          )}
          {activePassengerRide && (
            <ActiveRidePanel ride={activePassengerRide} isDriver={false} large memberId={memberId} />
          )}
          {!hasActive && (
            <div style={{ ...styles.card, textAlign: 'center', padding: '3rem 1rem' }}>
              <div style={{ fontSize: '3rem', marginBottom: '0.8rem', opacity: 0.3 }}>🗺️</div>
              <p style={styles.emptyText}>현재 활성 운행이 없습니다</p>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '0.4rem' }}>
                운행이 시작되면 여기에 지도가 표시됩니다
              </p>
            </div>
          )}
        </div>
      )}

      {/* 운행 이력 탭 */}
      {tab === 'history' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          <section style={styles.card}>
            <div style={styles.cardHeader}>
              <div style={styles.dot} />
              <h2 style={styles.cardTitle}>내 운행 이력 (드라이버)</h2>
            </div>
            {loadingDriver ? (
              <div style={styles.empty}><p style={styles.emptyText}>불러오는 중...</p></div>
            ) : driverRides.filter(r => r.status === 'COMPLETED').length === 0 ? (
              <div style={styles.empty}>
                <div style={styles.emptyIcon}>🚗</div>
                <p style={styles.emptyText}>완료된 운행이 없습니다</p>
              </div>
            ) : (
              <div style={styles.rideList}>
                {driverRides.filter(r => r.status === 'COMPLETED').map(r => (
                  <RideCard key={r.id} ride={r} isDriver onStart={handleStart} onComplete={handleComplete} />
                ))}
              </div>
            )}
          </section>

          <section style={styles.card}>
            <div style={styles.cardHeader}>
              <div style={styles.dot} />
              <h2 style={styles.cardTitle}>내 탑승 이력 (승객)</h2>
            </div>
            {loadingPassenger ? (
              <div style={styles.empty}><p style={styles.emptyText}>불러오는 중...</p></div>
            ) : passengerRides.filter(r => r.status === 'COMPLETED').length === 0 ? (
              <div style={styles.empty}>
                <div style={styles.emptyIcon}>🧳</div>
                <p style={styles.emptyText}>탑승 이력이 없습니다</p>
              </div>
            ) : (
              <div style={styles.rideList}>
                {passengerRides.filter(r => r.status === 'COMPLETED').map(r => (
                  <RideCard key={r.id} ride={r} isDriver={false} />
                ))}
              </div>
            )}
          </section>
        </div>
      )}
    </div>
  )
}

const styles = {
  page: { display: 'flex', flexDirection: 'column', gap: '1.5rem', maxWidth: 900 },
  tabBar: { display: 'flex', gap: '0.5rem', borderBottom: '2px solid var(--border)', paddingBottom: '0' },
  tabBtn: {
    position: 'relative', background: 'none', border: 'none',
    padding: '0.65rem 1.2rem', fontSize: '0.9rem', fontWeight: 600,
    color: 'var(--text-muted)', cursor: 'pointer', borderRadius: '8px 8px 0 0',
    borderBottom: '2px solid transparent', marginBottom: '-2px',
  },
  tabBtnActive: { color: 'var(--accent)', borderBottom: '2px solid var(--accent)', background: 'var(--accent-pale)' },
  tabDot: {
    position: 'absolute', top: 8, right: 8,
    width: 7, height: 7, borderRadius: '50%', background: '#27ae60',
  },
  card: { background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 16, padding: '1.5rem' },
  cardHeader: { display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.2rem' },
  dot: { width: 7, height: 7, background: 'var(--accent)', borderRadius: '50%', flexShrink: 0 },
  cardTitle: { fontSize: '1rem', fontWeight: 700, color: 'var(--text)', margin: 0 },
  empty: { textAlign: 'center', padding: '1.5rem 1rem', color: 'var(--text-muted)' },
  emptyIcon: { fontSize: '2rem', marginBottom: '0.6rem', opacity: 0.4 },
  emptyText: { fontWeight: 600, fontSize: '0.9rem', margin: 0, color: 'var(--text-muted)' },
  errorBox: {
    color: 'var(--accent3, #c0392b)', fontSize: '0.85rem',
    background: 'rgba(192,57,43,0.06)', borderRadius: 10, padding: '0.8rem 1rem',
  },
  rideList: { display: 'flex', flexDirection: 'column', gap: '0.75rem' },
  rideCard: {
    background: 'var(--surface2)', border: '1px solid var(--border)',
    borderRadius: 10, padding: '1rem',
  },
  rideTop: { display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.5rem' },
  badge: {
    fontSize: '0.7rem', fontWeight: 700, padding: '0.15rem 0.5rem',
    borderRadius: 6, flexShrink: 0,
  },
  rideId: { fontSize: '0.78rem', color: 'var(--text-muted)' },
  rideMeta: { fontSize: '0.82rem', color: 'var(--text-muted)', marginBottom: '0.2rem' },
  actionBtn: {
    marginTop: '0.8rem', background: 'var(--accent)', color: '#fff',
    border: 'none', borderRadius: 7, padding: '0.4rem 0.9rem',
    fontSize: '0.82rem', fontWeight: 700, cursor: 'pointer',
  },
  actionBtnDanger: { background: 'rgba(192,57,43,0.1)', color: 'var(--accent3, #c0392b)' },
  activeBadge: {
    fontSize: '0.68rem', fontWeight: 700, padding: '0.15rem 0.5rem', borderRadius: 100,
    background: 'rgba(39,174,96,0.12)', color: '#27ae60',
  },
  passengerRow: {
    display: 'flex', alignItems: 'center', gap: '0.6rem',
    background: 'var(--surface)', borderRadius: 8, padding: '0.7rem 0.9rem',
    border: '1px solid var(--border)',
  },
  passengerId: { fontSize: '0.82rem', fontWeight: 600, color: 'var(--text)', marginBottom: '0.15rem' },
  smallBtn: {
    background: 'var(--accent)', color: '#fff', border: 'none',
    borderRadius: 6, padding: '0.3rem 0.7rem',
    fontSize: '0.75rem', fontWeight: 700, cursor: 'pointer', whiteSpace: 'nowrap',
  },
  smallBtnDanger: { background: 'rgba(192,57,43,0.1)', color: 'var(--accent3, #c0392b)' },
  routeBox: {
    display: 'flex', alignItems: 'center', gap: '0.5rem',
    marginBottom: '0.8rem', fontSize: '0.88rem',
  },
  routeFrom: { fontWeight: 600, color: 'var(--text)' },
  routeTo: { fontWeight: 600, color: 'var(--text)' },
  countdownBox: {
    display: 'flex', alignItems: 'center', gap: '0.5rem',
    background: 'var(--surface2)', borderRadius: 8, padding: '0.6rem 0.9rem',
    marginBottom: '0.8rem',
  },
  reviewSection: {
    marginTop: '0.8rem', paddingTop: '0.8rem',
    borderTop: '1px solid var(--border)',
  },
  reviewBtn: {
    background: '#f0c040', color: '#333',
    border: 'none', borderRadius: 7, padding: '0.5rem 1rem',
    fontSize: '0.84rem', fontWeight: 700, cursor: 'pointer',
    width: '100%',
  },
  reviewDone: {
    textAlign: 'center', fontSize: '0.82rem', color: '#27ae60',
    fontWeight: 700, padding: '0.4rem',
  },
}
