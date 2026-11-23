param([switch]$RequireComplete, [switch]$AllowIncompletePlan)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $taskRoot
function Invoke-TaskGit([string[]]$Arguments) {
    $taskResult = & git @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Git failed: $($Arguments[0])" }
    return $taskResult
}
$taskPath = Join-Path $taskRoot '.tools/commit-queue/manifest.json'
if (!(Test-Path -LiteralPath $taskPath)) { throw 'This audit needs the local reviewed-checkpoint manifest.' }
$taskQueue = Get-Content -LiteralPath $taskPath -Raw | ConvertFrom-Json
$taskExpected = @('M00.2','M00.3') + @(1..13 | ForEach-Object { $taskMilestone = $_; 1..3 | ForEach-Object { 'M{0:D2}.{1}' -f $taskMilestone, $_ } })
$taskParts = @($taskQueue.checkpoints | ForEach-Object { $_.part })
$taskCompare = if ($AllowIncompletePlan -and !$RequireComplete) { @($taskExpected | Select-Object -First $taskParts.Count) } else { $taskExpected }
if (($taskParts -join ',') -ne ($taskCompare -join ',')) { throw 'Expected ordered checkpoint parts, three commits per milestone including the M00 root.' }
$taskPreviousTree = Invoke-TaskGit -Arguments @('rev-parse', "$($taskQueue.initialHead)^{tree}")
foreach ($taskCheckpoint in $taskQueue.checkpoints) {
    if ([string]::IsNullOrWhiteSpace($taskCheckpoint.evidence)) { throw 'Checkpoint lacks verification evidence.' }
    if ($taskCheckpoint.tree -eq $taskPreviousTree) { throw "Empty checkpoint: $($taskCheckpoint.part)" }
    $taskFiles = Invoke-TaskGit -Arguments @('ls-tree','-r','--name-only',$taskCheckpoint.tree)
    if (@($taskFiles | Where-Object { $_ -match '(^|/)(\.tools|\.local-validation|node_modules|target|dist)/|(^|/)\.env$|LOCAL_CHECKLIST' }).Count) { throw "Private/build file captured in $($taskCheckpoint.part)" }
    if ($taskCheckpoint.message -match '(?i)co-authored-by|codex|openai|assistant credit') { throw 'Unwanted attribution in a checkpoint message.' }
    $taskPreviousTree = $taskCheckpoint.tree
}
$taskHistory = @(Invoke-TaskGit -Arguments @('log','--reverse','--format=%H|%T|%at|%ct|%an <%ae>|%cn <%ce>|%s','HEAD'))
if ($taskHistory[0].Split('|')[0] -ne $taskQueue.initialHead) { throw 'History does not start at the reviewed root.' }
if ($taskHistory.Count -gt 42) { throw 'Unexpected commits outside the checkpoint sequence.' }
$taskPreviousAuthor = 0L; $taskPreviousCommitter = 0L
for ($taskIndex = 0; $taskIndex -lt $taskHistory.Count; $taskIndex++) {
    $taskFields = $taskHistory[$taskIndex].Split('|')
    if ($taskFields[4] -ne 'ErwanLucienMMV <ultimemmv@gmail.com>' -or $taskFields[5] -ne $taskFields[4]) { throw 'Unexpected author or committer.' }
    if ($taskIndex -gt 0) {
        if ([long]$taskFields[2] - $taskPreviousAuthor -lt 3600 -or [long]$taskFields[3] - $taskPreviousCommitter -lt 3600) { throw 'An actual commit violates the one-hour spacing.' }
        $taskCheckpoint = $taskQueue.checkpoints[$taskIndex - 1]
        if ($taskFields[1] -ne $taskCheckpoint.tree -or $taskFields[6] -ne $taskCheckpoint.message) { throw 'Committed tree/message does not match the reviewed sequence.' }
        if ([DateTimeOffset]::Parse($taskCheckpoint.reviewedAt).ToUnixTimeSeconds() -gt [long]$taskFields[3]) { throw 'Commit predates its checkpoint review.' }
    }
    $taskBody = Invoke-TaskGit -Arguments @('show','-s','--format=%B',$taskFields[0])
    if (($taskBody -join "`n") -match '(?i)co-authored-by|codex|openai|assistant credit') { throw 'Unwanted attribution in actual history.' }
    $taskPreviousAuthor = [long]$taskFields[2]; $taskPreviousCommitter = [long]$taskFields[3]
}
if ($RequireComplete -and (!$taskQueue.sealed -or $taskHistory.Count -ne 42)) { throw 'The sealed 42-commit sequence is not complete yet.' }
$taskResult = [pscustomobject]@{
    checkedAt = [DateTimeOffset]::UtcNow.ToString('o')
    actualCommits = $taskHistory.Count
    pendingCommits = 42 - $taskHistory.Count
    reviewedCheckpoints = $taskParts.Count
    sealed = $taskQueue.sealed
    identityAndHourlySpacingValid = $true
    complete = ($taskQueue.sealed -and $taskHistory.Count -eq 42)
    head = (Invoke-TaskGit -Arguments @('rev-parse','HEAD'))
}
if ($taskResult.complete) {
    [IO.File]::WriteAllText((Join-Path $taskRoot '.tools/commit-queue/final-audit.json'), ($taskResult | ConvertTo-Json), (New-Object Text.UTF8Encoding $false))
}
$taskResult | ConvertTo-Json
