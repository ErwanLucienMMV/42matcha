# Matcha implementation plan

Mandatory scope and its original acceptance gates are described below.
Bonus work is deferred at the user's request. See the setup and verification guides for
the current delivery state and verification commands.

See [local startup and checks](running.md) and the
[architecture and acceptance map](architecture.md).

## Subject and stack

Source: the supplied French Matcha subject, version 6.0, 12 PDF pages
(printed pages 1-11, plus the cover). Page references below use printed numbers.

The subject is the application's specification. The requested stack, containers,
commit spacing, private checklist, and commit identity are repository requirements
from the user. The subject does not impose a commit schedule or containers.

The backend needs an adjustment to the requested Spring Boot approach:

- Chapter III, page 4 restricts frameworks to micro-frameworks without a bundled
  ORM, validator, or user-account manager. It explicitly lists Spark for Java.
- Chapter III, pages 4-5 requires handwritten database queries.
- Use **Java + Spark Java + Maven**, with JDBC and parameterized handwritten SQL.
  Maven is the build tool, not a competing backend framework. Avoid Spring Boot
  starters, Spring Security account management, Hibernate/JPA, and automatic
  validation frameworks. Write application validation and authentication flows.
  Focused libraries for password hashing, JSON, mail, and other utilities are
  consistent with the subject's permission to use necessary libraries.
- Use **PostgreSQL** and **Angular**, both permitted by the subject.
- Use **Docker Compose** with separate backend, frontend/web-proxy, and database
  services; add a local SMTP test service. Build with multi-stage Dockerfiles.
- Select and pin compatible maintained tool and dependency versions during M01,
  checking their official documentation at that point.

## Commit workflow

- Author and committer: `ErwanLucienMMV <ultimemmv@gmail.com>`.
- At least three substantive, reviewed commits per milestone. Use messages with
  the milestone and part, for example `M01.1: add backend health service`.
  Complete and verify each part before its commit; complete the milestone only
  after all three parts and its acceptance checks pass.
- Each consecutive milestone commit must occur at least **3,600 real seconds**
  after the previous one. Keep actual timestamps; do not backdate, postdate,
  amend timestamps, or bypass the spacing hook to simulate elapsed time.
- The versioned `.githooks/pre-commit` checks the elapsed time against HEAD.
  Activate it in each checkout with `git config core.hooksPath .githooks`.
  Hooks are a local safeguard and can be bypassed; always check timestamps too.
- Verify before committing: `git diff --cached --check`, inspect the staged diff,
  run the milestone's checks, and inspect the previous commit's time.
- Use descriptive messages without assistant credit or co-author trailers.
- Maintain `LOCAL_CHECKLIST.txt` locally; it is explicitly ignored by Git.
  Never force-add it. Keep credentials in an ignored `.env`; commit only an
  `.env.example` containing names and inert placeholders.
- Work may continue during a spacing interval. The user authorized a local
  queue of reviewed checkpoints. Commits use real timestamps at least one hour
  apart; a milestone is committed only after all three actual commits exist.

## Ordered mandatory milestones

