import { api } from './client'

export async function applyPost(postId) {
  const res = await api.post(`/posts/${postId}/applications`)
  return res.data
}

export async function getMyApplications() {
  const res = await api.get('/applications/me')
  return res.data
}

export async function getPostApplications(postId) {
  const res = await api.get(`/posts/${postId}/applications`)
  return (res.data || []).map(app => ({ ...app, nickname: app.applicantNickname ?? app.nickname }))
}

export async function acceptApplication(id) {
  const res = await api.patch(`/applications/${id}/accept`)
  return res.data
}

export async function rejectApplication(id) {
  const res = await api.patch(`/applications/${id}/reject`)
  return res.data
}

export async function cancelAcceptApplication(id) {
  const res = await api.patch(`/applications/${id}/cancel-accept`)
  return res.data
}

export async function cancelRejectApplication(id) {
  const res = await api.patch(`/applications/${id}/cancel-reject`)
  return res.data
}
