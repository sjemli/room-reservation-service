# Room Reservation Service

Spring Boot application that manages hotel room reservations with support for multiple payment methods (Cash, Credit Card, Bank Transfer).

## Assignment Goals (Implemented Features)

This service fulfills the following requirements:

1. **REST API to confirm a room reservation**
    - Endpoint: `POST /reservations`
    - Immediate confirmation for **CASH** payments
    - Synchronous call to external `credit-card-payment-service` for **CREDIT_CARD** (with circuit breaker & retry)
    - Pending status for **BANK_TRANSFER** (confirmation via Kafka later)
    - Input validations: max 30 days stay, valid dates, required fields

2. **Event-driven confirmation for bank transfers**
    - Consumes Kafka topic `bank-transfer-payment-update`
    - Validates strict transaction description format:  
      `E2E<10 characters unique id> <reservationId exactly 8 uppercase alphanumeric>`  
      Example: `E2E1234567890 ABC12345`
    - Confirms reservation if valid & pending → idempotent handling
    - Invalid/malformed messages are **logged and sent directly to DLQ** (no retries) but other exceptions lead to retrying.

3. **Automatic cancellation of overdue bank-transfer reservations**
    - Scheduled task (cron) cancels reservations where payment not confirmed **2 days before start date**
    - Only affects PENDING_PAYMENT + BANK_TRANSFER reservations

## Key Features

- **Multi-payment support**
    - Cash → instant confirmation
    - Credit Card → external synchronous API call with Resilience4j (retry + circuit breaker)
    - Bank Transfer → asynchronous confirmation via Kafka

- **Event-driven architecture**
    - Kafka consumer with manual acknowledgment
    - Strict validation of payment event format
    - Malformed message handling: malformed → direct DLQ (no blocking retries)

- **Resilience & observability**
    - Resilience4j circuit breaker & retry (with exponential backoff and jitter) on credit-card calls
    - Structured logging (SLF4J)
    - Actuator endpoints (health, metrics)
    - Double-booking prevention: rooms cannot be booked for overlapping dates


- **Production-ready aspects**
    - Idempotent processing
    - Transactional DB operations
    - Input validation (Jakarta Bean Validation)
    - OpenAPI/Swagger documentation
    - Test pyramid: unit, integration (with Embedded Kafka), E2E => coverage 0.99 with Jacoco

## Technology Stack

| Technology             | Purpose                                                | Version |
|------------------------|--------------------------------------------------------|---------|
| Spring Boot            | Application framework, REST, scheduling, configuration | 4.0.1   |
| Spring Web             | REST API                                               | -       |
| Spring Data JPA        | Database access (H2 for tests, PostgreSQL ready)       | -       |
| Spring Kafka           | Kafka producer & consumer (KRaft mode in tests)        | -       |
| Resilience4j           | Circuit breaker & retry for external credit-card calls | 2.2.0   |
| Lombok                 | Boilerplate reduction                                  | -       |
| H2 Database            | In-memory testing database                             | -       |
| WireMock               | External service stubbing in E2E tests                 | 3.x     |
| Awaitility             | Async assertions in integration & E2E tests            | 4.2.1   |
| Embedded Kafka (KRaft) | Real Kafka broker simulation in tests (no Zookeeper)   | -       |
| OpenAPI / Springdoc    | Automatic Swagger UI documentation                     | 2.6.0   |
| JUnit 5 + Mockito      | Unit & integration testing                             | -       |
| Java                   |                                                        | 21      |
## Getting Started

### Prerequisites
- Java 21
- Maven 3.8+

### Run locally
```bash
# Build & run
mvn clean install
mvn spring-boot:run

# Or with custom profile
mvn spring-boot:run -Dspring-boot.run.profiles=test
