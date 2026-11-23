# Application architecture and acceptance map

## Scope

M01-M13 follow the [complete implementation plan](implementation-plan.md).
All bonus milestones are deferred. M00 has
three parts: the existing subject review, the revised execution policy, and this
architecture/acceptance map. Keep real commit times at least one hour apart.

## Runtime

The browser reaches an Angular single-page application and `/api` through one
web origin. Nginx serves the frontend, proxies API requests to Spark Java, and
forwards live updates. The Java service uses JDBC prepared statements against
PostgreSQL. A development SMTP inbox captures account emails locally.

PostgreSQL holds account and relationship state. Migrations are explicit SQL
files applied transactionally under a database advisory lock. No ORM, automatic
form validator, or account-management framework is used. Controllers parse input;
application services validate and authorize; repositories issue handwritten SQL.

Configuration comes from environment variables. Compose reads ignored `.env`.
The committed `.env.example` has placeholders only. Credentials never become
frontend build variables. Development services bind host ports to loopback.

## Security decisions

- Use opaque random session tokens in HttpOnly, SameSite cookies. Store token
  digests, expiration, and revocation in PostgreSQL. Require CSRF protection for
  authenticated writes. Production cookies require HTTPS.
- Hash passwords with a dedicated slow password hash; reject weak/common English
  passwords. Verification/reset links use random, expiring, single-use tokens
  whose digests alone are stored. Resetting a password revokes old sessions.
- Validate ownership and relationship permissions on every request. A browser
  route guard is navigation support, never the authorization boundary.
- Public DTOs explicitly select safe fields and omit email, credentials, token
  digests, and precise private location data.
- Restrict file size, decoded dimensions and supported raster formats. Decode
  and re-encode photos, reject active content, and enforce the five-photo limit
  in a locked database transaction.
- Allowlist SQL sorting expressions. Bind values through prepared statements.
  Angular interpolation displays user text without interpreting it as HTML.
- Rate-limit account/token endpoints. Do not return secrets in errors or logs.

## Product decisions

- Collect age data while completing a profile because age is required by search.
- Matching requires an approximate location. Ask for GPS consent and provide a
  city/neighborhood entry flow when denied or unavailable; never infer IP location.
- Treat unspecified orientation as bisexual. Apply documented compatibility
  rules consistently to discovery, with geography taking precedence in ranking.
- Define a bounded, explainable public popularity score from legitimate incoming
  activity. Record the formula with implementation and test its updates.
- A directed like is independent of the reverse like. A mutual pair enables chat.
  Removing either like disables chat. Blocking overrides all interaction rights.
- Emit the disconnection event when a connected user unlikes, then suppress
  subsequent events from the disconnected relationship. A block prevents alerts
  from the blocked account and excludes that relationship from discovery/search.
- Persist chat and notification state. Deliver live changes with a maximum of
  ten seconds, recover missed state after reconnect, and authorize reads/sends
  against current relationship state. Multiple tabs must not create stale presence.

## Acceptance traceability

| Subject section | Implementation milestone | Evidence required |
| --- | --- | --- |
| III, pages 4-5: permitted stack and manual queries | M01-M02 | Dependency review, JDBC/SQL inspection, clean builds and real PostgreSQL checks |
| III: structured responsive interface, browsers | M01, M12-M13 | Header/main/footer, mobile and desktop checks, Chrome and Firefox flows |
| IV.1, page 6: accounts | M03 | Registration, weak-password rejection, verification, login, reset, logout; expired/reused-token checks |
| IV.2, pages 6-7: profiles | M04-M05 | Editable fields/tags, photo selection/limit, visitors/likes, popularity, GPS denied/manual/edit cases |
| IV.3, page 7: suggestions | M06 | Preference matrix, bisexual default, geography-first ranking, all sorting/filtering cases |
| IV.4, page 7: search | M07 | Combined ranges/location/tags, ordering/filtering, boundary and pagination cases |
| IV.5, page 8: consultation | M08 | Safe public data, visits, photo-required likes, mutual state, unlike, presence, reports and blocks |
| IV.6, page 8: chat | M09 | Two-account persisted conversations, global arrival indicator, <=10s timing, unauthorized/revoked access |
| IV.7, pages 8-9: notifications | M10 | All five event types, unread/read state, global indicator, <=10s timing and suppression |
| III: >=500 distinct profiles | M11 | SQL counts, dataset diversity, repeatable seed and evaluation scenarios |
| III and VI: security/no unintended errors | M12 | Injection/XSS/upload/ownership/CSRF/session tests; browser and server output review |
| VI, page 11: repository submission | M13 | Fresh clone, documented startup, committed source, safe configuration and history audit |

## Delivery discipline

Every milestone has at least three substantive commits as listed in the plan.
Parts include implementation and checks; a passing build alone does not complete
a feature. The private ignored checklist records evidence, commit identifiers,
remaining work, and the next real eligible commit time. Do not replace missing
checks with claims of completion. Run the full mandatory acceptance review after
all feature milestones, then repeat the documented setup from a fresh checkout.