| ID | Work and acceptance gate | Suggested commit message |
| --- | --- | --- |
| M00 | Review the entire subject, resolve stack constraints, list every requirement, create the private checklist, and establish the commit policy. | `docs: define Matcha requirements and implementation milestones` |
| M01 | Create the Maven backend and Angular frontend, Dockerfiles, Compose networking, persistent PostgreSQL storage, local SMTP, reverse proxy, health checks, and placeholder-only configuration example. Demonstrate a clean container build and browser-to-API health request. | `build: add containerized Java Angular and PostgreSQL foundation` |
| M02 | Design the schema, constraints, indexes, migrations, JDBC connection handling, handwritten SQL repositories, transactions, API payloads, and error handling. Verify migrations on a fresh database and rollback behavior for failed writes. | `feat: add relational schema and JDBC persistence` |
| M03 | Implement registration, password policy and hashing, verification email, login, sessions, password-reset email, and global logout. Build the Angular account pages. Verify invalid, expired, reused, and unauthorized flows. | `feat: implement verified accounts and authentication` |
| M04 | Implement editable profiles, reusable interest tags, image handling, five-photo maximum, selected profile picture, and profile-completion flow. Add frontend forms and backend ownership checks. Verify all edits and upload constraints. | `feat: add editable profiles tags and photos` |
| M05 | Implement consent-based GPS, mandatory manual location fallback, location editing, age data for matching, and a documented public popularity score. Verify denied/unavailable GPS and location-dependent matching eligibility. | `feat: add location preferences and popularity scoring` |
| M06 | Implement compatible suggested profiles, bisexual default, proximity-first multi-criterion ranking, sorting, filtering, and paginated Angular discovery. Verify deterministic ranking and all sort/filter combinations. | `feat: add ranked profile suggestions` |
| M07 | Implement advanced search across age range, popularity range, location, and one or multiple tags; support combined criteria, sorting, filtering, and pagination. Test boundary values and safe query construction. | `feat: add advanced profile search` |
| M08 | Implement public profile views, visit history, visitor/liker lists, likes, mutual connections, unlike, fake-account reports, blocks, and relationship indicators. Verify all permissions, state transitions, and block exclusions. | `feat: add profile interactions and relationship rules` |
| M09 | Implement persisted chat for mutually connected users, authenticated real-time transport, online status, last-seen time, and a site-wide new-message indicator. Measure delivery within 10 seconds and immediate access revocation after unlike/block. | `feat: add connection chat and presence` |
| M10 | Implement persistent real-time notifications for all five required events, unread tracking, and a site-wide unread indicator. Verify delivery within 10 seconds, reconnect handling, and suppression rules. | `feat: add real-time notifications and unread tracking` |
| M11 | Supply a repeatable evaluation seed with at least 500 distinct profiles and varied ages, locations, genders, preferences, tags, photos, popularity, and relationship states. Verify the dataset exercises discovery, search, chat, and notifications. | `test: add diverse evaluation profiles and scenarios` |
| M12 | Complete security and end-to-end verification: SQL injection, XSS, sessions, CSRF where applicable, object ownership, uploads, token abuse, blocked relationships, concurrent requests, browser compatibility, mobile layout, and the mandatory acceptance matrix. Resolve unintended server/client errors and warnings. | `fix: complete security and mandatory acceptance checks` |
| M13 | Verify a fresh clone with documented commands, persistent data, at least 500 profiles, Chrome and Firefox, responsive pages, and measured real-time delivery. Complete setup/architecture/API documentation and an evaluation walkthrough. Audit tracked files and commit spacing. | `docs: finalize setup and evaluation guide` |

Each feature milestone includes its API, Angular interface, validation, persistence,
and relevant tests; frontend implementation is not deferred to the last milestone.

### Three-commit breakdown

| Milestone | Part 1 | Part 2 | Part 3 |
| --- | --- | --- | --- |
| M00 | Subject review (existing commit) | Revised execution and commit policy | Architecture and acceptance traceability |
| M01 | Maven service and health API | Angular application shell | Compose services, proxy and startup verification |
| M02 | SQL schema and migrations | JDBC transactions and repositories | API conventions and database integration verification |
| M03 | Registration and verification | Login, sessions, reset and logout | Angular account flows and abuse/authorization verification |
| M04 | Editable profile data and tags | Safe photo storage and selection | Angular profile editor and concurrent-limit verification |
| M05 | Age, preferences and popularity | Consented GPS and manual location | Angular location flow and matching eligibility verification |
| M06 | Compatibility and ranking SQL | Discovery sort/filter API | Angular discovery and ranking verification |
| M07 | Combined search query and API | Angular advanced search | Boundary, injection and pagination verification |
| M08 | Visits, likes and connections | Blocks, reports and revocation | Angular interactions and transition verification |
| M09 | Chat authorization and persistence | Live delivery and presence | Angular messaging and delivery/revocation verification |
| M10 | Persistent notification events | Unread state and live delivery | Angular global indicators and suppression verification |
| M11 | Deterministic diverse profile seed | Relationship and evaluation fixtures | Seed repeatability and dataset verification |
| M12 | Security audit and fixes | Cross-browser and mobile corrections | Full mandatory acceptance regression |
| M13 | Setup and architecture documentation | Fresh-clone evaluation walkthrough | Final repository, commit and acceptance audit |

