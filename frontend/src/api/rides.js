import { api } from './client'

export async function getMyRidesAsDriver() {
  const res = await api.get('/rides/me')
  return res.data
}

export async function getMyRidesAsPassenger() {
  const res = await api.get('/rides/me/passenger')
  return res.data
}

export async function getRide(rideId) {
  const res = await api.get(`/rides/${rideId}`)
  return res.data
}

export async function createRide(postId) {
  const res = await api.post('/rides', { postId })
  return res.data
}

export async function startRide(rideId) {
  const res = await api.post(`/rides/${rideId}/start`)
  return res.data
}

export async function completeRide(rideId) {
  const res = await api.post(`/rides/${rideId}/complete`)
  return res.data
}

export async function getPassengers(rideId) {
  const res = await api.get(`/rides/${rideId}/passengers`)
  return res.data
}

export async function getLocation(rideId) {
  const res = await api.get(`/rides/${rideId}/location`)
  return res.data
}

export async function boardPassenger(rideId, applicationId) {
  const res = await api.post(`/rides/${rideId}/passengers/${applicationId}/board`)
  return res.data
}

export async function dropOffPassenger(rideId, applicationId) {
  const res = await api.post(`/rides/${rideId}/passengers/${applicationId}/dropoff`)
  return res.data
}

export async function getMyHistory() {
  const res = await api.get('/rides/me/history')
  return res.data
}

export async function getHistoryDetail(rideId) {
  const res = await api.get(`/rides/${rideId}/history`)
  return res.data
}
