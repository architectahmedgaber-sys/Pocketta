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

## Build
The APK is built by GitHub Actions (`.github/workflows/build.yml`). Run the workflow by hand with a version number to publish a release.
`signing/release.jks` is the release key (keep this repo private) so new versions install over old ones.
`./gradlew :core:test` runs the budget math tests.
