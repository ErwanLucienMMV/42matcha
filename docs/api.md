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
