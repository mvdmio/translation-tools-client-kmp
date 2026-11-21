# translationtools-client-kmp

Kotlin Multiplatform client and Gradle plugin for TranslationTools — local Android `strings.xml` is the editable source of truth, TranslationTools is the remote store, and the runtime client serves translations to shared KMP code. Shipped as the `io.mvdm.translationtools:translationtools-client-kmp` (+ `-compose`) Maven Central artifacts.

## Shared rules

- Work on the repo's default branch unless a skill or the user names a branch. This overrides any default that creates branches.
- Preserve the worktree: parallel sessions share each checkout. Inspect existing changes before editing and leave them intact. When the index already holds another session's staged changes, commit with `git commit -- <your paths>`; for a file another session also edited, stage only your hunks (`git apply --cached` of your filtered diff) and run plain `git commit`.
- Search early. Quote exact errors.
- Ask when blocked, or when the design has more than one reasonable path.
- Telegraph style in replies and agent docs: drop filler and grammar.
- The main session orchestrates: unless the task is trivial, delegate exploring, implementing, testing and reviewing to subagents, at a model and effort that fit the task.
- [judgement] Prefer small diffs over broad refactors. Keep files under ~500 lines; tests may exceed it. Add an abstraction only once a second use exists. Ship no backward-compat shim unless real consumers or persisted data need one.
- Every feature and every bug fix comes with a test; a bug fix's test fails without the fix.
- Build, then test, one at a time: parallel builds deadlock on file locks. A build that fails on a file another process holds: stop that process, retry.

## Essentials

- **Build tool:** Gradle via the wrapper (`./gradlew.bat` on Windows, `sh ./gradlew` on POSIX). Composite build. Published **4.1.0**; Kotlin 2.3.21; runtime Ktor 3.5.2 (plugin stays Ktor 2.3.12). KMP targeting Android, JVM, and iOS.
- **Build:** `./gradlew.bat build`
- **Test:** `./gradlew.bat allTests` (or `jvmTest` for a faster JVM-only loop).
- Compose module from repo root: `./gradlew.bat :translationtools-client-compose:build` or `./gradlew.bat :translationtools-client-compose:allTests`.
- Plugin included build: use its local delegating wrapper, `./gradle/translationtools-plugin/gradlew.bat build` on Windows or `sh ./gradle/translationtools-plugin/gradlew build` on POSIX. For tests: `./gradle/translationtools-plugin/gradlew.bat test` on Windows or `sh ./gradle/translationtools-plugin/gradlew test` on POSIX.
- Do not use ad-hoc `-p` for normal repo commands. Do not guess project paths for the included plugin build.
- Before finishing any change, confirm it builds (`./gradlew.bat build`) then tests pass (`./gradlew.bat allTests`).
- This is a published Maven Central library: treat the public API (`io.mvdm.translationtools.client`) as a contract. No API changes unless intentional and called out.
- **Bump the version** in [build.gradle.kts](build.gradle.kts) only for changes that affect the published artifact or its documented release version, following semver (MAJOR = incompatible API, MINOR = backward-compatible feature, PATCH = backward-compatible fix). Keep [README.md](README.md) version snippets in sync.

## Universal rules

- Write tests before implementing features. Prefer `src/commonTest` unless platform-specific behavior forces `src/jvmTest`.
- **Always update [README.md](README.md)** when public API, runtime behavior, install/version snippets, or contributor tooling changes.
- Keep [opencode.jsonc](opencode.jsonc) aligned with these instructions. It should point OpenCode at `CLAUDE.md` via the `instructions` setting. Prefer repo-root, checked-in OpenCode config. Keep it ASCII and comment only where it adds real value.

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

### Tracker

Issues live as GitHub Issues in `mvdmio/translation-tools-client-kmp`, statuses as labels. See `.agents/refs/tracker.md`.

### Domain docs

Single-context (`CONTEXT.md` + `docs/adr/` at the repo root). See `.agents/refs/domain.md`.
