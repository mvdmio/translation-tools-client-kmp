# Coding Conventions

## General

- Prefer small diffs over broad refactors.
- Avoid speculative abstractions — add them when a second caller actually appears.
- No backward-compat shims unless required by real consumers or persisted data. This library ships to Maven Central, so treat the public API (`io.mvdm.translationtools.client`) as a contract: preserve its shape unless the change is an intentional, called-out break.
- Keep production files under roughly 500 lines when practical (test files may exceed this); split or refactor as needed.

## Naming

- **Public types / functions / properties:** PascalCase for types, camelCase for functions and properties.
- **Backing/private properties:** `_camelCase` only where the codebase already does; otherwise plain `camelCase`.
- **Interfaces:** named for the role, no `I` prefix (Kotlin idiom) — e.g. `TranslationToolsApi`, `TranslationSnapshotStore`.
- **Suspend functions:** no `Async` suffix; suspension is in the signature.
- **Packages:** follow the folder structure (`io.mvdm.translationtools.client`, `io.mvdm.translationtools.gradle`).
- Generated accessors are always `Translations.*` and `TranslationsBundledSnapshot` (renamed from `Res.*` in 2.0.0 — do not reintroduce the old names).

## Visibility

- **Public API is minimal** — only the client, options, factory, refs, and Compose helpers are public.
- Mark implementation detail `internal` rather than leaking it across module boundaries.
- The Compose module depends on the root client via `api(project(":"))`; keep that boundary clean.

## Coroutines / async

- Public client APIs are `suspend` or expose `Flow`; respect structured concurrency.
- Thread cancellation through — don't swallow `CancellationException`.
- Use `kotlinx.coroutines.test.runTest` in tests for deterministic time.

## Code style

- `kotlin.code.style=official` (set in `gradle.properties`); 3-space indentation as in the existing files.
- Keep import lists minimal — remove duplicates and dead imports.
- Prefer explicit domain types (`TranslationRef`, `Locale` strings) over primitive bags.
- Keep example API keys and secrets as placeholders only — never commit a real key.
- iOS source sets are declared but most logic lives in `commonMain`; keep platform code thin (`androidMain`/`jvmMain` snapshot stores).

## Error handling

- Never swallow exceptions silently.
- Quote the real exception text when reporting/logging a failure.
- In tests, assert the exact behavior or message when it matters (see `TranslationToolsExceptions`).
