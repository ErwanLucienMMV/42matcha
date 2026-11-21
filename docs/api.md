# API conventions

All application endpoints use `/api` and the same origin as the frontend. JSON
requests use `Content-Type: application/json`. Public profile payloads select
explicit fields; account credentials and email addresses never appear in them.

`GET /api/health` indicates process liveness. `GET /api/ready` returns HTTP 200
with `{"status":"ready"}` only when PostgreSQL is reachable; otherwise HTTP 503.

Errors have `error` (user-readable text) and, for application validation and
authorization failures, `code`. Expected failures use appropriate HTTP status
codes: 400 invalid input, 401 authentication required, 403 forbidden, 404
unavailable resource, 409 conflict, 413 request too large, 415 unsupported media,
and 429 request limit. Unexpected errors return a generic message without server
internals. They must be investigated; they are not an accepted evaluation outcome.

JSON form bodies are limited to 64 KiB. Photo endpoints have their own stricter
format/dimension/content checks and an upload-size limit. Numeric identifiers,
ranges, sorting choices and other values are validated before explicit SQL.
Parameters are always bound; SQL identifiers and sort expressions are allowlisted.

Authenticated sessions use HttpOnly cookies, and authenticated writes require a
matching CSRF header. Passwords and usable session/reset/verification tokens are
never included in API responses or logged. Account links are delivered by email.

Feature endpoints and event contracts are documented as their milestones are
implemented. Browser authorization is enforced by the API on each operation.

## Accounts

| Method | Path | Body / behavior |
| --- | --- | --- |
| POST | `/auth/register` | `username`, `email`, `first_name`, `last_name`, `password`; sends a verification email |
| POST | `/auth/verify` | Single-use `token`; verifies an account or requested email change |
| POST | `/auth/login` | `username`, `password`; requires verified email and sets session/CSRF cookies |
| GET | `/auth/session` | Own account under `user`, or `null`; never returns credential digests |
| POST | `/auth/logout` | Requires session and `X-XSRF-TOKEN`; invalidates this session |
| POST | `/auth/forgot-password` | `email`; neutral response, sends a link for an eligible account |
| POST | `/auth/reset-password` | Single-use `token`, strong `password`; revokes all existing sessions |
| POST | `/auth/resend-verification` | `email`; neutral response and renewed link for an unverified account |

All listed paths have the `/api` prefix. JSON form limits and origin validation
apply to public writes as well. Verification links expire after 24 hours, reset
links after 30 minutes, and sessions after seven days. Raw account tokens are held
in memory only while composing/sending the email; only SHA-256 digests are stored
in the token table. SMTP delivery failure aborts the account transaction so the
operation can be retried. The SMTP inbox necessarily contains the delivered link.
