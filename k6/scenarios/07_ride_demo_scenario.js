/**
 * 시나리오 7: 운행 데모 — 출발 전 집결 + 실시간 위치 공유 시각 확인
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │  실행 전 준비                                            │
 * │                                                         │
 * │  1. 백엔드 실행: docker-compose up -d                   │
 * │  2. 프론트 실행: npm run dev  (localhost:5173)          │
 * │  3. 브라우저 탭 2개 열기:                               │
 * │     탭1 (드라이버): test@carpool.com  / password1234    │
 * │     탭2 (승객):    admin@carpool.com / admin1234!       │
 * │     → 양쪽 모두 "내 운행" 탭으로 이동                   │
 * │  4. 두 탭 콘솔에서 테스트 모드 설정 후 새로고침:        │
 * │     localStorage.setItem('rideTestMode','1')            │
 * │                                                         │
 * │  실행: k6 run k6/scenarios/07_ride_demo_scenario.js     │
 * └─────────────────────────────────────────────────────────┘
 *
 * 시나리오 흐름:
 *   PHASE 1  위치 공유 시작 (드라이버=출발점, 승객 3명=출발점 근처)
 *   PHASE 2  승객들이 출발점으로 모임 (각자 도보 이동)
 *   PHASE 3  운행 시작 + 한 명씩 탑승 확인
 *   PHASE 4  출발점 → 목적지 이동 (도로 경로)
 *   PHASE 5  하차 + 운행 종료
 */
import http from 'k6/http';
import ws   from 'k6/ws';
import { sleep, check } from 'k6';
import { BASE_URL, WS_URL, login, authHeaders } from '../utils/auth.js';
import { stompConnectFrame, stompSendLocationFrame } from '../utils/data.js';
import { checkStatus, checkAndExtractId } from '../utils/checks.js';

const DEP = { lat: 37.4979, lng: 127.0276, name: '강남역' };
const DST = { lat: 37.3943, lng: 127.1110, name: '판교역'  };
const INTERVAL_SEC = 3; // 이동 간격 (초)

// 승객 출발 전 위치 — 출발점(강남역) 도보권 (각자 다른 방향, ~150m)
const PASSENGER_START = [
    { lat: 37.4972, lng: 127.0292 },  // 승객1: 강남역 동쪽
    { lat: 37.4990, lng: 127.0266 },  // 승객2: 강남역 북서쪽
    { lat: 37.4966, lng: 127.0262 },  // 승객3: 강남역 남서쪽
];

// 드라이버 대기 위치 — 출발점에서 약간 떨어진 도로변 (승객 마커와 구분되게)
const DRIVER_WAIT = { lat: 37.4985, lng: 127.0283 };

// 출발점 → 목적지 도로 경로 (OSRM 하드코딩, 강남역 → 판교역)
const ROUTE_TO_DEST = [
    { lat: 37.497887, lng: 127.027606 },
    { lat: 37.497925, lng: 127.027502 },
    { lat: 37.494943, lng: 127.028948 },
    { lat: 37.489709, lng: 127.03146  },
    { lat: 37.48677,  lng: 127.032847 },
    { lat: 37.484385, lng: 127.034077 },
    { lat: 37.481498, lng: 127.036979 },
    { lat: 37.480035, lng: 127.037993 },
    { lat: 37.476732, lng: 127.038566 },
    { lat: 37.472762, lng: 127.038532 },
    { lat: 37.470643, lng: 127.038668 },
    { lat: 37.468406, lng: 127.039909 },
    { lat: 37.465115, lng: 127.04201  },
    { lat: 37.463074, lng: 127.038658 },
    { lat: 37.463396, lng: 127.038228 },
    { lat: 37.46419,  lng: 127.038443 },
    { lat: 37.464121, lng: 127.039071 },
    { lat: 37.458145, lng: 127.0448   },
    { lat: 37.446538, lng: 127.055472 },
    { lat: 37.438759, lng: 127.0613   },
    { lat: 37.429269, lng: 127.0708   },
    { lat: 37.406492, lng: 127.093725 },
    { lat: 37.400897, lng: 127.099092 },
    { lat: 37.397511, lng: 127.099894 },
    { lat: 37.394808, lng: 127.104404 },
    { lat: 37.39163,  lng: 127.110909 },
    { lat: 37.393601, lng: 127.112951 },
    { lat: 37.393714, lng: 127.11099  },
];

