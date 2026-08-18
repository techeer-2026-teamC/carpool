import { api } from './client'

export async function fetchPosts() {
  const res = await api.get('/posts')
  return res.data
}

export async function getPostById(id) {
  const res = await api.get(`/posts/${id}`)
  return res.data
}

export async function createPost(payload) {
  const res = await api.post('/posts', payload)
  return res.data
}

export async function updatePost(id, payload) {
  const res = await api.patch(`/posts/${id}`, payload)
  return res.data
}

export async function removePost(id) {
  return api.delete(`/posts/${id}`)
}

export async function closePost(id) {
  const res = await api.post(`/posts/${id}/close`)
  return res.data
}
