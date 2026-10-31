# M-SAL2.0

تطبيق ميزانية شخصية بالعربي: ميزانية شهرية، ومصاريف يومية حسب دورة الراتب، وإحصاءات.
إعادة بناء [M-SAL](https://github.com/AbdullahAltaher/M-SAL) ببنية قوية.

A personal budget app in Arabic (RTL): a monthly budget ledger, daily expenses tracked by salary cycle, and statistics.
A rebuild of [M-SAL](https://github.com/AbdullahAltaher/M-SAL) on a proper architecture.

## Stack

Angular (PWA) · Spring Boot (Java 21) · Supabase Postgres + Auth · Flyway · Docker · Render · GitHub Pages

## Status

Phases 0 (setup), 1 (backend foundation) and 2 (business logic) complete. Next: phase 3, the API.

## Run locally

Requires Docker Desktop, Java 21 and Node 22. Copy `.env.example` to `.env` first.

```bash
docker compose up -d --build   # Postgres on :5433, Spring Boot on :8080
curl localhost:8080/actuator/health
```

```bash
cd backend && ./mvnw verify    # backend tests (needs Docker: Testcontainers)
```

```bash
cd frontend && npm ci && npm start   # Angular on :4200
```

## Docs

| Document | Contents |
|---|---|
| [docs/PLAN.md](docs/PLAN.md) | Full plan, decisions and implementation phases |
| [docs/BUSINESS_RULES.md](docs/BUSINESS_RULES.md) | Every calculation and rule, extracted from M-SAL |
| [docs/DATABASE.md](docs/DATABASE.md) | Tables, relations and draft Flyway migrations |
| [docs/API.md](docs/API.md) | The API contract between Angular and Spring Boot |
| [docs/SCREENS.md](docs/SCREENS.md) | Screens, sheets, components and the matching checklist |
