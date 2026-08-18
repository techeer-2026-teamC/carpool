// Mock API Plugin — Vite dev server intercepts based on exact backend DTO shapes
// Run with: VITE_MOCK=true npm run dev

const MOCK_TOKEN =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9' +
  '.eyJtZW1iZXJJZCI6MSwiaWF0IjoxNzQ4MjIwMDAwLCJleHAiOjE3NDkwMDAwMDB9' +
  '.mocksig'

const TAGS = [
  { id: 1, name: '금연' },
  { id: 2, name: '흡연' },
  { id: 3, name: '조용한 분위기' },
  { id: 4, name: '반려동물' },
  { id: 5, name: '짐 있음' },
  { id: 6, name: '여성전용' },
]

const CAR_COLORS = [
  { name: 'WHITE',  label: '흰색' },
  { name: 'BLACK',  label: '검정' },
  { name: 'GRAY',   label: '회색' },
  { name: 'SILVER', label: '은색' },
  { name: 'RED',    label: '빨강' },
  { name: 'BLUE',   label: '파랑' },
  { name: 'BROWN',  label: '갈색' },
]

// In-memory DB (resets on server restart)
const db = {
  nextId: 100,
  profile: {
    id: 1,
    email: 'test@example.com',
    nickname: '테스트유저',
    createdAt: '2026-01-15T09:00:00',
  },
  driver: null,
  posts: [
    {
      id: 1, memberId: 1, nickname: '테스트유저', driverAverageRating: 4.8,
      title: '강남역 → 판교역',
      departureLocation: '강남역 3번 출구', destinationLocation: '판교역 2번 출구',
      departureLat: 37.4979, departureLng: 127.0276,
      destinationLat: 37.3943, destinationLng: 127.1110,
      departureTime: new Date(Date.now() + 3 * 60 * 60 * 1000).toISOString(),
      maxPassengers: 3, currentPassengers: 1,
      status: 'OPEN', autoAccept: true, price: 5000,
      tags: [TAGS[0], TAGS[2]], createdAt: new Date().toISOString(),
    },
    {
      id: 2, memberId: 2, nickname: '김민준', driverAverageRating: 3.5,
      title: '홍대입구 → 여의도역',
      departureLocation: '홍대입구역 9번 출구', destinationLocation: '여의도역 1번 출구',
      departureLat: 37.5572, departureLng: 126.9247,
      destinationLat: 37.5215, destinationLng: 126.9242,
      departureTime: new Date(Date.now() + 5 * 60 * 60 * 1000).toISOString(),
      maxPassengers: 2, currentPassengers: 0,
      status: 'OPEN', autoAccept: false, price: 3000,
      tags: [TAGS[0], TAGS[1]], createdAt: new Date().toISOString(),
    },
    {
      id: 3, memberId: 3, nickname: '이서연', driverAverageRating: 5.0,
      title: '신촌 → 구로디지털단지',
      departureLocation: '신촌역 2번 출구', destinationLocation: '구로디지털단지역',
      departureLat: 37.5552, departureLng: 126.9369,
      destinationLat: 37.4851, destinationLng: 126.9012,
      departureTime: new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString(),
      maxPassengers: 4, currentPassengers: 2,
      status: 'OPEN', autoAccept: true, price: null,
      tags: [TAGS[5]], createdAt: new Date().toISOString(),
    },
    {
      id: 4, memberId: 4, nickname: '박지호', driverAverageRating: 4.2,
      title: '잠실역 → 강변역',
      departureLocation: '잠실역 8번 출구', destinationLocation: '강변역 4번 출구',
      departureLat: 37.5133, departureLng: 127.1001,
      destinationLat: 37.5347, destinationLng: 127.0942,
      departureTime: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
      maxPassengers: 3, currentPassengers: 3,
      status: 'OPEN', autoAccept: true, price: 2000,
      tags: [TAGS[0], TAGS[4]], createdAt: new Date().toISOString(),
    },
    {
      id: 5, memberId: 1, nickname: '테스트유저', driverAverageRating: 4.8,
      title: '선릉역 → 수원역',
      departureLocation: '선릉역 3번 출구', destinationLocation: '수원역 환승센터',
      departureLat: 37.5045, departureLng: 127.0495,
      destinationLat: 37.2636, destinationLng: 127.0286,
      departureTime: new Date(Date.now() + 6 * 60 * 60 * 1000).toISOString(),
      maxPassengers: 4, currentPassengers: 1,
      status: 'OPEN', autoAccept: false, price: 8000,
      tags: [TAGS[0], TAGS[2]], createdAt: new Date().toISOString(),
    },
    // 시나리오: 자리 꽉 찬 게시글 (magang 표시 테스트)
    {
      id: 6, memberId: 5, nickname: '최수진', driverAverageRating: 4.0,
      title: '서울역 → 인천공항',
      departureLocation: '서울역 버스환승센터', destinationLocation: '인천국제공항 T1',
      departureLat: 37.5547, departureLng: 126.9706,
      destinationLat: 37.4491, destinationLng: 126.4510,
      departureTime: new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString(),
      maxPassengers: 2, currentPassengers: 2,
      status: 'OPEN', autoAccept: true, price: 15000,
      tags: [TAGS[0], TAGS[4]], createdAt: new Date().toISOString(),
    },
    // 시나리오: CLOSED 게시글
    {
      id: 7, memberId: 6, nickname: '정민영', driverAverageRating: 4.6,
      title: '건대입구 → 성수역',
      departureLocation: '건대입구역 5번 출구', destinationLocation: '성수역 2번 출구',
      departureLat: 37.5403, departureLng: 127.0694,
      destinationLat: 37.5444, destinationLng: 127.0558,
      departureTime: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
      maxPassengers: 3, currentPassengers: 2,
      status: 'CLOSED', autoAccept: true, price: 1000,
      tags: [TAGS[2]], createdAt: new Date().toISOString(),
    },
    // 시나리오: 좌표 없는 게시글 (지도 마커 미표시 테스트)
    {
      id: 8, memberId: 7, nickname: '한도연', driverAverageRating: 0.0,
      title: '분당 → 판교',
      departureLocation: '분당 서현역', destinationLocation: '판교 현대백화점',
      departureLat: null, departureLng: null,
      destinationLat: null, destinationLng: null,
      departureTime: new Date(Date.now() + 12 * 60 * 60 * 1000).toISOString(),
      maxPassengers: 3, currentPassengers: 0,
      status: 'OPEN', autoAccept: true, price: 0,
      tags: [], createdAt: new Date().toISOString(),
    },
  ],
  comments: {
    1: [
      { id: 51, postId: 1, memberId: 2, nickname: '김민준', content: '판교 KT 본사 앞에서 내릴 수 있나요?', createdAt: new Date(Date.now() - 30 * 60 * 1000).toISOString(), updatedAt: new Date(Date.now() - 30 * 60 * 1000).toISOString() },
      { id: 52, postId: 1, memberId: 1, nickname: '테스트유저', content: '네, 가능합니다!', createdAt: new Date(Date.now() - 20 * 60 * 1000).toISOString(), updatedAt: new Date(Date.now() - 20 * 60 * 1000).toISOString() },
    ],
    2: [],
    3: [
      { id: 53, postId: 3, memberId: 1, nickname: '테스트유저', content: '짐이 캐리어 하나인데 괜찮나요?', createdAt: new Date(Date.now() - 10 * 60 * 1000).toISOString(), updatedAt: new Date(Date.now() - 10 * 60 * 1000).toISOString() },
    ],
    4: [], 5: [], 6: [], 7: [], 8: [],
  },
  applications: {
    // 내 게시글(id:1)에 온 신청들 — 신청 관리 UI 테스트
    1: [
      { id: 61, postId: 1, applicantId: 2, applicantNickname: '김민준', status: 'PENDING',  createdAt: new Date(Date.now() - 20 * 60 * 1000).toISOString() },
      { id: 62, postId: 1, applicantId: 3, applicantNickname: '이서연', status: 'ACCEPTED', createdAt: new Date(Date.now() - 40 * 60 * 1000).toISOString() },
      { id: 63, postId: 1, applicantId: 4, applicantNickname: '박지호', status: 'REJECTED', createdAt: new Date(Date.now() - 60 * 60 * 1000).toISOString() },
    ],
    2: [], 3: [], 4: [],
    // 내 게시글(id:5)에 온 신청들
    5: [
      { id: 64, postId: 5, applicantId: 2, applicantNickname: '김민준', status: 'PENDING', createdAt: new Date(Date.now() - 5 * 60 * 1000).toISOString() },
    ],
    6: [], 7: [], 8: [],
  },
  // 내가 신청한 카풀 내역 — "내가 신청한 카풀" 탭 테스트
  myApplications: [
    { id: 71, postId: 2, applicantId: 1, status: 'PENDING',  createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString() },
    { id: 72, postId: 3, applicantId: 1, status: 'ACCEPTED', createdAt: new Date(Date.now() - 5 * 60 * 60 * 1000).toISOString() },
    { id: 73, postId: 6, applicantId: 1, status: 'REJECTED', createdAt: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString() },
  ],
  rides: {
    driver: [
      {
        id: 201, postId: 1, driverId: 1,
        status: 'SCHEDULED',
        departureTime: new Date(Date.now() + 25 * 60 * 1000).toISOString(), // 25분 후
        startedAt: null, completedAt: null,
        departureLocation: '강남역 3번 출구',
        departureLat: 37.4979, departureLng: 127.0276,
        destinationLocation: '판교역 2번 출구',
        destinationLat: 37.3943, destinationLng: 127.1110,
      },
      {
        id: 202, postId: 5, driverId: 1,
        status: 'IN_PROGRESS',
        departureTime: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
        startedAt: new Date(Date.now() - 8 * 60 * 1000).toISOString(),
        completedAt: null,
        departureLocation: '선릉역 3번 출구',
        departureLat: 37.5045, departureLng: 127.0495,
        destinationLocation: '수원역 환승센터',
        destinationLat: 37.2636, destinationLng: 127.0286,
      },
      {
        id: 203, postId: 1, driverId: 1,
        status: 'COMPLETED',
        departureTime: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString(),
        startedAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000 + 5 * 60 * 1000).toISOString(),
        completedAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000 + 45 * 60 * 1000).toISOString(),
        departureLocation: '강남역 3번 출구',
        departureLat: 37.4979, departureLng: 127.0276,
        destinationLocation: '판교역 2번 출구',
        destinationLat: 37.3943, destinationLng: 127.1110,
      },
    ],
    passenger: [
      {
        id: 301, postId: 2, driverId: 2,
        status: 'COMPLETED',
        departureTime: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString(),
        startedAt: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000 + 3 * 60 * 1000).toISOString(),
        completedAt: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000 + 40 * 60 * 1000).toISOString(),
        departureLocation: '홍대입구역 9번 출구',
        departureLat: 37.5572, departureLng: 126.9247,
        destinationLocation: '여의도역 1번 출구',
        destinationLat: 37.5215, destinationLng: 126.9242,
      },
      {
        id: 302, postId: 3, driverId: 3,
        status: 'SCHEDULED',
        departureTime: new Date(Date.now() + 3 * 60 * 60 * 1000).toISOString(),
        startedAt: null, completedAt: null,
        departureLocation: '신촌역 2번 출구',
        departureLat: 37.5552, departureLng: 126.9369,
        destinationLocation: '구로디지털단지역',
        destinationLat: 37.4851, destinationLng: 126.9012,
      },
    ],
  },
  passengers: {
    202: [
      { id: 401, applicationId: 401, passengerId: 2, status: 'BOARDED' },
      { id: 402, applicationId: 402, passengerId: 3, status: 'PENDING' },
    ],
    201: [
      { id: 403, applicationId: 403, passengerId: 4, status: 'PENDING' },
    ],
  },
  reviews: {},
}