The one-hour interval applies between every pair of consecutive commits, including
parts of the same milestone. M00's existing commit remains intact; its two remaining
parts will meet the updated policy. No bonus work is currently authorized.

## Detailed mandatory acceptance checklist

### M01-M02: foundation and data

- Provide at least a header, main content, and footer; establish a responsive
  application shell and navigation with global logout/message/notification UI.
- Keep Angular-to-API traffic on the same origin through the web proxy where
  possible. Configure TLS for deployed GPS and secure-cookie operation.
- Store accounts, profiles, reusable tags and joins, photo metadata, verification
  and reset tokens, sessions, directed likes, visits, blocks, reports, chat
  messages, notifications, and presence/last-seen data.
- Enforce unique usernames/emails, foreign keys, unique directed relationships,
  no self-interactions, ownership, and safe concurrent writes.
- Prevent more than five photos under concurrent uploads; keep picture selection
  consistent when a photo is deleted. Define atomic mutual-like transitions.
- Use prepared statements for values and allowlists for dynamic sort columns.
  Do not use ORM-generated queries. Version schema migrations as plain SQL.
- Define REST contracts and real-time event payloads, pagination, validation
  errors, authorization checks, and consistent expected failure responses.

### M03: registration and login (IV.1, page 6)

- Register with email, username, last name, first name, and a secure password.
- Reject commonly used English words as passwords; define and test a meaningful
  password-strength policy in addition to length. Hash passwords with a suitable
  password-hashing algorithm and unique salts; never store plaintext passwords.
- Send a unique email-verification link and require verification for account use.
- Log in with username and password; allow a reset request by email.
- Use unpredictable, expiring, single-use verification and reset tokens. Store
  token digests, not usable raw tokens; invalidate affected sessions after reset.
- Allow one-click logout from every page and invalidate the server-side session.
- Validate all forms on the client and server; limit abusive authentication and
  token requests without exposing account secrets.

### M04-M05: profiles (IV.2, pages 6-7)

- Collect gender, sexual preferences, biography, reusable interest tags, and up
  to five photos, one designated as the profile photo.
- Allow editing those fields, first/last name, email, and location at any time.
  Reverify changed email addresses before treating them as verified.
- Collect date of birth or equivalent age data because browsing/search require
  age. This is an implementation prerequisite, not an explicit registration field.
- Show who visited the user's profile and who liked it.
- Publish a consistent popularity score; document its formula and test updates.
  The subject delegates the exact formula to the implementation.
- Request explicit consent before accessing GPS. Use neighborhood-level location
  for required matching; do not disclose raw precise coordinates unnecessarily.
- If GPS is declined, unavailable, or fails, require manually entered approximate
  city/neighborhood before matching. Do not silently substitute IP tracking.
- Let users correct their location in their profile at any time.

### M06-M07: discovery and search (IV.3-IV.4, page 7)

- Suggest profiles that correspond to the user's sexual preferences and handle
  bisexuality; treat unspecified orientation as bisexual by default.
- Define consistent compatibility rules and verify heterosexual, homosexual,
  bisexual, and unspecified preferences. Exclude self and blocked relationships.
- Rank using geographical proximity, shared-tag count, and popularity, giving
  priority to users in the same geographical area. Document tie-breaking.
- Allow suggestion sorting and filtering by age, location, popularity, and common
  tags, with useful empty/error/loading states.
- Advanced search accepts one or more criteria: age range, popularity range,
  location, and one or several interest tags. Define multiple-tag semantics.
- Search results are also sortable and filterable by age, location, popularity,
  and interest tags. Paginate consistently without duplicate/missing rows.

