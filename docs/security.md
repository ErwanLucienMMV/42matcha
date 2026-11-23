# Security model

The API uses the maintained `org.zoomba-lang:spark-core` 3.0.5 micro-framework
with Jetty 12.1.15. Persistence is handwritten, parameterized JDBC SQL. There
is no ORM, generated account manager, or external form-validation framework.

Passwords use bcrypt cost 12 and independent salts. The policy requires 12
characters, upper/lowercase, a number and a symbol, within bcrypt's 72 UTF-8
byte limit. A bundled 370,105-word public-domain English dictionary rejects
single dictionary words with numeric/symbol decorations and common leet
substitutions. Use a long, unrelated multiword password. Dictionary source:
<https://github.com/dwyl/english-words/blob/master/words_alpha.txt>, source
SHA-256 `3ed0c94610d8bcf7c11bbb49c56aa49c7234d32b66824df91f554169e572da48`.
Its Unlicense is bundled beside the compressed dictionary.

Session cookies are opaque random tokens: HttpOnly, SameSite=Lax, seven-day
expiry, and Secure when COOKIE_SECURE=true. Only token and CSRF digests are
stored. Every authenticated write requires the matching X-XSRF-TOKEN header;
foreign Origin writes are rejected. Verification links expire in 24 hours,
password-reset links in 30 minutes, and both are single-use. Reset revokes
all sessions and their live sockets. Email changes require verification of
the new address before replacing the previous address.

Registration, authentication, token requests and photo uploads are rate
limited. The API has no published port. Nginx overwrites X-Forwarded-For with
the client address; never expose the API directly while trusting that header.
The default registration allowance is 30/hour/address. `compose.qa.yaml`
temporarily sets 300/hour for local acceptance fixtures; restore normal
Compose configuration after running the suite. Limits are in-memory and
reset on process restart; deployment across replicas needs a shared limiter.

All object reads and writes are authorized on the server, including photos,
chat and notification ownership. Blocking is checked in both directions;
relationship changes serialize on pair-specific PostgreSQL advisory locks.
Live events contain refresh hints, and clients fetch authorized HTTP data.
Sockets require the exact application Origin and a valid session, cap client
counts and frame sizes, and use bounded asynchronous sends and heartbeats.

Uploads are bounded JPEG/PNG content, decoded, dimension-checked and re-encoded
before storage in PostgreSQL. Names cannot select filesystem paths. Angular
renders user text with interpolation. Nginx supplies CSP, frame protection,
content-type and referrer policies; access logs omit query strings containing
email tokens. Local configuration, reports, traces and checklist are ignored.

On 22 November 2026, the resolved Java runtime packages (50) were checked
against OSV with zero reported matches, and `npm audit --omit=dev` reported
zero frontend runtime advisories. These are dated dependency checks, not a
guarantee against future vulnerabilities. Update pinned versions and image
digests together, then repeat the acceptance suite before deployment.
