import { api } from './client'

export async function fetchTags() {
  const res = await api.get('/tags')
  return res.data
}
