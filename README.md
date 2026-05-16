# Claims Transaction Processing System

ABC Health Insurance — claims processor demo for Senior Software Engineer assessment.

**Stack:** Java 25 · Spring Boot 3.4.5 · Apache POI · OpenCSV · Thymeleaf · Spring Security · Micrometer/Prometheus · springdoc-openapi · Lombok · JUnit 5 · Angular 18 · Bootstrap 5

---

## 1. What it does

Processes medical-insurance claim transactions in **three operating modes**:

| Mode | Entry point | Use case |
|------|-------------|----------|
| **Batch CSV** | `java -jar … batch in.csv out.csv` | Nightly bulk reprocessing |
| **REST API** | `POST /claims-svc/api/v1/claims` | Real-time adjudication from partners |
| **GUI** | `http://localhost:8080/claims-svc/` (Thymeleaf) or `http://localhost:4200` (Angular) | Operator-driven upload + form testing |

All three modes share the same core `ClaimsEngine`, which applies coverage rules from the plan/coverage tables and tracks individual + family deductibles.

---

## 2. Prerequisites

- **JDK 25** (configured: `C:\Users\LSikhara\OneDrive - Alameda County\work\jdk-25.0.3+9`)
- **Apache Maven 3.9.x** (configured: `C:\Users\LSikhara\OneDrive - Alameda County\work\apache-maven-3.9.11`)
- **Node 18+** (for Angular frontend; optional)

```powershell
$env:JAVA_HOME = "C:\Users\LSikhara\OneDrive - Alameda County\work\jdk-25.0.3+9"
$env:Path      = "$env:JAVA_HOME\bin;C:\Users\LSikhara\OneDrive - Alameda County\work\apache-maven-3.9.11\bin;$env:Path"
java -version
mvn -v
```

---

## 3. Build & run

```bash
# Build (skip tests for speed)
mvn -q -DskipTests package

# Run with embedded Tomcat
mvn spring-boot:run

# Or run the fat jar
java -jar target/claims-processor-1.0.0.jar
```

**Context root:** `/claims-svc` (configured in `application.yml`). All URLs below assume this prefix.

| What | URL |
|------|-----|
| Thymeleaf GUI | http://localhost:8080/claims-svc/ |
| REST endpoint | http://localhost:8080/claims-svc/api/v1/claims |
| File upload | http://localhost:8080/claims-svc/process |
| Swagger UI | http://localhost:8080/claims-svc/swagger-ui.html |
| OpenAPI spec | http://localhost:8080/claims-svc/v3/api-docs |
| Actuator health | http://localhost:8080/claims-svc/actuator/health |
| Prometheus scrape | http://localhost:8080/claims-svc/actuator/prometheus |

---

## 4. Security (HTTP Basic)

| User | Password | Roles |
|------|----------|-------|
| `admin` | `admin123` | `ADMIN`, `PROCESSOR` |
| `processor` | `claims123` | `PROCESSOR` |

- `/api/**`, `/`, `/process/**` → require `PROCESSOR`
- `/actuator/health`, `/actuator/info`, `/actuator/prometheus`, `/swagger-ui/**`, `/v3/api-docs/**` → public
- All other `/actuator/**` → require `ADMIN`

---

## 5. Quick smoke test

### Batch mode (CLI)

```bash
mvn -q -DskipTests package
java -jar target/claims-processor-1.0.0.jar batch samples/input-claims.csv samples/output-generated.csv
```

### REST mode (curl)

```bash
# Bash / Git Bash / WSL
./samples/curl-samples.sh

# Or PowerShell
.\samples\curl-samples.ps1
```

Single happy-path call:

```bash
curl -u processor:claims123 -X POST http://localhost:8080/claims-svc/api/v1/claims \
  -H "Content-Type: application/json" \
  -d '{
    "policyId":"100001","policyHolderId":"1000011",
    "dateOfService":"2016-05-08",
    "coverageMainCategory":"Inpatient Hospital Care",
    "coverageSubCategory":"ROOM AND BOARD",
    "billedAmount":1000,
    "individualAccumulatedDeductible":6000,
    "familyAccumulatedDeductible":6000
  }'
```

### Postman

Import `samples/claims-processor.postman_collection.json`. Collection-level Basic Auth uses `{{username}}` / `{{password}}` vars (defaulted to `processor` / `claims123`). Base URL var is `http://localhost:8080`; each request prefixes `/claims-svc`.

### GUI (Thymeleaf)

