# Claims Transaction Processing System
## Design Document v2 — Senior Software Engineer Assessment

**Technology stack:** Java 23 · Spring Boot 3.x · Apache POI · OpenCSV · Thymeleaf · JUnit 5  
**Cloud target:** Microsoft Azure · AKS · Azure Cache for Redis · Azure SQL / Oracle MI  
**Author:** Candidate

---

## 1. Assumptions

| # | Assumption | Reasoning |
|---|---|---|
| A1 | Apache POI reads Excel dates as proper `LocalDate` via `cell.getLocalDateTimeCellValue().toLocalDate()`. Dates are stored as `LocalDate` throughout — never as serial numbers. | POI detects the cell's date format and converts automatically. Verified by reading the xlsx. |
| A2 | The "Individual accumulated deductible" in PolicyData is the YTD starting value before processing the sample transactions. Each transaction updates the running state in order. | Confirmed by tracing the expected output: Sam Collins starts at 0, grows each claim. |
| A3 | Family deductible accumulates across ALL policyholders sharing the same PolicyId. Individual accumulates per PolicyHolderId only. | Confirmed in expected output: Jina's payments add to the Collins family total alongside Sam's. |
| A4 | For Inpatient Hospital Care, the single coverage rule applies to all sub-services (ROOM AND BOARD, SURGERY, ANESTHESIA, etc.). Matched by mainCategory. | Stated in PlanCoverage notes tab. |
| A5 | "No Charge" coverage does NOT accumulate toward the deductible. Policyholder pays $0, nothing to accumulate. | Confirmed in expected output: preventive care (Sally Adams) leaves deductible unchanged. |
| A6 | Flat-dollar coverage: the policyholder's overage (billedAmount − flatAmount) accumulates toward both individual and family deductible. | Stated explicitly in spec and confirmed via Mack Lee expected output. |
| A7 | When a claim crosses the deductible threshold ("split payment"), the portion to deductible is applied first. The plan's percentage applies only to the overage amount. | Confirmed by expected output row with threshold crossing. |
| A8 | The REST API accepts deductible values from the caller (as spec states). In batch/GUI mode the system manages running state via Redis (or in-memory for local mode). | Stated explicitly in spec. |
| A9 | PolicyHolderID not found → E0001. No fallback to PolicyId lookup. | Matches expected output for holderId 1000016. |
| A10 | Claims with service date outside coverage dates (start inclusive, end inclusive if populated) → E0002. | Reasonable validation; spec says "consider other error scenarios." |
| A11 | Future-dated claims (post 2016-12-31 in context of this assignment) → E0004. | Spec explicitly calls out future-dated claims. |
| A12 | Coverage category matching is case-insensitive and trimmed. | Defensive coding for real-world CSV input variation. |
| A13 | BigDecimal for ALL monetary calculations. Scale=2, RoundingMode.HALF_UP. Never double or float. | Industry standard for financial systems. Double gives 0.1 + 0.2 ≠ 0.3. |
| A14 | Deductible Service is a separate microservice, not just a class, because the spec says "designed as a reusable service that can be exposed to other internal and external systems." | Direct quote from spec section 3C. |
| A15 | Batch processing pulls unprocessed claims FROM the existing Claims Oracle DB (not from a CSV on disk in production). The CronJob reads via JDBC. For the demo, CSV input is also supported. | Spec section 4: "Batch processing pulls claims from the Claims database." |
| A16 | The five existing databases (Plan, Policy, Claims, Prescription, Clinical) are read-only from this system, except Claims DB which receives processed results. Prescription and Clinical are out of scope for this assignment but wired as future integration points. | Spec section 4. |

---

## 2. Why Deductible Service Is a Separate Microservice

The spec states (section 3C):
> *"The accumulated deductible calculation capability should be designed as a **reusable service** that can be exposed to other internal and external systems."*

This is the key sentence. It means:

- **Claims Service** calls Deductible Service to check and update state for each claim
- **Other internal systems** (e.g. a member portal showing remaining deductible balance, a pre-authorization service) can call it independently
- **External partners** (third-party administrators, pharmacy benefit managers) may call it via API
- The service owns its own data (Redis for hot state, Policy DB for thresholds) — not shared with Claims Service

