# Contributing — Hotel PMS

Guida per sviluppatori che vogliono contribuire al progetto o fare onboarding.

---

## 1. Prerequisiti

| Tool | Versione minima | Verifica |
|---|---|---|
| Java JDK | 21 | `java -version` |
| Node.js | 18+ | `node -version` |
| npm | 9+ | `npm -version` |
| Docker Engine | 24+ | `docker info` |
| Docker Compose | v2 | `docker compose version` |

---

## 2. Setup ambiente di sviluppo

```bash
# 1. Clonare il repository
git clone https://github.com/diegoandruccioli/hotel-pms.git
cd hotel-pms

# 2. Generare il segreto HMAC (una tantum)
# Linux/macOS:
chmod +x setup-hmac-secret.sh && ./setup-hmac-secret.sh
# Windows PowerShell:
.\setup-hmac-secret.ps1

# 3. Copiare il template delle variabili d'ambiente
cp .env.example .env
# Editare .env con le proprie credenziali (vedi commenti nel file)

# 4. Avviare lo stack completo
# Linux/macOS:
./start.sh
# Windows CMD:
start.bat
# Windows PowerShell:
.\start.ps1
```

L'applicazione è raggiungibile su **http://localhost:5173**.
Credenziali default (solo sviluppo): `admin` / `password`.

### Avvio parziale (solo backend)

```bash
# Avvia solo i container Docker (DB, Redis, Config, ecc.)
docker compose up -d

# Avvia un singolo servizio durante lo sviluppo
./gradlew :stay-service:bootRun
```

### Avvio parziale (solo frontend)

```bash
cd frontend
npm install
npm run dev   # Vite dev server su :5173 con proxy verso :8080
```

---

## 3. Convenzioni commit

