# Auto-add transactions (bank SMS & statements)

Settings → **Auto-add transactions** lets you pick how transactions get in:

| Mode | What happens |
|------|--------------|
| Off | Everything is entered manually (default) |
| Bank SMS | Bank alerts are read on the phone and queued in **Review transactions** |
| Statement import only | Use the CSV importer with your bank's statement |
| SMS + statement import | Both |

Nothing leaves the phone - SMS are parsed locally.

## How it works

1. `BankSmsReceiver` gets every incoming SMS.
2. `BankMessageRouter` finds the bank by sender id (only banks you selected).
3. `GenericMessageParser` extracts amount, currency, merchant, card last 4 digits,
   debit/credit and ignores OTPs, declines, reminders and promos.
4. `CaptureProcessor` links it to an Ivy account (Settings → bank → *Link card*),
   suggests a category (learned from your choices and your past transactions),
   flags possible duplicates of what you already entered manually and saves it
   for review (or adds it straight away if *Add without review* is on).

## Adding / fixing a bank

All banks live in
`feature/auto-capture/src/main/java/com/ivy/autocapture/parser/UaeBanks.kt`.

1. Add a `BankProfile` with an `id`, a display name and the SMS sender ids.
2. Add it to `UaeBanks.all`.
3. Paste a few real messages into
   `feature/auto-capture/src/test/.../BankMessageParserTest.kt` and run the tests.
4. Only if the generic parser gets it wrong, add a `MessagePattern` regex with named
   groups `amount`, `currency`, `merchant`, `last4` to the profile.

Without code changes you can also: enable **Other bank** and add any sender name,
or add extra sender names to an existing bank (Settings → bank → Edit), and use
**Test a message** to see how a message is understood.
