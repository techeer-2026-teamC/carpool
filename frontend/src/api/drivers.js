import { api } from './client'

export async function getMyDriver() {
  const res = await api.get('/drivers/me')
  return res.data
}

export async function registerDriver(carModel, carColor, carNumber) {
  const res = await api.post('/drivers', { carModel, carColor, carNumber })
  return res.data
}

export async function updateDriver(carModel, carColor, carNumber) {
  const res = await api.put('/drivers', { carModel, carColor, carNumber })
  return res.data
}
