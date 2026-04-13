# Agent Instructions

This document provides instructions for AI coding assistants working on this codebase.

## General instructions

- Ask questions if you need clarification.
- Search early; quote exact errors; prefer newer sources.
- Style: telegraph. Drop filler/grammar. Min tokens (global AGENTS + replies).
- Keep files shorter than ~500 LOC; split/refactor as needed. Does not apply to test files.
- Always add tests when adding functionality.
- Always create or modify tests when fixing a bug.
- Always build the solution and run the tests after making changes. Fix all build errors and test failures before finishing your work.
- If the build fails because some process is running and locking the file, kill the process.
- Always update the README.md file so that it reflects the latest state of the project
- Always bump the version number in the `build.gradle.kts` file when making changes that affect those projects. Follow semantic versioning principles (MAJOR.MINOR.PATCH):
   - Increment the MAJOR version when you make incompatible API changes.
   - Increment the MINOR version when you add functionality in a backward-compatible manner.
   - Increment the PATCH version when you make backward-compatible bug fixes.

### Test-Driven Development

Write tests before implementing features. Tests should cover all new code.

## OpenCode notes

- Keep `opencode.jsonc` and `AGENTS.md` aligned. If one changes, check whether the other should change too.
- `opencode.jsonc` should point OpenCode at `AGENTS.md` via the `instructions` setting.
- Prefer repo-root, checked-in OpenCode config. Keep it ASCII and comment only where it adds real value.

## Repository map

- Composite Gradle build.
- Root project publishes `io.mvdm.translationtools:translationtools-client-kmp`.
- Root subproject `:translationtools-client-compose` publishes `io.mvdm.translationtools:translationtools-client-compose` and targets Android, JVM, and iOS.
- Included build `gradle/translationtools-plugin` builds the local Gradle plugin. It is not a root project path. Do not address it as `:translationtools-plugin`.
- Main source sets: `src/commonMain`, `src/androidMain`, `src/jvmMain`.
- Test source sets: `src/commonTest`, `src/jvmTest`.
- Public package namespace: `io.mvdm.translationtools.client`.

## Build and test commands

- Windows shell: prefer `./gradlew.bat`.
- Full verification: `./gradlew.bat build`.
- Tests only: `./gradlew.bat allTests`.
- Narrow JVM loop when needed: `./gradlew.bat jvmTest`.
- Compose module from repo root: `./gradlew.bat :translationtools-client-compose:build` or `./gradlew.bat :translationtools-client-compose:allTests`.
- Plugin included build: use its local delegating wrapper, `./gradle/translationtools-plugin/gradlew.bat build` on Windows or `sh ./gradle/translationtools-plugin/gradlew build` on POSIX. For tests: `./gradle/translationtools-plugin/gradlew.bat test` on Windows or `sh ./gradle/translationtools-plugin/gradlew test` on POSIX.
- Do not use ad-hoc `-p` for normal repo commands. Do not guess project paths for the included plugin build.

## Docs and release hygiene

- Update `README.md` when public API, runtime behavior, install/version snippets, or contributor tooling changes.
- Bump `build.gradle.kts` version only for changes that affect the published library artifact or its documented release version.
- Keep README dependency/version examples in sync with `build.gradle.kts`.

## Implementation guidance

- Keep example API keys and secrets as placeholders only.
- Follow existing package structure and keep new production files under ~500 LOC when practical.
- For behavior changes, prefer tests in `src/commonTest` unless platform-specific behavior forces `src/jvmTest`.
