# Rate Limiter Service

A portfolio-grade Spring Boot service built from scratch in Java + Spring Boot (no rate-limiting library used) for implementing, comparing, and orchestrating common rate limiting strategies.

The project demonstrates rule-driven rate limiting, algorithm selection, validation, Redis-backed distributed state, and automatic fallback behavior for resilient API traffic control.

## Current implementation status

Implemented:
- Fixed Window
- Token Bucket
- Leaky Bucket
- Sliding Window Counter
- Rule engine with priority matching
- Admin API for rule management
- In-memory storage with TTL cleanup
- Redis-backed storage abstraction with Lua-based atomic increment logic
- Circuit breaker fallback to in-memory storage when Redis is unavailable
- Request validation and consistent response contract

## Features

- Per-client and per-endpoint rate limiting
- Per-algorithm configuration via the rule engine
- Support for multiple limiter strategies in one service
- In-memory local mode for fast single-node execution
- Redis-backed distributed state for coordination across instances
- Circuit breaker fallback for resilience during Redis outages
- Validation and a consistent response contract for API callers

## Architecture overview

```text
Client Request
      |
      v
RateLimitController (validation)
      |
      v
RateLimiterService (orchestrator)
      |
      +--> RuleEngineService (priority-based rule resolution)
      |        |
      |        +--> exact clientId + endpoint
      |        +--> clientId only
      |        +--> endpoint only
      |        +--> global default
      |
      +--> RateLimiter implementation (Fixed / Token / Leaky / Sliding)
      |
      +--> CircuitBreakerStorage
                 |
                 +--> RedisStorage (primary, atomic Lua)
                 +--> InMemoryStorage (fallback)
      |
      v
CheckResponse (allowed / remaining / retryAfter)
```

Priority order for rule matching:
1. clientId + endpoint exact match
2. clientId only match
3. endpoint only match
4. default/global rule

This is designed to operate as a single Redis-backed instance for distributed state coordination, with no clustering configuration in the current project.

## Algorithms implemented

1. **Fixed Window** — Simplest approach. The counter resets at a fixed time boundary.
   - Trade-off: boundary spikes can occur at the window edge
   - Use when: simple per-minute or per-hour quotas are enough

2. **Token Bucket** — Tokens refill gradually and each request consumes one token.
   - Trade-off: small bursts are allowed up to bucket capacity
   - Use when: APIs need controlled burst tolerance without aggressive throttling

3. **Leaky Bucket** — Requests are queued and drained at a fixed rate.
   - Trade-off: queueing adds slight latency
   - Use when: strict smooth throughput is more important than burst bursts

4. **Sliding Window Counter** — A weighted approximation across the previous and current windows.
   - Trade-off: small estimation error but much better than a fixed boundary reset
   - Use when: balancing accuracy and efficiency, similar to common production patterns

## Rule engine

The service resolves limits using the `Rule` model:

```java
public class Rule {
    private String id;
    private String clientId;
    private String endpoint;
    private int limit;
    private int windowSeconds;
    private String algorithm;
    private String tier;
}
```

The engine loads rules from `rules.json` and supports in-memory CRUD updates through the admin API.

Example rules included in the project:

```json
[
  {
    "id": "54d43860-e0d9-4c5b-b3b8-0b21cfa0e844",
    "clientId": "user-123",
    "endpoint": "/api/checkout",
    "limit": 5,
    "windowSeconds": 60,
    "algorithm": "TOKEN_BUCKET",
    "tier": "pro"
  },
  {
    "id": "7609e603-7ff3-4655-a09f-c0e7bedcecae",
    "clientId": "user-123",
    "endpoint": "/api/*",
    "limit": 100,
    "windowSeconds": 60,
    "algorithm": "SLIDING_WINDOW_COUNTER",
    "tier": "pro"
  },
  {
    "id": "44931f89-f2e3-40fc-b67a-0c90f06e873d",
    "clientId": "*",
    "endpoint": "/api/*",
    "limit": 50,
    "windowSeconds": 60,
    "algorithm": "TOKEN_BUCKET",
    "tier": "default"
  }
]
```

## Request and response contract

### Check request

```json
{
  "clientId": "user-123",
  "endpoint": "/api/checkout",
  "algorithm": "TOKEN_BUCKET"
}
```

The `algorithm` field is optional. If omitted, the rule engine resolves it from the matching rule.

### Check response

```json
{
  "allowed": true,
  "remaining": 3,
  "retryAfter": 0,
  "message": "Request allowed"
}
```

When blocked:

```json
{
  "allowed": false,
  "remaining": 0,
  "retryAfter": 45,
  "message": "Rate limit exceeded"
}
```

Response semantics in this project:
- `allowed`: true means the request was accepted
- `remaining`: remaining quota for the matched limiter state
- `retryAfter`: seconds until the next allowed request, 0 when allowed
- `message`: simple status description

## API endpoints

### 1. Check rate limit

POST `/api/v1/check`

Request example:

```json
{
  "clientId": "user-123",
  "endpoint": "/api/checkout"
}
```

Response example (allowed):

```json
{
  "allowed": true,
  "remaining": 2,
  "retryAfter": 0,
  "message": "Request allowed"
}
```

