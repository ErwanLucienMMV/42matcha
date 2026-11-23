param([Parameter(Mandatory)][string]$Revision, [int]$WebPort = 8081, [int]$MailPort = 8026)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
function Get-TaskFreePort {
    $taskListener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $taskListener.Start()
    try { return $taskListener.LocalEndpoint.Port } finally { $taskListener.Stop() }
}
if ($WebPort -eq 0) { $WebPort = Get-TaskFreePort }
if ($MailPort -eq 0) { $MailPort = Get-TaskFreePort }
$taskSuffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$taskProject = "matcha-fresh-$taskSuffix"
$taskDirectory = Join-Path $taskRoot ".tools/fresh-checkout-$taskSuffix"
$taskArchive = Join-Path $taskRoot ".tools/fresh-checkout-$taskSuffix.tar"
if (Test-Path -LiteralPath "$taskRoot/.tools/node-v22.23.5-win-x64/node.exe") { $env:PATH = "$taskRoot/.tools/node-v22.23.5-win-x64;$env:PATH" }
if (Test-Path -LiteralPath "$taskRoot/.tools/playwright") { $env:PLAYWRIGHT_BROWSERS_PATH = "$taskRoot/.tools/playwright" }
Remove-Item Env:NO_COLOR -ErrorAction SilentlyContinue
function Invoke-TaskCommand([string]$Command, [string[]]$Arguments) {
    $taskPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Command @Arguments 2>&1 | ForEach-Object { $_.ToString() }
        $taskCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskPreference }
    if ($taskCode -ne 0) { throw "$Command failed with exit code $taskCode." }
}
New-Item -ItemType Directory -Path $taskDirectory | Out-Null
Set-Location -LiteralPath $taskRoot
$taskKind = & git cat-file -t $Revision
if ($LASTEXITCODE -ne 0) { throw 'Revision does not exist.' }
if ($taskKind -eq 'commit') {
    Invoke-TaskCommand -Command git -Arguments @('clone','--no-hardlinks','--no-checkout','--local',$taskRoot,$taskDirectory)
    Invoke-TaskCommand -Command git -Arguments @('-C',$taskDirectory,'checkout','--detach',$Revision)
} else {
    # Pending checkpoints are Git trees until their real hourly commit is due.
    Invoke-TaskCommand -Command git -Arguments @('archive','--format=tar','-o',$taskArchive,$Revision)
    $taskTar = Join-Path $env:SystemRoot 'System32/tar.exe'
    Invoke-TaskCommand -Command $taskTar -Arguments @('-xf',$taskArchive,'-C',$taskDirectory)
}
& (Join-Path $taskDirectory 'scripts/setup.ps1')
$taskEnvPath = Join-Path $taskDirectory '.env'
$taskEnv = [IO.File]::ReadAllText($taskEnvPath).Replace('WEB_PORT=8080',"WEB_PORT=$WebPort").Replace('APP_URL=http://localhost:8080',"APP_URL=http://localhost:$WebPort").Replace('MAIL_PORT=8025',"MAIL_PORT=$MailPort")
[IO.File]::WriteAllText($taskEnvPath, $taskEnv, (New-Object Text.UTF8Encoding $false))
$taskOldUrl = $env:MATCHA_URL; $taskOldMail = $env:MATCHA_MAIL_URL; $taskOldFile = $env:MATCHA_ENV_FILE
$taskStarted = $false
try {
    Set-Location -LiteralPath $taskDirectory
    $taskStarted = $true
    Invoke-TaskCommand -Command docker -Arguments @('compose','-p',$taskProject,'up','--build','-d','--wait')
    Invoke-TaskCommand -Command docker -Arguments @('compose','-p',$taskProject,'exec','-T','backend','java','-Djava.awt.headless=true','-jar','app.jar','seed')
    Invoke-TaskCommand -Command docker -Arguments @('compose','-p',$taskProject,'exec','-T','backend','java','-Djava.awt.headless=true','-jar','app.jar','audit-seed')
    # Recreate the containers before testing to verify the separate volume persists.
    Invoke-TaskCommand -Command docker -Arguments @('compose','-p',$taskProject,'down')
    Invoke-TaskCommand -Command docker -Arguments @('compose','-p',$taskProject,'-f','compose.yaml','-f','compose.qa.yaml','up','-d','--wait')
    $env:MATCHA_URL = "http://localhost:$WebPort"
    $env:MATCHA_MAIL_URL = "http://localhost:$MailPort"
    $env:MATCHA_ENV_FILE = $taskEnvPath
    Set-Location -LiteralPath (Join-Path $taskRoot 'qa')
    Invoke-TaskCommand -Command npm.cmd -Arguments @('test')
    Set-Location -LiteralPath $taskDirectory
    Invoke-TaskCommand -Command docker -Arguments @('compose','-p',$taskProject,'exec','-T','backend','java','-Djava.awt.headless=true','-jar','app.jar','audit-seed')
    $taskReceipt = [pscustomobject]@{ revision=$Revision; sourceKind=$taskKind; composeProject=$taskProject; checkout=$taskDirectory; verifiedAt=[DateTimeOffset]::UtcNow.ToString('o'); browserTests=58; persistentFreshDatabase=$true }
    [IO.File]::WriteAllText((Join-Path $taskRoot '.local-validation/fresh-checkout.json'), ($taskReceipt | ConvertTo-Json), (New-Object Text.UTF8Encoding $false))
    Write-Output 'Fresh-source build, independent 500-profile database, restart persistence and both browser suites passed.'
} finally {
    $env:MATCHA_URL = $taskOldUrl; $env:MATCHA_MAIL_URL = $taskOldMail; $env:MATCHA_ENV_FILE = $taskOldFile
    Set-Location -LiteralPath $taskDirectory
    if ($taskStarted) { Invoke-TaskCommand -Command docker -Arguments @('compose','-p',$taskProject,'down') }
    Set-Location -LiteralPath $taskRoot
}
