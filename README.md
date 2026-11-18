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
