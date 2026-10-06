# Ivy Wallet — UAE edition

[![APK](https://github.com/12iqq/ivy-wallet/actions/workflows/apk.yml/badge.svg)](https://github.com/12iqq/ivy-wallet/actions/workflows/apk.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

A personal fork of [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet), the open-source money manager for Android.
The original project stopped being maintained in November 2024. This fork keeps Ivy's look and feel. It fixes the bugs
I ran into and adds features for people who bank in the **United Arab Emirates**: credit cards, and transactions added
automatically from bank SMS alerts.

> This is a hobby project for my own use, shared in case it helps others in the UAE. It isn't on Google Play and isn't
> affiliated with the original Ivy Wallet team or with any bank.

## What's different from Ivy Wallet

### Fixes
- **Planned payments** now show up reliably. Upcoming occurrences are created automatically, both one-time and recurring
  ones, and that includes transfers between accounts. Paying one no longer loses the date it was due.

### New features
- **Credit card accounts** with:
  - a credit limit, statement day and payment due day
  - a usage bar (green, orange or red) and a "Due in N days" reminder on the card
  - their own section in the Accounts tab
- **Auto-add transactions from bank SMS**:
  - picks up card and account alerts on your phone and puts them in a review list
  - **nothing is uploaded**, every message is processed on the device
  - you choose which banks to read, link each card (by its last 4 digits) to an account, and approve, edit or skip each one
  - learns the category and title you pick for each merchant
  - warns about possible duplicates
  - can optionally add transactions without review
- **UAE bank list**, user-selectable: DIB, ADCB, Emirates NBD, Liv, FAB, Mashreq, RAKBANK, Emirates Islamic, SIB, ADIB,
  CBD, Wio, Ajman Bank, NBF, CBI, HSBC, Citi, Tabby, Tamara. You can add more. See [docs/Auto-Capture.md](docs/Auto-Capture.md).
- A cleaner Settings screen, without the original project's community, share and rating links.

Your existing Ivy Wallet backups (`.zip`) import as-is.

## Install

1. Open the latest successful [APK workflow run](https://github.com/12iqq/ivy-wallet/actions/workflows/apk.yml) and download
   the `Ivy-Wallet-Demo.apk` artifact. You need to be signed in to GitHub.
2. Android's Play Protect may block the install because the app isn't from the Play Store. Choose **More details →
   Install anyway**. If there's no such option, temporarily turn off **Play Store → Play Protect → ⚙ → Scan apps with
   Play Protect**, then turn it back on afterwards.
3. To let the app read bank SMS, Android 13+ requires one more step for apps installed outside the Play Store:
   **Settings → Apps → Ivy Wallet → ⋮ → Allow restricted settings**, then grant the SMS permission.
4. In your old Ivy Wallet, go to **Settings → Backup data**, then in this app use **Settings → Import data**.

## Build it yourself

- Java 17+
- The latest stable Android Studio

Clone the repo, open it in Android Studio, and run the `app` configuration. Use `./gradlew assembleDemo` for a debug APK.
Contribution guidelines from the original project are in [CONTRIBUTING.md](./CONTRIBUTING.md).

## Tech Stack

### Core

- 100% [Kotlin](https://kotlinlang.org/)
- 100% [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material3 design](https://m3.material.io/) (UI components)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) (structured concurrency)
- [Kotlin Flow](https://kotlinlang.org/docs/flow.html) (reactive data stream)
- [Hilt](https://dagger.dev/hilt/) (DI)
- [ArrowKt](https://arrow-kt.io/) (functional programming)


### Testing
- [JUnit4](https://github.com/junit-team/junit4) (test framework, compatible with Android)
- [Kotest](https://kotest.io/) (unit test assertions)
- [Paparazzi](https://github.com/cashapp/paparazzi) (screenshot testing)

### Local Persistence
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) (key-value storage)
- [Room DB](https://developer.android.com/training/data-storage/room) (SQLite ORM)

### Networking
- [Ktor client](https://ktor.io/docs/getting-started-ktor-client.html) (HTTP client)
- [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) (JSON serialization)

### Build & CI
- [Gradle KTS](https://docs.gradle.org/current/userguide/kotlin_dsl.html) (Kotlin DSL)
- [Gradle convention plugins](https://docs.gradle.org/current/samples/sample_convention_plugins.html) (build logic)
- [Gradle version catalogs](https://developer.android.com/build/migrate-to-catalogs) (dependencies versions)
- [GitHub Actions](https://github.com/12iqq/ivy-wallet/actions) (CI/CD)

### Other
- [Firebase Crashlytics](https://firebase.google.com/products/crashlytics) (stability monitoring)
- [Timber](https://github.com/JakeWharton/timber) (logging)
- [Detekt](https://github.com/detekt/detekt) (linter)
- [Ktlint](https://github.com/pinterest/ktlint) (linter)
- [Slack's compose-lints](https://slackhq.github.io/compose-lints/) (linter)

## Credits & license

Built on top of [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet) by Iliyan Germanov and the Ivy Wallet contributors.
Licensed under [GPL-3.0](LICENSE), like the original. You're free to use, modify and share it under the same license.
The software is provided as-is, without warranty.
