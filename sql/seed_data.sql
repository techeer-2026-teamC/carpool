-- =============================================================================
-- 카풀 부하테스트용 시드 데이터 (100만 건)
-- 목적: EC2 리소스 제한 환경에서 실제 병목을 유발하기 위한 대량 데이터
-- 실행: docker exec -i carpool-db psql -U user -d carpool < sql/seed_data.sql
-- 주의: 앱 컨테이너가 먼저 실행되어 Hibernate DDL로 테이블이 생성된 후 실행할 것
-- =============================================================================

\echo '=== [1/5] 회원 10,000명 삽입 ==='

INSERT INTO members (email, password, nickname, deleted, created_at, updated_at)
SELECT
  'seed_' || i || '@test.com',
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
  'seeduser' || i,
  false,
  NOW() - (random() * INTERVAL '365 days'),
  NOW()
FROM generate_series(1, 10000) AS i
ON CONFLICT (email) DO NOTHING;

\echo '  완료: ' || (SELECT COUNT(*) FROM members WHERE email LIKE 'seed_%@test.com') || '명 삽입됨'

-- =============================================================================

\echo '=== [2/5] 태그 20개 삽입 ==='

INSERT INTO tags (name, created_at, updated_at) VALUES
  ('출퇴근',    NOW(), NOW()),
  ('학교',      NOW(), NOW()),
  ('공항',      NOW(), NOW()),
  ('쇼핑',      NOW(), NOW()),
  ('장거리',    NOW(), NOW()),
  ('여행',      NOW(), NOW()),
  ('반려동물',  NOW(), NOW()),
  ('여성전용',  NOW(), NOW()),
  ('비흡연',    NOW(), NOW()),
  ('조용히',    NOW(), NOW()),
  ('대화환영',  NOW(), NOW()),
  ('음악',      NOW(), NOW()),
  ('강남',      NOW(), NOW()),
  ('판교',      NOW(), NOW()),
  ('수원',      NOW(), NOW()),
  ('인천공항',  NOW(), NOW()),
  ('고속터미널',NOW(), NOW()),
  ('신촌',      NOW(), NOW()),
  ('홍대',      NOW(), NOW()),
  ('잠실',      NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

\echo '  완료: ' || (SELECT COUNT(*) FROM tags) || '개 태그 존재'

-- =============================================================================

\echo '=== [3/5] 게시글 100만 건 삽입 (시간 소요: 30~120초) ==='

-- 시드용 회원 ID 임시 테이블 (효율적인 JOIN을 위해)
CREATE TEMP TABLE _seed_member_ids AS
SELECT id, (row_number() OVER (ORDER BY id) - 1) AS rn
FROM members
WHERE email LIKE 'seed_%@test.com';

CREATE INDEX ON _seed_member_ids(rn);

DO $$
DECLARE member_cnt bigint;
BEGIN SELECT COUNT(*) INTO member_cnt FROM _seed_member_ids; END $$;

INSERT INTO posts (
  member_id,
  title,
  departure_location,  departure_lat,  departure_lng,
  destination_location, destination_lat, destination_lng,
  departure_time,
  max_passengers, current_passengers,
  status,
  auto_accept,
  price,
  version,
  deleted,
  created_at, updated_at
)
SELECT
  m.id,
  '카풀 모집 ' || i,
  (ARRAY[
    '강남역', '홍대입구역', '신촌역', '판교역', '수원역',
    '잠실역', '건대입구역', '서울역', '종각역', '광화문역'
  ])[(i % 10) + 1],
  37.45 + (random() * 0.15),
  126.95 + (random() * 0.15),
  (ARRAY[
    '판교역', '강남역', '수원역', '신촌역', '홍대입구역',
    '서울역', '잠실역', '건대입구역', '광화문역', '종각역'
  ])[((i + 5) % 10) + 1],
  37.35 + (random() * 0.15),
  126.85 + (random() * 0.15),
  NOW() + ((1 + (i % 30)) || ' days')::INTERVAL,
  (i % 4) + 1,
  0,
  CASE (i % 5)
    WHEN 0 THEN 'CLOSED'
    WHEN 1 THEN 'CANCELLED'
    ELSE 'OPEN'
  END,
  (i % 3 = 0),
  3000 + (i % 8) * 1000,
  0,
  false,
  NOW() - ((i % 365) || ' days')::INTERVAL,
  NOW()
FROM generate_series(1, 1000000) AS i
JOIN _seed_member_ids m ON m.rn = (i % (SELECT COUNT(*) FROM _seed_member_ids));

\echo '  완료: ' || (SELECT COUNT(*) FROM posts WHERE title LIKE '카풀 모집 %') || '건 삽입됨'

-- =============================================================================

\echo '=== [4/5] post_tags 삽입 (게시글당 태그 1개, ~100만 건) ==='

CREATE TEMP TABLE _seed_tag_ids AS
SELECT id, (row_number() OVER (ORDER BY id) - 1) AS rn FROM tags;

CREATE INDEX ON _seed_tag_ids(rn);

-- 시드 게시글에만 태그 연결 (기존 게시글 제외)
INSERT INTO post_tags (post_id, tag_id)
SELECT
  p.id,
  t.id
FROM (
  SELECT id, (row_number() OVER (ORDER BY id) - 1) AS rn
  FROM posts
  WHERE title LIKE '카풀 모집 %'
) p
JOIN _seed_tag_ids t ON t.rn = (p.rn % (SELECT COUNT(*) FROM _seed_tag_ids))
ON CONFLICT DO NOTHING;

\echo '  완료: post_tags 삽입됨'

-- =============================================================================

\echo '=== [5/5] 통계 업데이트 (쿼리 플래너 최적화) ==='

ANALYZE members;
ANALYZE posts;
ANALYZE tags;
ANALYZE post_tags;

\echo ''
\echo '=== 시드 데이터 삽입 완료 ==='
SELECT
  'members' AS table_name, COUNT(*) AS row_count FROM members
UNION ALL
SELECT 'posts',     COUNT(*) FROM posts
UNION ALL
SELECT 'tags',      COUNT(*) FROM tags
UNION ALL
SELECT 'post_tags', COUNT(*) FROM post_tags;
