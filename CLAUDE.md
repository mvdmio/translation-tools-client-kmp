# translationtools-client-kmp

Kotlin Multiplatform client and Gradle plugin for TranslationTools — local Android `strings.xml` is the editable source of truth, TranslationTools is the remote store, and the runtime client serves translations to shared KMP code. Shipped as the `io.mvdm.translationtools:translationtools-client-kmp` (+ `-compose`) Maven Central artifacts. Work style: telegraph, low-filler, direct.

## Essentials

- **Build tool:** Gradle via the wrapper (`./gradlew.bat` on Windows, `sh ./gradlew` on POSIX). Composite build. Kotlin 2.1.20, KMP targeting Android, JVM, and iOS.
- **Build:** `./gradlew.bat build`
- **Test:** `./gradlew.bat allTests` (or `jvmTest` for a faster JVM-only loop).
- Before finishing any change, confirm it builds (`./gradlew.bat build`) then tests pass (`./gradlew.bat allTests`). Run `dotnet`-style steps **sequentially, never in parallel** — overlapping Gradle daemons cause file locks. If a build fails because a process is locking a file, kill the process.
- This is a published Maven Central library: treat the public API (`io.mvdm.translationtools.client`) as a contract. No API changes unless intentional and called out.
- **Bump the version** in [build.gradle.kts](build.gradle.kts) for any change that affects the published artifact, following semver (MAJOR = incompatible API, MINOR = backward-compatible feature, PATCH = backward-compatible fix). Keep [README.md](README.md) version snippets in sync.

## Universal rules

- **Never branch.** This repo uses a single-branch workflow — when asked to commit/push, commit on the current branch (`master`) and push directly. Only create a branch when the user explicitly asks for one by name.
- **Always add or modify tests** when adding functionality or fixing a bug. Prefer `src/commonTest` unless platform-specific behavior forces `src/jvmTest`.
- **Always update [README.md](README.md)** when public API, runtime behavior, install/version snippets, or contributor tooling changes.
- Keep [opencode.jsonc](opencode.jsonc) and [AGENTS.md](AGENTS.md) aligned with these instructions — if one changes, check whether the others should too.
- The main session is the orchestrator. Unless the task is trivial, delegate the actual work (explore, implement, test, review) to subagents using a model and reasoning level appropriate for the task.
- Search early. Quote exact errors. If blocked or the design is unclear, ask.

## Reference docs

Read the relevant file before working in that area:

- [Architecture & layout](.agents/ref/architecture.md) — modules, source sets, client lifecycle, key files
- [Coding conventions](.agents/ref/conventions.md) — naming, visibility, coroutines, code style
- [Testing](.agents/ref/testing.md) — common/jvm/plugin test patterns, test utilities
- [Common tasks](.agents/ref/common-tasks.md) — adding a snapshot store, a Gradle task, a target
- [Dependencies & CI/CD](.agents/ref/dependencies.md) — package list and publish pipeline

## Agent skills

### Issue tracker

Issues live as markdown files under `.agents/<feature>/`. See `.agents/ref/issue-tracker.md`.

### Triage labels

Default vocabulary (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). See `.agents/ref/triage-labels.md`.

### Domain docs

Single-context (`CONTEXT.md` + `docs/adr/` at the repo root). See `.agents/ref/domain.md`.
