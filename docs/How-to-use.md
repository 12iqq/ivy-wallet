# How to use Ivy Wallet UAE Edition

- [Getting started](#getting-started)
- [Adding transactions](#adding-transactions)
- [Transactions from bank SMS](#transactions-from-bank-sms)
- [Banks](#banks)
- [Card bills](#card-bills)
- [SEWA bills](#sewa-bills)
- [Planned payments, Tabby and Tamara](#planned-payments-tabby-and-tamara)
- [Reports and budgets](#reports-and-budgets)
- [Backups and moving to a new phone](#backups-and-moving-to-a-new-phone)
- [Other settings](#other-settings)
- [Something not working?](#something-not-working)

## Getting started

1. Open the app and pick your currency.
2. Add your accounts in the **Accounts** tab with the **+** button. For a credit card, tick **Credit card** and fill in
   the credit limit, the statement day and the payment due day. The card then shows how much of the limit you've used
   and when the next payment is due.
3. If you used the original Ivy Wallet, make a backup there (**Settings → Backup data**) and open it here with
   **Settings → Import data**. Import into an empty app, because importing adds to what's already there.

## Adding transactions

- Tap **+** at the bottom and choose income, expense or transfer. You can also swipe up from **+** for an expense,
  up and left for income, or up and right for a transfer.
- Paying a credit card is a transfer from your bank account to the card. The transfer screen shows what the card owes,
  and **Pay all** fills in the full amount.
- The quick-add shortcuts on the home screen come from what you add often. Tap one, check the amount and save. You can
  hide them in **Settings → Quick-add shortcuts**.
- To search, tap **Home** while you're already on the home screen. The search bar in the menu at the top right works
  too. Planned payments you haven't paid yet don't show up in search.

## Transactions from bank SMS

1. Go to **Settings → Auto-add transactions** and choose **Bank SMS** (or **SMS + statement import**). Allow SMS access
   when Android asks. On Android 13 or newer you may first need **Settings → Apps → Ivy Wallet UAE → ⋮ → Allow
   restricted settings**.
2. Under **My banks & cards**, turn on the banks you use. Only messages from those banks are read.
3. Link your cards and accounts. Tap a bank, then **Add a link**:
   - **1. Which messages?** One card or account by its last 4 digits, or **Everything else** from that bank.
   - **2. Which account do they go to?** Pick the account in the app, then tap **Add link**.
4. New transactions wait in **Review transactions**. Open it from the notification, or from the menu at the top right
   (**Auto-add**). Check the name, category and account, then add or skip each one, or use **Add all**.
5. If you'd rather not check them first, turn on **Add without review**.
6. Tap **Add note** on the notification to write what the bank's text doesn't say, like the film you saw or what you
   ordered. It's saved as the transaction's description.
7. **Check for new messages** decides when the app looks: as they arrive, or every few hours so nothing runs in
   between.
8. **Scan last 30 days of SMS** picks up messages from before you turned this on.

A few things it does on its own:

- When you rename a shop (say "GALADARI ICE CREAM" to "Baskin Robbins") and pick a category, the next purchase from that
  shop gets the same name and category. The bank's original text stays under the transaction's bank details.
- When money moves between two of your own accounts, like paying your card from your bank, both banks send a message.
  The two become one transfer.
- Transactions that look like something you already entered are marked as possible duplicates.

To also catch tap-to-pay payments, turn on **Google Wallet payments** and allow notification access. If the bank sends
an SMS for the same payment, it's only added once.

You can also import a bank statement with **Import bank statement (CSV)** on the same screen.

## Banks

DIB, ADCB, Emirates Islamic, Emirates NBD, Liv., First Abu Dhabi Bank, Mashreq / Neo, RAKBANK, Sharjah Islamic Bank,
Abu Dhabi Islamic Bank, Commercial Bank of Dubai, Wio, Ajman Bank, National Bank of Fujairah, Commercial Bank
International, HSBC, Citibank, Tabby and Tamara.

DIB, ADCB, Emirates Islamic and Tabby were tested with real messages. The others use a general reader that handles
the usual "AED 25.00 spent at …" style, but may miss some messages. **Other bank** lets you add any sender name.
**Test a message** shows how the app reads a message you paste in.

## Card bills

Some banks text you each card bill: the total due and the due date. When one arrives, the total becomes a planned
payment into the card, due on the bill's due date. It's paid from the account you paid that card from last time, and
waits under **Upcoming** on the home screen.

When your payment to the card comes in through auto-add, the bill is marked paid. A smaller payment lowers what's left.
If you pay the card some other way, tap **Pay** on the planned payment instead of adding a separate transfer.

So far the app knows ADCB's bill texts. Some banks, like Emirates Islamic, don't text the bill at all; for those cards,
set the payment due day on the card and you'll get a reminder before each payment instead. If your bank does text the
bill, [open an issue](https://github.com/12iqq/ivy-wallet/issues/new/choose) with one of those texts (numbers changed)
and I'll add it.

You can switch this off with **Card bills become planned payments** in the auto-add settings.

## SEWA bills

In **Reports**, tap **Set up** on **Utility bills** and turn on **Read SEWA bills from SMS**. Each bill then shows the
electricity, water and gas parts, the change since the last bill, and your averages. Bills are kept, and included in
backups, even after you delete the texts.

Turn on **Add bills as planned payments** to have each new bill wait under **Upcoming** until SEWA confirms the payment.

It's SEWA only for now. If you're in Dubai and want DEWA bills too,
[open an issue](https://github.com/12iqq/ivy-wallet/issues/new/choose) with one of DEWA's bill texts (numbers
changed) and I'll add it.

## Planned payments, Tabby and Tamara

- Open the menu at the top right, then **Planned payments**, to add rent, subscriptions and other regular payments.
  They show under **Upcoming** on the home screen. Tap **Pay** when you've paid.
- For Tabby or Tamara, tap **Split** on the Planned payments screen. Enter where you bought it, the total and the number
  of payments. The first payment is recorded now and the rest are added as planned payments.

## Reports and budgets

- Open the menu at the top right, then **Reports**. Switch between month and year, and between spending and income.
  Tap a category in the chart to see only that category everywhere on the screen.
- **Recurring charges** lists subscriptions, bills and fees that come back every week, month, three months or year,
  with what they cost a month. Tap one to see its past charges. **Plan it** makes it a planned payment, and **Not
  recurring** hides it.
- Set budgets in the menu under **Budgets**. You get an alert when a budget reaches 80% and 100%.

## Backups and moving to a new phone

- **Settings → Automatic backup**: choose daily or weekly and pick a backup file. Pick one in Google Drive if you want
  it off the phone. **Back up now** saves one straight away.
- **Settings → Backup data** saves a backup file whenever you want.
- On a new phone, install the app and use **Settings → Import data** with your backup file.

The backup has your accounts, transactions, categories, budgets, planned payments, loans, tags and SEWA bills. It
doesn't have the auto-add setup (your banks and card links), your notification choices or the transaction look, so set
those again after importing.

The daily backup is also what the Windows app reads, so you can see your reports on your computer. Save it in a
Google Drive folder called `ivywallet-uae` and the Windows app finds it by itself. The
[README](../README.md#on-your-computer) shows how to set it up.

## Other settings

- **Find duplicates** shows transactions with the same amount, account and day side by side, so you can delete the
  extra copy.
- **Transaction look** changes the account tag color, the text size of the account and category tags, and can put the
  account on the right.
- **Notifications** lets you choose which notifications you get: detected transactions, card payment reminders,
  budget alerts, the daily reminder and backup problems.

## Something not working?

[Open an issue](https://github.com/12iqq/ivy-wallet/issues) and describe what happened. If a bank message isn't read
correctly, paste the message and change your card number, balance and the amounts first.
