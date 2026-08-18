import React, { useState } from 'react'
import { submitReview } from '../api/reviews'

function StarRating({ value, onChange }) {
  const [hovered, setHovered] = useState(0)
  return (
    <div style={{ display: 'flex', gap: '0.3rem', justifyContent: 'center', margin: '1rem 0' }}>
      {[1, 2, 3, 4, 5].map(star => (
        <button
          key={star}
          type="button"
          style={{
            background: 'none', border: 'none', cursor: 'pointer',
            fontSize: '2rem',
            color: star <= (hovered || value) ? '#f0c040' : 'var(--border)',
            transition: 'color 0.15s, transform 0.1s',
            transform: star <= (hovered || value) ? 'scale(1.15)' : 'scale(1)',
            padding: '0.1rem',
          }}
          onMouseEnter={() => setHovered(star)}
          onMouseLeave={() => setHovered(0)}
          onClick={() => onChange(star)}
        >
          ★
        </button>
      ))}
    </div>
  )
}

const RATING_LABELS = { 1: '별로예요', 2: '아쉬워요', 3: '보통이에요', 4: '좋았어요', 5: '최고예요!' }

export default function ReviewModal({ ride, onClose, onSubmitted }) {
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [loading, setLoading] = useState(false)
  const [err, setErr] = useState('')

  async function handleSubmit(e) {
    e.preventDefault()
    if (!rating) { setErr('별점을 선택해주세요.'); return }
    setLoading(true)
    setErr('')
    try {
      await submitReview(ride.id, rating, comment.trim() || null)
      onSubmitted?.()
      onClose()
    } catch (error) {
      setErr(error.message || '평가 등록에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={styles.overlay} onClick={onClose}>
      <div style={styles.modal} onClick={e => e.stopPropagation()}>
        <div style={styles.header}>
          <h2 style={styles.title}>드라이버 평가</h2>
          <button style={styles.closeBtn} onClick={onClose}>✕</button>
        </div>

        <div style={styles.routeInfo}>
          <span style={styles.routeText}>
            {ride.departureLocation || '출발지'} → {ride.destinationLocation || '목적지'}
          </span>
          <span style={styles.rideId}>운행 #{ride.id}</span>
        </div>

        <form onSubmit={handleSubmit}>
          <StarRating value={rating} onChange={setRating} />
          {rating > 0 && (
            <div style={styles.ratingLabel}>{RATING_LABELS[rating]}</div>
          )}

          <textarea
            style={styles.textarea}
            placeholder="후기를 남겨주세요 (선택사항)"
            value={comment}
            onChange={e => setComment(e.target.value)}
            maxLength={500}
            rows={4}
          />
          <div style={styles.charCount}>{comment.length}/500</div>

          {err && <div style={styles.errorBox}>{err}</div>}

          <div style={styles.actions}>
            <button type="button" style={styles.cancelBtn} onClick={onClose}>취소</button>
            <button type="submit" style={styles.submitBtn} disabled={loading || !rating}>
              {loading ? '등록 중...' : '평가 등록'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

const styles = {
  overlay: {
    position: 'fixed', inset: 0,
    background: 'rgba(0,0,0,0.55)',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    zIndex: 1000, padding: '1rem',
  },
  modal: {
    background: 'var(--surface)',
    borderRadius: 20,
    padding: '1.8rem',
    width: '100%', maxWidth: 400,
    boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
  },
  header: {
    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
    marginBottom: '1rem',
  },
  title: {
    fontSize: '1.1rem', fontWeight: 700, color: 'var(--text)', margin: 0,
  },
  closeBtn: {
    background: 'none', border: 'none', cursor: 'pointer',
    fontSize: '1.1rem', color: 'var(--text-muted)', padding: '0.2rem',
  },
  routeInfo: {
    display: 'flex', flexDirection: 'column', alignItems: 'center',
    gap: '0.3rem', marginBottom: '0.5rem',
  },
  routeText: {
    fontSize: '0.92rem', fontWeight: 600, color: 'var(--text)',
  },
  rideId: {
    fontSize: '0.72rem', color: 'var(--text-muted)',
  },
  ratingLabel: {
    textAlign: 'center', fontSize: '0.9rem', fontWeight: 600,
    color: '#f0c040', marginBottom: '1rem',
  },
  textarea: {
    width: '100%', boxSizing: 'border-box',
    border: '1px solid var(--border)', borderRadius: 10,
    padding: '0.8rem', fontSize: '0.85rem',
    fontFamily: 'inherit', color: 'var(--text)',
    background: 'var(--surface2)', resize: 'vertical',
    outline: 'none',
  },
  charCount: {
    textAlign: 'right', fontSize: '0.72rem', color: 'var(--text-muted)',
    marginTop: '0.3rem',
  },
  errorBox: {
    color: 'var(--accent3, #c0392b)', fontSize: '0.83rem',
    background: 'rgba(192,57,43,0.06)', borderRadius: 8,
    padding: '0.6rem 0.8rem', marginTop: '0.8rem',
  },
  actions: {
    display: 'flex', gap: '0.6rem', marginTop: '1.2rem',
  },
  cancelBtn: {
    flex: 1, background: 'var(--surface2)',
    border: '1px solid var(--border)', borderRadius: 10,
    padding: '0.7rem', fontSize: '0.88rem',
    fontWeight: 600, cursor: 'pointer', color: 'var(--text-muted)',
  },
  submitBtn: {
    flex: 2, background: 'var(--accent)', color: '#fff',
    border: 'none', borderRadius: 10,
    padding: '0.7rem', fontSize: '0.88rem',
    fontWeight: 700, cursor: 'pointer',
  },
}
