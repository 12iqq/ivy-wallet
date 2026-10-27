# Ivy Wallet UAE for Windows

Shows the phone app's reports on your computer. It reads the backup file the phone app saves, so you keep adding
transactions on the phone and look at them here on a bigger screen.

## Setting it up

1. In the phone app, go to **Settings → Automatic backup**, choose **Daily**, and save the backup file in your
   Google Drive.
2. Install [Google Drive for desktop](https://www.google.com/drive/download/) so the file is on your computer too.
3. Install the desktop app and choose that file. When the phone saves a new backup, the desktop app shows it within
   a minute.

The app only reads that file. It doesn't connect to the internet.

## What it shows

- **Overview:** your accounts and credit cards, this month's spending and income, and the last 12 months
- **Reports:** a month or a year, spending or income, by category and by place, like Reports in the phone app
- **Transactions:** everything, newest first, with search
- **Recurring charges:** subscriptions, bills and fees that come back regularly

You can't add or change transactions on the computer yet.

## Building it

This is its own Gradle build, separate from the Android app. You need Java 17. From this folder:

- `./gradlew run` starts it
- `./gradlew test` runs the tests
- `./gradlew packageMsi` builds the Windows installer (on Windows)

The report maths is the phone app's own code (`feature/insights`), compiled into the desktop app too, so both show
the same numbers.
