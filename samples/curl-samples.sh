#!/usr/bin/env bash
# Claims Processor — curl smoke tests (bash / Git Bash / WSL / Linux / macOS)
#
# Prerequisites:
#   1. App running:  mvn spring-boot:run
#   2. curl + jq (jq optional — used for pretty output)
#
# Usage:
#   ./curl-samples.sh                 # run all scenarios
#   BASE_URL=http://host:8080 ./curl-samples.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/claims-svc}"
API="${BASE_URL}/api/v1/claims"
GUI="${BASE_URL}/process"
AUTH="${AUTH:-processor:claims123}"

pretty() {
  if command -v jq >/dev/null 2>&1; then jq .; else cat; fi
}

call() {
  local title="$1"; shift
  local body="$1"; shift
  echo
  echo "==================================================================="
  echo "  $title"
  echo "==================================================================="
  curl -sS -u "$AUTH" -X POST "$API" \
    -H 'Content-Type: application/json' \
    -H 'Accept: application/json' \
    -d "$body" | pretty
}

call "1) Happy path — 40% AFTER DEDUCTIBLE (deductible already met)" '{
  "policyId": "100001",
  "policyHolderId": "1000011",
  "dateOfService": "2016-05-08",
  "coverageMainCategory": "Inpatient Hospital Care",
  "coverageSubCategory": "ROOM AND BOARD",
  "billedAmount": 1000,
  "individualAccumulatedDeductible": 6000,
  "familyAccumulatedDeductible": 6000
}'

call "2) No Charge — preventive care fully covered" '{
  "policyId": "100002",
  "policyHolderId": "1000021",
  "dateOfService": "2016-06-15",
  "coverageMainCategory": "Preventive Care",
  "coverageSubCategory": "ROUTINE PHYSICAL EXAM",
  "billedAmount": 350,
  "individualAccumulatedDeductible": 4000,
  "familyAccumulatedDeductible": 4000
}'

call "3) Flat dollar — plan pays \$120 regardless of deductible" '{
  "policyId": "100007",
  "policyHolderId": "1000071",
  "dateOfService": "2016-07-10",
  "coverageMainCategory": "Emergency And Urgent Care",
  "coverageSubCategory": "URGENT CARE VISIT",
  "billedAmount": 250,
  "individualAccumulatedDeductible": 4460.82,
  "familyAccumulatedDeductible": 4460.82
}'

call "4) E0001 — unknown policy holder" '{
  "policyId": "100001",
  "policyHolderId": "9999999",
  "dateOfService": "2016-10-12",
  "coverageMainCategory": "Prescription Drugs",
  "coverageSubCategory": "GENERIC",
  "billedAmount": 61.4
}'

call "5) E0002 — coverage not active on date of service" '{
  "policyId": "100004",
  "policyHolderId": "1000041",
  "dateOfService": "2015-12-31",
  "coverageMainCategory": "Outpatient Services",
  "coverageSubCategory": "PRIMARY CARE OFFICE VISIT",
  "billedAmount": 150
}'

call "6) E0003 — unknown service sub-category" '{
  "policyId": "100002",
  "policyHolderId": "1000021",
  "dateOfService": "2016-06-15",
  "coverageMainCategory": "Preventive Care",
  "coverageSubCategory": "UNKNOWN SUBCATEGORY XYZ",
  "billedAmount": 350
}'

call "7) E0004 — future-dated claim rejected" '{
  "policyId": "100002",
  "policyHolderId": "1000021",
  "dateOfService": "2017-01-15",
  "coverageMainCategory": "Outpatient Services",
  "coverageSubCategory": "LAB TESTS",
  "billedAmount": 1200
}'

call "8) E0005 — malformed claim (blank IDs, negative amount) returned as 200/E0005" \
  '{"policyId":"","policyHolderId":"","billedAmount":-10}'

call "9) E0005 — malformed claim (missing date of service)" '{
  "policyId": "100001",
  "policyHolderId": "1000011",
  "coverageMainCategory": "Inpatient Hospital Care",
  "coverageSubCategory": "ROOM AND BOARD",
  "billedAmount": 1000
}'

echo
echo "==================================================================="
echo "  10) GUI — upload CSV (multipart) → HTML results"
echo "==================================================================="
SAMPLE_CSV="${SAMPLE_CSV:-samples/sample-transactions.csv}"
if [ -f "$SAMPLE_CSV" ]; then
  curl -sS -u "$AUTH" -o results.html -w "HTTP %{http_code}  saved → results.html\n" \
    -F "file=@${SAMPLE_CSV}" "$GUI"
else
  echo "  (Skipped — '${SAMPLE_CSV}' not found. Set SAMPLE_CSV=... to override.)"
fi

echo
echo "Done."
