param(
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $Username = 'analyst',
    [string] $Password = 'local_demo_only_change_me'
)

$ErrorActionPreference = 'Stop'
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${Username}:${Password}"))
$headers = @{ Authorization = "Basic $auth" }
function Get-Status {
    param([hashtable] $Request)
    try {
        return [int](Invoke-WebRequest @Request -UseBasicParsing).StatusCode
    } catch {
        if ($_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw
    }
}
$identifier = 'DEMO-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$request = @{
    applicant = @{ name = 'Maria Silva'; syntheticIdentifier = $identifier }
    declaredMonthlyIncome = 8000.00
    currency = 'BRL'
} | ConvertTo-Json -Depth 4

$created = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/proposals" -Headers $headers -ContentType 'application/json' -Body $request
if ($created.status -ne 'DRAFT' -or $created.currency -ne 'BRL' -or $created.declaredMonthlyIncome -ne 8000) {
    throw 'T01: proposal response is incorrect'
}
$found = Invoke-RestMethod -Uri "$BaseUrl/api/v1/proposals/$($created.id)" -Headers $headers
if ($found.id -ne $created.id -or $found.applicant.syntheticIdentifier -ne $identifier) {
    throw 'T01: persisted proposal differs from creation'
}
Write-Host "T01 PASS proposal=$($created.id)"

$invalid = Get-Status @{ Method = 'Post'; Uri = "$BaseUrl/api/v1/proposals"; Headers = $headers; ContentType = 'application/json'; Body = '{"applicant":{"name":"","syntheticIdentifier":"BAD"},"declaredMonthlyIncome":-1,"currency":"USD"}' }
$missing = Get-Status @{ Uri = "$BaseUrl/api/v1/proposals/$([guid]::NewGuid())"; Headers = $headers }
$unauthorized = Get-Status @{ Uri = "$BaseUrl/api/v1/proposals/$($created.id)" }
if ($invalid -ne 400 -or $missing -ne 404 -or $unauthorized -ne 401) {
    throw "T02: unexpected statuses $invalid/$missing/$unauthorized"
}
Write-Host 'T02 PASS invalid=400 missing=404 unauthenticated=401'

$fixture = (Resolve-Path (Join-Path $PSScriptRoot '../fixtures/documents/income-proof-demo.pdf')).Path
$uri = "$BaseUrl/api/v1/proposals/$($created.id)/documents"
$first = (& curl.exe --silent --show-error --user "${Username}:${Password}" --form 'type=INCOME_PROOF' --form "file=@$fixture;type=application/pdf" $uri) | ConvertFrom-Json
$second = (& curl.exe --silent --show-error --user "${Username}:${Password}" --form 'type=INCOME_PROOF' --form "file=@$fixture;type=application/pdf" $uri) | ConvertFrom-Json
$documents = Invoke-RestMethod -Uri $uri -Headers $headers
if ($first.version -ne 1 -or $second.version -ne 2 -or $documents.Count -ne 2 -or $first.sha256 -ne $second.sha256) {
    throw 'T03: versioning or checksum is incorrect'
}
Write-Host "T03 PASS versions=1,2 checksum=$($first.sha256)"

$invalidFixture = (Resolve-Path (Join-Path $PSScriptRoot '../.env.example')).Path
$invalidStatus = & curl.exe --silent --show-error --output NUL --write-out '%{http_code}' --user "${Username}:${Password}" --form 'type=INCOME_PROOF' --form "file=@$invalidFixture;type=application/pdf" $uri
$afterInvalid = Invoke-RestMethod -Uri $uri -Headers $headers
$blankFixture = (Resolve-Path (Join-Path $PSScriptRoot '../fixtures/documents/blank-demo.pdf')).Path
$blankStatus = & curl.exe --silent --show-error --output NUL --write-out '%{http_code}' --user "${Username}:${Password}" --form 'type=INCOME_PROOF' --form "file=@$blankFixture;type=application/pdf" $uri
$oversized = Join-Path $env:TEMP ('credit-intelligence-oversized-' + [guid]::NewGuid().ToString('N') + '.pdf')
try {
    [IO.File]::WriteAllBytes($oversized, (New-Object byte[] (10 * 1024 * 1024 + 1)))
    $oversizedStatus = & curl.exe --silent --show-error --output NUL --write-out '%{http_code}' --user "${Username}:${Password}" --form 'type=INCOME_PROOF' --form "file=@$oversized;type=application/pdf" $uri
} finally {
    Remove-Item -LiteralPath $oversized -ErrorAction SilentlyContinue
}
$afterInvalid = Invoke-RestMethod -Uri $uri -Headers $headers
if ($invalidStatus -ne '422' -or $blankStatus -ne '422' -or $oversizedStatus -ne '413' -or $afterInvalid.Count -ne 2) {
    throw "T04: statuses=$invalidStatus/$blankStatus/$oversizedStatus count=$($afterInvalid.Count)"
}
Write-Host 'T04 PASS invalid PDF=422 blank PDF=422 oversized=413, no extra metadata'
