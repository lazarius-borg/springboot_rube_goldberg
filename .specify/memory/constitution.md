<!--
Sync Impact Report:
- Version change: Uninitialized template → 1.0.0
- List of modified principles:
  - [PRINCIPLE_1_NAME] → I. Strict Specification Adherence (NON-NEGOTIABLE)
  - [PRINCIPLE_2_NAME] → II. Maven Reactor & Microservices Architecture
  - [PRINCIPLE_3_NAME] → III. Modern Spring Boot Feature Showcase
  - [PRINCIPLE_4_NAME] → IV. Agentic AI-Aided Development & Traceability
  - [PRINCIPLE_5_NAME] → V. Comprehensive Testing & Quality Gates (NON-NEGOTIABLE)
- Added sections:
  - Architecture & Technical Constraints
  - Development & Quality Workflow
  - Governance
- Removed sections: None
- Follow-up TODOs: None
-->

# SpringBoot Rube Goldberg Constitution

## Core Principles

### I. Strict Specification Adherence (NON-NEGOTIABLE)
All implementation details, microservice boundaries, interface contracts, and architectural decisions MUST strictly conform to the approved project specification. No arbitrary assumptions, undocumented shortcuts, or deviations from the spec are permitted.

### II. Maven Reactor & Microservices Architecture
The application MUST be organized as a Maven reactor (multi-module) project with `groupId: nl.invokedynamic.demo` and parent `artifactId: springboot.rubegoldberg`. Each microservice MUST be encapsulated in its own Maven submodule with explicit dependencies, independent runnable lifecycle, and clear boundary interfaces.

### III. Modern Spring Boot Feature Showcase
Each service and component MUST demonstrate idiomatic, modern Spring Boot conventions and capabilities (including Auto-Configuration, Spring Boot Actuator, Health Checks, Structured Metrics, Spring Data, and declarative clients). Best practices regarding dependency injection, configuration properties, and profiles MUST be upheld across all modules.

### IV. Agentic AI-Aided Development & Traceability
The repository structure, code conventions, documentation, and build automation MUST be optimized for transparent, deterministic agentic AI pair programming. Every module, service contract, and workflow MUST maintain clear documentation, predictable build commands, and traceable change management.

### V. Comprehensive Testing & Quality Gates (NON-NEGOTIABLE)
Quality verification is non-negotiable. Every microservice MUST include automated unit tests, Spring slice tests (e.g., `@WebMvcTest`, `@DataJpaTest`), and integration tests validating inter-service communication. The entire reactor build MUST pass cleanly via `mvn clean verify` without warnings or skipped quality gates.

## Architecture & Technical Constraints

- **Maven Reactor Structure**: Root parent POM (`nl.invokedynamic.demo:springboot.rubegoldberg`) managing dependency versions, plugins, and module declarations.
- **Java / Spring Boot Baseline**: Java 21+ and Spring Boot 3.x baseline across all submodules.
- **Service Decoupling**: Microservices must communicate through well-defined contracts (e.g., RESTful APIs, event channels) without direct coupling or leaky abstractions.
- **Observability & Diagnostics**: All microservices must expose Actuator endpoints for health, info, and metrics to ensure runtime visibility.

## Development & Quality Workflow

- **Specification First**: Feature additions and modifications must first be captured in specification and planning artifacts before implementation begins.
- **Reactor Build Verification**: All builds must be verifiable from the root project using standard Maven commands (`mvn clean test`, `mvn clean verify`).
- **Clean Code & Contracts**: Consistent package naming (`nl.invokedynamic.demo.*`), strong typing for domain entities/DTOs, and explicit error handling schemas across microservice boundaries.

## Governance

This Constitution is the supreme governing document for the project. All architectural choices, pull requests, automated agent workflows, and code changes MUST comply with these principles. Amendments to this Constitution require documented rationale, semantic version increments, and formal ratification.

- **MAJOR** version bumps for breaking governance or foundational architectural shifts.
- **MINOR** version bumps for new principles or significant expansions to technical constraints.
- **PATCH** version bumps for wording refinements, corrections, or non-semantic clarifications.

**Version**: 1.0.0 | **Ratified**: 2026-08-31 | **Last Amended**: 2026-08-31
