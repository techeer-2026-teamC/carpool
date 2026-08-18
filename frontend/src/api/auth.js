import { api } from './client'

export async function signup(email, password, nickname) {
  return api.post('/auth/signup', { email, password, nickname })
}

export async function login(email, password) {
  const res = await api.post('/auth/login', { email, password })
  return res.data.accessToken
}

export async function logout() {
  return api.post('/auth/logout')
}
