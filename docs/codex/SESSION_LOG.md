# WaveSim Codex session log

This is a concise, Git-tracked record of durable context from WaveSim Codex conversations. It is intentionally not a transcript. Do not put secrets or unrelated personal information here.

## 2026-09-23 — Track WaveSim Codex conversations

- Objective: Keep WaveSim Codex work anchored to the established `/home/joseph/Projects/WaveSim` Git checkout and make durable context portable across devices.
- Project state: The Codex `WaveSim` project already uses `/home/joseph/Projects/WaveSim` as its primary folder. The checkout is on `main`, tracking `origin/main`, with no pre-existing working-tree changes.
- Changes: Added this session log and root-level `AGENTS.md` with the project workflow.
- Verification: Confirmed the repository is Git-backed and the `origin` remote is `https://github.com/JosFa1/WaveSim.git`.
- Follow-up: Start future coding chats from the Codex `WaveSim` project, update this log at the end of substantive work, and commit/push the log when it should be available on another device.

## Entry template

### YYYY-MM-DD — Conversation title

- Objective:
- Changes:
- Verification:
- Follow-up:

### 2026-09-23 — Plan 2D parametric curve rendering

- Objective: Plan a WaveSim drawing model for 2D waves and curves that can represent paths where one horizontal coordinate has multiple vertical coordinates.
- Changes: Added `docs/codex/2D_CURVE_RENDERING_PLAN.md` with a parametric-curve model, separation of curve parameter from animation time, sampling and coordinate-mapping steps, and an incremental renderer roadmap. Left the existing uncommitted Java experiments untouched.
- Verification: Reviewed the current WaveSim renderer and wave prototype to align the proposal with the existing AWT overlay and Vulkan texture-upload path. No code build or tests were run because this was a planning-only change.
- Follow-up: If implementing, start by capturing one animation-time value per frame and drawing sampled parametric paths into the existing overlay.

### 2026-09-23 — Set up the ChatGPT Work bridge

- Objective: Use a WaveSim ChatGPT Work project alongside Codex CLI while keeping durable project context connected through Git.
- Changes: Created the ChatGPT Work project `WaveSim`, added project instructions linking it to `JosFa1/WaveSim`, and added cross-surface guidance to `AGENTS.md`. Started a Work chat with the GitHub connector to read the published repository.
- Verification: The Work project's GitHub connector read the repository's default `main` branch, README, `AGENTS.md`, and session log. The Work project and local Codex project named `WaveSim` both appear in the project list. Work cannot see local uncommitted files, and CLI conversation history remains separate.
- Follow-up: Use the GitHub connector in Work chats for published repository context; use the local Codex project for checkout/Git operations. Keep only concise decisions and outcomes in this log, then commit and push the relevant notes when they should be available on other devices.

### 2026-09-23 — Index earlier WaveSim CLI chats

- Objective: Make previous WaveSim Codex CLI work discoverable from the Git repository and ChatGPT Work without copying complete transcripts.
- Changes: Added `docs/codex/CLI_CHAT_INDEX.md` with titles and concise scope notes for the Java/LWJGL tasks and earlier C++ tasks found under the WaveSim and legacy `cpp` project labels.
- Verification: Cross-checked the listed titles and summaries against the Codex task list. The index explicitly warns that current code state must be checked in the working tree.
- Follow-up: Update the index when new substantive WaveSim CLI tasks add durable context; keep outcomes in this session log and transcripts in their original Codex chats.
