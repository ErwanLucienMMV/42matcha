# Repository and hourly history

The mandatory implementation and its acceptance checks are saved in reviewed
Git trees. The local queue turns those trees into actual commits at least one
real hour apart. There are fourteen milestones, M00 through M13, with three
substantive parts each: the original M00 root plus 41 queued checkpoints.
All commits use `ErwanLucienMMV <ultimemmv@gmail.com>` as author and committer.
Messages have no assistant attribution or co-author trailers. No dates are
overridden, and the spacing hook remains enabled.

The local ignored checklist is `LOCAL_CHECKLIST.txt`. The queue manifest,
evidence, process ID, state and logs are in `.tools/commit-queue/`. Only reviewed
trees are queued. The runner stops if HEAD or the branch changes outside it;
it holds an exclusive lock to prevent duplicate commits. The manifest is sealed
once every part is reviewed. The normal index is aligned with the final tree,
so it can show later work staged while HEAD is still catching up hourly.

Inspect without interrupting the running queue:

```powershell
./scripts/run-commit-queue.ps1 -DryRun
./scripts/audit-commits.ps1
git log --reverse --format='%h %ci %an <%ae> %s'
Get-Content .tools/commit-queue/runner.log -Tail 10
```

Keep the computer awake. After interruption, resume the same queue with
`./scripts/run-commit-queue.ps1`; it preserves completed work and waits for the
next eligible real time. Missed hours are never represented by fake commits.
Do not commit manually or switch branches while the queue is active.

At the end, the runner executes `audit-commits.ps1 -RequireComplete`: it checks
all 42 actual commits, reviewed tree/message equality, review time, identity,
one-hour author/committer spacing and excluded paths. Its receipt is
`.tools/commit-queue/final-audit.json`. It then verifies a real fresh clone of
the final commit, including a new 500-profile database, restart persistence and
both browser suites, on automatically selected loopback ports. The separate
receipt is `.local-validation/fresh-checkout.json`; `sourceKind: commit`
distinguishes this from an earlier pending-tree export. Runner failures appear
in `runner-error.log`; resolve them before considering delivery complete.

Before the schedule finishes, fresh-source verification uses `git archive` of
the reviewed tree: it excludes local dependencies, credentials, generated
builds and untracked helpers. This validates the exact future source without
inventing a commit date. It does not claim that all scheduled commits already
exist. The final clone must match the fully committed source before submission.

Do not push an intermediate HEAD as the completed project. After the runner
finishes successfully, verify `git status --short` is empty and run the strict
audit once more. Publishing or pushing is a separate user action.
