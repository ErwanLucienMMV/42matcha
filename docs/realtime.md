# Chat, presence and live updates

Chat is persisted in PostgreSQL and authorized on every read/send/read receipt.
Both users must still like each other, and neither direction may be blocked.
Relationship and chat operations acquire the same pair lock. Unlike/block takes
effect immediately for new API operations, including an already open chat.

Messages use escaped Angular text, not HTML rendering. Latest history is paged
with `before` and new messages with `after`; stable message IDs avoid duplicates
when reconnecting. Read receipts have an explicit highest displayed message ID,
so a new concurrent arrival is not accidentally marked as read.

`/api/live` is an authenticated WebSocket for refresh hints. It checks the exact
configured origin and a valid HttpOnly session cookie. Account tokens are never
placed in the socket URL. Application writes still go through authenticated,
CSRF-protected HTTP endpoints. The socket carries no private message/profile data.

Refresh hints are sent only after the triggering database transaction commits.
The browser then fetches its own authorized state. A five-second polling fallback
recovers missed hints and unavailable sockets; delivery is tested against the
subject's ten-second maximum. New-message badges appear in the global navigation.

Presence reflects active authenticated sockets, including multiple tabs. A
five-second heartbeat checks session validity and requires a client reply;
unresponsive clients are closed after twenty seconds. Closed sockets no longer
count as online, and the last connection time is retained. Logout/reset revokes
affected sockets; reset also revokes all sessions for the account.

The implementation runs one backend service. Multiple backend replicas would
need a shared event transport; this Compose application does not claim support
for that deployment mode.

Endpoints: `GET /api/connections`, `GET` / `POST /api/chat/:id`,
`POST /api/chat/:id/read` with `through`, and `GET /api/updates` for own unread
message/notification counts. History batches contain at most fifty messages and
a `has_more` flag.
