param([switch]$Once, [switch]$DryRun)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $taskRoot
$taskDirectory = Join-Path $taskRoot '.tools/commit-queue'
$taskManifest = Join-Path $taskDirectory 'manifest.json'
$taskStatePath = Join-Path $taskDirectory 'state.json'
if (!(Test-Path -LiteralPath $taskManifest)) { throw 'No reviewed checkpoint queue exists.' }
function Invoke-TaskGit([string[]]$Arguments) {
    $taskResult = & git @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Git failed: $($Arguments[0])" }
    return $taskResult
}
if ($env:GIT_AUTHOR_DATE -or $env:GIT_COMMITTER_DATE) { throw 'Git date overrides are forbidden.' }
if (($env:GIT_AUTHOR_NAME -and $env:GIT_AUTHOR_NAME -ne 'ErwanLucienMMV') -or
    ($env:GIT_COMMITTER_NAME -and $env:GIT_COMMITTER_NAME -ne 'ErwanLucienMMV') -or
    ($env:GIT_AUTHOR_EMAIL -and $env:GIT_AUTHOR_EMAIL -ne 'ultimemmv@gmail.com') -or
    ($env:GIT_COMMITTER_EMAIL -and $env:GIT_COMMITTER_EMAIL -ne 'ultimemmv@gmail.com')) { throw 'Unexpected Git identity environment override.' }
if ((Invoke-TaskGit -Arguments @('config', 'user.name')) -ne 'ErwanLucienMMV' -or
    (Invoke-TaskGit -Arguments @('config', 'user.email')) -ne 'ultimemmv@gmail.com') { throw 'Unexpected commit identity.' }
if ((Invoke-TaskGit -Arguments @('config', 'core.hooksPath')) -ne '.githooks') { throw 'The spacing hook must be active.' }
$taskLock = if (!$DryRun) { [IO.File]::Open((Join-Path $taskDirectory 'runner.lock'), 'OpenOrCreate', 'ReadWrite', 'None') } else { $null }
$taskOldIndex = $env:GIT_INDEX_FILE
try {
    $taskLastWait = ''
    while ($true) {
        $taskQueue = Get-Content -LiteralPath $taskManifest -Raw | ConvertFrom-Json
        $taskState = if (Test-Path -LiteralPath $taskStatePath) {
            Get-Content -LiteralPath $taskStatePath -Raw | ConvertFrom-Json
        } else { [pscustomobject]@{ head = $taskQueue.initialHead; completed = @() } }
        if ((Invoke-TaskGit -Arguments @('branch', '--show-current')) -ne $taskQueue.branch) { throw 'Branch changed; queue stopped.' }
        if ((Invoke-TaskGit -Arguments @('rev-parse', 'HEAD')) -ne $taskState.head) { throw 'HEAD changed outside this queue; review before resuming.' }
        $taskPending = @($taskQueue.checkpoints | Where-Object { $_.part -notin @($taskState.completed | ForEach-Object { $_.part }) })
        if (!$taskPending.Count) {
            if ($taskQueue.sealed -or $Once -or $DryRun) {
                if ($taskQueue.sealed -and !$DryRun) {
                    Remove-Item Env:GIT_INDEX_FILE -ErrorAction SilentlyContinue
                    & (Join-Path $PSScriptRoot 'audit-commits.ps1') -RequireComplete
                    # The final history now exists, so verify an actual fresh clone of it.
                    & (Join-Path $PSScriptRoot 'verify-fresh.ps1') -Revision $taskState.head -WebPort 0 -MailPort 0
                    $taskChecklist = Join-Path $taskRoot 'LOCAL_CHECKLIST.txt'
                    if (Test-Path -LiteralPath $taskChecklist) {
                        [IO.File]::AppendAllText($taskChecklist, "`n[x] FINAL DELIVERY VERIFIED $([DateTimeOffset]::Now.ToString('o')): all 42 hourly commits and final fresh clone passed; HEAD $($taskState.head).`n", (New-Object Text.UTF8Encoding $false))
                    }
                }
                Write-Output 'No pending reviewed checkpoints.'; break
            }
            Start-Sleep -Seconds 30
            continue
        }
        $taskCheckpoint = $taskPending[0]
        $taskPreviousEpoch = [long](Invoke-TaskGit -Arguments @('log', '-1', '--format=%ct'))
        $taskNow = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
        $taskRemaining = [Math]::Max(0, 3600 - ($taskNow - $taskPreviousEpoch))
        if ($DryRun) {
            Write-Output "Next: $($taskCheckpoint.message); wait $taskRemaining seconds; reviewed evidence: $($taskCheckpoint.evidence)"
            break
        }
        if ($taskRemaining -gt 0) {
            if ($taskLastWait -ne $taskCheckpoint.part) {
                Write-Output "$([DateTimeOffset]::Now.ToString('o')) Waiting $taskRemaining seconds for $($taskCheckpoint.part)."
                $taskLastWait = $taskCheckpoint.part
            }
            if ($Once) { break }
            Start-Sleep -Seconds ([Math]::Min(30, $taskRemaining))
            continue
        }
        $env:GIT_INDEX_FILE = Join-Path $taskDirectory 'commit.index'
        Invoke-TaskGit -Arguments @('read-tree', $taskCheckpoint.tree) | Out-Null
        Invoke-TaskGit -Arguments @('diff', '--cached', '--check') | Out-Null
        Invoke-TaskGit -Arguments @('commit', '-m', $taskCheckpoint.message) | Out-Null
        $taskNewHead = Invoke-TaskGit -Arguments @('rev-parse', 'HEAD')
        $taskActualTree = Invoke-TaskGit -Arguments @('rev-parse', 'HEAD^{tree}')
        $taskAuthor = Invoke-TaskGit -Arguments @('log', '-1', '--format=%an <%ae>|%cn <%ce>')
        $taskActualTime = [long](Invoke-TaskGit -Arguments @('log', '-1', '--format=%ct'))
        if ($taskActualTree -ne $taskCheckpoint.tree -or ($taskActualTime - $taskPreviousEpoch) -lt 3600) { throw 'Post-commit tree/time verification failed.' }
        if ($taskAuthor -ne 'ErwanLucienMMV <ultimemmv@gmail.com>|ErwanLucienMMV <ultimemmv@gmail.com>') { throw 'Post-commit identity verification failed.' }
        $taskState.head = $taskNewHead
        $taskState.completed = @($taskState.completed) + [pscustomobject]@{ part = $taskCheckpoint.part; commit = $taskNewHead; epoch = $taskActualTime }
        [IO.File]::WriteAllText("$taskStatePath.tmp", ($taskState | ConvertTo-Json -Depth 8), (New-Object Text.UTF8Encoding $false))
        Move-Item -LiteralPath "$taskStatePath.tmp" -Destination $taskStatePath -Force
        Write-Output "$([DateTimeOffset]::Now.ToString('o')) Committed $($taskCheckpoint.part): $taskNewHead."
        if ($Once) { break }
    }
} finally {
    if ($null -eq $taskOldIndex) { Remove-Item Env:GIT_INDEX_FILE -ErrorAction SilentlyContinue } else { $env:GIT_INDEX_FILE = $taskOldIndex }
    if ($null -ne $taskLock) { $taskLock.Dispose() }
}
