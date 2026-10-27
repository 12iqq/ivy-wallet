# Ivy Wallet UAE Edition

[![APK](https://github.com/12iqq/ivy-wallet/actions/workflows/apk.yml/badge.svg)](https://github.com/12iqq/ivy-wallet/actions/workflows/apk.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

A free money manager for Android, set up for banking in the UAE.

I've used [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet) to keep track of my money for years. It's open source,
and it stopped being updated in November 2024. Instead of making yet another new app, I took Ivy Wallet, fixed the
problems I kept running into, and added what I wanted for everyday use here, like credit cards and adding transactions
from bank SMS.

It's free. There are no ads, no account to sign up for and no tracking, and your data stays on your phone.

If something doesn't work, or you have an idea, [open an issue](https://github.com/12iqq/ivy-wallet/issues) and I'll
take a look.

## What it adds to Ivy Wallet

- Credit cards: the credit limit, statement and due dates, a reminder before each payment is due, what you've spent
  this statement, and what's left when you pay the card off
- Card bills your bank sends by SMS become planned payments, and are marked paid when you pay them
  ([which banks](docs/How-to-use.md#card-bills))
- Transactions added from your bank's SMS alerts and from Google Wallet payments. You check them in a list first, or
  have them added straight away.
- Works with DIB, ADCB, Emirates NBD, FAB, Mashreq, RAKBANK, Emirates Islamic, ADIB, Liv, Wio, Tabby, Tamara and
  [more](docs/How-to-use.md#banks)
- It remembers what you call each shop and which category it goes in
- Utility bills from SEWA's texts: electricity, water and gas, bill by bill, and each bill can wait as a planned
  payment until it's paid. It's SEWA only for now. If you're in Dubai and want DEWA,
  [open an issue](https://github.com/12iqq/ivy-wallet/issues/new/choose) with one of their bill texts and I'll add it.
- Reports by month or year: spending by category, where the money went, and recurring charges
- Split a Tabby or Tamara purchase into its payments
- Automatic backups, daily or weekly, to a file you choose (for example in Google Drive)
- Budget alerts, quick-add shortcuts and a duplicate finder

[How to use it](docs/How-to-use.md) explains each of these step by step.

## Install

1. Download `IvyWalletUAE-vX.Y.Z.apk` from the [latest release](https://github.com/12iqq/ivy-wallet/releases/latest)
   and open it. Updates install over the old version and keep your data.
2. The app isn't on the Play Store, so Play Protect may warn you. Tap **More details → Install anyway**. If that option
   isn't there, turn off **Play Store → Play Protect → ⚙ → Scan apps with Play Protect**, install, then turn it back on.
3. To let it read bank SMS on Android 13 or newer, first go to **Settings → Apps → Ivy Wallet UAE → ⋮ → Allow
   restricted settings**, then turn on SMS access in the app.

Moving from the original Ivy Wallet: in Ivy Wallet go to **Settings → Backup data**, then in this app **Settings →
Import data**. Import into an empty app, because importing adds to what's already there.

Had version 1.0.x of this app? Version 1.1.0 installs as a separate app. The
[1.1.0 release notes](https://github.com/12iqq/ivy-wallet/releases/tag/v1.1.0) show how to move your data over.

## Privacy

The app doesn't collect anything about you. It has no analytics or crash reporting, and it never sends your
transactions or messages anywhere. The only thing it downloads is exchange rates. [PRIVACY.md](PRIVACY.md) explains
what each permission is for.

## Building it yourself

You need Java 17 and Android Studio. Open the project and run the `app` configuration, or run `./gradlew assembleDemo`
for a test APK.

To publish a release, raise `version-name` and `version-code` in `gradle/libs.versions.toml` and push to `main`.
GitHub Actions builds and signs the APK and publishes it with the notes in `docs/release-notes/v<version>.md`.
How the SMS reading works, and how to add a bank, is in [docs/Auto-Capture.md](docs/Auto-Capture.md).

## Credits and license

Built on [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet) by Iliyan Germanov and its contributors, and licensed
under [GPL-3.0](LICENSE) like the original. You're free to use, change and share it under the same license. It comes
without any warranty.

This project isn't affiliated with the Ivy Wallet team or with any bank.
