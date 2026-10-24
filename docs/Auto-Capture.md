# Auto-add transactions (bank SMS & statements)

Settings → **Auto-add transactions** lets you pick how transactions get in:

| Mode | What happens |
|------|--------------|
| Off | Everything is entered manually (default) |
| Bank SMS | Bank alerts are read on the phone and queued in **Review transactions** |
| Statement import only | Use the CSV importer with your bank's statement |
| SMS + statement import | Both |

Nothing leaves the phone - SMS are parsed locally.

**Check for new messages** decides what runs in the background (`BackgroundCapture`):

| Choice | What runs |
|--------|-----------|
| As they arrive (default) | `BankSmsReceiver` - Android wakes the app briefly for each SMS |
| Every 1 / 3 / 6 / 12 / 24 h | The receiver is switched off; `SmsCheckWorker` reads new SMS from the inbox on that schedule, and again whenever the app is opened |

The Google Wallet listener is only enabled while *Google Wallet payments* is on and SMS are
picked up as they arrive. Android keeps an enabled notification listener connected all the time,
so it's switched off (not just ignored) whenever it isn't needed.

## How it works

1. `BankSmsReceiver` gets every incoming SMS.
2. `BankMessageRouter` finds the bank by sender id (only banks you selected).
3. `GenericMessageParser` extracts amount, currency, merchant, card last 4 digits,
   debit/credit and ignores OTPs, declines, reminders and promos.
4. `CaptureProcessor` links it to an Ivy account using the links made in
   Settings → Auto-add → bank: the link for the message's last 4 digits, otherwise
   the bank's *Everything else* link. Wallet notifications don't name the bank, so
   they match the last 4 digits against the links of every bank. (Endings once typed
   on accounts are copied into links automatically.)
   Google Wallet notifications (optional, `WalletNotificationListener`) go
   through the same steps; a purchase seen in both an SMS and a Wallet
   notification within 15 minutes is captured once. It then
   fills in the title and category you used last time at that shop (see *Shop names* below),
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

## Shop names

A detected transaction keeps the bank's own name for the shop (`GALADARI ICE CREAM CO LLC`)
in its **bank details** (`transactions.bankDetails`), shown read-only in the transaction
editor; the description stays free for your own notes. `MerchantNames.key` reduces the name
to what identifies the shop (`galadari ice cream`: no locations, company types or store
numbers). For the next message from the same shop, `CaptureProcessor` takes the title and
category of your most recent transaction with matching bank details, so renaming one anywhere
in the app (review screen or transaction editor) teaches it. The first time, the title is the
bank's name made readable (`MerchantNames.tidy`: `Galadari Ice Cream`).

## Transfers between your own accounts

Paying a credit card or topping up a wallet sends two messages, one from each side:

| Money moves | Message from where it left | Message from where it arrived |
|---|---|---|
| Bank → credit card | DIB: "AED 1,627.21 has been debited … for Card Payment" | ADCB: "Your payment of AED 1627.21 against Credit Card … was received"; EI: "receipt of your payment of AED 289.96 …" (often the next day) |
| Bank card → Tabby Cash | DIB: "Online Purchase of AED 500.00 … from TABB*" | Tabby: "AED 500 was added to your account using card ending 1234" |

The parser marks the messages that read as transfers (`ParsedMessage.isTransfer`), and
`CaptureLogic.otherHalf` finds the other side: same amount, the other direction, another of
your accounts, up to 3 days apart (6 hours for a plain card charge joining a top-up).
`CaptureProcessor` keeps one transfer from the account the money left to the one it reached;
the second message is only marked as seen (`MERGED`). If one side was already added on its own,
that transaction becomes the transfer. A Tabby top-up names the card it came from, so it is a
transfer straight away when that card is linked. A card payment whose other side hasn't arrived
yet waits in review (as a transfer with the other account to pick) instead of being added as a
spend.

Tabby messages only count when they move money in Tabby Cash (spends, top-ups, money sent or
received). Pay-later orders, reminders and requests to confirm are skipped: the instalments
show up on the card that pays them.

## To do

- Sort every message into a kind first (purchase, refund, money received, transfer, card
  payment, reminder or statement, information only) instead of keyword lists.
- Cash: ATM withdrawals and cash deposits could become transfers to and from a Cash account
  the way you recorded them last time.
- Card statements ("Total due … Due date …") could update the card's due date and amount.
