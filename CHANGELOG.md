# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0-SNAPSHOT] - 2026-09-28

### Added
- `CorrelationIdFilter` to manage request correlation IDs via HTTP header `X-Correlation-Id` and SLF4J MDC (`correlationId`).
- `CorrelationIdFilterTest` verifying correlation ID generation, propagation, validation, and MDC cleanup.
- Correlation ID propagation in `ErrorResponse` and corresponding assertions in `GlobalExceptionHandlerTest`.
- `spring-boot-starter-webmvc-test` dependency for WebMVC test slice support in Spring Boot 4.
