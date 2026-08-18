// VITE_API_BASE 미설정 시(로컬 dev) 상대경로 → Vite proxy 사용.
// Vercel 배포 시 VITE_API_BASE=https://<백엔드도메인> 으로 EC2 직접 지정.
const BASE = `${import.meta.env.VITE_API_BASE ?? ''}/api/v1`

function getToken() {
  return localStorage.getItem('accessToken')
}

function setToken(token) {
  localStorage.setItem('accessToken', token)
}

function clearToken() {
  localStorage.removeItem('accessToken')
}

let refreshPromise = null

async function tryRefresh() {
  if (refreshPromise) return refreshPromise
  refreshPromise = fetch(`${BASE}/auth/refresh`, { method: 'POST', credentials: 'include' })
    .then(res => {
      if (!res.ok) throw new Error('refresh failed')
      return res.json()
    })
    .then(body => setToken(body.data.accessToken))
    .finally(() => { refreshPromise = null })
  return refreshPromise
}

async function request(path, options = {}, retry = true) {
  const token = getToken()
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers,
  }
  const res = await fetch(`${BASE}${path}`, { ...options, headers })

  if (res.status === 401 && retry) {
    try {
      await tryRefresh()
      return request(path, options, false)
    } catch {
      clearToken()
      window.dispatchEvent(new CustomEvent('auth:logout'))
      throw new Error('로그인이 필요합니다.')
    }
  }

  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    const err = new Error(body.message || `HTTP ${res.status}`)
    err.status = res.status
    err.body = body
    throw err
  }
  return res.json()
}

export const api = {
  get:    (path)        => request(path),
  post:   (path, body)  => request(path, { method: 'POST',   body: JSON.stringify(body) }),
  put:    (path, body)  => request(path, { method: 'PUT',    body: JSON.stringify(body) }),
  patch:  (path, body)  => request(path, { method: 'PATCH',  body: JSON.stringify(body) }),
  delete: (path)        => request(path, { method: 'DELETE' }),
}
