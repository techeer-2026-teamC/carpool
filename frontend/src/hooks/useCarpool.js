import { useState, useEffect, useMemo, useCallback } from 'react'
import { fetchPosts, createPost as apiCreatePost, removePost as apiRemovePost, closePost as apiClosePost } from '../api/posts'
import { applyPost } from '../api/applications'

const COLORS = ['#6b7c3f', '#8a9e50', '#4a5a2a', '#a0845c', '#7a9a6b', '#5c7a4a']

function normalizePost(post, memberId) {
  const dt = post.departureTime || ''
  return {
    id: post.id,
    from: post.departureLocation,
    to: post.destinationLocation,
    date: dt.split('T')[0] || '',
    time: (dt.split('T')[1] || '').slice(0, 5),
    seats: post.maxPassengers,
    filled: post.currentPassengers,
    price: post.price ?? '',
    nickname: post.nickname || '익명',
    desc: post.description || '',
    color: COLORS[post.id % COLORS.length],
    rating: post.driverAverageRating != null ? post.driverAverageRating.toFixed(1) : '0.0',
    trips: 0,
    isMe: post.memberId === memberId,
    memberId: post.memberId,
    status: post.status,
    tags: post.tags ?? [],
    departureLat: post.departureLat ?? null,
    departureLng: post.departureLng ?? null,
    destinationLat: post.destinationLat ?? null,
    destinationLng: post.destinationLng ?? null,
  }
}

export function useCarpool(memberId) {
  const [posts, setPosts] = useState([])
  const [loading, setLoading] = useState(true)
  const [currentView, setCurrentView] = useState('card')
  const [currentFilter, setCurrentFilter] = useState('all')
  const [searchQuery, setSearchQuery] = useState({ from: '', to: '', date: '' })
  const [selectedTagFilters, setSelectedTagFilters] = useState(new Set())
  const [toast, setToast] = useState('')

  const showToast = useCallback((msg) => {
    setToast(msg)
    setTimeout(() => setToast(''), 2500)
  }, [])

  const loadPosts = useCallback(async () => {
    if (!memberId) { setLoading(false); return }
    try {
      setLoading(true)
      const data = await fetchPosts()
      setPosts((data.content ?? data).map(p => normalizePost(p, memberId)))
    } catch {
      showToast('게시글을 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [memberId, showToast])

  useEffect(() => {
    loadPosts()
  }, [loadPosts])

  const filteredPosts = useMemo(() => {
    const today = new Date().toISOString().split('T')[0]
    let f = posts.filter(p => p.status !== 'CLOSED')
    if (searchQuery.from) f = f.filter(p => p.from.includes(searchQuery.from) || p.to.includes(searchQuery.from))
    if (searchQuery.to)   f = f.filter(p => p.to.includes(searchQuery.to) || p.from.includes(searchQuery.to))
    if (searchQuery.date) f = f.filter(p => p.date === searchQuery.date)
    if (currentFilter === 'today') f = f.filter(p => p.date === today)
    if (currentFilter === 'seats') f = f.filter(p => p.filled < p.seats)
    if (currentFilter === 'cheap') f = [...f].sort((a, b) => (Number(a.price) || 99999) - (Number(b.price) || 99999))
    if (selectedTagFilters.size > 0) {
      f = f.filter(p => [...selectedTagFilters].every(tid => (p.tags || []).some(tag => tag.id === tid)))
    }
    return f
  }, [posts, searchQuery, currentFilter, selectedTagFilters])

  const myPosts = useMemo(() => posts.filter(p => p.isMe), [posts])

  const filteredMyPosts = useMemo(() => {
    let f = myPosts
    if (searchQuery.from) f = f.filter(p => p.from.includes(searchQuery.from) || p.to.includes(searchQuery.from))
    if (searchQuery.to)   f = f.filter(p => p.to.includes(searchQuery.to) || p.from.includes(searchQuery.to))
    if (searchQuery.date) f = f.filter(p => p.date === searchQuery.date)
    return f
  }, [myPosts, searchQuery])

  const toggleTagFilter = useCallback((id) => {
    setSelectedTagFilters(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }, [])

  const clearTagFilters = useCallback(() => setSelectedTagFilters(new Set()), [])

  const addPost = useCallback(async (form) => {
    try {
      const created = await apiCreatePost({
        title: `${form.from} → ${form.to}`,
        departureLocation: form.from,
        destinationLocation: form.to,
        departureLat: form.departureLat ?? null,
        departureLng: form.departureLng ?? null,
        destinationLat: form.destinationLat ?? null,
        destinationLng: form.destinationLng ?? null,
        departureTime: `${form.date}T${form.time}:00`,
        maxPassengers: Number(form.seats),
        description: form.desc || '',
        autoAccept: true,
        price: form.price ? Number(form.price) : null,
        tagIds: form.tags || [],
      })
      setPosts(prev => [normalizePost(created, memberId), ...prev])
      showToast('게시글이 등록되었습니다!')
    } catch {
      showToast('게시글 등록에 실패했습니다.')
    }
  }, [memberId, showToast])

  const deletePost = useCallback(async (id) => {
    try {
      await apiRemovePost(id)
      setPosts(prev => prev.filter(p => p.id !== id))
      showToast('게시글이 삭제되었습니다')
    } catch {
      showToast('삭제에 실패했습니다.')
    }
  }, [showToast])

  const joinCarpool = useCallback(async (id) => {
    try {
      await applyPost(id)
      setPosts(prev => prev.map(p =>
        p.id === id ? { ...p, filled: Math.min(p.filled + 1, p.seats) } : p
      ))
      showToast('참여 신청이 완료되었습니다!')
    } catch (e) {
      showToast(e.message || '신청에 실패했습니다.')
    }
  }, [showToast])

  const closePost = useCallback(async (id) => {
    try {
      await apiClosePost(id)
      setPosts(prev => prev.map(p => p.id === id ? { ...p, status: 'CLOSED' } : p))
      showToast('신청이 마감되었습니다. 운행 페이지에서 확인하세요!')
      return true
    } catch (e) {
      showToast(e.message || '신청 마감에 실패했습니다.')
      return null
    }
  }, [showToast])

  return {
    posts,
    filteredPosts,
    myPosts,
    filteredMyPosts,
    loading,
    currentView, setCurrentView,
    currentFilter, setCurrentFilter,
    searchQuery, setSearchQuery,
    selectedTagFilters,
    toggleTagFilter,
    clearTagFilters,
    toast,
    addPost,
    deletePost,
    joinCarpool,
    closePost,
  }
}
