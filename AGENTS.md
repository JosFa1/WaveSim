# WaveSim project guidance

This repository is the primary local workspace for the WaveSim Codex project.

## Durable context

- Keep code, project decisions, setup notes, and verification results in this repository.
- For each substantive Codex conversation, update `docs/codex/SESSION_LOG.md` before wrapping up.
- Record a short summary: date, conversation title, objective, files changed, verification performed, and any follow-up work.
- Read the session log and the current Git status when resuming older WaveSim work.
- Do not copy complete private conversation transcripts, credentials, API keys, or unrelated personal information into the repository.

Codex conversation transcripts are managed by the Codex app and are not automatically written into the repository. The checked-in session log is the portable project memory that can be reviewed on another device after the repository is pulled.

## Repository boundary

- Keep WaveSim changes inside this repository unless the user explicitly asks to work elsewhere.
- Preserve unrelated working-tree changes.
- Run an appropriate build or test after code changes and record the result in the session log.