### M08: profile consultation (IV.5, page 8)

- Show public profile information while excluding email, password, password
  hashes, authentication tokens, and other internal credentials.
- Record profile visits and expose the owner's visitors and received likes.
- Allow liking another user's profile photo only if the acting user has a
  profile photo. Mutual likes create a connection and enable chat.
- Show the other user's popularity, whether they liked the viewer, and whether
  they are connected. Offer unlike/disconnection clearly.
- Show online status; when offline, show the last connection date and time.
- Allow reporting a fake account and blocking a user.
- Blocking removes that user from discovery/search and prevents notifications
  and chat; enforce it on the backend and on existing real-time connections.
- Unliking disables chat and suppresses subsequent notifications from that user.
  Send the required disconnection notification for the unlike transition itself
  before applying suppression to subsequent events.

### M09-M10: chat and notifications (IV.6-IV.7, pages 8-9)

- Permit chat only between mutually liked, unblocked users. Authorize each send
  and read, including after a relationship changes during an open connection.
- Deliver chat messages within **10 seconds maximum**. Show new-message arrival
  from every page, persist messages, and recover after reconnecting.
- Maintain online/offline state and last-seen timestamps, handling multiple tabs
  and disconnected clients without leaving stale online indicators.
- Notify when a user receives a like, a profile visit, a message, a reciprocal
  like that creates a connection, or an unlike from a connected user.
- Deliver notifications within **10 seconds maximum**. Persist read/unread state
  and expose unread notifications from every page.
- Avoid duplicate state transitions and unwanted repeated alerts. Enforce block
  and unlike suppression for both stored and live events.

### M11-M13: evaluation, security, and delivery (III, VI)

- Provide **at least 500 distinct profiles** in the evaluation database.
- Support the latest Chrome and Firefox at evaluation time and acceptable mobile
  layouts. Verify actual browser flows, not only build success.
- Validate every form and upload. Reject unauthorized content; inspect image
  content, cap size/dimensions, choose server-controlled names and paths, and
  prevent executable uploads or arbitrary file access.
- Test SQL injection, unsafe HTML/JavaScript rendering, CSRF as applicable to
  session design, unauthorized object access, session expiry, and token reuse.
- Keep credentials, API keys, and local configuration in an ignored `.env`.
  Verify no secrets are present in Git history or frontend bundles.
- Resolve unintended application errors, warnings, and notices on both server
  and browser during the evaluation flows.
- Run meaningful backend unit/integration tests, PostgreSQL-backed data checks,
  Angular checks, cross-browser end-to-end flows, and timed live-event tests.
- Document prerequisites, environment fields, container commands, migrations,
  seeding, reset procedures, architecture, endpoints, and evaluation scenarios.
- Verify file names and submission contents; only committed repository work is
  evaluated. Audit commit identity, messages, and actual one-hour spacing.

## Optional milestones after mandatory completion (V, page 10)

Bonuses are evaluated only when every mandatory feature works without defects.
These are possible extensions, not prerequisites for the mandatory release.

| ID | Optional work | Acceptance gate |
| --- | --- | --- |
| B01 | External authentication strategies (the subject calls these OmniAuth). | Use a Java-compatible OAuth/OIDC approach with safe linking and verified identity; it does not require adding Ruby OmniAuth to this Java project. |
| B02 | Personal photo gallery, drag-and-drop uploads, and basic crop/rotate/filter editing. | Preserve mandatory profile-photo limits and upload protections. |
| B03 | Interactive user map using more precise JavaScript GPS. | Obtain explicit consent for the additional precision; retain manual fallback for mandatory matching. |
| B04 | Audio/video chat for connected users. | Enforce relationship authorization, call lifecycle, and camera/microphone permissions. |
| B05 | Plan dates or real-life events between matched users. | Restrict access to eligible connected users and validate time/location changes. |

Commit each implemented optional milestone separately under the same one-hour
spacing and identity rules, after completing its own verification.
