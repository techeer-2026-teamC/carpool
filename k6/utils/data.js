import { randomString, randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const DEPARTURES = [
    { name: '강남역', lat: 37.4979, lng: 127.0276 },
    { name: '홍대입구', lat: 37.5572, lng: 126.9247 },
    { name: '서울역', lat: 37.5547, lng: 126.9707 },
    { name: '잠실역', lat: 37.5133, lng: 127.1001 },
    { name: '신촌역', lat: 37.5551, lng: 126.9368 },
];

const DESTINATIONS = [
    { name: '판교역', lat: 37.3943, lng: 127.1110 },
    { name: '여의도역', lat: 37.5215, lng: 126.9242 },
    { name: '수원역', lat: 37.2664, lng: 127.0003 },
    { name: '분당구청', lat: 37.3854, lng: 127.1229 },
    { name: '인천공항', lat: 37.4491, lng: 126.4506 },
];

const CAR_COLORS = ['WHITE', 'BLACK', 'GRAY', 'SILVER', 'RED', 'BLUE'];
const CAR_MODELS = ['아반떼', 'K5', '쏘나타', '그랜저', '모닝'];

export function randomEmail() {
    return `k6_${randomString(10)}@loadtest.com`;
}

export function randomNickname() {
    return `user_${randomString(6)}`;
}

// toISOString()은 UTC 반환 → 서버의 @Future 검증(로컬 타임)이 과거로 판단하는 버그 방지
function toLocalISOString(date) {
    const pad = n => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
           `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

export function randomPostPayload(futureMinutes = 60) {
    const dep = DEPARTURES[randomIntBetween(0, DEPARTURES.length - 1)];
    const dst = DESTINATIONS[randomIntBetween(0, DESTINATIONS.length - 1)];
    const departureTime = toLocalISOString(new Date(Date.now() + futureMinutes * 60 * 1000));
    return {
        title: `${dep.name} → ${dst.name}`,
        departureLocation: dep.name,
        departureLat: dep.lat,
        departureLng: dep.lng,
        destinationLocation: dst.name,
        destinationLat: dst.lat,
        destinationLng: dst.lng,
        departureTime,
        maxPassengers: randomIntBetween(1, 4),
        description: `K6 부하테스트 게시글`,
        autoAccept: false,
        price: randomIntBetween(1000, 20000),
        tagIds: [],
    };
}

export function randomDriverPayload() {
    const num1 = randomIntBetween(10, 99);
    const num2 = randomIntBetween(1000, 9999);
    const chars = ['가', '나', '다', '라', '마', '바', '사'];
    const ch = chars[randomIntBetween(0, chars.length - 1)];
    return {
        carModel: CAR_MODELS[randomIntBetween(0, CAR_MODELS.length - 1)],
        carColor: CAR_COLORS[randomIntBetween(0, CAR_COLORS.length - 1)],
        carNumber: `${num1}${ch}${num2}`,
    };
}

export function randomLocation(baseLat = 37.5, baseLng = 127.0) {
    return {
        latitude: baseLat + (Math.random() - 0.5) * 0.05,
        longitude: baseLng + (Math.random() - 0.5) * 0.05,
    };
}

// STOMP over WebSocket 헬퍼 (k6/ws 모듈용 raw frame 생성)
export function stompConnectFrame(token) {
    return `CONNECT\naccept-version:1.2\nheart-beat:0,0\nAuthorization:Bearer ${token}\n\n\0`;
}

export function stompSendLocationFrame(rideId, latitude, longitude) {
    const body = JSON.stringify({ latitude, longitude });
    return `SEND\ndestination:/app/ride/${rideId}/location\ncontent-type:application/json\ncontent-length:${body.length}\n\n${body}\0`;
}
