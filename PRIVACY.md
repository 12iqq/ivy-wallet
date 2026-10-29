# Privacy

Ivy Wallet UAE Edition doesn't collect any data about you. There's no account, no ads, no analytics and no crash
reporting. There's no server either: everything you enter stays on your phone.

## What the app connects to

The only thing it downloads is exchange rates, from a free public list
([fawazahmed0/exchange-api](https://github.com/fawazahmed0/exchange-api)). That request doesn't include any of your
data.

Backups go only where you save them, for example a file in your own Google Drive.

The Windows app only reads your backup, and never changes it. It gets it in one of two ways:

- **Connect Google Drive**: you sign in with Google in your browser and choose the backup in Google's file picker. The
  app can then open that file and nothing else in your Google Drive. While it's open, it asks Google about once a minute
  whether the backup changed, and downloads it when it did. It keeps the sign-in on your computer so you don't have to
  sign in again; **Disconnect Drive** deletes it and cancels it with Google. You can also remove the app's access in
  your Google account, under [Third-party apps & services](https://myaccount.google.com/connections). Nothing else is
  sent to Google or anywhere else.
- **From your computer**: the newest backup in your `ivywallet-uae` folder, or a file you choose, which it remembers.
  Then it doesn't connect to the internet at all.

Files you export from it as CSV are saved where you choose.

## Permissions

| Permission | What it's for |
|---|---|
| SMS | Only if you turn on Auto-add transactions. Messages from the banks you choose are read on the phone to find transactions, and never sent anywhere. |
| Notification access | Only if you turn on Google Wallet payments. Android shows the app all notifications; it only looks at Google Wallet's payment notifications and ignores the rest. |
| Notifications | Detected transactions, card payment reminders, budget alerts and backup problems. You can turn each one off in Settings → Notifications. |
| Internet | Exchange rates. |
| Run at startup | So reminders and SMS checks keep working after the phone restarts. |
| Biometrics | Only if you turn on the app lock. |
| Files | Importing a backup or a bank statement, and saving backups, through Android's file picker. |

## Deleting your data

Uninstalling the app deletes everything it stored on the phone. **Settings → Delete all user data** does the same
without uninstalling. Backups you saved somewhere else stay where you put them.

Questions? [Open an issue](https://github.com/12iqq/ivy-wallet/issues).
