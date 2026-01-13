# 실행 및 배포 가이드

이 폴더에는 LAGO 프로젝트의 배포 및 실행에 필요한 문서들이 포함되어 있습니다.

## 📄 문서 목록

- **빌드_배포_문서 (1).md**: 프로젝트 빌드, 배포 및 환경 설정 가이드
- **외부_서비스_정보_문서 (1).md**: 외부 API 연동 방법 및 인증 설정
- **시연시나리오.pdf**: 프로젝트 주요 기능 시연 시나리오

## 🚀 빠른 시작

### 1. 환경 변수 설정

프로젝트 루트에 `.env` 파일 생성:

```bash
# PostgreSQL/TimescaleDB
POSTGRES_DB=stock_db
POSTGRES_USER=your_username
POSTGRES_PASSWORD=your_secure_password
POSTGRES_URL=jdbc:postgresql://localhost:5432/stock_db

# Redis
SPRING_REDIS_HOST=redis
SPRING_REDIS_PORT=6379
REDIS_STREAM_KEY=orders-stream

# OAuth2
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
KAKAO_CLIENT_ID=your_kakao_client_id
KAKAO_CLIENT_SECRET=your_kakao_client_secret

# KIS API
APP_KEY_A=your_kis_app_key
APP_SECRET_A=your_kis_app_secret

# Firebase
FIREBASE_KEY_PATH=/path/to/firebaseServiceAccountKey.json
```

### 2. Docker Compose 실행

```bash
# 모든 서비스 시작
docker-compose up -d

# 로그 확인
docker-compose logs -f backend
```

### 3. Spring Boot 직접 실행 (개발 모드)

```bash
cd BE

# 환경변수 로드 후 실행
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## 🗄️ 데이터베이스 초기화

### 자동 스키마 생성

JPA가 자동으로 테이블을 생성합니다 (`application.properties`에서 `spring.jpa.hibernate.ddl-auto=update` 설정).

### TimescaleDB Hypertable 생성

PostgreSQL에 연결하여 다음 SQL 실행:

```sql
-- 주가 데이터 하이퍼테이블 생성
SELECT create_hypertable('stock_minute_prices', 'timestamp');
SELECT create_hypertable('stock_day_prices', 'timestamp');
SELECT create_hypertable('stock_month_prices', 'timestamp');
SELECT create_hypertable('stock_year_prices', 'timestamp');

-- 자동 압축 정책 (선택사항)
ALTER TABLE stock_minute_prices SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'stock_code'
);

SELECT add_compression_policy('stock_minute_prices', INTERVAL '7 days');
```

### 초기 데이터 삽입 (선택사항)

```bash
cd BE

# Python 스크립트로 샘플 데이터 삽입
python bulk_insert_all.py
```

## 🔧 트러블슈팅

### 데이터베이스 연결 실패

```
Caused by: org.postgresql.util.PSQLException: Connection refused
```

**해결 방법:**
1. Docker Compose가 실행 중인지 확인: `docker-compose ps`
2. PostgreSQL 컨테이너 로그 확인: `docker-compose logs timescaledb`
3. 포트 충돌 확인: `netstat -ano | findstr :5432`

### KIS API 인증 실패

```
401 Unauthorized
```

**해결 방법:**
1. App Key와 App Secret 확인
2. KIS API 포털에서 IP 화이트리스트 등록
3. API 사용 한도 확인
4. 토큰 만료 시 재발급

### Redis 연결 실패

```
io.lettuce.core.RedisConnectionException
```

**해결 방법:**
1. Redis 컨테이너 상태 확인: `docker-compose ps redis`
2. Redis 포트 확인: `SPRING_REDIS_PORT=6379`
3. Redis 컨테이너 재시작: `docker-compose restart redis`

### FinBERT 서비스 타임아웃

```
java.net.SocketTimeoutException: Read timed out
```

**해결 방법:**
1. FinBERT 컨테이너 상태 확인: `docker-compose ps finbert-service`
2. 타임아웃 설정 증가: `finbert.client.timeout=60000`
3. 컨테이너 리소스 확인: `docker stats`

## 📞 추가 지원

자세한 내용은 다음 문서를 참조하세요:

- [빌드/배포 문서](빌드_배포_문서%20(1).md) - 전체 빌드 및 배포 가이드
- [외부 서비스 정보](외부_서비스_정보_문서%20(1).md) - OAuth2, KIS API, Firebase 설정
- [메인 README](../README.md) - 프로젝트 개요 및 아키텍처