Response example (blocked):

```json
{
  "allowed": false,
  "remaining": 0,
  "retryAfter": 60,
  "message": "Rate limit exceeded"
}
```

### 2. Admin API for rules

GET `/admin/rules`  
List all configured rules

GET `/admin/rules/{id}`  
Fetch a single rule

POST `/admin/rules`  
Create a rule

PUT `/admin/rules/{id}`  
Update an existing rule

DELETE `/admin/rules/{id}`  
Delete a rule

Example create rule payload:

```json
{
  "clientId": "user-456",
  "endpoint": "/api/orders",
  "limit": 25,
  "windowSeconds": 60,
  "algorithm": "FIXED_WINDOW",
  "tier": "pro"
}
```

## Storage and resilience

This project includes a storage abstraction:

- `RateLimiterStorage` interface
- `InMemoryStorage` for local state and fallback handling
- `RedisStorage` for atomic Redis counting with Lua scripts
- `CircuitBreakerStorage` for automatic failover to in-memory storage when Redis is unhealthy

Redis behavior:
- Redis is optional
- the app can boot without Redis running
- when Redis is unavailable, the circuit breaker automatically transitions to in-memory fallback and remains transparent to callers
- the current design uses a single Redis instance; no clustering is configured yet

## API Documentation

Base URL: `http://localhost:8080`

Swagger/OpenAPI documentation is auto-generated and available at:

`http://localhost:8080/swagger-ui.html`

The app exposes:
- request and response schemas
- example payloads
- parameter descriptions
- HTTP status codes

## Performance

Performance characteristics vary by mode:

| Mode | Characteristics |
|------|-----------------|
| In-Memory | High-throughput, single-node, generally best for local or low-latency scenarios |
| Redis | Distributed consistency and cross-instance coordination with higher latency than in-memory |
| Circuit Breaker | Automatic fallback to in-memory when Redis is unavailable |

Detailed benchmark numbers are coming with JMH or load-testing validation.

## Configuration

Key application settings are in `src/main/resources/application.properties`.

```properties
spring.application.name=rate-limiter-service
maxTokens=4
windowSeconds=60
maxTokensForTokenBucket=4.0
leakyBucketSize=4
outflowRate=2
slidingWindowMaxRequests=4

spring.redis.host=localhost
spring.redis.port=6379

circuit.breaker.failure.threshold=5
circuit.breaker.timeout.seconds=30
rule.engine.storage.file=rules.json

springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.enabled=true
```

## Running the project

From the project root:

```bash
./mvnw spring-boot:run
```

## Running with Redis

Start Redis locally:

```bash
docker run -d -p 6379:6379 redis:latest
```

Then run the app:

```bash
./mvnw spring-boot:run
```

The application auto-detects Redis and uses distributed count tracking when it is available.

## Docker Compose

Start both the app and Redis together:

```bash
docker-compose up
```

The repository includes a `docker-compose.yml` for local orchestration.

## Tech stack

- Java 21
- Spring Boot 3.2.6
- Maven
- Redis (optional)
- Spring Validation
- Springdoc OpenAPI
- Jackson
- ConcurrentHashMap / AtomicInteger for in-memory concurrency

## Implementation Highlights

- No external rate-limiting libraries — built from first principles
- Thread-safe design using `ConcurrentHashMap` and synchronized logic for correctness
- Atomic Redis operations via Lua scripts to reduce race conditions
- Circuit breaker pattern with automatic fallback to in-memory mode
- Rule engine with priority resolution: clientId + endpoint > clientId > endpoint > default
- Clean separation between controller, service, algorithm, and storage layers

## Testing

The project includes a test suite covering algorithm behavior, validation, and application startup.

Run tests:

```bash
./mvnw test
```

## Git history

This project is organized with feature-oriented commits so that algorithm, rule-engine, storage, and Redis changes remain easy to review and reason about.

## Known Limitations

- Single Redis instance; no clustering is configured yet
- In-memory fallback is local to the running instance during Redis outages
- TTL-based expiry is lazy and cleaned on access, not continuously background-managed for every key
- Rule changes are immediate and do not yet include persistent versioning or rollback history

These are practical trade-offs for a portfolio project and are suitable for a clean MVP-level architecture.

## Notes

- This is a portfolio project focused on showing strong understanding of rate limiting concepts and Java/Spring patterns.
- It intentionally avoids calling any real downstream systems.
- Redis is not required to run the app, but it is supported as an optional distributed backend for state coordination.
- The default logic is configurable through the rule engine and `rules.json` file.

## Project structure

```text
src/
  main/
    java/
      com/ratelimiter/rate_limiter_service/
        controller/
        dto/
        service/
        service/impl/
        storage/
    resources/
      application.properties
rules.json
pom.xml
README.md
docker-compose.yml
Dockerfile
```

## Summary

This project demonstrates the core building blocks of a rate limiter:
- algorithm selection by rule matching
- request validation and consistent response shapes
- in-memory and Redis-backed storage options
- circuit breaker fallback behavior
- admin-managed configuration
- API documentation with Swagger

It is a strong portfolio-style project for showcasing understanding of distributed API traffic control and backend resilience patterns.
