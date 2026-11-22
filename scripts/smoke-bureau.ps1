param(
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $Username = 'analyst',
    [string] $Password = 'local_demo_only_change_me',
    [string] $ExpectedEnvironment = 'MOCK',
    [string] $SecondCase = 'CLEAN'
)

$ErrorActionPreference = 'Stop'
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${Username}:${Password}"))
$headers = @{ Authorization = "Basic $auth"; 'X-Correlation-ID' = 'smoke-bureau-demo' }
$identifier = 'DEMO-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$proposal = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/proposals" -Headers $headers -ContentType 'application/json' -Body (@{
    applicant = @{ name = 'Bureau Demo'; syntheticIdentifier = $identifier }
    declaredMonthlyIncome = 8000.00
    currency = 'BRL'
} | ConvertTo-Json -Depth 4)
$base = "$BaseUrl/api/v1/proposals/$($proposal.id)"
$initial = Invoke-RestMethod -Uri "$base/bureau-consultations/latest" -Headers $headers
if ($initial.id -or $initial.report.status -ne 'NOT_REQUESTED') { throw 'Initial bureau status is incorrect' }

$first = Invoke-RestMethod -Method Post -Uri "$base/bureau-consultations" -Headers $headers -ContentType 'application/json' -Body '{"caseName":"COMPLETE"}'
if ($first.report.environment -ne $ExpectedEnvironment -or $first.report.status -ne 'AVAILABLE' -or
    $first.report.referenceId -ne $first.consultationId) { throw 'Bureau consultation mapping failed' }
if ($ExpectedEnvironment -eq 'SANDBOX' -and $null -ne $first.report.restrictions) {
    throw 'Sandbox restriction count was invented'
}
$fixture = (Resolve-Path (Join-Path $PSScriptRoot '../fixtures/documents/payslip-2026-08-demo.pdf')).Path
$uploaded = (& curl.exe --silent --show-error --user "${Username}:${Password}" --form 'type=PAYSLIP' --form 'period=2026-08' --form "file=@$fixture;type=application/pdf" "$base/documents") | ConvertFrom-Json
if (-not $uploaded.id) { throw 'Synthetic payslip upload failed' }
$scenario = @{ principalRequested = $null; termMonths = 36; monthlyInterestRate = 0.02;
    currency = 'BRL'; pricingSource = 'MANUAL_DEMO' } | ConvertTo-Json
$analysisHeaders = $headers.Clone()
$analysisHeaders['Idempotency-Key'] = 'bureau-' + [guid]::NewGuid().ToString('N')
$analysis = Invoke-RestMethod -Method Post -Uri "$base/analyses" -Headers $analysisHeaders -ContentType 'application/json' -Body $scenario
if ($analysis.financialAssessment.bureau.referenceId -ne $first.consultationId -or
    $analysis.financialAssessment.bureau.environment -ne $ExpectedEnvironment) {
    throw 'Analysis did not bind bureau snapshot'
}

$second = Invoke-RestMethod -Method Post -Uri "$base/bureau-consultations" -Headers $headers -ContentType 'application/json' -Body (@{ caseName = $SecondCase } | ConvertTo-Json)
if ($SecondCase -eq 'UNAVAILABLE' -and ($second.report.status -ne 'UNAVAILABLE' -or $null -ne $second.report.restrictions)) {
    throw 'Unavailable bureau response was misrepresented'
}
$decisionBody = @{
    analysisId = $analysis.analysisId; documentSnapshotHash = $analysis.documentSnapshotHash;
    outcome = 'MORE_INFO_REQUIRED'; reason = 'Revisar novo contexto sintético de bureau antes da decisão.'
} | ConvertTo-Json
try {
    Invoke-RestMethod -Method Post -Uri "$base/decisions" -Headers $headers -ContentType 'application/json' -Body $decisionBody | Out-Null
    throw 'Stale bureau snapshot allowed decision'
} catch {
    if (-not $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 409) { throw }
}
$analysisHeaders['Idempotency-Key'] = 'bureau-' + [guid]::NewGuid().ToString('N')
$newAnalysis = Invoke-RestMethod -Method Post -Uri "$base/analyses" -Headers $analysisHeaders -ContentType 'application/json' -Body $scenario
if ($newAnalysis.analysisId -eq $analysis.analysisId -or
    $newAnalysis.financialAssessment.bureau.referenceId -ne $second.consultationId) {
    throw 'New bureau snapshot did not produce a new analysis'
}
Write-Host "PASS bureau=$ExpectedEnvironment consultation, versioned dossier, stale decision=409, new analysis=$($newAnalysis.analysisId)"
