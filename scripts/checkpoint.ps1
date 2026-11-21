param(
    [Parameter(Mandatory)][ValidatePattern('^M\d{2}\.[123]$')][string]$Part,
    [Parameter(Mandatory)][string]$Description,
    [Parameter(Mandatory)][string[]]$Paths,
    [Parameter(Mandatory)][string]$Evidence
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $taskRoot
$taskQueueDirectory = Join-Path $taskRoot '.tools/commit-queue'
New-Item -ItemType Directory -Force -Path $taskQueueDirectory | Out-Null
$taskQueuePath = Join-Path $taskQueueDirectory 'manifest.json'
function Invoke-TaskGit([string[]]$Arguments) {
    $taskResult = & git @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Git failed: $($Arguments[0])" }
    return $taskResult
}
if (Test-Path -LiteralPath $taskQueuePath) {
    $taskQueue = Get-Content -LiteralPath $taskQueuePath -Raw | ConvertFrom-Json
    if ($taskQueue.sealed) { throw 'The queue is sealed.' }
} else {
    $taskQueue = [pscustomobject]@{
        version = 1
        branch = (Invoke-TaskGit -Arguments @('branch', '--show-current'))
        initialHead = (Invoke-TaskGit -Arguments @('rev-parse', 'HEAD'))
        sealed = $false
        checkpoints = @()
    }
}
if (@($taskQueue.checkpoints | Where-Object { $_.part -eq $Part }).Count) {
    throw "$Part is already captured. Capture only reviewed, verified checkpoints."
}
$taskPriorTree = if (@($taskQueue.checkpoints).Count) { $taskQueue.checkpoints[-1].tree } else { "$($taskQueue.initialHead)^{tree}" }
$taskOldIndex = $env:GIT_INDEX_FILE
try {
    $env:GIT_INDEX_FILE = Join-Path $taskQueueDirectory 'capture.index'
    Invoke-TaskGit -Arguments @('read-tree', $taskPriorTree) | Out-Null
    Invoke-TaskGit -Arguments (@('add', '--') + $Paths) | Out-Null
    Invoke-TaskGit -Arguments @('diff', '--cached', '--check') | Out-Null
    $taskTree = Invoke-TaskGit -Arguments @('write-tree')
    if ($taskTree -eq (Invoke-TaskGit -Arguments @('rev-parse', $taskPriorTree))) { throw 'An empty checkpoint is forbidden.' }
} finally {
    if ($null -eq $taskOldIndex) { Remove-Item Env:GIT_INDEX_FILE -ErrorAction SilentlyContinue } else { $env:GIT_INDEX_FILE = $taskOldIndex }
}
$taskQueue.checkpoints = @($taskQueue.checkpoints) + [pscustomobject]@{
    part = $Part
    message = "${Part}: $Description"
    tree = $taskTree
    evidence = $Evidence
    reviewedAt = [DateTimeOffset]::UtcNow.ToString('o')
}
$taskTemporaryPath = "$taskQueuePath.tmp"
[IO.File]::WriteAllText($taskTemporaryPath, ($taskQueue | ConvertTo-Json -Depth 8), (New-Object Text.UTF8Encoding $false))
Move-Item -LiteralPath $taskTemporaryPath -Destination $taskQueuePath -Force
Write-Output "Captured $Part ($taskTree). No commit made; actual commit time is controlled by the queue."
