# Quickstart Validation Guide: Code Style Refactoring

**Branch**: `009-clean-code-style` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

This guide provides runnable instructions to verify that all inline FQCNs have been eliminated and stream API refactorings preserve 100% functionality without regressions.

---

## 1. Prerequisites

- **Java Environment**: JDK 21+ (or JDK 26 via SDKMAN)
  ```bash
  export JAVA_HOME=/Users/lazolazarev/.sdkman/candidates/java/current
  $JAVA_HOME/bin/java -version
  ```

---

## 2. Static Verification: Scan for Disallowed Inline FQCNs

Run ripgrep across Java files to verify that no inline package qualifications remain for targeted types in class bodies:

```bash
# 1. Verify JsonNode FQCN elimination in event listeners:
git grep -n "com.fasterxml.jackson.databind.JsonNode" -- "services/*/src/main/java/**/*.java"
# Expected: 0 matches outside of import statements

# 2. Verify List.of / Map.of FQCN elimination in security tests:
git grep -n "java.util.List.of" -- "services/*/src/test/java/**/*.java"
git grep -n "java.util.Map.of" -- "services/*/src/test/java/**/*.java"
# Expected: 0 matches
```

---

## 3. Automated Test Suite Verification

Verify that all unit, slice, integration, and security tests pass across the entire reactor:

```bash
export JAVA_HOME=/Users/lazolazarev/.sdkman/candidates/java/current
./mvnw clean test
```

**Expected Result**:
```text
[INFO] Reactor Summary:
[INFO]   springboot-rube-goldberg ........................... SUCCESS
[INFO]   event-contracts .................................... SUCCESS
[INFO]   gateway ............................................ SUCCESS
[INFO]   customer-service ................................... SUCCESS
[INFO]   restaurant-service ................................. SUCCESS
[INFO]   reservation-service ................................ SUCCESS
[INFO]   availability-service ............................... SUCCESS
[INFO]   waiting-list-service ............................... SUCCESS
[INFO]   notification-service ............................... SUCCESS
[INFO]   analytics-service .................................. SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
```

---

## 4. End-to-End Container Verification (Optional)

Rebuild and verify running Docker services:
```bash
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml up --build -d
```
Verify container health:
```bash
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.apps.yml ps
```