This is the correct reading. A private class inside Claims Service would not satisfy "reusable service exposed to other systems."

---

## 3. Architecture Summary

```
CLIENTS
  Browser (Thymeleaf GUI)
  Hospitals / Pharmacies (REST)
  Kubernetes CronJob (batch trigger)
        │
        ▼
  Azure API Management (auth · rate-limit · TLS termination)
        │
        ▼
  ┌──────────────────────────── AKS CLUSTER ────────────────────────────┐
  │                                                                      │
  │  gui-service (2 pods)        claims-service (2–20 pods, HPA)        │
  │  GuiController               ClaimsController                        │
  │  → calls claims-service      ClaimsEngine                           │
  │    via Feign client          ValidationService                       │
  │                              CoverageRuleParser (Strategy)           │
  │                              PlanRepository (Plan DB)                │
  │                              PolicyRepository (Policy DB)            │
  │                              → calls deductible-service              │
  │                                                                      │
  │  deductible-service (2–10 pods)    batch-processor (K8s CronJob)    │
  │  DeductibleController              ClaimsBatchRunner                  │
  │  DeductibleService                 reads Claims DB (unprocessed)     │
  │  RedisStateStore ─→ Redis          calls claims-service per record   │
  │  PolicyRepository (Policy DB)      writes results → Claims DB        │
  │                                    exports CSV → Blob Storage        │
  │                                                                      │
  │  Kubernetes objects: HPA · KEDA · CronJob · ConfigMap · Secret      │
  │  Istio service mesh: mTLS · circuit breaker · observability         │
  │  PodDisruptionBudget · Liveness · Readiness probes                  │
  └──────────────────────────────────────────────────────────────────────┘
        │                               │
        ▼                               ▼
  Azure Redis Cache             Azure Monitor + App Insights
  (YTD deductible state)        Azure Key Vault (secrets)
  Azure Blob Storage            Azure Container Registry (ACR)
  (output CSV · audit logs)     Azure DevOps (CI/CD · Helm deploy)

  EXISTING ORACLE DATABASES (on-prem → future: Azure SQL MI)
  Plan DB · Policy DB · Claims DB · Prescription DB · Clinical DB
  Connected via JDBC over ExpressRoute / VPN Gateway
```

---

## 4. Project Structure

