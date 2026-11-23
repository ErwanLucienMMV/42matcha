$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $taskRoot
function Invoke-TaskCommand([string]$Command, [string[]]$Arguments) {
    $taskPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Command @Arguments 2>&1 | ForEach-Object { $_.ToString() }
        $taskCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskPreference }
    if ($taskCode -ne 0) { throw "$Command failed with exit code $taskCode." }
}
if (!(Test-Path -LiteralPath .env)) { throw 'Run scripts/setup.ps1 first.' }
if (Test-Path -LiteralPath "$taskRoot/.tools/node-v22.23.5-win-x64/node.exe") {
    $env:PATH = "$taskRoot/.tools/node-v22.23.5-win-x64;$env:PATH"
}
if (!(Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'Install Node.js 22.22.3+ and npm first.' }
if (Test-Path -LiteralPath "$taskRoot/.tools/playwright") { $env:PLAYWRIGHT_BROWSERS_PATH = "$taskRoot/.tools/playwright" }
Remove-Item Env:NO_COLOR -ErrorAction SilentlyContinue
$taskEnv = [IO.File]::ReadAllText((Join-Path $taskRoot '.env'))
if (!$env:MATCHA_URL) { $env:MATCHA_URL = [regex]::Match($taskEnv, '(?m)^APP_URL=([^\r\n]+)').Groups[1].Value }
if (!$env:MATCHA_MAIL_URL) {
    $taskMailPort = [regex]::Match($taskEnv, '(?m)^MAIL_PORT=([^\r\n]+)').Groups[1].Value
    $env:MATCHA_MAIL_URL = "http://localhost:$taskMailPort"
}
Invoke-TaskCommand -Command docker -Arguments @('compose','up','--build','-d','--wait')
Invoke-TaskCommand -Command docker -Arguments @('compose','exec','-T','backend','java','-Djava.awt.headless=true','-jar','app.jar','seed')
Invoke-TaskCommand -Command docker -Arguments @('compose','-f','compose.yaml','-f','compose.test.yaml','run','--rm','backend-test')
try {
    Invoke-TaskCommand -Command docker -Arguments @('compose','-f','compose.yaml','-f','compose.qa.yaml','up','-d','--wait','backend','frontend')
    Invoke-TaskCommand -Command npm.cmd -Arguments @('ci','--prefix','qa','--no-fund','--no-audit')
    Push-Location (Join-Path $taskRoot 'qa')
    try { Invoke-TaskCommand -Command npm.cmd -Arguments @('test') } finally { Pop-Location }
    Invoke-TaskCommand -Command docker -Arguments @('compose','exec','-T','backend','java','-Djava.awt.headless=true','-jar','app.jar','audit-seed')
} finally {
    # Restore the real registration limit even when acceptance checks fail.
    Invoke-TaskCommand -Command docker -Arguments @('compose','up','-d','--wait','backend','frontend')
}
Write-Output 'Backend, browser and evaluation-data checks passed. Normal rate limits restored.'
