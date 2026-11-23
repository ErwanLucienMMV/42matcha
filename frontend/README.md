# Angular frontend

Standalone Angular 22 components use signals for asynchronous state, Angular
forms for local input feedback, and an HttpClient wrapper for same-origin API
requests. The server validates every operation independently. Session cookies
stay HttpOnly; Angular sends the readable CSRF cookie on authenticated writes.

Nginx serves the production bundle and proxies `/api`, including authenticated
WebSockets. Use the repository's Docker Compose flow to keep one origin for
cookies, CSRF and live updates. `npm ci && npm run build` checks the production
bundle locally with a supported Node version. End-to-end tests live in `../qa`.

Pages cover accounts, profile/photo/location editing, discovery, search, public
profiles, activity/moderation, messages and notifications. Global live updates
refresh unread badges and page state, with a polling fallback. User text renders
through Angular interpolation. Styles use system fonts and local CSS artwork.

See [setup and verification](../docs/running.md), [API](../docs/api.md) and
[live behavior](../docs/realtime.md).
