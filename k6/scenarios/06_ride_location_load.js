/**
 * 시나리오 6: 운행 위치 부하테스트 — WebSocket STOMP DB INSERT 부하 측정
 * 목적: 운행 중 위치 정보가 지속적으로 INSERT될 때 DB 부하 측정
 *       10 / 100 / 1000 사용자 규모별 처리량 측정 → Write Behind 캐싱 전/후 비교 기준선
 *
 * 실행:
 *   k6 run -e SCALE=10   k6/scenarios/06_ride_location_load.js
 *   k6 run -e SCALE=100  k6/scenarios/06_ride_location_load.js
 *   k6 run -e SCALE=1000 k6/scenarios/06_ride_location_load.js
 *
 * 결과 저장 (비교용):
 *   k6 run -e SCALE=100 --out json=results/location_before_writebehind_100.json k6/scenarios/06_ride_location_load.js
 *
 * Grafana 확인:
 *   - location_send_errors   → 전송 실패율
 *   - ws_connect_duration    → WS 연결 시간
 *   - JVM Micrometer Heap    → DB INSERT 부하 중 메모리 압박
 */
import http from 'k6/http';
import ws from 'k6/ws';
import { sleep, check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { BASE_URL, WS_URL, authHeaders } from '../utils/auth.js';
import { randomDriverPayload, randomPostPayload, randomLocation, stompConnectFrame, stompSendLocationFrame } from '../utils/data.js';

const SCALE = parseInt(__ENV.SCALE) || 10;

// setup()에서 생성할 드라이버 풀 크기 (최대 100개로 제한해 setup 시간 조정)
// SCALE > 100이면 여러 VU가 같은 드라이버/운행을 공유 — 동시 INSERT 효과 극대화
const POOL_SIZE = Math.min(SCALE, 100);

// 각 WS 세션당 위치 전송 횟수 (1초 간격, 30회 = 30초)
const SENDS_PER_SESSION = 30;

export const options = {
    scenarios: {
        location_load: {
            executor: 'constant-vus',
            vus: SCALE,
            duration: '120s',
        },
    },
    thresholds: {
        'ws_connect_duration':  ['p(95)<500'],
        'location_send_errors': [`count<${Math.ceil(SCALE * SENDS_PER_SESSION * 0.01)}`],
        'http_req_failed':      ['rate<0.05'],
    },
};

const wsConnectDuration  = new Trend('ws_connect_duration');
const locationSendErrors = new Counter('location_send_errors');
const locationInsertRate = new Counter('location_insert_success');

export function setup() {
    const password = 'password1234';
    const ts       = Date.now();
    const pool     = [];

    console.log(`드라이버 풀 ${POOL_SIZE}개 생성 중 (SCALE=${SCALE})...`);

    for (let i = 0; i < POOL_SIZE; i++) {
        const email = `load_driver_${i}_${ts}@test.com`;

        // 회원가입
        http.post(`${BASE_URL}/api/v1/auth/signup`,
            JSON.stringify({ email, password, nickname: `load_driver_${i}_${ts}` }),
            { headers: { 'Content-Type': 'application/json' } });

        // 로그인
        const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`,
            JSON.stringify({ email, password }),
            { headers: { 'Content-Type': 'application/json' } });
        if (loginRes.status !== 200) continue;

        const token = loginRes.json('data.accessToken');
        const hdr   = authHeaders(token);

        // 드라이버 등록
        http.post(`${BASE_URL}/api/v1/drivers`,
            JSON.stringify(randomDriverPayload()), hdr);

        // 게시글 생성
        const postRes = http.post(`${BASE_URL}/api/v1/posts`,
            JSON.stringify(randomPostPayload(120)), hdr);
        if (postRes.status !== 201) continue;

        const postId = postRes.json('data.id');

        // 운행 생성
        const rideRes = http.post(`${BASE_URL}/api/v1/rides`,
            JSON.stringify({ postId }), hdr);
        if (rideRes.status !== 201) continue;

        const rideId = rideRes.json('data.id');

        // 운행 시작 (IN_PROGRESS 상태여야 위치 INSERT 허용)
        const startRes = http.post(`${BASE_URL}/api/v1/rides/${rideId}/start`, null, hdr);
        if (startRes.status !== 200) continue;

        pool.push({ token, rideId });
        sleep(0.01);
    }

    console.log(`setup 완료: 풀 ${pool.length}개 (SCALE=${SCALE})`);
    return { pool };
}

export default function (data) {
    const { pool } = data;
    if (!pool || pool.length === 0) return;

    // VU마다 풀에서 드라이버 선택 (round-robin)
    const { token, rideId } = pool[(__VU - 1) % pool.length];
    const wsParams = { headers: { 'Authorization': `Bearer ${token}` } };

    let sendCount = 0;
    let connected = false;
    const startMs = Date.now();

    const res = ws.connect(WS_URL, wsParams, function (socket) {
        socket.on('open', () => {
            socket.send(stompConnectFrame(token));
        });

        socket.on('message', (data) => {
            if (data.startsWith('CONNECTED') && !connected) {
                connected = true;
                wsConnectDuration.add(Date.now() - startMs);

                socket.setInterval(() => {
                    if (sendCount >= SENDS_PER_SESSION) {
                        socket.close();
                        return;
                    }
                    try {
                        const { latitude, longitude } = randomLocation(37.5, 127.0);
                        socket.send(stompSendLocationFrame(rideId, latitude, longitude));
                        locationInsertRate.add(1);
                        sendCount++;
                    } catch (e) {
                        locationSendErrors.add(1);
                    }
                }, 1000);
            }
        });

        socket.on('error', (e) => {
            locationSendErrors.add(1);
            console.error(`VU ${__VU} WS 오류:`, e);
        });

        // 최대 (SENDS_PER_SESSION + 5)초 후 강제 종료
        socket.setTimeout(() => socket.close(), (SENDS_PER_SESSION + 5) * 1000);
    });

    check(res, { 'WS 연결 성공': (r) => r && r.status === 101 });
    check({ sendCount }, { [`위치 ${SENDS_PER_SESSION}회 전송`]: (d) => d.sendCount >= SENDS_PER_SESSION });
}

export function teardown(data) {
    // 운행 완료 처리 (선택적 — 장시간 테스트 시 DB 정리)
    const { pool } = data;
    if (!pool) return;
    for (const { token, rideId } of pool) {
        http.post(`${BASE_URL}/api/v1/rides/${rideId}/complete`, null, authHeaders(token));
    }
    console.log('teardown 완료: 모든 운행 종료');
}
