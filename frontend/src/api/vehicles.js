import { api } from './client'

export async function getCarColors() {
  const res = await api.get('/drivers/colors')
  return res.data
}
