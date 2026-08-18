// DB 태그 24개에 대한 이모지 + 스타일 매핑
export const TAG_STYLE_MAP = {
  // 행동 규칙
  '금연':          { emoji: '🚭', bg: '#fde8e8', tc: '#b94040' },
  '비흡연':        { emoji: '🙅', bg: '#fde8e8', tc: '#c04040' },
  '흡연':          { emoji: '🚬', bg: '#f0ece0', tc: '#6b5a2a' },
  // 분위기
  '조용한 분위기': { emoji: '🤫', bg: '#e8eef8', tc: '#3a5080' },
  '조용히':        { emoji: '🔇', bg: '#eef0f8', tc: '#4050a0' },
  '대화환영':      { emoji: '💬', bg: '#e8f8ec', tc: '#2a6040' },
  '음악':          { emoji: '🎵', bg: '#f4e8f8', tc: '#7040a0' },
  // 동승자 조건
  '여성전용':      { emoji: '👩', bg: '#fde8f4', tc: '#904070' },
  '반려동물':      { emoji: '🐾', bg: '#e8f8ec', tc: '#2a7040' },
  '짐 있음':       { emoji: '🎒', bg: '#fff4e0', tc: '#8a5c10' },
  // 목적/유형
  '출퇴근':        { emoji: '🏢', bg: '#e8f0ff', tc: '#2040a0' },
  '학교':          { emoji: '🎓', bg: '#fff8e0', tc: '#806000' },
  '장거리':        { emoji: '🛣️', bg: '#f0ffe8', tc: '#2a6020' },
  '여행':          { emoji: '✈️', bg: '#e8f8ff', tc: '#2060a0' },
  // 목적지
  '강남':          { emoji: '🏙️', bg: '#f0f0ff', tc: '#404090' },
  '판교':          { emoji: '💻', bg: '#e8fff0', tc: '#206040' },
  '수원':          { emoji: '🏰', bg: '#fff0e8', tc: '#804020' },
  '인천공항':      { emoji: '🛫', bg: '#e8f4ff', tc: '#2050a0' },
  '고속터미널':    { emoji: '🚌', bg: '#fff0f8', tc: '#903060' },
  '신촌':          { emoji: '🎭', bg: '#f8f0ff', tc: '#702090' },
  '홍대':          { emoji: '🎨', bg: '#fff0f0', tc: '#a02040' },
  '잠실':          { emoji: '⚾', bg: '#f0fff0', tc: '#207020' },
  '공항':          { emoji: '🛬', bg: '#e8f8ff', tc: '#1060a0' },
  '쇼핑':          { emoji: '🛍️', bg: '#fff4f8', tc: '#a03060' },
}

export const DEFAULT_TAG_STYLE = { emoji: '🏷️', bg: '#f0f0f0', tc: '#606060' }

export function getTagStyle(name) {
  return TAG_STYLE_MAP[name] || DEFAULT_TAG_STYLE
}