export const options = {
    scenarios: {
        demo: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '15m',
        },
    },
    thresholds: {
        checks: ['rate>=0.9'],
    },
};

// ── 헬퍼: 위치 1회 전송 (전송 후 즉시 종료 → 빠른 반복용) ──────
function sendOneLocation(token, rideId, lat, lng, isPassenger) {
    const dest = isPassenger
        ? `/app/ride/${rideId}/passenger-location`
        : `/app/ride/${rideId}/location`;
    ws.connect(WS_URL, { headers: { 'Authorization': `Bearer ${token}` } }, (socket) => {
        socket.on('open', () => socket.send(stompConnectFrame(token)));
        socket.on('message', (msg) => {
            if (!msg.startsWith('CONNECTED')) return;
            const body = JSON.stringify({ latitude: lat, longitude: lng });
            socket.send(`SEND\ndestination:${dest}\ncontent-type:application/json\ncontent-length:${body.length}\n\n${body}\0`);
            socket.setTimeout(() => socket.close(), 150);  // 전송 직후 종료
        });
        socket.setTimeout(() => socket.close(), 2000);     // 안전장치
    });
}

// ── 헬퍼: 여러 승객을 출발점으로 "동시에" 집결시킴 ─────────────
// 각 시간 스텝마다 드라이버 + 모든 승객 위치를 연속 전송 → 시각적으로 병렬 이동
// driverToken: 집결 동안 드라이버 위치(출발점)를 계속 전송해 마커 유지
function gatherPassengers(passTokens, rideId, startPositions, dest, steps, driverToken, driverPos) {
    const paths = startPositions.map(s => interpolate(s, dest, steps));
    for (let step = 0; step < steps; step++) {
        // 드라이버는 출발점에 대기 — 매 스텝 위치 전송 (마커 유지)
        if (driverToken && driverPos) {
            sendOneLocation(driverToken, rideId, driverPos.lat, driverPos.lng, false);
        }
        for (let i = 0; i < passTokens.length; i++) {
            const { lat, lng } = paths[i][step];
            sendOneLocation(passTokens[i], rideId, lat, lng, true);
        }
        const pct = Math.round((step / (steps - 1)) * 100);
        const bar = '█'.repeat(Math.floor(pct / 10)) + '░'.repeat(10 - Math.floor(pct / 10));
        console.log(`  🚶 집결 [${bar}] ${pct}%  (승객 ${passTokens.length}명 동시 이동 + 드라이버 대기)`);
        if (step < steps - 1) sleep(INTERVAL_SEC);
    }
}

// ── 헬퍼: 한 좌표 배열을 따라 이동하며 위치 연속 전송 ──────────
function moveAlong(token, rideId, coords, isPassenger, label) {
    const dest = isPassenger
        ? `/app/ride/${rideId}/passenger-location`
        : `/app/ride/${rideId}/location`;

    ws.connect(WS_URL, { headers: { 'Authorization': `Bearer ${token}` } }, (socket) => {
        socket.on('open', () => socket.send(stompConnectFrame(token)));
        socket.on('message', (msg) => {
            if (!msg.startsWith('CONNECTED')) return;
            let step = 0;
            socket.setInterval(() => {
                if (step >= coords.length) { socket.close(); return; }
                const { lat, lng } = coords[step];
                const body = JSON.stringify({ latitude: lat, longitude: lng });
                socket.send(`SEND\ndestination:${dest}\ncontent-type:application/json\ncontent-length:${body.length}\n\n${body}\0`);
                const pct = Math.round((step / (coords.length - 1)) * 100);
                const bar = '█'.repeat(Math.floor(pct / 10)) + '░'.repeat(10 - Math.floor(pct / 10));
                console.log(`  ${label} [${bar}] ${pct}%`);
                step++;
            }, INTERVAL_SEC * 1000);
        });
        socket.setTimeout(() => socket.close(), (coords.length + 3) * INTERVAL_SEC * 1000);
    });
}