```
claims-processor/                    ← mono-repo (3 services + shared lib)
├── claims-common/                   ← shared Maven module (published to ACR)
│   └── src/main/java/com/abc/claims/common/
│       ├── model/
│       │   ├── ClaimRequest.java    # Java 23 record
│       │   ├── ClaimResult.java     # Java 23 record
│       │   ├── Plan.java            # Java 23 record
│       │   └── PolicyHolder.java    # Java 23 record — LocalDate fields
│       ├── error/
│       │   └── ErrorCode.java       # E0001–E0004 enum
│       └── util/
│           └── MoneyUtils.java      # BigDecimal scale/rounding helpers
│
├── claims-service/                  ← Spring Boot 3.x microservice
│   └── src/main/java/com/abc/claims/service/
│       ├── ClaimsServiceApplication.java
│       ├── api/
│       │   ├── ClaimsController.java          # POST /api/v1/claims
│       │   └── dto/
│       │       ├── ClaimRequestDto.java        # @Valid annotations
│       │       └── ClaimResponseDto.java
│       ├── engine/
│       │   ├── ClaimsEngine.java              # @Service — orchestrator
│       │   ├── ValidationService.java         # E0001–E0004 checks
│       │   ├── CoverageRuleParser.java        # String → CoverageRule
│       │   ├── CoverageCalculator.java        # applies rule → PaymentResult
│       │   └── rule/
│       │       ├── CoverageRule.java          # interface
│       │       ├── PercentageRule.java        # 40%/60% after deductible
│       │       ├── NoChargeRule.java          # plan pays 100%
│       │       └── FlatDollarRule.java        # plan pays $N
│       ├── repository/
│       │   ├── PlanRepository.java            # reads Plan DB (Oracle JDBC)
│       │   ├── CoverageRepository.java        # reads Plan DB coverage rules
│       │   └── PolicyRepository.java          # reads Policy DB
│       ├── client/
│       │   └── DeductibleServiceClient.java   # Feign client → deductible-svc
│       ├── config/
│       │   └── DataLoaderConfig.java          # loads xlsx into cache on startup
│       └── exception/
│           └── GlobalExceptionHandler.java    # @ControllerAdvice, RFC 7807
│
├── deductible-service/              ← Spring Boot 3.x microservice (reusable)
│   └── src/main/java/com/abc/claims/deductible/
│       ├── DeductibleServiceApplication.java
│       ├── api/
│       │   └── DeductibleController.java      # POST /api/v1/deductible/check
│       │                                      # POST /api/v1/deductible/update
│       ├── service/
│       │   └── DeductibleService.java         # threshold logic + split payment
│       ├── store/
│       │   └── RedisStateStore.java           # ConcurrentHashMap (local)
│       │                                      # Azure Redis (cloud)
│       └── repository/
│           └── PolicyRepository.java          # reads thresholds from Policy DB
│
├── batch-processor/                 ← Spring Boot — Kubernetes CronJob pod
│   └── src/main/java/com/abc/claims/batch/
│       ├── BatchProcessorApplication.java
│       ├── ClaimsBatchRunner.java             # ApplicationRunner
│       ├── ClaimsServiceClient.java           # Feign → claims-service
│       ├── CsvClaimParser.java                # OpenCSV for demo/local mode
│       └── BlobStorageWriter.java             # Azure Blob SDK for output
│
├── gui-service/                     ← Spring Boot + Thymeleaf
│   └── src/main/java/com/abc/claims/gui/
│       ├── GuiController.java
│       ├── ClaimsServiceClient.java           # Feign → claims-service
│       └── resources/templates/
│           ├── index.html                     # file upload form
│           ├── results.html                   # results table + summary stats
│           └── dashboard.html                 # charts, error summary
│
├── helm/                            ← Helm charts (one per service)
│   ├── claims-service/
│   ├── deductible-service/
│   ├── batch-processor/             # kind: CronJob
│   └── gui-service/
│
└── k8s/                             ← Raw manifests (optional, Helm preferred)
    └── cronjob.yaml                 # schedule: "0 2 * * *"
```

---

## 5. Date Handling — Corrected

**All dates are `LocalDate` everywhere.** No serial numbers. No strings.

```java
// DataLoaderConfig.java — reading from xlsx on startup
LocalDate coverageStart = cell.getLocalDateTimeCellValue().toLocalDate();
LocalDate coverageEnd   = isBlank(cell) ? null : cell.getLocalDateTimeCellValue().toLocalDate();

// PolicyHolder record — stored as LocalDate
public record PolicyHolder(
    String policyId,
    String policyHolderId,
    String planId,
    LocalDate coverageStartDate,   // from Excel via POI — clean LocalDate
    LocalDate coverageEndDate,     // null = still active
    BigDecimal individualStartingDeductible,
    BigDecimal familyStartingDeductible
) {}

// ValidationService — clean readable validation
if (claimDate.isBefore(holder.coverageStartDate())) {
    return error("E0002", "Coverage not active on date of service");
}
if (holder.coverageEndDate() != null && claimDate.isAfter(holder.coverageEndDate())) {
    return error("E0002", "Coverage not active on date of service");
}
```

Why this works: Apache POI detects cells formatted as dates and returns a proper `LocalDateTime` via `getLocalDateTimeCellValue()`. No manual epoch math needed. The Excel UI shows `4/21/2016` because Excel renders the underlying date — POI reads that same date correctly.

---

## 6. Deductible Logic — Detailed