Il progetto usa **Conventional Commits** (`feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `build`, `ci`).

```
<type>(<scope>): <descrizione imperativa>

[corpo opzionale — spiega il PERCHÉ, non il COSA]
```

**Esempi validi:**
```
feat(stay-service): add Alloggiati SOAP auto-submit on check-in
fix(billing): add @Version on Invoice to prevent concurrent write loss
docs(user-manual): add PS portal credentials setup procedure
test(auth-service): add controller tests for reset-password endpoint
refactor(frontend): extract GuestAlloggiatiFields into shared component
```

**Regole:**
- Scope = nome del servizio o area (`auth-service`, `frontend`, `billing`, `ci`, `docker`)
- Descrizione in inglese, imperativa, ≤ 72 caratteri
- `Co-Authored-By:` **non va mai aggiunto** ai commit di questo progetto

---

## 4. Branch strategy

| Branch | Scopo | Regola |
|---|---|---|
| `main` | Stato finale integrato — build deve essere sempre verde | Solo merge da feature branch con CI verde |
| `feature/secure-coding-hardening` | Storico hardening sicurezza esame | Congelato — non eliminare, non modificare |
| `pre-secure-coding` | Snapshot baseline pre-hardening | Congelato — non modificare |

**Per nuovi sviluppi:** creare branch da `main`.

```bash
git checkout main && git pull
git checkout -b feature/nome-feature
# ... sviluppo ...
git push -u origin feature/nome-feature
# Aprire PR verso main
```

**Per sprint di sicurezza:** creare branch `feature/security-sprint-N`.
Ogni commit di sicurezza deve aggiornare `THREAT_MODEL.md` e il report LaTeX.

---

## 5. Code quality gates

Il build fallisce se una di queste condizioni non è rispettata:

### Backend
```bash
./gradlew clean build   # Compila + test + PMD + Checkstyle + JaCoCo threshold
```

- **PMD**: zero warning (policy enforced — build fallisce)
- **JaCoCo**: ≥ 40% instruction coverage su ogni modulo
- **Layering**: nessuna entity esposta via REST — usare sempre Record DTO + MapStruct

### Frontend
```bash
cd frontend
npm run lint          # ESLint zero warning
npm run test          # 324 test Vitest — tutti devono passare
npm run build         # TypeScript strict check + Vite build
```

- **ESLint**: zero warning
- **TypeScript**: `strict: true`, zero `any` — usare `unknown` + type guard
- **i18n**: zero testo hardcoded — ogni stringa visibile all'utente usa `t('chiave')`

---

## 6. Pattern di sviluppo backend

### Aggiungere un nuovo microservizio

1. Creare la directory `<service-name>/`
2. Copiare la struttura `build.gradle.kts` da un servizio esistente (es. `guest-service`)
3. Aggiungere a `settings.gradle.kts`: `include("<service-name>")`
4. Aggiungere route nell'API Gateway: `config-service/src/main/resources/config/api-gateway.yml`
5. Aggiungere configurazione in: `config-service/src/main/resources/config/<service-name>.yml`
6. Aggiungere al `docker-compose.yml` con healthcheck, resource limits, reti corrette
7. Creare schema DB in: `docker/postgres/init-multiple-databases.sql`
8. **Obbligatorio:** aggiungere `InternalAuthFilter` per verifica HMAC `X-Internal-Signature`
9. **Obbligatorio:** aggiungere `GlobalExceptionHandler` con RFC 7807 `ProblemDetail`
10. **Obbligatorio:** aggiungere `hotel_id NOT NULL` su ogni entità rilevante

### Struttura layer obbligatoria

```
Controller → Service (Interface + Impl) → Repository → Entity
```

- Nessuna entity esposta direttamente via REST
- Record DTO mappati con MapStruct
- UUID primary keys su tutte le entità
- Soft delete (`active` boolean) — mai hard delete
- `@CreatedDate` + `@LastModifiedDate` su tutte le entità

### Aggiungere un client Feign inter-service

```java
@FeignClient(name = "target-service", fallbackFactory = TargetClientFallback.class)
public interface TargetClient {
    @GetMapping("/api/v1/...")
    TargetResponse getData(...);
}
```

Ogni `@FeignClient` deve avere `@CircuitBreaker` Resilience4j con fallback dichiarato.

---

## 7. Pattern di sviluppo frontend

### Aggiungere un nuovo componente

- Un componente per file, PascalCase filename = component name
- Barrel export (`index.ts`) per feature folder
- `React.memo()` per componenti con props stabili
- `React.lazy()` + `<Suspense>` per page-level components

### Aggiungere una nuova pagina

1. Creare `src/pages/NuovaPagina.tsx`
2. Aggiungere il lazy import in `App.tsx`
3. Aggiungere la route con `requireAuth` e `allowedRoles`
4. Aggiungere i18n keys in `src/locales/en/<namespace>.json` e `it/<namespace>.json`

### Accessibilità (obbligatoria)

- Contrasto minimo 7:1 per testo normale, 4.5:1 per testo grande
- `vitest-axe` su ogni component test (già nel template)
- Focus trap nei modali con `focus-trap-react`
- Tutti i form con `<label htmlFor>` o `aria-label`
- Touch target minimo 40×40 px

---

## 8. Aggiungere stringhe i18n

1. Aggiungere la chiave in `frontend/src/locales/en/<namespace>.json`
2. Aggiungere la traduzione italiana in `frontend/src/locales/it/<namespace>.json`
3. Usare `t('<chiave>')` nel componente
4. Convenzioni: `snake_case`, prefisso funzionale (`label_`, `err_`, `action_`, ecc.)

Vedi [`docs/I18N.md`](docs/I18N.md) per la guida completa.

---

## 9. Scrivere i test

### Backend (JUnit 5 + Mockito)

```java
@ExtendWith(MockitoExtension.class)
class NuovoServiceImplTest {
    @Mock NuovoRepository nuovoRepository;
    @InjectMocks NuovoServiceImpl service;

    @Test
    void shouldReturnDataForCorrectHotel() { ... }

    @Test
    void shouldThrowWhenHotelIdMismatch() { ... }
}
```

- Testare happy path **e** edge case
- Per controller: usare `MockMvc` standaloneSetup
- Coverage minima enforced: ≥ 40% instruction (JaCoCo threshold) — puntare al 70%+

### Frontend (Vitest + vitest-axe)

```typescript
import { render, screen } from '@testing-library/react';
import { axe } from 'jest-axe';

it('renders without accessibility violations', async () => {
  const { container } = render(<NuovoComponente />);
  const results = await axe(container);
  expect(results).toHaveNoViolations();
});
```

Ogni component test **deve** includere il check `vitest-axe`.

---

## 10. CI — GitHub Actions

Il workflow `.github/workflows/ci.yml` esegue su ogni push/PR:

1. Build Gradle (tutti i microservizi)
2. JUnit 5 (backend)
3. JaCoCo coverage check (≥ 40%)
4. ESLint (frontend)
5. Vitest (frontend) con coverage thresholds
6. Playwright E2E (Docker stack)
7. Trivy image scan (CVE scanning)

Il PR non può essere mergiato se il CI fallisce.
