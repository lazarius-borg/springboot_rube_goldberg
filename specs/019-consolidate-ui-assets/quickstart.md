# Quickstart: Verification Guide for Consolidated UI Assets

**Feature**: `019-consolidate-ui-assets`  
**Date**: 2026-09-18  

This guide provides step-by-step verification commands to validate the consolidation of frontend UI assets into `gateway/src/main/resources/static/ui/` and the safe removal of `ui/`.

---

## 1. Verify Parity Prior to Removal

Confirm that `gateway/src/main/resources/static/ui` contains exact, byte-for-byte identical copies of all files in `ui/src`:

```bash
# Verify 0 diffs across all frontend assets:
diff -r ui/src gateway/src/main/resources/static/ui
```
*Expected Output*: No output (exit code `0`).

---

## 2. Directory Removal

Remove the redundant top-level `ui/` directory:

```bash
git rm -rf ui
```

Verify that `ui/` is deleted and untracked:

```bash
git status
test ! -d ui && echo "ui/ successfully removed"
```

---

## 3. Maven Reactor Build & Test Verification

Verify that the multi-module Maven build and all unit tests succeed without references to `ui/`:

```bash
JAVA_HOME=/Users/lazolazarev/.sdkman/candidates/java/current ./mvnw clean test
```
*Expected Outcome*: `BUILD SUCCESS` with 100% test pass rate across all modules.

---

## 4. Jib Container Image Build Verification

Verify that the `gateway` container image packages successfully with the consolidated static assets:

```bash
JAVA_HOME=/Users/lazolazarev/.sdkman/candidates/java/current ./mvnw package jib:dockerBuild -pl gateway -DskipTests
```
*Expected Outcome*: `BUILD SUCCESS`, image `springboot-rube-goldberg/gateway:latest` loaded to Docker daemon.

Verify static assets exist inside the container:

```bash
docker run --rm --entrypoint sh springboot-rube-goldberg/gateway:latest \
  -c "ls -la /app/resources/static/ui/customer/index.html /app/resources/static/ui/manager/index.html"
```
*Expected Outcome*: Both files listed inside `/app/resources/static/ui/`.

---

## 5. Runtime Static Asset Delivery Verification

Start the API Gateway container (or local stack) and query the static asset endpoints:

```bash
# 1. Customer Portal HTML
curl -f -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/ui/customer/index.html
# Expected: 200

# 2. Customer App Script
curl -f -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/ui/customer/app.js
# Expected: 200

# 3. Manager Portal HTML
curl -f -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/ui/manager/index.html
# Expected: 200

# 4. Manager App Script
curl -f -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/ui/manager/app.js
# Expected: 200
```

---

## 6. Documentation Reference Check

Verify that `README.md` contains zero references to `ui/src/` and accurately points to the gateway UI locations:

```bash
grep -n "ui/src" README.md || echo "Zero references found in README.md"
```
