# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

LAGO (라고할때) is a stock investment simulation platform developed by team D203 for SSAFY. The system consists of:

- **Spring Boot Backend** (Java 21) - Main API server with trading simulation, AI analysis, and chart patterns
- **Chart Analysis Service** (Python) - Microservice for technical chart pattern detection
- **PostgreSQL/TimescaleDB** - Time-series database for stock data
- **Redis** - Caching and real-time data streaming
- **Android App** - Mobile client (separate repository)

## Architecture

### Multi-service Architecture
- **BE/**: Spring Boot application (port 8081)
- **chart_analysis/**: Python FastAPI service (port 5000) for chart pattern detection
- **Docker Compose**: Orchestrates TimescaleDB, Redis, backend, and chart analysis services
- **Jenkins**: CI/CD pipeline with health checks and Mattermost notifications

### Key Backend Modules
- **AI Services**: Sentiment analysis (FinBERT), trading strategies, bot management
- **Real-time Processing**: WebSocket for live data, Redis streams for order processing
- **Trading System**: Mock trading, portfolio management, account handling
- **Chart Analysis**: Integration with Python service for pattern detection
- **Study System**: Educational features, quizzes, chart learning

### Database Design
- Uses existing EC2 database with strict naming conventions
- **TimescaleDB** for time-series stock data (minute, day, month, year intervals)
- **PostgreSQL** for relational data (users, accounts, trades, AI strategies)
- Database schema must match exactly - no arbitrary changes to table/column names

## Development Commands

### Backend Development
```bash
# Navigate to backend directory
cd BE

# Run locally with environment variables from .env
./gradlew bootRun

# Build for production
./gradlew clean build

# Run tests
./gradlew test

# Build Docker image
docker build -t lago-backend .
```

### Full Stack Development
```bash
# Start all services (TimescaleDB, Redis, Backend, Chart Analysis)
docker-compose up -d

# View logs
docker-compose logs backend
docker-compose logs redis
docker-compose logs timescaledb

# Rebuild specific service
docker-compose build --no-cache backend

# Stop all services
docker-compose down
```

### Chart Analysis Service
```bash
cd chart_analysis

# Install dependencies
pip install -r requirements.txt
# OR using Poetry
poetry install

# Run Python service
python -m chart_patterns.pattern_detect
```

### Testing
```bash
# Backend unit tests
cd BE && ./gradlew test

# Health checks
curl http://localhost:8081/actuator/health
curl http://localhost:5000/health
```

## Code Conventions

### Backend Code Organization
Follow the established Spring Boot structure:
- `controller/` - REST API endpoints
- `service/` - Business logic
- `repository/` - JPA data access
- `domain/` - JPA entities (match DB exactly)
- `dto/request/` - Request DTOs only
- `dto/response/` - Response DTOs only
- `dto/` - Internal/shared DTOs
- `ai/` - AI-related services (sentiment, strategy)
- `realtime/` - WebSocket and Redis streaming

### Naming Conventions
- Request DTOs: `TradeRequest.java` (in dto/request/)
- Response DTOs: `TradeResponse.java` (in dto/response/)
- Entities: Match database table names exactly
- Controllers: Always return DTOs, never entities directly

### Database Integration
- **Critical**: All table names, column names, and relationships must match the connected EC2 database exactly
- Use the VSCode extension to verify table structures before making changes
- No arbitrary renaming or schema modifications
- Entity field names must correspond to actual database columns

## Environment Configuration

### Profiles
- **dev**: Local development (`application-dev.properties`) - connects to EC2 database
- **prod**: Production deployment (`application-prod.properties`) - Docker environment

### Environment Variables
Required in `.env` file:
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `SPRING_REDIS_PORT`, `REDIS_STREAM_KEY`

### Service Integration
- Swagger UI: `http://localhost:8081/swagger-ui.html`
- Chart Analysis: `http://localhost:5000/detect-patterns`
- Health Checks: `/actuator/health`

## AI Services Integration

### FinBERT Sentiment Analysis
- Endpoint: `/analyze` (configured via `finbert.server.analyze-endpoint`)
- Health check: `/health`
- Used for news sentiment and trading decision support

### Trading Strategy AI
- AI bot management with customizable strategies
- Character-based trading recommendations
- Technical analysis integration with chart patterns

## Development Notes

### Virtual Threads (Java 21)
The project leverages Java 21's virtual threads for better concurrency performance in trading operations and real-time data processing.

### Real-time Features
- WebSocket endpoints for live chart updates
- Redis streams for order processing
- Minute-level candlestick data updates

### Chart Pattern Detection
Integration with Python service supports:
- Double tops/bottoms
- Head and shoulders patterns
- Triangle patterns (ascending, descending, symmetrical)
- Flag and pennant patterns

### CI/CD Pipeline
Jenkins pipeline includes:
- Gradle build and test
- Docker image creation
- Health checks with retry logic
- Mattermost notifications for deployment status

## API Endpoints Structure

Main API groups:
- `/api/stocks` - Stock data and trading
- `/api/accounts` - Account and portfolio management
- `/api/ai-bots` - AI trading bot management
- `/api/study` - Educational features and chart learning
- `/api/news` - News and market updates
- `/api/users` - User profiles and preferences

All endpoints must match the established API specification exactly.