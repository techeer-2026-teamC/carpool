import { check } from 'k6';

export function checkStatus(res, expectedStatus, label) {
    const ok = check(res, {
        [`[${label}] status ${expectedStatus}`]: (r) => r.status === expectedStatus,
    });
    if (!ok) {
        console.warn(`[${label}] 실패: status=${res.status} body=${res.body ? res.body.substring(0, 200) : ''}`);
    }
    return ok;
}

export function checkAndExtractId(res, expectedStatus, label) {
    if (!checkStatus(res, expectedStatus, label)) return null;
    try {
        return res.json('data.id');
    } catch (_) {
        console.warn(`[${label}] data.id 추출 실패: ${res.body ? res.body.substring(0, 200) : ''}`);
        return null;
    }
}
