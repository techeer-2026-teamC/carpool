import React, { useState } from 'react'
import { login, signup } from '../api/auth'

export default function LoginPage({ onLogin }) {
  const [tab, setTab] = useState('login')
  const [form, setForm] = useState({ email: '', password: '', nickname: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  function set(key, val) {
    setForm(prev => ({ ...prev, [key]: val }))
  }

  async function handleSubmit() {
    setError('')
    if (!form.email || !form.password) {
      setError('이메일과 비밀번호를 입력해주세요.')
      return
    }
    if (tab === 'signup' && !form.nickname) {
      setError('닉네임을 입력해주세요.')
      return
    }
    setLoading(true)
    try {
      if (tab === 'signup') {
        await signup(form.email, form.password, form.nickname)
      }
      const token = await login(form.email, form.password)
      onLogin(token)
    } catch (e) {
      setError(e.message || '요청에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter') handleSubmit()
  }

  return (
    <div style={styles.wrapper}>
      <div style={styles.bg} />

      <div style={styles.card}>
        <div style={styles.logo}>
          같이<span style={{ color: 'var(--text)' }}>타</span>
        </div>
        <p style={styles.tagline}>목적지가 같은 사람들을 연결합니다</p>

        <div style={styles.tabs}>
          <button
            style={{ ...styles.tab, ...(tab === 'login' ? styles.tabActive : {}) }}
            onClick={() => { setTab('login'); setError('') }}
          >
            로그인
          </button>
          <button
            style={{ ...styles.tab, ...(tab === 'signup' ? styles.tabActive : {}) }}
            onClick={() => { setTab('signup'); setError('') }}
          >
            회원가입
          </button>
        </div>

        <div style={styles.form}>
          <div style={styles.fieldGroup}>
            <label style={styles.label}>이메일</label>
            <input
              style={styles.input}
              type="email"
              placeholder="example@email.com"
              value={form.email}
              onChange={e => set('email', e.target.value)}
              onKeyDown={handleKeyDown}
            />
          </div>

          {tab === 'signup' && (
            <div style={styles.fieldGroup}>
              <label style={styles.label}>닉네임</label>
              <input
                style={styles.input}
                type="text"
                placeholder="닉네임을 입력하세요"
                value={form.nickname}
                onChange={e => set('nickname', e.target.value)}
                onKeyDown={handleKeyDown}
              />
            </div>
          )}

          <div style={styles.fieldGroup}>
            <label style={styles.label}>비밀번호</label>
            <input
              style={styles.input}
              type="password"
              placeholder="비밀번호를 입력하세요 (8자 이상)"
              value={form.password}
              onChange={e => set('password', e.target.value)}
              onKeyDown={handleKeyDown}
            />
          </div>

          {error && <div style={styles.errorBox}>{error}</div>}

          <button style={{ ...styles.submitBtn, opacity: loading ? 0.7 : 1 }} onClick={handleSubmit} disabled={loading}>
            {loading ? '처리 중...' : tab === 'login' ? '로그인' : '회원가입'}
          </button>
        </div>
      </div>
    </div>
  )
}

const styles = {
  wrapper: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '1rem',
    position: 'relative',
  },
  bg: {
    position: 'fixed',
    inset: 0,
    zIndex: 0,
  },
  card: {
    position: 'relative',
    zIndex: 1,
    background: 'var(--surface)',
    border: '1px solid var(--border)',
    borderRadius: 24,
    padding: '2.5rem 2rem',
    width: '100%',
    maxWidth: 400,
    boxShadow: '0 20px 60px rgba(42,42,31,0.1)',
  },
  logo: {
    fontFamily: "'Space Mono', monospace",
    fontSize: '2rem',
    fontWeight: 700,
    color: 'var(--accent)',
    textAlign: 'center',
    marginBottom: '0.4rem',
  },
  tagline: {
    textAlign: 'center',
    fontSize: '0.85rem',
    color: 'var(--text-muted)',
    marginBottom: '2rem',
  },
  tabs: {
    display: 'flex',
    background: 'var(--surface2)',
    border: '1px solid var(--border)',
    borderRadius: 12,
    padding: 4,
    marginBottom: '1.5rem',
    gap: 4,
  },
  tab: {
    flex: 1,
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    padding: '0.55rem',
    borderRadius: 9,
    fontSize: '0.9rem',
    fontWeight: 600,
    color: 'var(--text-muted)',
    transition: 'all 0.2s',
  },
  tabActive: {
    background: 'var(--surface)',
    color: 'var(--accent)',
    boxShadow: '0 1px 4px rgba(0,0,0,0.1)',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.2rem',
  },
  fieldGroup: {
    marginBottom: '0.8rem',
  },
  label: {
    display: 'block',
    fontSize: '0.75rem',
    fontWeight: 600,
    color: 'var(--text-muted)',
    textTransform: 'uppercase',
    letterSpacing: '0.5px',
    marginBottom: '0.4rem',
  },
  input: {
    width: '100%',
    background: 'var(--surface2)',
    border: '1px solid var(--border)',
    borderRadius: 10,
    padding: '0.75rem 1rem',
    color: 'var(--text)',
    fontSize: '0.9rem',
    outline: 'none',
    transition: 'border-color 0.2s',
    boxSizing: 'border-box',
  },
  errorBox: {
    color: 'var(--accent3, #c0392b)',
    fontSize: '0.82rem',
    marginBottom: '0.6rem',
    padding: '0.5rem 0.8rem',
    background: 'rgba(192,57,43,0.06)',
    borderRadius: 8,
  },
  submitBtn: {
    width: '100%',
    background: 'var(--accent)',
    color: '#fff',
    border: 'none',
    cursor: 'pointer',
    fontWeight: 700,
    fontSize: '1rem',
    padding: '0.85rem',
    borderRadius: 10,
    marginTop: '0.5rem',
    transition: 'all 0.2s',
  },
}
