# Pocketta

Your money for the month, on your phone only. Android app (Kotlin, Jetpack Compose, Room, Glance widgets, Material You).

## Phase 1
- **Daily allowance**: what you can spend today = (budget − spent − bills/installments still due this month) ÷ days left.
- **Income**: fixed salary added by itself every month, plus any other income (gam'eya payout, borrowed money also saved as a debt, side work...).
- **Quick add** in 3 taps, or type `koshary 60` and it guesses the category. From the app, the + widget, the Quick Settings tile or the app shortcut.
- **Categories and sub-categories** with optional monthly limits.
- **Wallets** (cash, bank, Vodafone Cash, cards) with balances and transfers.
- **Bills & subscriptions**: weekly, monthly, every 3/6 months, yearly or every N days. Quarterly and yearly ones are spread over the months.
- **Installments**: consumer finance (valU, Aman, Contact...), credit card, or your own types.
- **Gam'eya**: your share, your turn, your payout.
- **Loans**: money you lent or borrowed, partial payments, WhatsApp reminder.
- **Reminders** with a "Mark paid" button, and an alert when you go over today's budget.
- **4 widgets**: Today's allowance, Add expense, This month, Next up.
- **Fingerprint lock** and **backup/restore** to a file.
- Month start day is up to you. EGP by default, other currencies with your own rate.

## Web version
The same app for any browser lives in `web/` (plain HTML/CSS/JS, no build step) and is published at https://architectahmedgaber-sys.github.io/Pocketta/ by `.github/workflows/pages.yml` when `web/` changes on main. Data stays in the browser. See `web/README.md`.

## Build
The APK is built by GitHub Actions (`.github/workflows/build.yml`). Run the workflow by hand with a version number to publish a release.
The release key is stored encrypted (`signing/release.jks.enc`). CI unlocks it with the `KEYSTORE_PASSPHRASE` secret, so new versions install over old ones. (The key used for 1.0.0 is retired.)
`./gradlew :core:test` runs the budget math tests.

## Releasing
Open a pull request, merge it to `main`. If `version.txt` holds a version without a release yet, the build publishes it as a GitHub release (the in-app updater reads that). Raise `version.txt` in the PR to ship a new version; otherwise main only builds a test APK.

## 1.25.0 (Quick AI entry)
- More > Quick AI entry: type a sentence in Egyptian Arabic ("كشري ٦٠ وسلفت باسم ٥٠٠"), check what was understood, tap Save. Nothing is saved before that tap.
- Always the model `claude-haiku-5-5` (one constant: `QuickAi.MODEL` in `core/.../QuickAi.kt`, `MODEL` in `web/js/quickai.js`). Every answer's `model` field is checked; any other model is thrown away and logged.
- Token use is logged per request; Settings shows the month's estimated cost. Prices are two constants (`INPUT_USD_PER_MILLION`, `OUTPUT_USD_PER_MILLION`), source https://www.anthropic.com/claude-haiku-5-5.
- The user's own Anthropic key: Android keeps it AES-GCM encrypted with an Android Keystore key (file `pocketta_quickai.xml`, excluded from backups and device transfer); web keeps it in its own localStorage entry. Neither is in the database, exports, web backups or cloud sync.
- Tests: `./gradlew :core:test` (`QuickAiTest`) and `node tests/web/quickai.test.mjs`.

## 1.24.0 (v3)
- Category budgets alert at 80% and at 100% (once per level per month), "Budgets" card on Month.
- Month-end forecast card on Today (over / close / on track, and the day the money runs out).
- Fixed bills can record themselves on their due date.
- Savings goals can have a target date ("you need X a month").
- Monthly report screen, CSV export (Excel/Google Sheets), search with filters.
- Lock: choose how long the app stays open after you leave; optional "hide in recent apps".