```
For each claim:

1. VALIDATE (ValidationService)
   PolicyHolder exists? → E0001
   Coverage dates valid for serviceDate? → E0002
   MainCategory + SubCategory known? → E0003
   ServiceDate in the future? → E0004

2. LOOKUP COVERAGE RULE (CoverageRepository → CoverageRuleParser)
   planId + mainCategory + subCategory → CoverageRule

3. CHECK DEDUCTIBLE (DeductibleService via Feign call)
   individualYTD = Redis.get("indiv:{holderId}")
   familyYTD     = Redis.get("family:{policyId}")
   deductibleMet = (individualYTD >= plan.individualThreshold)
                OR (familyYTD     >= plan.familyThreshold)

4. APPLY RULE (CoverageCalculator)

   IF NoChargeRule:
     planPays = billedAmount, holderPays = ZERO
     NO deductible update

   IF FlatDollarRule($N):
     planPays = $N, holderPays = billedAmount − $N
     Redis.INCR indivYTD and familyYTD by holderPays

   IF PercentageRule(pct) AND deductibleMet:
     planPays   = billedAmount × pct
     holderPays = billedAmount × (1 − pct)
     Redis.INCR indivYTD and familyYTD by holderPays

   IF PercentageRule(pct) AND NOT deductibleMet:
     remaining = MIN(threshold_indiv − indivYTD, threshold_family − familyYTD)
     IF billedAmount <= remaining:
       holderPays = billedAmount (100% to deductible)
       planPays   = ZERO
     ELSE (split payment — claim crosses threshold):
       overage    = billedAmount − remaining
       holderPays = remaining + (overage × (1 − pct))
       planPays   = overage × pct
     Redis.INCR indivYTD and familyYTD by holderPays

5. PERSIST result → Claims DB
6. RETURN ClaimResult
```

---

## 7. Kubernetes CronJob — Batch Processing

```yaml
# helm/batch-processor/templates/cronjob.yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: claims-batch-processor
spec:
  schedule: "0 2 * * *"        # 2am every night
  concurrencyPolicy: Forbid     # never run two at once
  startingDeadlineSeconds: 600
  jobTemplate:
    spec:
      backoffLimit: 2
      template:
        spec:
          restartPolicy: Never
          containers:
          - name: batch-processor
            image: acr.azurecr.io/batch-processor:latest
            env:
            - name: CLAIMS_SERVICE_URL
              value: http://claims-service:8081
            - name: SPRING_DATASOURCE_URL
              valueFrom:
                secretKeyRef:
                  name: oracle-secret
                  key: jdbc-url
```

**Batch flow:**
1. CronJob spawns a pod at 2am
2. Pod starts, `ClaimsBatchRunner.run()` fires
3. Reads all unprocessed claims from Claims DB: `SELECT * FROM CLAIMS WHERE PROCESSED = 'N'`
4. Processes in pages of 500, calls `claims-service` via Feign for each
5. Updates Claims DB: `UPDATE CLAIMS SET PROCESSED='Y', PLAN_PAYS=?, HOLDER_PAYS=? WHERE CLAIM_ID=?`
6. Exports full output CSV + audit log to Azure Blob Storage
7. Emits metrics to Azure Monitor (count, errors, duration)
8. Pod exits cleanly (exit code 0). Azure Monitor alerts if exit code ≠ 0

**Java 23 virtual threads** are used in the batch processor to parallelize the Feign HTTP calls to claims-service without blocking OS threads, giving high throughput at low memory cost.

---

## 8. REST API Design

### Claims Service — POST /api/v1/claims

**Request:**
```json
{
  "policyId": "100001",
  "policyHolderId": "1000011",
  "dateOfService": "2016-04-21",
  "coverageMainCategory": "Inpatient Hospital Care",
  "coverageSubCategory": "ROOM AND BOARD",
  "billedAmount": 1000.00,
  "individualAccumulatedDeductible": 0.00,
  "familyAccumulatedDeductible": 0.00
}
```

**Response (success):**
```json
{
  "policyHolderPays": 1000.00,
  "planPays": 0.00,
  "ruleUsed": "40% AFTER DEDUCTIBLE",
  "individualAccumulatedDeductible": 1000.00,
  "familyAccumulatedDeductible": 1000.00,
  "processingMessage": "ANNUAL DEDUCTIBLE not met, plan pays 0%"
}
```

### Deductible Service — POST /api/v1/deductible/check

```json
// Request
{ "policyHolderId": "1000011", "policyId": "100001", "billedAmount": 1000.00 }

// Response
{
  "deductibleMet": false,
  "remaining": 5000.00,
  "planPercentage": 0.40,
  "individualYtd": 1000.00,
  "familyYtd": 1000.00
}
```

