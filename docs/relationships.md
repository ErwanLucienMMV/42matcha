# Profiles and relationship rules

Profile consultation shows public information, photos, interests, popularity and
presence. It excludes email, date of birth, stored coordinates and credentials.
The browser records a consultation with `POST /api/people/:id/visit` before fetching
the profile. The read-only GET can refresh relationship state without generating
another visit. Unique visitor-target rows retain the last visit time.

A user needs a selected own profile photo before sending a like. The target must
also have a profile photo to like. Likes are directed and unique. Two likes create
a connection; removing either one disables chat. Profile state distinguishes
outgoing interest, incoming interest and a mutual connection.

Unlike emits the required disconnection event when appropriate, then suppresses
subsequent notifications from that person to the user who removed the like.
A renewed like removes that user's suppression; a renewed mutual connection
clears suppression in both directions. Existing history remains persisted.

Blocking overrides both directions: profiles disappear from discovery/search,
public views/photos are unavailable, likes are removed, and chat/notifications
are prohibited. Unblocking removes only the acting user's block and does not
restore likes. Reports are stored once per reporter-target pair with a reason.

Every relationship change acquires the same transaction advisory lock for the
unordered pair of user IDs. Chat reads/sends use that lock too, preventing races
between permission checks and unlike/block changes.

## Endpoints

All paths use `/api`; all are authenticated and writes require CSRF.

- `GET /people/:id`: safe public profile and relationship state.
- `POST /people/:id/visit`: record a real consultation.
- `POST` / `DELETE /people/:id/like`: express or withdraw interest.
- `POST` / `DELETE /people/:id/block`: block or unblock.
- `POST /people/:id/report`: record a fake-account report with `reason`.
- `GET /me/visitors`, `/me/likes`, `/me/blocks`: own activity and controls.

Notification events are stored transactionally with the triggering interaction;
recipient read/unread controls and live UI are described in [live updates](realtime.md).
