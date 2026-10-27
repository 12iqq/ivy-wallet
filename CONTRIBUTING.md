# Contributing

Thanks for wanting to help. This is a personal project I work on in my spare time, so I can't promise to add
everything, but I read every issue.

## Report a problem

[Open an issue](https://github.com/12iqq/ivy-wallet/issues/new/choose) and pick **Something isn't working**. Say what
you did, what happened, and what you expected instead. Screenshots help, but hide your account numbers and balances
first.

## A bank message isn't read right

Pick **A bank message isn't read correctly** and paste the SMS. Before you send it, change your card or account number,
your balance and the amounts, but keep the wording and the layout as they are: that's what the app reads.

The same goes for a bank or a utility that isn't supported yet, like DEWA bills or another bank's card bill texts.
Send one of their messages and I'll add it.

## Suggest something

Pick **Suggestion** and say what you'd like and how it would help you.

## Send a fix

1. Fork the repository and make a branch for your change.
2. Build it with Java 17 and Android Studio (see [Building it yourself](README.md#building-it-yourself)).
3. Keep the change to one thing, and test it on a phone.
4. Before you push, run `./gradlew testDebugUnitTest detekt`. GitHub runs these and a few more checks on every pull
   request.
5. Open a pull request to `main` and say what it changes and how you tested it.

Please don't put real account numbers, balances or messages in code, tests or screenshots. Use made-up ones.

## License

The app is free and open source under the [GPL-3.0](LICENSE). Anyone can use, change and share it, as long as what
they share stays under the same license. By sending a change, you agree it's shared under that license too.
