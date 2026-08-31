# Quickstart Validation Guide: Upgrade Spring Boot Version

## Prerequisites
- Java 21+ / Java 26 (`JAVA_HOME` configured)
- Docker & Docker Compose

## Step 1: Clean Reactor Build & Test Execution
Execute the full Maven reactor build to compile all microservices and execute all unit, domain, service, and web test suites:
```bash
./mvnw clean test
```
*Expected Outcome*: `[INFO] BUILD SUCCESS` across all 10 reactor modules with 0 failures and 0 errors.

## Step 2: Start Infrastructure
```bash
docker compose -f infrastructure/docker-compose.yml up -d
```

## Step 3: Start Target Service
```bash
./mvnw spring-boot:run -pl services/reservation-service
```

## Step 4: Verify Actuator Probes & Swagger UI
- Health Check: `curl http://localhost:8085/actuator/health` (Expected: `{"status":"UP"}`)
- Swagger UI: Open `http://localhost:8085/swagger-ui.html`