function genId() { return db.nextId++ }

function api(message, data = null) {
  return JSON.stringify({ message, data })
}

function page(content) {
  return { content, totalElements: content.length, totalPages: 1, size: 20, number: 0 }
}

function send(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' })
  res.end(typeof body === 'string' ? body : JSON.stringify(body))
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = ''
    req.on('data', chunk => { data += chunk })
    req.on('end', () => {
      try { resolve(JSON.parse(data)) } catch { resolve({}) }
    })
  })
}

function postDetail(post) {
  return {
    ...post,
    description: '',
    comments: (db.comments[post.id] || []),
    updatedAt: post.createdAt,
  }
}

export function mockPlugin() {
  return {
    name: 'mock-api',
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        const url = req.url?.split('?')[0]
        const method = req.method

        if (!url?.startsWith('/api/v1')) return next()

        const path = url.replace('/api/v1', '')

        // ── Auth ──────────────────────────────────────────────
        if (method === 'POST' && path === '/auth/signup') {
          return send(res, 201, api('회원가입이 완료되었습니다.'))
        }
        if (method === 'POST' && path === '/auth/login') {
          return send(res, 200, api('로그인 성공', { accessToken: MOCK_TOKEN }))
        }
        if (method === 'POST' && path === '/auth/logout') {
          return send(res, 200, api('로그아웃 성공'))
        }
        if (method === 'POST' && path === '/auth/refresh') {
          return send(res, 200, api('토큰 재발급 성공', { accessToken: MOCK_TOKEN }))
        }

        // ── Tags ──────────────────────────────────────────────
        if (method === 'GET' && path === '/tags') {
          return send(res, 200, api('태그 목록 조회 성공', TAGS))
        }

        // ── Driver Colors ────────────────────────────────────
        if (method === 'GET' && path === '/drivers/colors') {
          return send(res, 200, api('차량 색상 목록 조회 성공', CAR_COLORS))
        }

        // ── Posts ─────────────────────────────────────────────
        if (method === 'GET' && path === '/posts') {
          return send(res, 200, api('게시글 목록 조회 성공', page(db.posts)))
        }
        if (method === 'POST' && path === '/posts') {
          const body = await readBody(req)
          const newPost = {
            id: genId(), memberId: 1, nickname: db.profile.nickname,
            driverAverageRating: 0.0,
            title: body.title,
            departureLocation: body.departureLocation,
            destinationLocation: body.destinationLocation,
            departureLat: body.departureLat ?? null,
            departureLng: body.departureLng ?? null,
            destinationLat: body.destinationLat ?? null,
            destinationLng: body.destinationLng ?? null,
            departureTime: body.departureTime,
            maxPassengers: body.maxPassengers,
            currentPassengers: 0,
            status: 'OPEN', autoAccept: body.autoAccept ?? true,
            price: body.price ?? null,
            tags: (body.tagIds || []).map(id => TAGS.find(t => t.id === id)).filter(Boolean),
            createdAt: new Date().toISOString(),
          }
          db.posts.unshift(newPost)
          db.comments[newPost.id] = []
          db.applications[newPost.id] = []
          return send(res, 201, api('게시글이 생성되었습니다.', postDetail(newPost)))
        }

        const postMatch = path.match(/^\/posts\/(\d+)$/)
        if (postMatch) {
          const id = Number(postMatch[1])
          const post = db.posts.find(p => p.id === id)
          if (method === 'GET') {
            if (!post) return send(res, 404, api('게시글을 찾을 수 없습니다.'))
            return send(res, 200, api('게시글 조회 성공', postDetail(post)))
          }
          if (method === 'PATCH') {
            if (!post) return send(res, 404, api('게시글을 찾을 수 없습니다.'))
            const body = await readBody(req)
            Object.assign(post, body)
            return send(res, 200, api('게시글이 수정되었습니다.', postDetail(post)))
          }
          if (method === 'DELETE') {
            const idx = db.posts.findIndex(p => p.id === id)
            if (idx !== -1) db.posts.splice(idx, 1)
            return send(res, 200, api('게시글이 삭제되었습니다.'))
          }
        }

        const closeMatch = path.match(/^\/posts\/(\d+)\/close$/)
        if (closeMatch && method === 'POST') {
          const id = Number(closeMatch[1])
          const post = db.posts.find(p => p.id === id)
          if (post) post.status = 'CLOSED'
          return send(res, 200, api('카풀이 마감되었습니다.'))
        }

        // ── Comments ──────────────────────────────────────────
        const commentsMatch = path.match(/^\/posts\/(\d+)\/comments$/)
        if (commentsMatch) {
          const postId = Number(commentsMatch[1])
          if (!(postId in db.comments)) db.comments[postId] = []
          if (method === 'GET') {
            return send(res, 200, api('댓글 목록 조회 성공', db.comments[postId]))
          }
          if (method === 'POST') {
            const body = await readBody(req)
            const comment = {
              id: genId(), postId, memberId: 1,
              nickname: db.profile.nickname,
              content: body.content,
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
            }
            db.comments[postId].push(comment)
            return send(res, 201, api('댓글이 작성되었습니다.', comment))
          }
        }

        const commentMatch = path.match(/^\/comments\/(\d+)$/)
        if (commentMatch) {
          const cid = Number(commentMatch[1])
          if (method === 'PUT') {
            const body = await readBody(req)
            for (const arr of Object.values(db.comments)) {
              const c = arr.find(x => x.id === cid)
              if (c) { c.content = body.content; return send(res, 200, api('댓글이 수정되었습니다.', c)) }
            }
            return send(res, 404, api('댓글을 찾을 수 없습니다.'))
          }
          if (method === 'DELETE') {
            for (const arr of Object.values(db.comments)) {
              const idx = arr.findIndex(x => x.id === cid)
              if (idx !== -1) { arr.splice(idx, 1); return send(res, 200, api('댓글이 삭제되었습니다.')) }
            }
            return send(res, 200, api('댓글이 삭제되었습니다.'))
          }
        }

        // ── Applications ──────────────────────────────────────
        const applyMatch = path.match(/^\/posts\/(\d+)\/applications$/)
        if (applyMatch) {
          const postId = Number(applyMatch[1])
          if (!(postId in db.applications)) db.applications[postId] = []
          if (method === 'POST') {
            const post = db.posts.find(p => p.id === postId)
            if (post?.memberId === 1) {
              return send(res, 400, api('자신이 등록한 카풀에는 신청할 수 없습니다.'))
            }
            const app = {
              id: genId(), postId, applicantId: 1,
              applicantNickname: db.profile.nickname,
              status: 'PENDING', createdAt: new Date().toISOString(),
            }
            db.applications[postId].push(app)
            db.myApplications.push(app)
            if (post) post.currentPassengers++
            return send(res, 201, api('카풀 신청이 완료되었습니다.', app))
          }
          if (method === 'GET') {
            return send(res, 200, api('신청 목록 조회 성공', db.applications[postId] || []))
          }
        }

        if (method === 'GET' && path === '/applications/me') {
          return send(res, 200, api('내 신청 내역 조회 성공', db.myApplications))
        }

        const appStatusMatch = path.match(/^\/applications\/(\d+)\/(accept|reject|cancel-accept|cancel-reject)$/)
        if (appStatusMatch && method === 'PATCH') {
          const [, aid, action] = appStatusMatch
          const appId = Number(aid)
          let found = null
          for (const arr of Object.values(db.applications)) {
            found = arr.find(a => a.id === appId)
            if (found) break
          }
          if (!found) return send(res, 404, api('신청을 찾을 수 없습니다.'))
          if (action === 'accept')        found.status = 'ACCEPTED'
          if (action === 'reject')        found.status = 'REJECTED'
          if (action === 'cancel-accept') found.status = 'PENDING'
          if (action === 'cancel-reject') found.status = 'PENDING'
          const msg = { accept:'신청을 수락했습니다.', reject:'신청을 거절했습니다.', 'cancel-accept':'수락을 취소했습니다.', 'cancel-reject':'거절을 취소했습니다.' }
          return send(res, 200, api(msg[action], found))
        }

        // ── Members ───────────────────────────────────────────
        if (path === '/members/me') {
          if (method === 'GET') return send(res, 200, api('프로필을 조회했습니다.', db.profile))
          if (method === 'PUT') {
            const body = await readBody(req)
            if (body.nickname) db.profile.nickname = body.nickname
            return send(res, 200, api('프로필이 수정되었습니다.', db.profile))
          }
          if (method === 'DELETE') return send(res, 200, api('회원 탈퇴가 완료되었습니다.'))
        }

        const memberMatch = path.match(/^\/members\/(\d+)$/)
        if (memberMatch && method === 'GET') {
          return send(res, 200, api('프로필을 조회했습니다.', db.profile))
        }

        // ── Drivers ───────────────────────────────────────────
        if (path === '/drivers/me') {
          if (method === 'GET') {
            if (!db.driver) return send(res, 404, api('드라이버 정보를 찾을 수 없습니다.'))
            return send(res, 200, api('운전자 정보 조회 성공', db.driver))
          }
        }
        if (path === '/drivers') {
          if (method === 'POST') {
            const body = await readBody(req)
            const color = CAR_COLORS.find(c => c.name === body.carColor)
            db.driver = {
              driverId: 1, memberId: 1,
              carModel: body.carModel, carColor: body.carColor,
              carColorLabel: color?.label ?? body.carColor,
              carNumber: body.carNumber, averageRating: 0.0,
              createdAt: new Date().toISOString(),
            }
            return send(res, 201, api('운전자 등록이 완료되었습니다.', db.driver))
          }
          if (method === 'PUT') {
            const body = await readBody(req)
            const color = CAR_COLORS.find(c => c.name === body.carColor)
            db.driver = { ...db.driver, ...body, carColorLabel: color?.label ?? body.carColor }
            return send(res, 200, api('운전자 정보가 수정되었습니다.', db.driver))
          }
        }

        // ── Rides ─────────────────────────────────────────────
        if (method === 'GET' && path === '/rides/me') {
          return send(res, 200, api('내 운행 목록 조회 성공', db.rides.driver))
        }
        if (method === 'GET' && path === '/rides/me/passenger') {
          return send(res, 200, api('내 탑승 내역 조회 성공', db.rides.passenger))
        }
        if (method === 'GET' && path === '/rides/me/history') {
          return send(res, 200, api('운행 이력 조회 성공', [...db.rides.driver, ...db.rides.passenger]))
        }
        if (method === 'POST' && path === '/rides') {
          const body = await readBody(req)
          const post = db.posts.find(p => p.id === body.postId)
          const ride = {
            id: genId(), postId: body.postId, driverId: 1,
            status: 'SCHEDULED', startedAt: null, completedAt: null,
            departureTime: post?.departureTime ?? null,
            departureLocation: post?.departureLocation ?? null,
            departureLat: post?.departureLat ?? null,
            departureLng: post?.departureLng ?? null,
            destinationLocation: post?.destinationLocation ?? null,
            destinationLat: post?.destinationLat ?? null,
            destinationLng: post?.destinationLng ?? null,
          }
          db.rides.driver.push(ride)
          return send(res, 201, api('운행이 생성되었습니다.', ride))
        }

        const rideMatch = path.match(/^\/rides\/(\d+)$/)
        if (rideMatch && method === 'GET') {
          const id = Number(rideMatch[1])
          const ride = [...db.rides.driver, ...db.rides.passenger].find(r => r.id === id)
          if (!ride) return send(res, 404, api('운행을 찾을 수 없습니다.'))
          return send(res, 200, api('운행 조회 성공', ride))
        }

        const rideStartMatch = path.match(/^\/rides\/(\d+)\/(start|complete)$/)
        if (rideStartMatch && method === 'POST') {
          const id = Number(rideStartMatch[1])
          const action = rideStartMatch[2]
          const ride = db.rides.driver.find(r => r.id === id)
          if (!ride) return send(res, 404, api('운행을 찾을 수 없습니다.'))
          if (action === 'start') {
            ride.status = 'IN_PROGRESS'
            ride.startedAt = new Date().toISOString()
            if (!db.passengers[id]) db.passengers[id] = []
          }
          if (action === 'complete') {
            ride.status = 'COMPLETED'
            ride.completedAt = new Date().toISOString()
          }
          const msg = action === 'start' ? '운행이 시작되었습니다.' : '운행이 종료되었습니다.'
          return send(res, 200, api(msg, ride))
        }

        const rideLocationMatch = path.match(/^\/rides\/(\d+)\/location$/)
        if (rideLocationMatch && method === 'GET') {
          return send(res, 200, api('드라이버 위치 조회 성공', null))
        }

        const passengersMatch = path.match(/^\/rides\/(\d+)\/passengers$/)
        if (passengersMatch && method === 'GET') {
          const rideId = Number(passengersMatch[1])
          return send(res, 200, api('탑승자 목록 조회 성공', db.passengers[rideId] || []))
        }

        const boardMatch = path.match(/^\/rides\/(\d+)\/passengers\/(\d+)\/(board|dropoff)$/)
        if (boardMatch && method === 'POST') {
          const rideId = Number(boardMatch[1])
          const appId = Number(boardMatch[2])
          const newStatus = boardMatch[3] === 'board' ? 'BOARDED' : 'DROPPED_OFF'
          const list = db.passengers[rideId] || []
          const p = list.find(x => x.applicationId === appId)
          if (p) p.status = newStatus
          return send(res, 200, api('확인되었습니다.', p || { id: genId(), applicationId: appId, passengerId: 2, status: newStatus }))
        }

        const rideHistoryMatch = path.match(/^\/rides\/(\d+)\/history$/)
        if (rideHistoryMatch && method === 'GET') {
          return send(res, 200, api('운행 상세 기록 조회 성공', null))
        }

        // ── Reviews ───────────────────────────────────────────
        const reviewCreateMatch = path.match(/^\/reviews\/rides\/(\d+)$/)
        if (reviewCreateMatch && method === 'POST') {
          const body = await readBody(req)
          const review = { id: genId(), rideId: Number(reviewCreateMatch[1]), reviewerId: 1, rating: body.rating, comment: body.comment, createdAt: new Date().toISOString() }
          db.reviews[reviewCreateMatch[1]] = review
          return send(res, 201, api('평가가 등록되었습니다.', review))
        }
        if (method === 'GET' && path === '/reviews/me') {
          return send(res, 200, api('내 평가 목록 조회 성공', Object.values(db.reviews)))
        }
        const reviewRideMatch = path.match(/^\/reviews\/ride\/(\d+)\/me$/)
        if (reviewRideMatch && method === 'GET') {
          return send(res, 200, api('내 평가 조회 성공', db.reviews[reviewRideMatch[1]] ?? null))
        }
        const driverRatingMatch = path.match(/^\/reviews\/driver\/(\d+)\/rating$/)
        if (driverRatingMatch && method === 'GET') {
          return send(res, 200, api('드라이버 평점 조회 성공', 4.5))
        }

        // 매칭 실패 → 실제 백엔드로 프록시
        next()
      })
    },
  }
}
