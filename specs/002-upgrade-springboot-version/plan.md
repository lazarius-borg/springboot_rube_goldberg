# Implementation Plan: Upgrade Spring Boot Version

**Branch**: `002-upgrade-springboot-version` | **Date**: 2026-08-31 | **Spec**: [spec.md](./spec.md)

## Summary
Upgrade Spring Boot framework version to 4.1.1 across the Maven reactor parent POM and all 8 microservices and gateway. Refactor code and test suites to use modern Spring Boot conventions, including `@MockitoBean`, configuration property alignment, and ecosystem BOM coordination.

## Technical Context
- **Parent POM**: `pom.xml` (`groupId: nl.invokedynamic.demo`, `artifactId: springboot.rubegoldberg`)
- **Modules**: `gateway`, `services/*`, `common/event-contracts`
- **Compiler / JDK**: Java 26 / Virtual Threads
- **Ecosystem**: Spring Cloud, Springdoc OpenAPI, Kafka, Redis, PostgreSQL, Mailpit

## Constitution Check
- **Principle I (Specification Adherence)**: Conforms strictly to user requirements.
- **Principle II (Maven Reactor)**: Preserves reactor hierarchy and modularity.
- **Principle III (Modern Spring Boot Showcase)**: Demonstrates modern Spring Boot recommended patterns.
- **Principle V (Testing Quality Gates)**: 100% test pass rate across all reactor submodules.

## Planned Phases & Work Breakdown

### Phase 1: Dependency & Parent POM Modernization
- Update `pom.xml` properties: Spring Boot version to `4.1.1`, Spring Cloud, and companion BOMs.
- Verify compiler plugins and surefire configuration.

### Phase 2: Configuration & Code Refactoring
- Review all `application.yml` files for deprecated property keys.
- Update security, web, and messaging configurations to modern idioms.

### Phase 3: Test Suite Refactoring & Modernization
- Replace any deprecated `@MockBean` with `@MockitoBean` or standalone runners.
- Verify all unit, domain, service, and web test suites.

### Phase 4: Full Reactor Verification
- Execute `./mvnw clean test` and verify clean build across all 10 modules.
