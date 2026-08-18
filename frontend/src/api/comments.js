import { api } from './client'

export async function fetchComments(postId) {
  const res = await api.get(`/posts/${postId}/comments`)
  return res.data
}

export async function createComment(postId, content) {
  const res = await api.post(`/posts/${postId}/comments`, { content })
  return res.data
}

export async function updateComment(commentId, content) {
  const res = await api.put(`/comments/${commentId}`, { content })
  return res.data
}

export async function removeComment(commentId) {
  return api.delete(`/comments/${commentId}`)
}