### Error response (RFC 7807 ProblemDetail)
```json
{
  "type": "https://abc-health.com/errors/policy-not-found",
  "title": "Policy holder does not exist",
  "status": 422,
  "detail": "No policy holder found for ID 1000016",
  "instance": "/api/v1/claims",
  "errorCode": "E0001"
}
```

---

## 9. Key Design Decisions & Trade-offs

### 9.1 Why AKS over Azure Container Apps?

At 700k claims/day the system needs fine-grained control over:
- **HPA targets** — scale claims-service on CPU AND custom metrics (queue depth via KEDA)
- **CronJob scheduling** — Kubernetes CronJob is native; Container Apps has limited scheduling
- **Istio service mesh** — mTLS between microservices, distributed tracing, circuit breaker
- **Node pool tuning** — memory-optimised nodes for Redis client, CPU-optimised for claims engine
- Container Apps is a good fit for smaller or simpler workloads; at 700k/day with Oracle JDBC, AKS gives full control.

### 9.2 Why separate Deductible Service?

The spec explicitly says it must be "a reusable service that can be exposed to other internal and external systems." This rules out a shared library or a private class. Benefits:
- Member portal can call it to show remaining deductible balance
- Pre-authorisation service can call it before approving procedures
- Third-party administrators can call it via API Management
- Scales independently of Claims Service (reads are fast; writes go to Redis atomically)

### 9.3 BigDecimal — never float/double for money

`double` arithmetic on financial amounts causes precision errors. `0.1 + 0.2 = 0.30000000000000004` in floating point. BigDecimal with `HALF_UP` and scale=2 gives exact cents always. All monetary fields in records, DTOs, and DB columns are `BigDecimal`.

### 9.4 Redis for deductible state

- **Why not Oracle for YTD?** Each claim needs a read + write of YTD. At 700k/day (≈8 claims/sec average, spikes to 50+/sec) an Oracle round-trip per claim for deductible state creates a bottleneck. Redis INCR is atomic, sub-millisecond, and scales horizontally.
- **Source of truth:** At end of day (or on CronJob start), Redis is synced to Policy DB so the DB is always authoritative.

### 9.5 Strategy pattern for coverage rules

`CoverageRule` interface → `PercentageRule`, `NoChargeRule`, `FlatDollarRule`. Adding a new rule type (e.g. copay, tiered percentage) requires one new class and one new branch in `CoverageRuleParser`. The engine never changes. Classic Open/Closed principle.

### 9.6 LocalDate for all dates

Dates come from Excel via `cell.getLocalDateTimeCellValue().toLocalDate()`. Stored as `LocalDate` in records, DTOs, and the API (ISO-8601 string `"2016-04-21"` in JSON via `@JsonFormat`). No serial number math, no timezone issues.

---

## 10. Non-Functional Requirements

### Performance
- 700k/day ≈ 8 claims/sec average, 50+ claims/sec at peak (end of business day)
- Claims Service: HPA 2–20 pods on CPU + custom metric (Service Bus queue depth via KEDA)
- Deductible Service: HPA 2–10 pods (reads heavy)
- Redis GET/INCR: <1ms
- Plan/Policy DB reads: cached in Spring (Caffeine cache, TTL 5min) — reference data changes rarely
- Oracle JDBC: HikariCP pool-size 20 per pod
- Java 23 virtual threads in batch for I/O-heavy parallel Feign calls

### Reliability
- Kubernetes CronJob: `backoffLimit: 2`, `concurrencyPolicy: Forbid`
- Resilience4j: circuit breaker on Deductible Service calls from Claims Service (fallback: use caller-supplied deductible values)
- Retry: 3 attempts with exponential backoff on transient JDBC failures
- Error rows do not abort the batch — logged to dead-letter table, processing continues
- Idempotency key = claimId on all writes to Claims DB

### Security
- Azure API Management: OAuth2 / JWT validation for external callers
- Istio mTLS: all pod-to-pod communication encrypted inside the cluster
- Managed Identity: AKS workload identity → Key Vault → DB credentials (no secrets in YAML)
- @Valid on all DTO fields; malformed input rejected at controller boundary

### Observability
- SLF4J + MDC: every log line carries `correlationId = claimId`
- Micrometer → Azure Application Insights: claims/sec, error rate, p99 latency per endpoint
- Azure Monitor alerts: batch job failure, error rate > 1%, latency > 500ms p99
- Distributed tracing: Istio + Application Insights (traceparent propagated across services)

