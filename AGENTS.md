# WaveSim project guidance

This repository is the primary local workspace for the WaveSim Codex project.

## Durable context

- Keep code, project decisions, setup notes, and verification results in this repository.
- For each substantive Codex conversation, update `docs/codex/SESSION_LOG.md` before wrapping up.
- Record a short summary: date, conversation title, objective, files changed, verification performed, and any follow-up work.
- Read the session log and the current Git status when resuming older WaveSim work.
- Do not copy complete private conversation transcripts, credentials, API keys, or unrelated personal information into the repository.

Codex conversation transcripts are managed by the Codex app and are not automatically written into the repository. The checked-in session log is the portable project memory that can be reviewed on another device after the repository is pulled.

## ChatGPT Work bridge

- The ChatGPT Work project named `WaveSim` has separate chat history from Codex CLI tasks; chats are not merged or automatically imported.
- Its GitHub connector can read the published `main` branch of `JosFa1/WaveSim`, but it cannot see local uncommitted changes. Use the local WaveSim project for the checkout and Git operations.
- Treat concise, committed entries in `docs/codex/SESSION_LOG.md` as the bridge between Work chats and CLI/Codex work. Do not put full transcripts or secrets in Git.
- When cross-device sync is wanted, publish only the relevant project notes and requested code changes; preserve unrelated working-tree changes.

## Repository boundary

- Keep WaveSim changes inside this repository unless the user explicitly asks to work elsewhere.
- Preserve unrelated working-tree changes.
- Run an appropriate build or test after code changes and record the result in the session log.
