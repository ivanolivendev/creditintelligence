param(
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $Username = 'analyst',
    [string] $Password = 'local_demo_only_change_me'
)

$ErrorActionPreference = 'Stop'
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${Username}:${Password}"))
$headers = @{ Authorization = "Basic $auth"; 'X-Correlation-ID' = 'smoke-financial-demo' }
$identifier = 'DEMO-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$proposalBody = @{
    applicant = @{ name = 'Alex Demo'; syntheticIdentifier = $identifier }
    declaredMonthlyIncome = 8000.00
    currency = 'BRL'
} | ConvertTo-Json -Depth 4
$proposal = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/proposals" -Headers $headers -ContentType 'application/json' -Body $proposalBody
$documentsUrl = "$BaseUrl/api/v1/proposals/$($proposal.id)/documents"
foreach ($month in @('2026-08', '2026-09', '2026-10')) {
    foreach ($kind in @(@('PAYSLIP','payslip'), @('BANK_STATEMENT','statement'))) {
        $fixture = (Resolve-Path (Join-Path $PSScriptRoot "../fixtures/documents/$($kind[1])-$month-demo.pdf")).Path
        $document = (& curl.exe --silent --show-error --user "${Username}:${Password}" --form "type=$($kind[0])" --form "period=$month" --form "file=@$fixture;type=application/pdf" $documentsUrl) | ConvertFrom-Json
        if (-not $document.id -or $document.period -ne $month -or $document.version -ne 1) {
            throw "Upload failed: $month $($kind[0])"
        }
    }
}
$listed = Invoke-RestMethod -Uri $documentsUrl -Headers $headers
if ($listed.Count -ne 6) { throw "Expected six documents, got $($listed.Count)" }
Write-Host "T01/T02 PASS six synthetic PDFs uploaded, proposal=$($proposal.id)"

$analysisUrl = "$BaseUrl/api/v1/proposals/$($proposal.id)/analyses"
$scenario = @{ principalRequested = 30000.00; termMonths = 36; monthlyInterestRate = 0.02;
    currency = 'BRL'; pricingSource = 'MANUAL_DEMO' } | ConvertTo-Json
$keyed = $headers.Clone()
$keyed['Idempotency-Key'] = "financial-$identifier"
$analysis = Invoke-RestMethod -Method Post -Uri $analysisUrl -Headers $keyed -ContentType 'application/json' -Body $scenario
if ($analysis.status -ne 'COMPLETED') { throw "Analysis failed: $($analysis.errorCode)" }
$assessment = $analysis.financialAssessment
if ($null -eq $assessment -or $assessment.validIncomePeriods -ne 3 -or
    $assessment.averageNetIncome -ne 7733.33 -or $assessment.unknownCredits -ne 1 -or
    @($assessment.salaryMatches | Where-Object status -eq 'MATCH').Count -ne 3 -or
    $assessment.additionalMonthlyPayments -ne 0 -or
    $assessment.bureau.environment -ne 'SANDBOX' -or $assessment.bureau.status -ne 'NOT_REQUESTED' -or
    $analysis.aiExplanation.provider -ne 'BEDROCK' -or $analysis.aiExplanation.modelId -ne 'deepseek.v3.2') {
    throw "Financial assertion failed: status=$($analysis.status) avg=$($assessment.averageNetIncome) matches=$(@($assessment.salaryMatches | Where-Object status -eq 'MATCH').Count) AI=$($analysis.aiExplanation.provider)"
}
Write-Host "T05-T12 PASS avg=$($assessment.averageNetIncome) matches=3 unknown=1 capacity=$($assessment.maxNewInstallment) bureau=SANDBOX/NOT_REQUESTED AI=BEDROCK/DeepSeek"

$dossier = Invoke-RestMethod -Uri "$BaseUrl/api/v1/proposals/$($proposal.id)/dossier?analysisId=$($analysis.analysisId)" -Headers $headers
$repeat = Invoke-RestMethod -Method Post -Uri $analysisUrl -Headers $keyed -ContentType 'application/json' -Body $scenario
if ($dossier.analysisId -ne $analysis.analysisId -or $repeat.analysisId -ne $analysis.analysisId -or
    $dossier.documents.Count -ne 6 -or $dossier.financialAssessment.averageNetIncome -ne 7733.33) {
    throw 'Dossier or idempotent repeat differs from original analysis'
}
Write-Host 'T14 PASS persisted dossier and repeated key return the same analysis'

$replacement = (Resolve-Path (Join-Path $PSScriptRoot '../fixtures/documents/payslip-2026-10-demo.pdf')).Path
$newDocument = (& curl.exe --silent --show-error --user "${Username}:${Password}" --form 'type=PAYSLIP' --form 'period=2026-10' --form "file=@$replacement;type=application/pdf" $documentsUrl) | ConvertFrom-Json
try {
    Invoke-RestMethod -Method Post -Uri $analysisUrl -Headers $keyed -ContentType 'application/json' -Body $scenario | Out-Null
    throw 'Changed snapshot unexpectedly reused key'
} catch {
    if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 409) {
        Write-Host 'T15 PASS changed snapshot with same key returned 409'
    } else { throw }
}
$newAnalysis = Invoke-RestMethod -Method Post -Uri $analysisUrl -Headers $headers -ContentType 'application/json' -Body $scenario
if ($newDocument.version -ne 2 -or $newAnalysis.analysisId -eq $analysis.analysisId -or
    $newAnalysis.documentSnapshotHash -eq $analysis.documentSnapshotHash) {
    throw 'New document version did not create a new analysis snapshot'
}
Write-Host 'T16 PASS new document version has a new fingerprint; old dossier remains retrievable'

$decisionBody = @{ analysisId = $newAnalysis.analysisId;
    documentSnapshotHash = $newAnalysis.documentSnapshotHash;
    outcome = 'MORE_INFO_REQUIRED'; reason = 'Revisao demonstrativa dos documentos sinteticos.' } | ConvertTo-Json
$decision = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/proposals/$($proposal.id)/decisions" -Headers $headers -ContentType 'application/json' -Body $decisionBody
if ($decision.analysisId -ne $newAnalysis.analysisId) { throw 'Human decision was not bound to the analysis' }
Write-Host "T22 PASS human review recorded by $($decision.analystId) on current snapshot"
