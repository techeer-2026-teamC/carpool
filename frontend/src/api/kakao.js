const KEY = import.meta.env.VITE_KAKAO_REST_API_KEY

export async function searchPlace(query) {
  if (!query.trim()) return []
  const res = await fetch(
    `https://dapi.kakao.com/v2/local/search/keyword.json?query=${encodeURIComponent(query)}&size=6`,
    { headers: { Authorization: `KakaoAK ${KEY}` } }
  )
  const data = await res.json()
  return data.documents || []
}
