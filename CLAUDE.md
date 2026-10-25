# Working on Ivy Wallet UAE Edition

- Commit as the repo owner, not as Claude: run
  `git config user.name "12iqq"` and
  `git config user.email "71027322+12iqq@users.noreply.github.com"` before committing.
- No `Co-Authored-By` or `Claude-Session` lines in commit messages.
- Work goes straight to `main` (no pull requests). A release is published by bumping
  `version-name` / `version-code` in `gradle/libs.versions.toml`, with notes in
  `docs/release-notes/v<version>.md`.
- Never commit personal data (backups, SMS exports, statements); keep them out of the repo.
