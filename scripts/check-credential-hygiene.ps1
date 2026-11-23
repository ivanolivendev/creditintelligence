$ErrorActionPreference = 'Stop'

$sensitiveNamePattern = '(?i)(^|/)(\.env($|\.)|credentials?|credenciais|.*access.*key.*|.*secret.*|.*private.*key.*|.*\.pem$|.*\.p12$|.*\.pfx$|.*\.key$)'
$trackedSensitive = @(git ls-files | Where-Object { $_ -match $sensitiveNamePattern })
$trackedSensitive = @($trackedSensitive | Where-Object { $_ -notmatch '(?i)(^|/)\.env\.example$' })

if ($trackedSensitive.Count -gt 0) {
    Write-Error "Sensitive-looking files are tracked by Git: $($trackedSensitive -join ', ')"
    exit 1
}

$localCandidates = @('.env', '.bedrock.env', 'credenciais.txt', 'BedrockAPIKey-6tlf_accessKeys.csv', 'chavenda.txt')
$notIgnored = @($localCandidates | Where-Object {
    if (-not (Test-Path -LiteralPath $_)) {
        return $false
    }
    git check-ignore --quiet -- $_
    return $LASTEXITCODE -ne 0
})

if ($notIgnored.Count -gt 0) {
    Write-Error "Local credential candidates are not ignored by Git: $($notIgnored -join ', ')"
    exit 1
}

Write-Output "Credential hygiene check passed: no sensitive-looking tracked files and local candidates are ignored."
