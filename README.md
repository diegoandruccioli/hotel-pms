# 🏨 Enterprise Hotel PMS

An enterprise-grade, microservices-based **Hotel Property Management System**.  
This platform orchestrates hotel operations — from reservations and guest management to food & beverage point-of-sale, billing, and housekeeping — powered by a modern React frontend and a highly scalable Spring Boot backend ecosystem.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language (Backend)** | Java 21 |
| **Language (Frontend)** | TypeScript 5.x |
| **Backend Framework** | Spring Boot 3.4.x, Spring Cloud 2024.x |
| **Frontend Framework** | React 19, Vite 7.x |
| **State Management** | Zustand |
| **Styling** | Tailwind CSS 3.x |
| **Internationalization** | i18next + react-i18next (EN / IT) |
| **API Gateway** | Spring Cloud Gateway (WebFlux / Reactive) |
| **Auth** | JWT (HMAC-SHA256) – stateless, cookie-based |
| **Rate Limiting** | Spring Cloud Gateway + Redis Token Bucket |
| **Database** | PostgreSQL 15 (one DB per microservice) |
| **Migrations** | Flyway |
| **ORM / Mapping** | Spring Data JPA, MapStruct, Lombok |
| **Resilience** | Resilience4j (Circuit Breaker on Feign clients) |
| **Observability** | Zipkin (distributed tracing), Prometheus (metrics), Spring Boot Actuator |
| **Code Quality** | PMD via `gradle-java-qa` plugin (zero-warning policy) |
| **Testing (Backend)** | JUnit 5 + Mockito |\
| **Testing (Frontend)** | Vitest (unit), Playwright (E2E), vitest-axe (a11y) |
| **Build** | Gradle (Kotlin DSL), npm |
| **Containerization** | Docker, Docker Compose |

---

## Architecture Overview

The system follows a **distributed microservices** pattern with centralized configuration and an API Gateway as the single entry point.

```
┌──────────────┐     ┌──────────────┐     ┌────────────────────┐
│   React SPA  │────▶│  API Gateway │────▶│   Config Server    │
│  (Vite dev)  │     │   :8080      │     │     :8888          │
│   :5173      │     └──────┬───────┘     └────────────────────┘
└──────────────┘            │
                 ┌──────────┼──────────────────────┐
                 │          │                      │
          ┌──────▼──┐ ┌─────▼────┐ ┌──────────┐ ┌─▼──────────┐
          │  Auth   │ │  Guest   │ │Inventory │ │Reservation │
          │ :8087   │ │  :8083   │ │  :8081   │ │   :8082    │
          └─────────┘ └──────────┘ └──────────┘ └────────────┘
                 │          │                      │
          ┌──────▼──┐ ┌─────▼────┐ ┌──────────┐
          │  Stay   │ │ Billing  │ │   F&B    │
          │ :8084   │ │  :8085   │ │  :8086   │
          └─────────┘ └──────────┘ └──────────┘
                 │
          ┌──────▼──────────────────────────────┐
          │   PostgreSQL :5432  │  Redis :6379   │
          │   Zipkin :9411     │  Prometheus :9090│
          └─────────────────────────────────────┘
```

### Ports Map

| Service | Port | Type |
|---------|------|------|
| React Frontend (dev) | `5173` | Frontend |
| Frontend (Docker/Nginx) | `80` | Frontend |
| API Gateway | `8080` | Edge Gateway |
| Config Server | `8888` | Infrastructure |
| Auth Service | `8087` | Microservice |
| Guest Service | `8083` | Microservice |
| Inventory Service | `8081` | Microservice |
| Reservation Service | `8082` | Microservice |
| Stay Service | `8084` | Microservice |
| Billing Service | `8085` | Microservice |
| F&B Service | `8086` | Microservice |
| PostgreSQL | `5432` | Database |
| Redis | `6379` | Cache |
| Zipkin | `9411` | Tracing |
| Prometheus | `9090` | Metrics |

> All client traffic should go through the **API Gateway** on port `8080`, which enforces JWT validation, CORS policies, and rate limiting.

---

## How to Run

### Prerequisites

- **Java 21** (JDK)
- **Node.js 18+** & **npm**
- **Docker** & **Docker Compose**

### HMAC Secret Setup (first time only)

Before starting, generate the shared HMAC secret used for internal service-to-service authentication:

```bash
# Linux / macOS
chmod +x setup-hmac-secret.sh && ./setup-hmac-secret.sh

# Windows PowerShell
.\setup-hmac-secret.ps1
```

### One-Click Startup

Use the provided scripts to boot the entire ecosystem. Each script will:
1. Start all Docker containers (`docker compose up -d`)
2. Wait for the **Config Server** to become healthy
3. Wait for the **API Gateway** to become healthy
4. Install frontend dependencies (if needed) and start the Vite dev server

```bash
# Linux / macOS
chmod +x start.sh && ./start.sh

# Windows CMD
start.bat

# Windows PowerShell
.\start.ps1
```

Once all services are running, open **http://localhost:5173** in your browser.

---

## Default Admin Credentials

The system is seeded with a default administrator account on first boot:

| Field | Value |
|-------|-------|
| **Username** | `admin` |
| **Password** | `password` |
| **Email** | `admin@hotel.com` |
| **Role** | `ADMIN` |

> ⚠️ **Change the default password** in any non-development environment.

---

## Project Structure

```
hotel-pms/
├── api-gateway/           # Spring Cloud Gateway (JWT validation, rate limiting, routing)
├── auth-service/          # Authentication & user management (JWT issuance)
├── billing-service/       # Invoices and payment tracking
├── config-service/        # Spring Cloud Config Server (native profile)
├── fb-service/            # Food & Beverage / Restaurant POS
├── guest-service/         # Guest profile management
├── inventory-service/     # Room types and room inventory
├── reservation-service/   # Booking management
├── stay-service/          # Check-in / check-out / Alloggiati reports
├── frontend/              # React SPA (Vite + TypeScript + Tailwind)
├── docker/                # Docker init scripts (PostgreSQL multi-DB setup)
├── docs/                  # Postman collection & seed data
├── docker-compose.yml     # Full production-grade compose file
├── start.sh               # One-click startup (Linux/macOS)
├── start.bat              # One-click startup (Windows CMD)
├── start.ps1              # One-click startup (Windows PowerShell)
├── setup-hmac-secret.sh   # HMAC secret generator (Linux/macOS)
└── setup-hmac-secret.ps1  # HMAC secret generator (Windows PowerShell)
```

---

## Testing

### Backend (JUnit 5 + Mockito)
```bash
./gradlew clean build        # Runs all tests + PMD checks
```

### Frontend Unit Tests (Vitest)
```bash
cd frontend
npm run test
```

### Frontend E2E Tests (Playwright)
```bash
cd frontend
npm run test:e2e
```

---

## License

This project is for educational and demonstration purposes.
