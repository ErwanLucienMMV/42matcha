# Evaluation dataset

Run `./scripts/seed.ps1` on Windows or `sh scripts/seed.sh` on Linux/macOS. It builds
and starts the local stack, generates a private `SEED_PASSWORD` if missing, and
creates the evaluation dataset. Nothing is sent to an external mail provider.

The dataset contains 500 distinct accounts named `demo_001` through `demo_500`,
with matching `@example.test` emails. They are synthetic adult profiles with
varied names, ages, genders, orientations, biographies, reusable tags and twelve
approximate cities. Each account has an independently salted bcrypt hash.
Use the password in your ignored `.env` to sign in; it is never committed or
printed by the seed command.

Each profile has a generated abstract evaluation picture; every twenty-fifth
profile has three to exercise the gallery. These are synthetic image fixtures,
not photographs of real people. Relationship fixtures cover received likes and
visits, popularity differences, mutual connections, messages, unread events,
an unlike, a fake-account report and a blocked pair.

- `demo_001` and `demo_002`: mutual connection and conversation.
- `demo_021` blocks `demo_022`: discovery/view/chat exclusion.
- `demo_031` has unliked `demo_032`: disconnected chat and unlike event.
- `demo_041` reports `demo_042`: stored fake-account report.

Existing account/profile values are preserved, and a conflicting non-demo
username stops seeding. Photo insertion respects the five-photo constraint.
Relationship fixtures are applied once under a versioned database marker and
advisory lock, so reruns do not duplicate messages or notifications or undo later
relationship changes. No application data is deleted by the seed command.

`docker compose exec -T backend java -jar app.jar audit-seed` validates the actual
database and prints aggregate counts without credentials. Run the seed a second
time and repeat the audit to check repeatability.
