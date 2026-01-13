# 🚀 LAGO 프로젝트 EC2 배포 가이드

## 배포 방식 개요

이 프로젝트는 **환경변수 오버라이드 방식**을 사용하여 로컬 개발 환경과 EC2 배포 환경을 분리합니다.

- **로컬 개발**: Docker Compose로 전체 스택 실행 (DB, Redis, Backend 모두 컨테이너)
- **EC2 배포**: 외부 DB/Redis 연결하여 Backend만 컨테이너로 실행

## 🏗️ 배포 아키텍처

```
EC2 인스턴스
├── 외부 PostgreSQL/TimescaleDB (실제 DB 서버)
├── 외부 Redis (실제 Redis 서버)  
└── Docker Container
    ├── spring-backend (포트 8081)
    └── chart-analysis (포트 5000)
```

## 📋 배포 전 준비사항

### 1. EC2 서버에서 필요한 것들
- Docker & Docker Compose 설치
- Git 설치
- 외부 PostgreSQL/TimescaleDB 연결 정보
- 외부 Redis 연결 정보

### 2. 데이터베이스 준비
- PostgreSQL/TimescaleDB 서버 구축 (별도 인스턴스 또는 RDS)
- `stock_db` 데이터베이스 생성
- 필요한 테이블 스키마 생성

### 3. Redis 서버 준비
- Redis 서버 구축 (별도 인스턴스 또는 ElastiCache)

## 🔧 EC2 배포 단계별 가이드

### 1단계: 프로젝트 클론

```bash
# EC2에서 프로젝트 클론
git clone <프로젝트-리포지토리-URL>
cd S13P11D203

# 배포용 브랜치로 체크아웃 (필요시)
git checkout config/ec2-deployment-settings
```

### 2단계: 환경변수 설정

```bash
# 템플릿 파일을 복사하여 실제 환경변수 파일 생성
cp .env.example .env

# 실제 환경 정보로 수정
vim .env
```

**중요: .env 파일에서 아래 값들을 실제 환경에 맞게 변경하세요:**

```bash
# Spring 프로파일을 운영용으로 변경
SPRING_PROFILES_ACTIVE=prod

# 실제 PostgreSQL 서버 연결 정보
SPRING_DATASOURCE_URL=jdbc:postgresql://실제DB서버주소:5432/stock_db
SPRING_DATASOURCE_USERNAME=실제사용자명
SPRING_DATASOURCE_PASSWORD=실제패스워드

# 실제 Redis 서버 연결 정보
SPRING_REDIS_HOST=실제Redis서버주소
SPRING_REDIS_PORT=6379

# AI 서비스가 외부에 있는 경우 (선택사항)
FINBERT_SERVER_HOST=http://실제AI서비스주소:5000
SERVICES_CHART_ANALYSIS_URL=http://실제AI서비스주소:5000/detect-patterns
```

### 3단계: Docker Compose 실행

```bash
# 배포용으로 실행 (외부 DB/Redis 사용)
docker-compose up -d backend chart-analysis

# 로그 확인
docker-compose logs -f backend

# 서비스 상태 확인
docker-compose ps
```

### 4단계: 서비스 확인

```bash
# Health Check
curl http://localhost:8081/actuator/health

# Swagger UI 접근
# http://EC2퍼블릭IP:8081/swagger-ui.html
```

## 🔄 업데이트 및 재배포

```bash
# 코드 업데이트
git pull origin main  # 또는 해당 브랜치

# 컨테이너 재빌드 및 재시작
docker-compose down
docker-compose build --no-cache backend
docker-compose up -d backend chart-analysis

# 로그 확인
docker-compose logs -f backend
```

## ⚠️ 환경별 차이점 정리

| 구분 | 로컬 개발 | EC2 배포 |
|------|-----------|----------|
| **데이터베이스** | Docker 컨테이너 (timescaledb) | 외부 서버 연결 |
| **Redis** | Docker 컨테이너 (redis) | 외부 서버 연결 |
| **Spring 프로파일** | `dev` | `prod` |
| **로그 레벨** | DEBUG (상세) | INFO (필수만) |
| **JPA DDL** | create-drop | validate |

## 🐛 트러블슈팅

### 데이터베이스 연결 실패
```bash
# 연결 정보 확인
docker-compose logs backend | grep -i "connection"

# 네트워크 테스트
docker-compose exec backend ping 데이터베이스주소
```

### Redis 연결 실패
```bash
# Redis 연결 테스트
docker-compose exec backend telnet Redis주소 6379
```

### 포트 충돌
```bash
# 포트 사용 현황 확인
netstat -tlnp | grep :8081

# 기존 프로세스 종료
sudo kill -9 $(sudo lsof -t -i:8081)
```

## 📁 파일 구조 (배포 관련)

```
프로젝트/
├── docker-compose.yml        # 환경변수 오버라이드 설정
├── .env.example              # 환경변수 템플릿
├── .env                      # 실제 환경변수 (git에서 제외)
├── .gitignore                # .env 파일 제외 설정
├── Jenkinsfile               # CI/CD 파이프라인
└── DEPLOYMENT.md             # 이 가이드 문서
```

## 🚨 보안 주의사항

1. **절대 .env 파일을 Git에 커밋하지 마세요**
2. **데이터베이스 패스워드는 강력한 것으로 설정**
3. **EC2 보안그룹에서 필요한 포트만 열기**
4. **SSL/TLS 설정 고려 (운영환경)**

## 🔗 관련 문서

- [CLAUDE.md](./CLAUDE.md) - 개발 환경 설정
- [BackEnd_Convention.md](./BackEnd_Convention.md) - 백엔드 코딩 컨벤션
- [Jenkinsfile](./Jenkinsfile) - CI/CD 파이프라인 설정