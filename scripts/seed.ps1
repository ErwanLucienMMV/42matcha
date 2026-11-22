$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $taskRoot
if (!(Test-Path -LiteralPath .env)) { & ./scripts/setup.ps1 }
$taskEnvPath = Join-Path $taskRoot '.env'
$taskContent = [IO.File]::ReadAllText($taskEnvPath)
$taskExisting = [regex]::Match($taskContent, '(?m)^SEED_PASSWORD=([^\r\n]*)')
if (!$taskExisting.Success -or [string]::IsNullOrWhiteSpace($taskExisting.Groups[1].Value) -or $taskExisting.Groups[1].Value.StartsWith('REPLACE_')) {
    $taskBytes = New-Object byte[] 24
    $taskRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $taskRandom.GetBytes($taskBytes) } finally { $taskRandom.Dispose() }
    $taskPassword = 'D!' + [Convert]::ToBase64String($taskBytes).TrimEnd('=').Replace('+','-').Replace('/','_') + 'a7'
    $taskLine = 'SEED_PASSWORD=' + $taskPassword
    if ($taskExisting.Success) { $taskContent = $taskContent.Replace($taskExisting.Value, $taskLine) } else { $taskContent = $taskContent.TrimEnd() + "`n" + $taskLine + "`n" }
    [IO.File]::WriteAllText($taskEnvPath, $taskContent, (New-Object Text.UTF8Encoding $false))
    Write-Output 'Created a local demo password in ignored .env.'
}
function Invoke-TaskDocker([string[]]$Arguments) {
    $taskPreviousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & docker @Arguments 2>&1 | ForEach-Object { $_.ToString() }
        $taskResult = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskPreviousPreference }
    if ($taskResult -ne 0) { throw "Docker command failed with exit code $taskResult." }
}
Invoke-TaskDocker -Arguments @('compose','up','--build','-d','--wait')
Invoke-TaskDocker -Arguments @('compose','exec','-T','backend','java','-Djava.awt.headless=true','-jar','app.jar','seed')