1. Open http://localhost:8080/claims-svc/
2. Sign in with `processor / claims123`
3. Upload `samples/sample-transactions.csv`
4. View results table; click **Download CSV** to export

### GUI (Angular)

```bash
cd frontend-angular
npm install
npm start    # serves http://localhost:4200, proxies /claims-svc to :8080
```

Sidebar nav: **Dashboard · REST API · Upload CSV · About**

---

## 6. Sample files

| File | Purpose |
|------|---------|
| `samples/input-claims.csv` | Assignment's "SampleTransactions" sheet exported |
| `samples/sample-transactions.csv` | GUI/CLI happy-path upload |
| `samples/sample-transactions-with-errors.csv` | E0001–E0005 coverage (4 error codes + malformed) |
| `samples/output-claims.csv` | Reference expected output |
| `samples/rest-request-samples.json` | One JSON payload per coverage mode |
| `samples/curl-samples.sh` / `.ps1` | All scenarios scripted |
| `samples/claims-processor.postman_collection.json` | Importable Postman collection |

---

## 7. Test & coverage

```bash
mvn verify
```

- **70 JUnit 5 tests** — controller, engine, parser, security, audit
- **JaCoCo gate: 95% INSTRUCTION coverage**; current bundle ≈ **98%**
- Report: `target/site/jacoco/index.html`

---

## 8. Observability

```bash
# Liveness
curl -u processor:claims123 http://localhost:8080/claims-svc/actuator/health

# Prometheus metrics — including custom claims_processed_total{channel,status}
curl http://localhost:8080/claims-svc/actuator/prometheus

# Loggers (ADMIN only)
curl -u admin:admin123 http://localhost:8080/claims-svc/actuator/loggers
```

**Audit log line example** (Logback pattern includes `[%X{user}]` MDC):

```
2026-05-16 14:11:22.341 INFO  [processor] [http-nio-8080-exec-3] c.a.claims.audit.ClaimAuditLogger - AUDIT user=processor channel=REST policy=100001 holder=1000011 rule=40% AFTER DEDUCTIBLE planPays=400 holderPays=600 status=OK
```

**Log files** — Logs are written to **both console and disk**:

| File | Contents | Rollover |
|---|---|---|
| `logs/claims-processor.log` | All application logs (INFO+) | Daily + 10 MB per file, 30-day history, 1 GB total cap (gzipped) |
| `logs/claims-audit.log` | Only `ClaimAuditLogger` lines — one per processed claim across REST/CSV/GUI/BATCH | Daily + 10 MB per file, 90-day history, 2 GB total cap (gzipped) |

Override the directory by setting `LOG_PATH=/var/log/claims` (env var) or `--logging.file.path=/var/log/claims` (CLI). The audit appender honours the same `LOG_PATH` so compliance logs follow the main directory.

---

## 9. Project layout

```
claims-txn-processing-system/
├── pom.xml
├── README.md                              ← you are here
├── ClaimsProcessor_DesignDocument.md      ← assumptions, diagrams, Azure future-state
├── ClaimsProcessing_Assignment.docx       ← original assignment
├── SamplePlanAndTransactionData_1.xlsx    ← source data
├── src/
│   ├── main/java/com/abc/claims/
│   │   ├── api/                ← REST controllers
│   │   ├── audit/              ← ClaimAuditLogger
│   │   ├── batch/              ← BatchProcessor, CLI runner
│   │   ├── config/             ← OpenApiConfig, WorkbookDataLoader
│   │   ├── dao/                ← Repository interfaces + in-memory + Oracle/Mongo/Redis stubs
│   │   ├── domain/             ← Java records (DTOs, entities)
│   │   ├── engine/             ← ClaimsEngine, rule strategies, deductible service
│   │   ├── security/           ← SecurityConfig (HTTP Basic + role rules)
│   │   └── web/                ← GuiController, Thymeleaf model
│   ├── main/resources/
│   │   ├── application.yml
│   │   └── templates/          ← Thymeleaf views (Bootstrap)
│   └── test/java/…             ← 70 JUnit 5 tests
├── frontend-angular/           ← Angular 18 standalone app (sidebar + dashboard)
└── samples/                    ← CSV, JSON, curl, Postman
```

---

## 10. Where to go next

- **Design rationale, diagrams, trade-offs:** `ClaimsProcessor_DesignDocument.md`
- **Azure future-state architecture + sequence flows:** see § "Future State — Azure" in the design doc
