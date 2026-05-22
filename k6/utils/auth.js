import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const WS_URL   = (BASE_URL.replace(/^http/, 'ws')) + '/ws';

export function signup(email, password, nickname) {
    const res = http.post(
        `${BASE_URL}/api/v1/auth/signup`,
        JSON.stringify({ email, password, nickname }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    return res.status === 201;
}

export function login(email, password) {
    const res = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email, password }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    if (!check(res, { 'login 200': (r) => r.status === 200 })) return null;
    return res.json('data.accessToken');
}

export function logout(token) {
    return http.post(`${BASE_URL}/api/v1/auth/logout`, null, {
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json',
        },
    });
}

export function authHeaders(token) {
    return { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` } };
}
