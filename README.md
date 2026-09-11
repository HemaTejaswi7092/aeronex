# AeroNex

Real-time airline operations and disruption recovery platform — backend foundation.

This is stage 1 of the backend: a minimal Spring Boot application with a single health
endpoint, wired up for PostgreSQL and Flyway. No domain entities or business logic yet.

## Stack

- Java 21
- Spring Boot 3.3.x
- Maven
- Spring Web, Spring Data JPA, Validation
- PostgreSQL driver
- Flyway (no migrations yet — added when the first real schema is designed)

## Prerequisites

- JDK 21
- Maven 3.9+ (or use the included `mvnw` wrapper if you add one)
- A local PostgreSQL instance (only required to run the app; not required to run tests)

## Local database setup

The app expects a database matching this config (`src/main/resources/application.yml`):

```
host:     localhost
port:     5432
database: aeronex
username: aeronex
password: aeronex
```

Create it with:

```bash
createdb aeronex
psql -d postgres -c "CREATE USER aeronex WITH PASSWORD 'aeronex';"
psql -d postgres -c "GRANT ALL PRIVILEGES ON DATABASE aeronex TO aeronex;"
```

Adjust `src/main/resources/application.yml` if your local Postgres uses different
credentials or connection details.

There are no Flyway migrations yet, so on startup Flyway will simply initialize its
schema history table against an otherwise empty database.

## Running the app

```bash
mvn spring-boot:run
```

Once started, verify the health endpoint:

```bash
curl http://localhost:8080/api/health
# {"status":"UP"}
```

## Running tests

```bash
mvn test
```

The test context uses an in-memory H2 database (see `src/test/resources/application.yml`)
with Flyway disabled, so tests run without needing a local PostgreSQL instance.

## Building

```bash
mvn clean package
```

Produces an executable jar in `target/`.

## Project structure

```
src/main/java/com/aeronex/
├── AeronexApplication.java      Spring Boot entry point
└── health/
    └── HealthController.java    GET /api/health

src/main/resources/
└── application.yml              App + datasource + Flyway config

src/test/java/com/aeronex/
└── AeronexApplicationTests.java Spring context load smoke test

src/test/resources/
└── application.yml              Test-only config (H2, Flyway disabled)
```
