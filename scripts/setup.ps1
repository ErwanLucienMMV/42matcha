$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskEnvPath = Join-Path $taskRoot '.env'
if (Test-Path -LiteralPath $taskEnvPath) {
    Write-Output '.env already exists; preserving local settings.'
    exit 0
}
$taskSecretBytes = New-Object byte[] 32
$taskRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $taskRandom.GetBytes($taskSecretBytes) } finally { $taskRandom.Dispose() }
$taskSecret = [Convert]::ToBase64String($taskSecretBytes)
$taskContent = [IO.File]::ReadAllText((Join-Path $taskRoot '.env.example'))
$taskContent = $taskContent.Replace('REPLACE_WITH_A_RANDOM_LOCAL_SECRET', $taskSecret)
$taskDemoBytes = New-Object byte[] 24
$taskRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $taskRandom.GetBytes($taskDemoBytes) } finally { $taskRandom.Dispose() }
$taskDemoSecret = 'D!' + [Convert]::ToBase64String($taskDemoBytes).TrimEnd('=').Replace('+','-').Replace('/','_') + 'a7'
$taskContent = $taskContent.Replace('REPLACE_WITH_A_RANDOM_DEMO_PASSWORD', $taskDemoSecret)
[IO.File]::WriteAllText($taskEnvPath, $taskContent, (New-Object Text.UTF8Encoding $false))
Write-Output 'Created ignored .env with a random database password.'
