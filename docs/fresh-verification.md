# Fresh-source verification

On 22 November 2026, Git tree
`190f6b7093879ff21fbafede06b0404eb017dfc6` (reviewed M13.1 source) was
exported with `git archive` into an independent local directory. No existing
`.env`, node_modules, target, uploaded data, or private checklist was copied.
New database and evaluation passwords were generated locally.

A distinct Compose project used loopback ports 8081 and 8026 and its own
PostgreSQL volume. Production images built from the exported source, automatic
migrations succeeded, and the seed created 500 new profiles with 540 photos.
The seed audit verified gender/preference/city diversity, independently salted
hashes, all five notification kinds, 100 messages and moderation fixtures.

The containers were removed and recreated while retaining that volume. All
**58 Chromium/Firefox checks passed again** against this separate instance,
including seeded login, photos, discovery and chat. A second seed audit passed
after the tests. The temporary project's containers/network were then stopped;
the main application and its database were preserved. Its private configuration
and evidence remain ignored.

`scripts/verify-fresh.ps1 -Revision <tree-or-commit>` reproduces this procedure.
It uses an export for a pending tree and an actual local clone for a committed
revision. `-WebPort 0 -MailPort 0` selects free loopback ports. Tests use
MATCHA_URL, MATCHA_MAIL_URL and MATCHA_ENV_FILE to target the separate instance
without printing its private credentials. Install QA dependencies and Playwright
browsers before invoking it.

The hourly policy means the fully implemented source is verified before its
final commit exists. Once all 42 scheduled commits exist, the runner repeats
this procedure with a real clone of the final commit and writes a new receipt.
See [delivery and audit](delivery.md) for the distinction and completion checks.
