# Mandatory acceptance evidence

Verified on 22 November 2026 against the local Docker Compose stack:

- Maven verify: **23 passed, zero failures/errors/skips**, including real
  PostgreSQL schema, migration, rollback, concurrency and HTTP security tests.
- Playwright: **58 passed** across bundled Chromium (Chrome for Testing) and
  Firefox, one worker, against the actual production Angular/Nginx/API stack.
- Production backend and frontend container builds and health checks passed.
- Seed audit: **500** distinct profiles, **500** independently salted hashes,
  **540** photos belonging to all 500 profiles, three genders, three preferences,
  twelve cities, five notification kinds, 100 messages, and moderation fixtures.
- OSV: zero matches among 50 resolved Java runtime packages. npm runtime
  audit: zero reported advisories. See [security details](security.md).

Repeat with `scripts/check.ps1` or `sh scripts/check.sh` after installing the
Playwright browsers. Logs, JSON reports, traces and screenshots are kept locally
under ignored `.local-validation`; they are excluded because they can contain
test credentials and account tokens. The tested browser binaries are Playwright's
version-pinned distributions, not every possible browser/OS combination.

| Requirement | Executable evidence |
| --- | --- |
| Handwritten SQL, migrations, rollback, commit-only effects | DatabaseIntegrationTest, source/dependency inspection |
| Password policy, salts and token digests | PasswordsTest, registration.spec.ts |
| Verification, login, CSRF, reset, logout and revocation | SecurityIntegrationTest, sessions.spec.ts, account-ui.spec.ts |
| Expired links/sessions, foreign origin, media type and SQL injection | SecurityIntegrationTest |
| Verified email changes retain previous address until confirmed | email-change.spec.ts |
| Editable profile, tags, safe account DTO and photos | profile-data.spec.ts, profile-ui.spec.ts |
| Five-photo concurrency, raster validation and ownership | photos.spec.ts, PhotosTest, DatabaseIntegrationTest |
| Approximate location, denied GPS/manual fallback, age policy | location-api.spec.ts, location-ui.spec.ts, PlacesTest, PreferencesTest |
| Compatibility, ranking, range filters, allowlisted sorts | discovery-api.spec.ts, discovery-ui.spec.ts, matching SQL review |
| Combined multiple-tag search and stable pagination | search-api.spec.ts, search-ui.spec.ts, SearchTest |
| Visits, incoming likes, mutual connections and unlike | interactions-api.spec.ts, interactions-ui.spec.ts |
| Fake-account reports and bidirectional blocks | moderation-api.spec.ts |
| Authorized persisted chat, escaped text and immediate revocation | chat-api.spec.ts, chat-ui.spec.ts |
| Live presence and committed refresh within ten seconds | live-transport.spec.ts, chat-ui.spec.ts |
| Five notification kinds, ownership, unlike/block suppression | notifications-api.spec.ts |
| Read boundaries preserve concurrent arrivals | notification-boundaries.spec.ts |
| Site-wide unread badges, delivery within ten seconds, reload persistence | notifications-ui.spec.ts, chat-ui.spec.ts |
| 500-profile diversity and usable seeded discovery/chat | SeedAudit CLI, evaluation.spec.ts |
| Header/main/footer, healthy browser console, mobile fit | foundation.spec.ts, discovery-ui.spec.ts |
| 320px authenticated pages and recoverable session-service outage | resilience-ui.spec.ts |

Rate-limit defaults are restored after the suite. Deliberately invalid requests
in negative tests are expected rejections. Traces from earlier failed test runs
are diagnostic artifacts, not passing evidence. Fresh-environment validation and
the actual commit-history audit are separate delivery gates, documented when
completed; queued checkpoints are not yet actual commits.
