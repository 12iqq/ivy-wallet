# Working on Ivy Wallet UAE Edition

- Commit as the repo owner, not as Claude: run
  `git config user.name "12iqq"` and
  `git config user.email "71027322+12iqq@users.noreply.github.com"` before committing.
- No `Co-Authored-By` or `Claude-Session` lines in commit messages.
- Work goes straight to `main` (no pull requests). A release is published by bumping
  `version-name` / `version-code` in `gradle/libs.versions.toml`, with notes in
  `docs/release-notes/v<version>.md`.
- Never commit personal data (backups, SMS exports, statements); keep them out of the repo.
- No analytics, crash reporting or other libraries that send data off the phone. The app only downloads
  exchange rates; keep PRIVACY.md true.
- Releases are signed with the private key in `release-key.jks.gpg`, which CI decrypts with the
  `RELEASE_KEY_PASSWORD` secret. Never commit a decrypted key (`sign.jks`) and never replace the key file:
  installed apps only accept updates signed with the same key.
