# Claims Processor - curl smoke tests (PowerShell on Windows)
#
# Prerequisites:
#   1. App running:  mvn spring-boot:run
#   2. curl.exe (ships with Windows 10+ as C:\Windows\System32\curl.exe).
#      DO NOT use PowerShell's built-in 'curl' alias - that's Invoke-WebRequest.
#
# Usage:
#   .\curl-samples.ps1
#   .\curl-samples.ps1 -BaseUrl http://host:8080

[CmdletBinding()]
param(
    [string]$BaseUrl    = "http://localhost:8080/claims-svc",
    [string]$SampleCsv  = "samples/sample-transactions.csv",
    [string]$User       = "processor",
    [string]$Password   = "claims123"
)

$Api = "$BaseUrl/api/v1/claims"
$Gui = "$BaseUrl/process"
$Auth = "$User`:$Password"

function Invoke-Claim {
    param([string]$Title, [string]$Body)
    Write-Host ""
    Write-Host "==================================================================="
    Write-Host "  $Title"
    Write-Host "==================================================================="
    $tmp = New-TemporaryFile
    try {
        Set-Content -Path $tmp -Value $Body -Encoding utf8
        & curl.exe -sS -u $Auth -X POST $Api `
            -H "Content-Type: application/json" `
            -H "Accept: application/json" `
            --data-binary "@$tmp"
        Write-Host ""
    } finally {
        Remove-Item $tmp -ErrorAction SilentlyContinue
    }
}

Invoke-Claim "1) Happy path - 40% AFTER DEDUCTIBLE (deductible already met)" @'
{
  "policyId": "100001",
  "policyHolderId": "1000011",
  "dateOfService": "2016-05-08",
  "coverageMainCategory": "Inpatient Hospital Care",
  "coverageSubCategory": "ROOM AND BOARD",
  "billedAmount": 1000,
  "individualAccumulatedDeductible": 6000,
  "familyAccumulatedDeductible": 6000
}
'@

Invoke-Claim "2) No Charge - preventive care fully covered" @'
{
  "policyId": "100002",
  "policyHolderId": "1000021",
  "dateOfService": "2016-06-15",
  "coverageMainCategory": "Preventive Care",
  "coverageSubCategory": "ROUTINE PHYSICAL EXAM",
  "billedAmount": 350,
  "individualAccumulatedDeductible": 4000,
  "familyAccumulatedDeductible": 4000
}
'@

Invoke-Claim "3) Flat dollar - plan pays \$120 regardless of deductible" @'
{
  "policyId": "100007",
  "policyHolderId": "1000071",
  "dateOfService": "2016-07-10",
  "coverageMainCategory": "Emergency And Urgent Care",
  "coverageSubCategory": "URGENT CARE VISIT",
  "billedAmount": 250,
  "individualAccumulatedDeductible": 4460.82,
  "familyAccumulatedDeductible": 4460.82
}
'@

Invoke-Claim "4) E0001 - unknown policy holder" @'
{
  "policyId": "100001",
  "policyHolderId": "9999999",
  "dateOfService": "2016-10-12",
  "coverageMainCategory": "Prescription Drugs",
  "coverageSubCategory": "GENERIC",
  "billedAmount": 61.4
}
'@

Invoke-Claim "5) E0002 - coverage not active on date of service" @'
{
  "policyId": "100004",
  "policyHolderId": "1000041",
  "dateOfService": "2015-12-31",
  "coverageMainCategory": "Outpatient Services",
  "coverageSubCategory": "PRIMARY CARE OFFICE VISIT",
  "billedAmount": 150
}
'@

Invoke-Claim "6) E0003 - unknown service sub-category" @'
{
  "policyId": "100002",
  "policyHolderId": "1000021",
  "dateOfService": "2016-06-15",
  "coverageMainCategory": "Preventive Care",
  "coverageSubCategory": "UNKNOWN SUBCATEGORY XYZ",
  "billedAmount": 350
}
'@

Invoke-Claim "7) E0004 - future-dated claim rejected" @'
{
  "policyId": "100002",
  "policyHolderId": "1000021",
  "dateOfService": "2017-01-15",
  "coverageMainCategory": "Outpatient Services",
  "coverageSubCategory": "LAB TESTS",
  "billedAmount": 1200
}
'@

Write-Host ""
Write-Host "==================================================================="
Write-Host "  8) E0005 - malformed claim (blank IDs, negative amount) returned as 200/E0005"
Write-Host "==================================================================="
Invoke-Claim "8) E0005 - malformed claim (blank IDs, negative amount)" @'
{"policyId":"","policyHolderId":"","billedAmount":-10}
'@

Invoke-Claim "9) E0005 - malformed claim (missing date of service)" @'
{
  "policyId": "100001",
  "policyHolderId": "1000011",
  "coverageMainCategory": "Inpatient Hospital Care",
  "coverageSubCategory": "ROOM AND BOARD",
  "billedAmount": 1000
}
'@

Write-Host ""
Write-Host "==================================================================="
Write-Host "  10) GUI - upload CSV (multipart) -> HTML results"
Write-Host "==================================================================="
if (Test-Path $SampleCsv) {
    & curl.exe -sS -u $Auth -o results.html -w "HTTP %{http_code}  saved -> results.html`n" `
        -F "file=@$SampleCsv" $Gui
} else {
    Write-Host "  (Skipped - '$SampleCsv' not found. Use -SampleCsv path\to\file.csv to override.)"
}

Write-Host ""
Write-Host "Done."
