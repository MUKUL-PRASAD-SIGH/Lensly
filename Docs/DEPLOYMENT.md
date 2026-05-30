# Deployment — ValueLens

---

## Overview

ValueLens has two deployable components:
1. **Android app** — distributed via Google Play Store
2. **Backend service** — Go HTTP server hosted on Railway or Fly.io

---

## Android App Deployment

### Build Configuration

**Build variants:**

| Variant | Purpose | API endpoint | Logging |
|---------|---------|-------------|---------|
| `debug` | Local development | `localhost:8080` | Full |
| `staging` | Internal testing | `staging.api.valuelens.in` | Partial |
| `release` | Production | `api.valuelens.in` | Errors only |

**Signing:**

Release builds are signed with a keystore stored in a GitHub Actions secret. The keystore is never committed to the repository.

```
storeFile=valuelens-release.jks
storePassword=${{ secrets.KEYSTORE_PASSWORD }}
keyAlias=valuelens
keyPassword=${{ secrets.KEY_PASSWORD }}
```

**ProGuard / R8:**

Enabled for release builds. Rules are maintained in `proguard-rules.pro`. TensorFlow Lite and Room require specific keep rules — these are documented in the rules file.

---

### CI/CD Pipeline — Android

**Trigger: Pull Request**

```yaml
jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - ./gradlew ktlintCheck
      
  unit-test:
    runs-on: ubuntu-latest
    steps:
      - ./gradlew testDebugUnitTest
      
  build:
    runs-on: ubuntu-latest
    steps:
      - ./gradlew assembleDebug
```

**Trigger: Merge to `main`**

```yaml
jobs:
  instrumented-test:
    steps:
      - Upload APK to Firebase Test Lab
      - Run on Pixel 6 (API 33) and Samsung Galaxy A54 (API 33)
      - Assert crash-free rate > 99%
```

**Trigger: Git tag `v*`**

```yaml
jobs:
  release:
    steps:
      - ./gradlew bundleRelease
      - Sign AAB with keystore
      - fastlane supply --track internal --aab app-release.aab
```

---

### Play Store Track Strategy

| Track | Users | Purpose |
|-------|-------|---------|
| Internal | Dev team (up to 100) | Immediate post-merge testing |
| Closed Testing (Alpha) | 200 beta users | V1 beta program |
| Open Testing (Beta) | Public opt-in | Pre-launch validation |
| Production | All users | Staged rollout: 10% → 50% → 100% over 3 days |

**Staged rollout policy:** If crash rate exceeds 0.5% at any stage, halt rollout and investigate before proceeding.

---

### Play Store Listing Notes

**Sensitive permissions disclosure:**

Google requires apps using Accessibility Services to submit a **Permissions Declaration Form** explaining the use. The declaration must include:
- Specific features that require Accessibility
- Why no alternative implementation exists
- Confirmation that the service does not collect data for advertising

ValueLens's declaration: the Accessibility Service is used exclusively to read product information from explicitly listed shopping apps to provide real-time comparison recommendations.

---

## Backend Deployment

### Infrastructure

**Platform:** Railway (V1), Fly.io (V2+ for multi-region)

**Service:** Single Go binary, containerized with Docker

**Dockerfile:**

```dockerfile
FROM golang:1.22-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN go build -o valuelens-api ./cmd/api

FROM alpine:3.19
RUN apk --no-cache add ca-certificates
COPY --from=builder /app/valuelens-api /valuelens-api
EXPOSE 8080
CMD ["/valuelens-api"]
```

**Environment variables (Railway secrets):**

```
ANTHROPIC_API_KEY=sk-ant-...
JWT_SECRET=...
REDIS_URL=redis://...
DATABASE_URL=postgresql://...  (V2+)
PORT=8080
ENV=production
```

---

### CI/CD Pipeline — Backend

**Trigger: Merge to `main`**

```yaml
jobs:
  test:
    steps:
      - go test ./...
      
  deploy-staging:
    steps:
      - Docker build + push to Railway staging environment
      - Run smoke tests against staging API
      
  deploy-production:
    needs: [test, deploy-staging]
    steps:
      - Railway deploy to production
      - Health check: GET /health → 200
```

**Zero-downtime deployment:**

Railway handles rolling deployments. The Go service is stateless (session state in Redis), so old and new instances can run concurrently during deployment without request failures.

---

### Backend Endpoints

| Endpoint | Method | Auth | Purpose |
|----------|--------|------|---------|
| `/health` | GET | None | Load balancer health check |
| `/v1/auth/token` | POST | Device ID | Issue JWT for device |
| `/v1/reason` | POST | JWT | Forward structured query to Claude API |
| `/v1/ingredients` | GET | JWT | Ingredient safety database lookup (V2) |

---

### Monitoring

**Error tracking:** Sentry (Go SDK). Alerts on error rate > 1% over 5 minutes.

**Uptime:** Better Uptime pinging `/health` every 60 seconds. PagerDuty alert if down > 2 minutes.

**Latency:** Railway metrics dashboard. Alert if P95 response time > 1.5 seconds.

---

## Rollback Procedure

**Android:** Play Store supports immediate rollout halt and rollback to previous production build via the console. Keep the previous 2 release APKs tagged in GitHub.

**Backend:** Railway maintains deployment history. Rollback is one click in the Railway dashboard. The Go service is versioned — old and new API contracts are maintained for at least one app version cycle.
