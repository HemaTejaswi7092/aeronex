# AeroNex

[![CI](https://github.com/HemaTejaswi7092/aeronex/actions/workflows/ci.yml/badge.svg)](https://github.com/HemaTejaswi7092/aeronex/actions/workflows/ci.yml)

A real-time airline operations and disruption-recovery backend. Tracks airports,
aircraft, and scheduled flights; records and resolves operational disruptions;
and propagates disruption events through Kafka via a transactional outbox, with
an idempotent consumer building an audit trail. Instrumented end-to-end with
Actuator health/readiness checks, Prometheus metrics, and structured JSON logs.

## Stack

- Java 21, Spring Boot 3.3, Maven
- Spring Web, Spring Data JPA, Bean Validation
- PostgreSQL + Flyway (7 migrations, V1–V7)
- Apache Kafka (KRaft mode) via Spring for Apache Kafka
- Transactional outbox + idempotent consumer for reliable event delivery
- Spring Boot Actuator + Micrometer (Prometheus registry)
- JSON structured logging (`logstash-logback-encoder`) with per-request correlation IDs
- Docker + Docker Compose for a fully containerized local stack

## Domains and API

| Domain | Endpoints |
|---|---|
| Airport | `POST /api/airports`, `GET /api/airports`, `GET /api/airports/{id}`, `GET /api/airports/iata/{iataCode}` |
| Aircraft | `POST /api/aircraft`, `GET /api/aircraft`, `GET /api/aircraft/{id}`, `GET /api/aircraft/registration/{registrationNumber}` |
| Flight | `POST /api/flights`, `GET /api/flights`, `GET /api/flights/{id}`, `GET /api/flights/number/{flightNumber}`, `GET /api/flights/airport/{iataCode}` |
| Disruption | `POST /api/disruptions`, `GET /api/disruptions`, `GET /api/disruptions/active`, `GET /api/disruptions/{id}`, `GET /api/disruptions/flight/{flightId}`, `PATCH /api/disruptions/{id}/resolve` |

A `Disruption` references a `Flight`, which references two `Airport`s and
optionally an `Aircraft`. Business rules (e.g. an aircraft that's
`OUT_OF_SERVICE` can't be assigned to a flight, a flight that's `ARRIVED` or
`CANCELLED` can't receive a new disruption) are enforced in the service layer
and surfaced as structured `4xx` responses via a centralized exception handler.

## Event-driven disruption workflow

Creating or resolving a `Disruption` writes an outbox row in the *same*
database transaction as the domain change — never a direct Kafka call from the
request thread. A background relay (`OutboxRelay`) polls that table and
publishes to Kafka; a Kafka outage cannot make disruption creation/resolution
fail, it only delays the event. Two topics carry the workflow:

- `aeronex.disruption.reported`
- `aeronex.disruption.resolved`

Both are declared explicitly as `NewTopic` beans (idempotent — safe if they
already exist) rather than relying on broker auto-create. A consumer
(`DisruptionEventListener` / `DisruptionEventProcessor`) processes each event
exactly once — a `processed_events` table guards against Kafka's at-least-once
redelivery — and records it into `disruption_event_log`, an append-only audit
trail of everything that happened to a disruption over time.

## Observability

- `GET /actuator/health` — overall health (DB + Kafka broker reachability)
- `GET /actuator/health/liveness`, `GET /actuator/health/readiness` — Kubernetes-style
  probe groups; deliberately **exclude** Kafka, so a broker outage never marks
  the app "not ready" to serve its REST API
- `GET /actuator/prometheus` — metrics in Prometheus exposition format, including:
  - `aeronex_outbox_backlog_size` — unpublished outbox rows (gauge)
  - `aeronex_outbox_publish_total{topic,result}` — relay publish attempts
  - `aeronex_disruption_events_processed_total{eventType}` / `..._duplicate_total` / `..._failed_total` — consumer outcomes
  - `http_server_requests_seconds` — request latency, with histogram buckets for percentile queries
- Only `health`, `info`, `prometheus`, and `metrics` are exposed — no `env`, `beans`,
  `heapdump`, `shutdown`, etc.
- Logs are JSON on stdout (`logback-spring.xml`) with an `X-Correlation-Id`
  attached to every request and echoed into every log line for that request.

## Running with Docker Compose (recommended)

Brings up PostgreSQL, Kafka (KRaft mode, no ZooKeeper), and the AeroNex app together.

**Prerequisites:** Docker and Docker Compose v2 (`docker compose`, not the legacy `docker-compose`).

```bash
docker compose up --build
```

This builds the app image (multi-stage: Maven build, then a slim non-root JRE
runtime), then starts Postgres and Kafka and waits for both to report healthy
*before* starting the app — no arbitrary sleeps involved, `depends_on` is
gated on each service's own healthcheck. Flyway applies all 7 migrations
against the real Postgres on first boot.

Once it's up:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/airports
```

Stop everything (data persists in named volumes):

```bash
docker compose down
```

Stop and wipe all data (fresh start):

```bash
docker compose down -v
```

**Ports exposed to the host:** `8080` (app), `5432` (Postgres, for connecting a
local DB client), `9092` (Kafka, for connecting a local Kafka CLI — the broker
advertises `localhost:9092` to host tools and `kafka:29092` to other containers).

**Configuration:** database credentials default to `aeronex`/`aeronex`/`aeronex`
(database/user/password) — fine for local dev, matching what this README has
always documented. `AERONEX_JWT_SECRET` is different: it has **no default** —
both the app and Docker Compose refuse to start without it set, so it can
never be silently signing tokens with a known value. Copy `.env.example` to
`.env` and adjust as needed; `.env` is gitignored and Compose loads it
automatically.

## Running on Kubernetes

Plain manifests under `k8s/` (no Helm, no Kustomize) deploy the same
architecture as Docker Compose — development-grade Postgres and Kafka
(single replica each, PVC-backed, no HA) alongside the app — to a local
[kind](https://kind.sigs.k8s.io/) cluster. No image registry is used: the
existing image is built locally and loaded directly into the cluster node.

```bash
kind create cluster --name aeronex
docker build -t aeronex-aeronex-app:latest .
kind load docker-image aeronex-aeronex-app:latest --name aeronex

cp k8s/secret.yaml.example k8s/secret.yaml   # edit with real local-dev values; never commit this file
kubectl apply -f k8s/app-configmap.yaml -f k8s/secret.yaml
kubectl apply -f k8s/postgres-pvc.yaml -f k8s/kafka-pvc.yaml
kubectl apply -f k8s/postgres-deployment.yaml -f k8s/postgres-service.yaml
kubectl apply -f k8s/kafka-deployment.yaml -f k8s/kafka-service.yaml
kubectl apply -f k8s/app-deployment.yaml -f k8s/app-service.yaml

kubectl wait --for=condition=Ready pod -l app=aeronex-app --timeout=240s
kubectl port-forward svc/aeronex-app 8080:8080
```

Notes on how this differs from Docker Compose, and why:

- **No external exposure for Postgres/Kafka.** Both Services are `ClusterIP`
  only; the app Service is also `ClusterIP`, reached locally via
  `kubectl port-forward` — there's no Ingress/TLS yet.
- **`initContainers` replace `depends_on: condition: service_healthy`.**
  Kubernetes Deployments have no native equivalent, so the app pod's
  `wait-for-postgres`/`wait-for-kafka` init containers block it from starting
  until both are actually reachable.
- **The app's Docker `HEALTHCHECK` becomes three native probes** —
  `startupProbe`/`readinessProbe`/`livenessProbe` — reusing the same
  `/actuator/health/readiness` and `/actuator/health/liveness` endpoints the
  observability milestone already built for exactly this.
- **Kafka's controller quorum voter points at `localhost`, not the Service
  name.** This single-node broker and controller are the same process; routing
  that self-registration RPC through the Service's ClusterIP hits a hairpin NAT
  path some `kind` CNI setups don't reliably support. Compose doesn't hit this
  because its embedded DNS resolves a container's own hostname with no NAT
  involved.
- **Single replica only.** The outbox relay's `@Scheduled` poller has no
  distributed lock, so running more than one app replica risks double-publishing
  outbox events — matching Compose's own single-instance posture.

Not yet included: a container registry, wiring this into CI, Ingress/TLS, and
autoscaling — all deliberately deferred to a later milestone.

## Running without Docker

**Prerequisites:** JDK 21, Maven 3.9+, a local PostgreSQL instance, a local Kafka broker (optional — see below).

Create the database:

```bash
createdb aeronex
psql -d postgres -c "CREATE USER aeronex WITH PASSWORD 'aeronex';"
psql -d postgres -c "GRANT ALL PRIVILEGES ON DATABASE aeronex TO aeronex;"
```

`src/main/resources/application.yml` points at `localhost:5432` and
`localhost:9092` by default. Adjust it if your local instances differ.

```bash
mvn spring-boot:run
```

Kafka is not required for the core REST API to work — if it's unreachable,
outbox events simply queue up until a broker becomes available (nothing is
lost, nothing fails). It's only required to see the disruption event
workflow complete end-to-end.

## Running tests

```bash
mvn test
```

Tests never require a running Postgres or Kafka: JPA/repository tests run
against an in-memory H2 database (`src/test/resources/application.yml`,
Flyway still applies all migrations against it), and the one true end-to-end
Kafka test (`DisruptionKafkaEndToEndTest`) uses `@EmbeddedKafka` — an
in-process broker, no Docker required even for that.

## Building

```bash
mvn clean package
```

Produces an executable jar in `target/`. `docker compose build` produces the
containerized equivalent via the multi-stage `Dockerfile`.

## Continuous integration

Every push to `main` and every pull request into it runs `.github/workflows/ci.yml`,
two sequential jobs:

1. **`build-test-package`** — `mvn clean package` on Java 21 (Maven dependency
   cache via `actions/setup-java`). Maven's `package` phase depends on `compile`
   and `test`, so this single command gates on clean compilation, the full
   122-test suite, and the jar build, in that order. The test suite needs no
   external services: it runs against an in-memory H2 database in
   PostgreSQL-compatibility mode, applying all Flyway migrations on every run.
   Surefire reports are uploaded as a build artifact on every run (pass or fail).
2. **`docker-integration-smoke`** (only runs if the first job passes) — builds
   the real multi-stage Docker image, brings up the full stack (Postgres, Kafka,
   app) via Compose, and verifies: the app reports healthy; login issues a JWT;
   the token is accepted on a protected endpoint; the same endpoint rejects an
   unauthenticated request with `401`; and a container started with a blank
   `AERONEX_JWT_SECRET` refuses to start, per the fail-fast guard. Container
   logs are dumped on failure, and the stack is always torn down afterward.

Both jobs run with least-privilege permissions (`contents: read`) and no
GitHub Actions secrets — nothing genuinely sensitive is required to build or
smoke-test this project, so the CI-only JWT secret and dev DB credentials are
plain, clearly-labeled placeholder values, matching `.env.example`. Superseded
runs on the same branch/PR are automatically cancelled.

## Project structure

```
src/main/java/com/aeronex/
├── AeronexApplication.java
├── airport/            Airport domain (entity, repository, service, controller, DTOs)
├── aircraft/            Aircraft domain
├── flight/              Flight domain (references Airport, Aircraft)
├── disruption/          Disruption domain (references Flight) + DisruptionEventLog
│   └── event/           Kafka payloads, listener, and idempotent processor
├── eventing/
│   ├── DomainEventPublisher.java   the only thing domain services call to emit events
│   ├── outbox/          Outbox entity/repository/relay/metrics
│   ├── idempotency/     Processed-event dedupe guard
│   └── kafka/           Topic declarations, producer/consumer config, Kafka health
└── common/web/          Centralized exception handling, correlation ID filter

src/main/resources/
├── application.yml            App, datasource, Kafka, Actuator config
├── logback-spring.xml         JSON structured logging
└── db/migration/               Flyway V1–V7

Dockerfile                      Multi-stage build (Maven → slim non-root JRE)
docker-compose.yml               Postgres + Kafka (KRaft) + app, health-gated startup
.env.example                     Documents overridable local credentials
k8s/                             Plain manifests for a local kind deployment
```
