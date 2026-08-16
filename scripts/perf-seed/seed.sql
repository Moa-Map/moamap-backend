-- 로컬 성능 측정용 대량 시드.
-- 데이터가 적으면 N+1도 인덱스 누락도 응답시간에 안 나타나기 때문에, before/after를 재려면 이게 선행돼야 한다.
-- scripts/demo-seed/ 와는 목적이 다르다(그쪽은 발표 화면용 소량·현실적 데이터).
--
-- 실행: docker compose exec -T postgres psql -U moamap -d moamap -f - < scripts/perf-seed/seed.sql
-- 재실행 안전: 대상 테이블을 TRUNCATE하고 다시 넣는다. 로컬 전용 — 운영 DB에 절대 돌리지 말 것.

\set users        5000
\set maps         7000
\set places       60000
\set reviews      300000
\set tags_per_map 3
\set members_per_map 6

BEGIN;

TRUNCATE place_service.place_review_images, place_service.place_reviews,
         place_service.place_photos, place_service.place_tag, place_service.places,
         map_service.map_tag, map_service.map_member, map_service.map_entity,
         user_service.users RESTART IDENTITY CASCADE;

-- 1) 유저
INSERT INTO user_service.users (nickname, provider, provider_id, role, created_at, updated_at, last_login_at)
SELECT 'perf' || i, 'perf', 'perf-' || i, 'USER',
       now() - (random() * 365 * interval '1 day'), now(),
       now() - (random() * 30 * interval '1 day')
FROM generate_series(1, :users) i;

-- 2) 지도. 5/7은 COMMUNITY(추천 후보), 나머지는 PRIVATE.
--    personal은 전부 false — uk_map_personal_owner(owner당 1개) 제약을 건드리지 않기 위함.
INSERT INTO map_service.map_entity
    (name, description, type, owner_id, member_count, place_count, personal, invite_code, created_at, updated_at)
SELECT '테스트 지도 ' || i,
       '성능 측정용 지도 ' || i,
       CASE WHEN i % 7 < 5 THEN 'COMMUNITY' ELSE 'PRIVATE' END,
       (i % :users) + 1,
       0, 0, false,
       CASE WHEN i % 7 < 5 THEN NULL ELSE substr(md5(i::text), 1, 12) END,
       now() - (random() * 400 * interval '1 day'), now()
FROM generate_series(1, :maps) i;

-- 3) 태그. 40개 어휘에서 지도당 :tags_per_map 개.
--    (i*3+k) 방식이라 한 지도 안에서 중복 태그가 안 나온다.
INSERT INTO map_service.map_tag (map_id, tag)
SELECT m.id, v.tag
FROM map_service.map_entity m
CROSS JOIN LATERAL generate_series(1, :tags_per_map) k
JOIN LATERAL (
    SELECT (ARRAY['맛집','카페','디저트','술집','브런치','한식','일식','중식','양식','분식',
                  '데이트','가족','친구','혼밥','회식','산책','드라이브','야경','전시','공연',
                  '반려동물','키즈','스터디','작업','조용한','시끌벅적','가성비','고급','뷰맛집','루프탑',
                  '강남','홍대','성수','연남','이태원','종로','부산','제주','경주','강릉'])[((m.id * 3 + k * 7) % 40) + 1] AS tag
) v ON true;

-- 4) 멤버십. 지도마다 OWNER 1명 + 일반 멤버 :members_per_map 명.
--    (m.id * 7 + k) 로 뽑아 한 지도 안에서 user_id가 겹치지 않는다(uk_map_member).
INSERT INTO map_service.map_member (map_id, user_id, role, created_at, updated_at)
SELECT m.id, m.owner_id, 'OWNER', m.created_at, m.created_at
FROM map_service.map_entity m;

INSERT INTO map_service.map_member (map_id, user_id, role, created_at, updated_at)
SELECT m.id, u.user_id,
       CASE WHEN k = 1 THEN 'ADMIN' ELSE 'MEMBER' END,
       m.created_at + (random() * 30 * interval '1 day'),
       now()
FROM map_service.map_entity m
CROSS JOIN LATERAL generate_series(1, :members_per_map) k
JOIN LATERAL (SELECT ((m.id * 7 + k * 13) % :users) + 1 AS user_id) u ON true
WHERE u.user_id <> m.owner_id
ON CONFLICT (map_id, user_id) DO NOTHING;

-- 5) 장소. 지도에 고르게 흩뿌린다. 대부분 APPROVED, 일부 PENDING/삭제.
INSERT INTO place_service.places
    (name, address, road_address, category, lat, lng, map_id, created_by, status, source_type,
     kakao_place_id, comment_count, created_at, updated_at, processed_at, processed_by, deleted_at)
SELECT '장소 ' || i,
       '서울시 테스트구 테스트동 ' || i,
       '서울시 테스트구 테스트로 ' || i,
       '음식점',
       37.4 + (random() * 0.2), 126.9 + (random() * 0.2),
       (i % :maps) + 1,
       (i % :users) + 1,
       CASE WHEN i % 20 = 0 THEN 'PENDING' ELSE 'APPROVED' END,
       'KAKAO_SEARCH',
       'kakao-' || i,
       0,
       now() - (random() * 300 * interval '1 day'), now(),
       CASE WHEN i % 20 = 0 THEN NULL ELSE now() END,
       CASE WHEN i % 20 = 0 THEN NULL ELSE 1 END,
       -- 소프트 삭제된 행이 섞여 있어야 삭제 필터 누락도 드러난다.
       CASE WHEN i % 50 = 0 THEN now() - (random() * 10 * interval '1 day') ELSE NULL END
FROM generate_series(1, :places) i;

-- 6) 리뷰. 장소당 평균 5개.
INSERT INTO place_service.place_reviews (place_id, user_id, rating, content, created_at, updated_at)
SELECT (i % :places) + 1,
       (i % :users) + 1,
       1 + (i % 5),
       '성능 테스트용 리뷰 본문 ' || i,
       now() - (random() * 200 * interval '1 day'), now()
FROM generate_series(1, :reviews) i;

-- 7) 비정규화 카운트 맞추기. 실제 API가 이 값으로 정렬·표시하기 때문에 현실과 어긋나면 측정이 왜곡된다.
UPDATE map_service.map_entity m
SET member_count = c.cnt
FROM (SELECT map_id, count(*) AS cnt FROM map_service.map_member GROUP BY map_id) c
WHERE m.id = c.map_id;

UPDATE map_service.map_entity m
SET place_count = c.cnt
FROM (SELECT map_id, count(*) AS cnt FROM place_service.places
      WHERE status = 'APPROVED' AND deleted_at IS NULL GROUP BY map_id) c
WHERE m.id = c.map_id;

UPDATE place_service.places p
SET comment_count = c.cnt, avg_rating = c.avg
FROM (SELECT place_id, count(*) AS cnt, round(avg(rating), 2) AS avg
      FROM place_service.place_reviews WHERE deleted_at IS NULL GROUP BY place_id) c
WHERE p.id = c.place_id;

COMMIT;

-- 통계가 낡으면 실행계획이 엉뚱하게 잡혀 측정이 흔들린다.
ANALYZE;

SELECT 'users' AS t, count(*) FROM user_service.users
UNION ALL SELECT 'maps', count(*) FROM map_service.map_entity
UNION ALL SELECT 'map_tag', count(*) FROM map_service.map_tag
UNION ALL SELECT 'map_member', count(*) FROM map_service.map_member
UNION ALL SELECT 'places', count(*) FROM place_service.places
UNION ALL SELECT 'reviews', count(*) FROM place_service.place_reviews;
