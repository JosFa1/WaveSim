# WaveSim Codex CLI chat index

This is a Git-tracked navigation aid, not a copy of Codex conversations. Codex
CLI history remains separate from ChatGPT Work history. These entries identify
WaveSim tasks visible in the Codex task list on 2026-09-23; read the current
source and Git status before relying on any historical state.

## Java / LWJGL work

- **Plan a 2D wave renderer** — developed the parametric-curve rendering plan.
  A follow-up requested implementation of a GPU curve renderer and was
  interrupted while work was in progress. Inspect the current working tree
  before resuming; the Java wave/renderer edits are not part of the published
  Git commit unless separately committed.
- **Limit login lockout duration** — set a target of a one-minute cooldown
  after five failures, with scaled lockouts capped at three minutes.
- **Locate FPS limits** — traced the code paths that can cap FPS at 60 or sync
  rendering to display refresh.
- **Refactor Java for beginners** — reorganized Java responsibilities and
  added beginner-oriented explanations.
- **Build WaveSim and update README** — addressed the Java build/setup workflow
  and documented build and run instructions.
- **Convert renderer thesis to Java** — migrated the earlier C++ Vulkan
  skeleton to Java/LWJGL, retaining a blank render surface and FPS display.

## Earlier C++ work in the same checkout

The legacy Codex project label **cpp** points to the same WaveSim directory.
These threads describe earlier code, superseded by the Java migration:

- **Create Vulkan wave sim** — initial C++ 2D wave-simulation and Vulkan
  renderer work.
- **can you clean up the code in the main.cpp file, make it more begginer
  friendly and explain what the stuff is doing. you can make changes to the
  code, as long as you preserve most functionality and it makes the code more
  begginer friendly.** — learning-oriented cleanup of the earlier C++ source.

## Cross-device workflow

- Use ChatGPT Work's GitHub connector for the published repository on `main`.
- Use the local WaveSim Codex project or CLI for the working checkout and Git
  operations. Work cannot see local uncommitted files.
- Keep durable decisions and completed outcomes in `SESSION_LOG.md`; keep
  transcripts in their original chat histories.
