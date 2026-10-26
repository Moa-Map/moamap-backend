-- 서비스별 스키마 분리. moamap DB 최초 기동 시 1회만 실행된다.
CREATE SCHEMA IF NOT EXISTS map_service          AUTHORIZATION moamap;
CREATE SCHEMA IF NOT EXISTS user_service         AUTHORIZATION moamap;
CREATE SCHEMA IF NOT EXISTS place_service        AUTHORIZATION moamap;
CREATE SCHEMA IF NOT EXISTS notification_service AUTHORIZATION moamap;