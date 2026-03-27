# Glowzi Identity Service — Pipeline & Operations Guide

## Architecture Overview

```
┌─────────────┐     ┌─────────────┐     ┌──────────────┐     ┌────────────┐
│  Developer   │────▶│  GitHub PR   │────▶│   CI Pipeline │────▶│  CD Pipeline│
│  Push/PR     │     │  Created     │     │  (identity-ci)│     │ (identity-cd)│
└─────────────┘     └─────────────┘     └──────┬───────┘     └─────┬──────┘
                                                │                    │
                                    ┌───────────┼──────────┐        │
                                    ▼           ▼          ▼        ▼
                               Compile    Unit Tests  Integration  Build Image
                                          Arch Tests  Tests (TC)   Push GHCR
                                                                   Deploy Staging
                                                                   Deploy Prod ←── Manual Gate
```

## Quick Start

### Local Development
```bash
# 1. Start databases
cd infra && docker compose up -d

# 2. Run identity service
cd glowzi-identity-service && ./mvnw spring-boot:run

# 3. Run tests
./mvnw test                                        # all tests
./mvnw test -Dtest="com.glowzi.identity.unit.**"   # unit only (no Docker needed)
```

### Docker Build & Run
```bash
# Build image
cd glowzi-identity-service
docker build -t glowzi-identity-service .

# Run full stack
cd infra && docker compose --profile app up -d
```

---

## Pipeline Details

### CI Pipeline (`identity-ci.yml`)
Triggers on: **push to main/develop**, **PRs to main**

| Stage | What it does | Parallelism |
|-------|-------------|-------------|
| **Compile** | `mvnw compile` + validate | First |
| **Unit Tests** | Domain + application + architecture tests | Parallel with integration |
| **Integration Tests** | Testcontainers PostgreSQL + HTTP tests | Parallel with unit |
| **Build JAR** | `mvnw package -DskipTests` | After all tests pass |
| **Docker Build** | Verify Dockerfile builds (no push) | After JAR |

### CD Pipeline (`identity-cd.yml`)
Triggers on: **push to main**, **manual dispatch**

| Stage | What it does | Gate |
|-------|-------------|------|
| **Test** | Full `mvnw verify` | Automatic |
| **Build & Push** | Multi-arch image → GHCR | After tests |
| **Staging Deploy** | Auto-deploy to staging | After push |
| **Production Deploy** | Deploy to production | Manual approval required |

---

## Setup Instructions

### 1. Move Workflow Files
```bash
# From repo root:
mkdir -p .github/workflows
cp infra/identity-ci.yml .github/workflows/
cp infra/identity-cd.yml .github/workflows/
```

### 2. GitHub Repository Settings

#### Secrets (Settings → Secrets and variables → Actions)
| Secret | Purpose | Example |
|--------|---------|---------|
| `JWT_SECRET` | JWT signing key (production) | 64+ char random string |
| `STAGING_HOST` | Staging server SSH | `user@staging.glowzi.com` |
| `SLACK_WEBHOOK` | Deploy notifications | `https://hooks.slack.com/...` |

> `GITHUB_TOKEN` is automatic — used for GHCR push.

#### Environments (Settings → Environments)
1. **staging** — No protection rules (auto-deploy)
2. **production** — Add required reviewers + wait timer

#### Branch Protection (Settings → Branches → main)
- ✅ Require pull request before merging
- ✅ Require status checks: `Compile & Validate`, `Unit & Architecture Tests`, `Integration Tests`
- ✅ Require branches to be up to date

---

## Docker Image Details

### Multi-stage Build
```
Stage 1 (builder):  eclipse-temurin:21-jdk-alpine  ~340MB
Stage 2 (runtime):  eclipse-temurin:21-jre-alpine   ~85MB final image
```

### Security
- ✅ Non-root user (`appuser`)
- ✅ Alpine-based minimal image
- ✅ No build tools in runtime image
- ✅ Secrets via environment variables (never baked in)
- ✅ Health check built into image

### Image Tags (GHCR)
```
ghcr.io/<owner>/glowzi-identity-service:latest          # latest main
ghcr.io/<owner>/glowzi-identity-service:main             # branch tag
ghcr.io/<owner>/glowzi-identity-service:<sha>            # commit SHA
ghcr.io/<owner>/glowzi-identity-service:20260325-080000  # timestamp
```

---

## Monitoring & Observability

### Endpoints (enabled)
| Endpoint | URL | Purpose |
|----------|-----|---------|
| Health | `/actuator/health` | Liveness + readiness probes |
| Prometheus | `/actuator/prometheus` | Metrics scraping |
| Info | `/actuator/info` | Build info |
| Metrics | `/actuator/metrics` | Micrometer metrics |

### Key Metrics Available
- `http_server_requests_seconds` — request latency per endpoint
- `jvm_memory_used_bytes` — JVM heap/non-heap usage
- `hikaricp_connections_active` — DB connection pool
- `process_cpu_usage` — CPU utilization
- `logback_events_total` — error/warn log counts

### Structured Logging (Docker profile)
JSON format for log aggregation (ELK, CloudWatch, Loki):
```json
{"time":"2026-03-25T08:00:00","level":"INFO","logger":"AuthController","msg":"User registered","trace":"abc123"}
```

### Recommended Monitoring Stack
```
┌──────────────┐    ┌────────────┐    ┌──────────┐
│  Prometheus   │───▶│  Grafana    │───▶│  Alerts   │
│  (scrape)     │    │  (dashboards)│   │  (Slack)  │
└──────┬───────┘    └────────────┘    └──────────┘
       │
       ▼
  /actuator/prometheus
```

### Alert Recommendations
| Alert | Condition | Severity |
|-------|-----------|----------|
| Service Down | `up == 0` for 1 min | 🔴 Critical |
| High Error Rate | `rate(http_server_requests{status=~"5.."}[5m]) > 0.05` | 🔴 Critical |
| High Latency | `http_server_requests_seconds{quantile="0.95"} > 2` | 🟡 Warning |
| DB Pool Exhaustion | `hikaricp_connections_active / hikaricp_connections_max > 0.8` | 🟡 Warning |
| High Memory | `jvm_memory_used_bytes / jvm_memory_max_bytes > 0.85` | 🟡 Warning |
| Auth Failures Spike | `rate(http_server_requests{uri="/auth/login",status="401"}[5m]) > 10` | 🟡 Warning |

---

## Rollback Strategy

### Quick Rollback (Docker tag pinning)
```bash
# Roll back to previous image by SHA
docker compose pull   # pulls the specific tag
docker compose up -d

# Or in CD: re-run workflow with a previous commit SHA
```

### Database Rollback
Flyway migrations are forward-only. For rollbacks:
1. Deploy a new migration that undoes the change
2. Never delete/edit existing migration files

---

## Version Control Strategy

### Branch Model
```
main ──────────────────────────────────── production-ready
  ├── develop ─────────────────────────── integration branch
  │     ├── feature/user-profile ──────── feature branches
  │     ├── feature/oauth-google
  │     └── fix/phone-validation
  └── hotfix/critical-bug ─────────────── emergency fixes
```

### Commit Convention
```
feat(identity): add OAuth2 login
fix(identity): handle null phone gracefully
test(identity): add JWT expiration edge cases
ci(identity): add code coverage reporting
```