---

## 11. Test Strategy

| Test | What | Tool |
|---|---|---|
| `CoverageRuleParserTest` | All rule strings, edge cases | JUnit 5 + AssertJ |
| `DeductibleServiceTest` | Individual met, family met, neither, split payment, boundary exact threshold | JUnit 5 |
| `ValidationServiceTest` | Each error code triggered, valid claim passes | JUnit 5 |
| `ClaimsEngineTest` | Full claim-to-result, each sample row (golden path) | JUnit 5 |
| `BatchProcessorTest` | Full CSV in → expected CSV out, compares row-by-row | JUnit 5 + MockServer |
| `DeductibleControllerTest` | REST contract test on /deductible/check | Spring MockMvc |
| `ClaimsControllerTest` | REST contract test on /api/v1/claims | Spring MockMvc |

Additional edge cases:
- Claim exactly at the threshold (boundary)
- Family deductible met before individual
- Coverage end date = service date (inclusive boundary)
- billedAmount = $0.00
- Malformed billedAmount in CSV input
- Empty CSV file / header-only CSV
- Future-dated service date

---

## 12. Cloud Migration Path

```
Phase 1 (current assignment demo):
  Monolith JAR → xlsx in-memory → CSV output

Phase 2 (cloud foundation):
  AKS cluster provisioned via Terraform
  ACR for container images
  Azure Redis Cache (Basic tier → Standard for HA)
  Azure DevOps pipeline: build → test → push → Helm upgrade

Phase 3 (existing DB integration):
  Oracle DB connected via Azure ExpressRoute or VPN Gateway
  OR migrated to Azure SQL Managed Instance (full Oracle compatibility)
  Spring JdbcTemplate → HikariCP → Oracle
  Secret rotation via Key Vault + managed identity

Phase 4 (production hardening):
  Multi-region AKS (East US + West US) with Azure Traffic Manager
  Azure Service Bus for claim event streaming (async, DLQ for failures)
  KEDA autoscaler on Service Bus queue depth
  Azure Backup for Redis snapshots
  Azure Policy + Defender for Cloud (HIPAA compliance)
```

---

## 13. Interview Talking Points

**"Why is Deductible Service separate?"**
> The spec says it directly: "designed as a reusable service that can be exposed to other internal and external systems." A shared library wouldn't satisfy that. The member portal, pre-auth service, and third-party admins all need to call it independently. It owns its own data (Redis + Policy DB). It scales independently.

**"Why AKS not Container Apps?"**
> 700k/day needs KEDA for queue-based autoscaling, native CronJob for batch, Istio for mTLS between services, and fine-grained node pool control for Oracle JDBC connection management. Container Apps abstracts those away. AKS gives full Kubernetes control at this scale.

**"How does batch processing work?"**
> Kubernetes CronJob at 2am spawns a pod. ClaimsBatchRunner reads unprocessed rows from Claims Oracle DB (STATUS='N'), processes each via Feign call to claims-service, writes results back to Claims DB, exports CSV to Blob Storage, emits metrics, exits. Java 23 virtual threads handle the parallel Feign calls without blocking OS threads.

**"Why Redis for deductible state?"**
> Each claim needs a read + atomic write of YTD deductible. At 50 claims/sec peak, Oracle round-trips per claim create a bottleneck. Redis INCR is atomic, <1ms, horizontally scalable. Oracle stays as source of truth — Redis is the hot cache for in-flight state.

**"How do you handle the 5 existing databases?"**
> Claims Service reads Plan DB (coverage rules, thresholds) and Policy DB (policyholder coverage dates). Deductible Service reads Policy DB for thresholds. Batch Processor reads/writes Claims DB. Prescription and Clinical are future integration points — architecture supports them via new repositories without engine changes. All via JDBC + HikariCP over ExpressRoute.

**"What if Deductible Service is down?"**
> Resilience4j circuit breaker on the Feign call in Claims Service. Fallback: use the caller-supplied deductible values (the spec already says the API accepts these). The fallback degrades gracefully — no claim processing is blocked.
