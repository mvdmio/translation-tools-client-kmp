# translationtools-client-kmp

Kotlin Multiplatform client and Gradle plugin for TranslationTools — local Android `strings.xml` is the editable source of truth, TranslationTools is the remote store, and the runtime client serves translations to shared KMP code. Shipped as the `io.mvdm.translationtools:translationtools-client-kmp` (+ `-compose`) Maven Central artifacts. Work style: telegraph, low-filler, direct.

## Essentials

- **Build tool:** Gradle via the wrapper (`./gradlew.bat` on Windows, `sh ./gradlew` on POSIX). Composite build. Published **3.0.0**; Kotlin 2.3.21; runtime Ktor 3.5.2 (plugin stays Ktor 2.3.12). KMP targeting Android, JVM, and iOS.
- **Build:** `./gradlew.bat build`
- **Test:** `./gradlew.bat allTests` (or `jvmTest` for a faster JVM-only loop).
- Compose module from repo root: `./gradlew.bat :translationtools-client-compose:build` or `./gradlew.bat :translationtools-client-compose:allTests`.
- Plugin included build: use its local delegating wrapper, `./gradle/translationtools-plugin/gradlew.bat build` on Windows or `sh ./gradle/translationtools-plugin/gradlew build` on POSIX. For tests: `./gradle/translationtools-plugin/gradlew.bat test` on Windows or `sh ./gradle/translationtools-plugin/gradlew test` on POSIX.
- Do not use ad-hoc `-p` for normal repo commands. Do not guess project paths for the included plugin build.
- Before finishing any change, confirm it builds (`./gradlew.bat build`) then tests pass (`./gradlew.bat allTests`). Run Gradle steps **sequentially, never in parallel** — overlapping daemons cause file locks. If a build fails because a process is locking a file, kill the process.
- This is a published Maven Central library: treat the public API (`io.mvdm.translationtools.client`) as a contract. No API changes unless intentional and called out.
- **Bump the version** in [build.gradle.kts](build.gradle.kts) only for changes that affect the published artifact or its documented release version, following semver (MAJOR = incompatible API, MINOR = backward-compatible feature, PATCH = backward-compatible fix). Keep [README.md](README.md) version snippets in sync.

## Universal rules

- **Never branch.** This repo uses a single-branch workflow — when asked to commit/push, commit on the current branch (`master`) and push directly. Only create a branch when the user explicitly asks for one by name.
- Ask if you need clarification. Search early. Quote exact errors. Prefer newer sources. If blocked or the design is unclear, ask.
- Style: telegraph. Drop filler/grammar. Min tokens.
- Keep production files under ~500 LOC; split/refactor as needed. Does not apply to test files.
- **Always add or modify tests** when adding functionality or fixing a bug. Write tests before implementing features. Prefer `src/commonTest` unless platform-specific behavior forces `src/jvmTest`.
- **Always update [README.md](README.md)** when public API, runtime behavior, install/version snippets, or contributor tooling changes.
- Keep [opencode.jsonc](opencode.jsonc) aligned with these instructions. It should point OpenCode at `CLAUDE.md` via the `instructions` setting. Prefer repo-root, checked-in OpenCode config. Keep it ASCII and comment only where it adds real value.
- The main session is the orchestrator. Unless the task is trivial, delegate the actual work (explore, implement, test, review) to subagents using a model and reasoning level appropriate for the task.

## Repository map

- Composite Gradle build.
- Root project publishes `io.mvdm.translationtools:translationtools-client-kmp`.
- Root subproject `:translationtools-client-compose` publishes `io.mvdm.translationtools:translationtools-client-compose` and targets Android, JVM, and iOS.
- Included build `gradle/translationtools-plugin` builds the local Gradle plugin. It is not a root project path. Do not address it as `:translationtools-plugin`.
- Main source sets: `src/commonMain`, `src/androidMain`, `src/jvmMain`.
- Test source sets: `src/commonTest`, `src/jvmTest`.
- Public package namespace: `io.mvdm.translationtools.client`.

## Implementation guidance

- Keep example API keys and secrets as placeholders only.
- Follow existing package structure.

## Reference docs

Read the relevant file before working in that area:

- [Architecture & layout](.agents/refs/architecture.md) — modules, source sets, client lifecycle, key files
- [Coding conventions](.agents/refs/conventions.md) — naming, visibility, coroutines, code style
- [Testing](.agents/refs/testing.md) — common/jvm/plugin test patterns, test utilities
- [Common tasks](.agents/refs/common-tasks.md) — adding a snapshot store, a Gradle task, a target
- [Dependencies & CI/CD](.agents/refs/dependencies.md) — package list and publish pipeline

## Agent skills

### Issue tracker

Issues live as markdown files under `.agents/issues/`. See `.agents/refs/issue-tracker.md`.

### Triage labels

Default vocabulary (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix). See `.agents/refs/triage-labels.md`.

### Domain docs

Single-context (`CONTEXT.md` + `docs/adr/` at the repo root). See `.agents/refs/domain.md`.
