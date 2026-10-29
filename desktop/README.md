# Ivy Wallet UAE for Windows

Shows the phone app's reports on your computer. It reads the backup file the phone app saves, so you keep adding
transactions on the phone and look at them here on a bigger screen.

## Setting it up

1. In the phone app, go to **Settings → Automatic backup** and choose **Daily**. When it asks where to save, choose
   Google Drive.
2. Download `IvyWalletUAE-Desktop-vX.Y.Z.msi` from the
   [latest release](https://github.com/12iqq/ivy-wallet/releases/latest) and install it. Windows may say it protected
   your PC, because not many people have downloaded the app yet. Click **More info → Run anyway**.
3. Open **Ivy Wallet UAE** from the Start menu and click **Connect Google Drive**. Your browser opens Google's sign-in:
   sign in with the same Google account, allow the app to see the files you choose, and choose your backup
   (`ivywallet-uae.zip`) in Google's file picker. From then on the app gets each new backup by itself, within a minute
   of it reaching Google Drive.

The app can only see the backup you choose, nothing else in your Google Drive. **Change backup** chooses another one,
and **Disconnect Drive** signs the app out of Google. You can also remove its access in your Google account, under
[Third-party apps & services](https://myaccount.google.com/connections). Without internet, it keeps showing the last
backup it got.

### Without connecting Google

If you use [Google Drive for desktop](https://www.google.com/drive/download/), save the phone's backup in a Google Drive
folder called `ivywallet-uae` and the app finds the newest backup in it by itself. It looks for the folder in "My Drive"
on the drive Google Drive for desktop adds (G: unless that's taken), and in a "My Drive" folder in your user folder if
you mirror files instead. Or choose a backup file yourself.

The app only reads the backup. It connects to the internet only to get it from Google Drive, when you've connected it.

To update it, open the new version's installer. It sees that you have it already, and offers **Update** or
**Uninstall** (**Repair** or **Uninstall** if you already have that version). To remove it, open **Uninstall Ivy Wallet
UAE** in its Start menu folder, **Uninstall Ivy Wallet UAE.exe** in `%LOCALAPPDATA%\Ivy Wallet UAE`, or use Windows
Settings → Apps. Your backups aren't touched.

## What it shows

- **Overview:** your accounts and credit cards, this month's spending and income, and the last 12 months or all time
- **Reports:** a month, a year or all time, spending or income, by category and by place, like Reports in the phone
  app
- **Highlights:** fun facts about everything since your first transaction, like where most of your money goes, your
  biggest purchase, your priciest month and the day of the week you spend the most, and each year side by side
- **Transactions:** everything, newest first, with search and the total of what it finds
- **Recurring charges:** subscriptions, bills and fees that come back regularly

Point at a chart to see the amounts for that month or day. **Export CSV** saves what a page shows for Excel or Google
Sheets: transactions with their category, account, tags and the bank's name for the shop, or a row for each month or
year with what went to each category and account. You can't add or change transactions on the computer yet.

## Building it

This is its own Gradle build, separate from the Android app. You need Java 17. From this folder:

- `./gradlew run` starts it
- `./gradlew test` runs the tests
- `./gradlew packageMsi` builds the Windows installer (on Windows). It includes `Uninstall Ivy Wallet UAE.exe`, built
  from `installer/Uninstaller.cs` with the C# compiler that comes with Windows. jpackage can't add an Uninstall
  shortcut, or a page for when the app is already installed, so `installer/finish-installer.ps1` adds them to the
  finished installer. CI tries installing and uninstalling it with `installer/test-install.ps1`, and clicks through
  its pages with `installer/test-choices.ps1`.

**Connect Google Drive** needs the app's registration with Google, which CI builds in from two secrets,
`GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`: [docs/Google-Drive-setup.md](../docs/Google-Drive-setup.md) shows how to
make them. The build's log says whether it has them ("Connect Google Drive: on"). A build without them has no Google
Drive button, and finds the backup in the `ivywallet-uae` folder or a file you choose, as before.

Each release on GitHub gets the installer too, built by `.github/workflows/release.yml`.

The report maths is the phone app's own code (`feature/insights`), compiled into the desktop app too, so both show
the same numbers.
