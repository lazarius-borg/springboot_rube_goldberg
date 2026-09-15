# Quickstart & Validation Guide: Refactor Configuration

**Feature**: `003-refactor-configuration` | **Date**: 2026-09-11

This guide outlines runnable verification steps to validate that all bean declarations have been extracted into dedicated `@Configuration(proxyBeanMethods = false)` classes without regressions.

---

## 1. Prerequisites

- **Java Version**: Java 26 preview (`JAVA_HOME=/Users/lazolazarev/.sdkman/candidates/java/current`)
- **Maven Wrapper**: `./mvnw` at project root

---

## 2. Validation Scenarios

### Scenario A: Verify Zero `@Bean` Annotations in Application Classes

Run a grep check to verify that no `*Application.java` class declares `@Bean` methods:

```bash
grep -rn "@Bean" */src/main/java/**/*Application.java services/*/src/main/java/**/*Application.java
```
**Expected Outcome**: Zero matches found.

---

### Scenario B: Verify Dedicated Configuration Classes

Confirm the presence of all dedicated `@Configuration` classes in their respective `config` packages:

```bash
# Domain Microservices Jackson Configurations
ls -la services/*/src/main/java/nl/invokedynamic/demo/*/config/JacksonConfig.java

# Availability Service Cache Configuration
ls -la services/availability-service/src/main/java/nl/invokedynamic/demo/availability/config/CacheConfig.java

# Gateway Security Configuration
ls -la gateway/src/main/java/nl/invokedynamic/demo/gateway/config/SecurityConfig.java
```
**Expected Outcome**: All 9 configuration classes exist and are annotated with `@Configuration(proxyBeanMethods = false)`.

---

### Scenario C: Execute Full Reactor Test Suite

Run the full automated test suite to ensure all bean injection points (publishers, listeners, services, controllers) resolve properly:

```bash
./mvnw clean test
```
**Expected Outcome**: `BUILD SUCCESS` across all 10 reactor modules with zero test failures or errors.

---

### Scenario D: Package Executable JARs

Build executable fat JARs with repackaged dependencies:

```bash
./mvnw package -DskipTests
```
**Expected Outcome**: `BUILD SUCCESS` with updated JARs created in each submodule's `target/` directory.