// 두 점 사이 직선 보간 (도보 이동용)
function interpolate(from, to, steps) {
    return Array.from({ length: steps }, (_, i) => ({
        lat: from.lat + (to.lat - from.lat) * (i / (steps - 1)),
        lng: from.lng + (to.lng - from.lng) * (i / (steps - 1)),
    }));
}

function toLocalISO(date) {
    const p = n => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${p(date.getMonth()+1)}-${p(date.getDate())}` +
           `T${p(date.getHours())}:${p(date.getMinutes())}:${p(date.getSeconds())}`;
}

// ── 메인 시나리오 ───────────────────────────────────────────────
export default function () {
    console.log('\n╔══════════════════════════════════════════╗');
    console.log('║     🚗 운행 데모 시나리오 시작            ║');
    console.log('╚══════════════════════════════════════════╝\n');
    console.log('브라우저 탭 확인 (테스트 모드 설정 필수):');
    console.log('  탭1 (드라이버): test@carpool.com  → 내 운행 탭');
    console.log('  탭2 (승객):    admin@carpool.com → 내 운행 탭');
    console.log("  콘솔: localStorage.setItem('rideTestMode','1') 후 새로고침\n");

    // ── 1. 로그인 ─────────────────────────────────────────────
    const driverToken = login('test@carpool.com', 'password1234');
    if (!driverToken) {
        console.error('❌ 드라이버 로그인 실패 — test@carpool.com 계정 없음');
        return;
    }
    const p1Token = login('admin@carpool.com', 'admin1234!');
    if (!p1Token) {
        console.error('❌ 승객1 로그인 실패 — admin@carpool.com 계정 없음');
        return;
    }

    // 승객2, 3: 동적 계정 생성 (실제처럼 보이는 닉네임)
    const NICKNAMES = ['김민준', '이서연', '박지호', '최수진', '정민영', '한도연', '윤지우', '임성현'];
    const ts = Date.now();
    const p2Nick = NICKNAMES[ts % NICKNAMES.length];
    const p3Nick = NICKNAMES[(ts + 3) % NICKNAMES.length];
    const p2Email = `demo_${p2Nick}_${ts}@test.com`;
    const p3Email = `demo_${p3Nick}_${ts}@test.com`;
    const pwd = 'password1234';

    for (const [email, nick] of [[p2Email, p2Nick], [p3Email, p3Nick]]) {
        http.post(`${BASE_URL}/api/v1/auth/signup`,
            JSON.stringify({ email, password: pwd, nickname: nick }),
            { headers: { 'Content-Type': 'application/json' } });
    }
    const p2Token = login(p2Email, pwd);
    const p3Token = login(p3Email, pwd);
    if (!p2Token || !p3Token) {
        console.error('❌ 동적 승객 계정 생성 실패');
        return;
    }
    console.log('✅ 드라이버 + 승객 3명 로그인 완료\n');

    const driverHdr       = authHeaders(driverToken);
    const passengerTokens = [p1Token, p2Token, p3Token];
    const passengerHdrs   = passengerTokens.map(t => authHeaders(t));

    // ── 2. 게시글 생성 (출발 5분 후 → 30분 이내 위치 공유 창 활성화) ─
    const postRes = http.post(`${BASE_URL}/api/v1/posts`, JSON.stringify({
        title:               `${DEP.name} → ${DST.name} (데모)`,
        departureLocation:   DEP.name,
        departureLat:        DEP.lat,  departureLng: DEP.lng,
        destinationLocation: DST.name,
        destinationLat:      DST.lat,  destinationLng: DST.lng,
        departureTime:       toLocalISO(new Date(Date.now() + 5 * 60 * 1000)),
        maxPassengers:       3,
        description:         '데모 운행 — 실시간 위치 공유 테스트',
        autoAccept:          true,
        price:               5000,
        tagIds:              [],
    }), driverHdr);

    const postId = checkAndExtractId(postRes, 201, '게시글 생성');
    if (!postId) return;
    console.log(`✅ 게시글 생성 (id=${postId})\n`);

    // ── 3. 승객 3명 신청 (autoAccept → 자동 수락) ─────────────
    for (let i = 0; i < passengerHdrs.length; i++) {
        const applyRes = http.post(`${BASE_URL}/api/v1/posts/${postId}/applications`, null, passengerHdrs[i]);
        checkStatus(applyRes, 201, `승객${i + 1} 신청`);
        sleep(0.3);
    }
    console.log('✅ 승객 3명 신청 + 자동 수락 완료\n');

    // ── 4. 신청 마감 + 운행 생성 ──────────────────────────────
    http.post(`${BASE_URL}/api/v1/posts/${postId}/close`, null, driverHdr);
    const rideRes = http.post(`${BASE_URL}/api/v1/rides`, JSON.stringify({ postId }), driverHdr);
    const rideId = checkAndExtractId(rideRes, 201, '운행 생성');
    if (!rideId) return;
    console.log(`✅ 운행 생성 (id=${rideId}) — SCHEDULED 상태\n`);

    // ══════════════════════════════════════════════════════════
    // PHASE 1: 위치 공유 시작
    // ══════════════════════════════════════════════════════════
    console.log('╔══════════════════════════════════════════╗');
    console.log('║  📍 PHASE 1: 위치 공유 시작              ║');
    console.log('╚══════════════════════════════════════════╝');
    console.log('  👁 드라이버=출발점, 승객 3명=출발점 근처에 표시\n');
    sleep(3);

    // 드라이버는 출발점 근처 도로변에 대기 (승객 마커와 구분)
    sendOneLocation(driverToken, rideId, DRIVER_WAIT.lat, DRIVER_WAIT.lng, false);
    console.log(`  📡 드라이버 위치: ${DEP.name} 인근 대기 중`);
    sleep(1);

    // 승객 3명 초기 위치 (출발점 근처)
    for (let i = 0; i < passengerTokens.length; i++) {
        const s = PASSENGER_START[i];
        sendOneLocation(passengerTokens[i], rideId, s.lat, s.lng, true);
        console.log(`  📡 승객${i + 1} 초기 위치: (${s.lat}, ${s.lng})`);
        sleep(1);
    }
    console.log('\n  ⏳ 6초 대기 — 브라우저에서 마커 위치를 확인하세요...\n');
    sleep(6);

    // ══════════════════════════════════════════════════════════
    // PHASE 2: 승객들이 출발점으로 모임
    // ══════════════════════════════════════════════════════════
    console.log('╔══════════════════════════════════════════╗');
    console.log('║  🚶 PHASE 2: 출발점으로 집결              ║');
    console.log('╚══════════════════════════════════════════╝');
    console.log('  👁 승객 3명 마커가 동시에 출발점으로 이동하는지 확인\n');

    gatherPassengers(passengerTokens, rideId, PASSENGER_START, DEP, 6, driverToken, DRIVER_WAIT);

    console.log('\n  ✅ 승객 전원 출발점 집결 완료\n');
    sleep(2);

    // ══════════════════════════════════════════════════════════
    // PHASE 3: 운행 시작 + 한 명씩 탑승 확인
    // ══════════════════════════════════════════════════════════
    console.log('╔══════════════════════════════════════════╗');
    console.log('║  🚀 PHASE 3: 운행 시작 + 탑승 확인       ║');
    console.log('╚══════════════════════════════════════════╝\n');

    const startRes = http.post(`${BASE_URL}/api/v1/rides/${rideId}/start`, null, driverHdr);
    checkStatus(startRes, 200, '운행 시작');
    console.log('  ✅ 운행 시작 → IN_PROGRESS\n');
    sleep(1);

    const passRes = http.get(`${BASE_URL}/api/v1/rides/${rideId}/passengers`, driverHdr);
    const passengers = passRes.json('data') || [];
    console.log(`  👥 탑승자 ${passengers.length}명 확인됨\n`);

    for (let i = 0; i < passengers.length; i++) {
        const p = passengers[i];
        const boardRes = http.post(
            `${BASE_URL}/api/v1/rides/${rideId}/passengers/${p.applicationId}/board`,
            null, driverHdr
        );
        checkStatus(boardRes, 200, `탑승 확인 #${p.passengerId}`);
        console.log(`  ✅ 승객${i + 1} (#${p.passengerId}) 탑승 확인!`);
        sleep(2);
    }
    console.log('\n  🎫 전원 탑승 완료! 목적지로 출발합니다.\n');
    sleep(1);

    // ══════════════════════════════════════════════════════════
    // PHASE 4: 목적지 이동 (도로 경로)
    // ══════════════════════════════════════════════════════════
    console.log('╔══════════════════════════════════════════╗');
    console.log(`║  🗺  PHASE 4: ${DEP.name} → ${DST.name} 이동`.padEnd(43) + '║');
    console.log('╚══════════════════════════════════════════╝');
    console.log('  👁 양쪽 탭에서 🚗 마커가 도로를 따라 이동\n');

    moveAlong(driverToken, rideId, ROUTE_TO_DEST, false, '🚗');

    console.log(`\n  ✅ ${DST.name} 도착!\n`);
    sleep(2);

    // ══════════════════════════════════════════════════════════
    // PHASE 5: 하차 + 운행 종료
    // ══════════════════════════════════════════════════════════
    console.log('╔══════════════════════════════════════════╗');
    console.log('║  🏁 PHASE 5: 하차 + 운행 종료            ║');
    console.log('╚══════════════════════════════════════════╝\n');

    const passRes2 = http.get(`${BASE_URL}/api/v1/rides/${rideId}/passengers`, driverHdr);
    const passengers2 = passRes2.json('data') || [];

    for (let i = 0; i < passengers2.length; i++) {
        const p = passengers2[i];
        if (p.status === 'BOARDED') {
            const dropRes = http.post(
                `${BASE_URL}/api/v1/rides/${rideId}/passengers/${p.applicationId}/dropoff`,
                null, driverHdr
            );
            checkStatus(dropRes, 200, `하차 확인 #${p.passengerId}`);
            console.log(`  ✅ 승객${i + 1} (#${p.passengerId}) 하차 확인`);
            sleep(1);
        }
    }

    const completeRes = http.post(`${BASE_URL}/api/v1/rides/${rideId}/complete`, null, driverHdr);
    checkStatus(completeRes, 200, '운행 종료');
    console.log('\n  ✅ 운행 종료 → COMPLETED\n');

    // ── 최종 결과 ─────────────────────────────────────────────
    console.log('╔══════════════════════════════════════════╗');
    console.log('║  🎉 데모 완료!                           ║');
    console.log('╚══════════════════════════════════════════╝');
    console.log('\n  확인 체크리스트:');
    console.log('  □ 탭1(드라이버): 승객 3명 하차 완료 표시');
    console.log('  □ 탭2(승객):    "★ 드라이버 평가하기" 버튼 표시');
    console.log(`\n  rideId: ${rideId}  |  postId: ${postId}`);
    console.log('══════════════════════════════════════════\n');
}
