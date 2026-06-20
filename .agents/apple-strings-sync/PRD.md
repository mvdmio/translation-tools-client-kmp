# PRD: Apple `.strings` as a second source of truth

Status: ready-for-agent

## Problem Statement

Our iOS app ships translations that live outside the Android XML the TranslationTools
client manages — specifically Apple `.strings` files such as `InfoPlist.strings` (the
localized app display name and permission usage descriptions) under `<locale>.lproj/`
directories. Today these are edited and translated by hand, separately from every other
string in the product. They are not in TranslationTools, so translators can't manage them
in one place, they drift out of sync with the rest of the app's copy, and there is no
push/pull workflow for them. A colleague asked, in plain terms: "the iOS app also has
texts with translations here (`de.lproj/InfoPlist.strings`) — can that be added to the
Translations tool?"

## Solution

Add Apple `.strings` files as an **independent second source of truth** alongside Android
`strings.xml`, managed by the existing Gradle plugin. A developer points the plugin at the
directories that contain `.lproj/` folders; the plugin auto-discovers every `.strings`
file, parses it into the same `(origin, key, locale)` model used for Android, and includes
it in the existing `pushTranslations` / `pullTranslations` sync. iOS translations are then
managed centrally in TranslationTools, pushed up from local files, and pulled back down
into the `.lproj/*.strings` files — which are themselves the iOS bundled fallback.

This delivers two of the three capabilities the tool offers: **central sync** and a
**bundled fallback**. It deliberately does **not** deliver **runtime refresh** for these
keys, and generates **no** typed `Translations.*` accessors for them, because Apple
`.strings` are read from the signed, read-only app bundle by the OS (or by native code via
`NSLocalizedString`), not by shared KMP code — there is no delivery path for a
runtime-fetched value. See ADR 0001 and ADR 0002.

## User Stories

1. As an iOS developer, I want my `InfoPlist.strings` permission descriptions managed in
   TranslationTools, so that translators handle them in the same place as all other app
   copy.
2. As an iOS developer, I want to point the plugin at the directory containing my `.lproj/`
   folders, so that I don't have to enumerate individual files.
3. As an iOS developer, I want every `.strings` file in those `.lproj/` folders
   auto-discovered, so that I don't maintain an allowlist of filenames.
4. As an iOS developer, I want `InfoPlist.strings` and `Localizable.strings` both
   supported, so that all my Apple string resources sync, not just one file.
5. As a translator, I want iOS strings to appear in TranslationTools next to Android
   strings, so that I can translate the whole product from one surface.
6. As a localization manager, I want a single `pushTranslations` to upload both Android and
   Apple strings, so that nobody forgets to sync one platform.
7. As a localization manager, I want a single `pullTranslations` to write back both Android
   XML and Apple `.strings`, so that local files reflect the latest remote state in one
   command.
8. As an iOS developer, I want my `de.lproj/InfoPlist.strings` to map to locale `de` and
   sit on the same locale axis as Android's `values-de`, so that a `de` translation is one
   concept regardless of which platform it came from.
9. As an iOS developer, I want `Base.lproj` treated as the project default locale, so that
   Apple's development-language fallback maps onto our default locale.
10. As an iOS developer, I want a warning when both `Base.lproj` and an explicit
    default-locale `.lproj` exist, so that I notice the ambiguity instead of silently
    merging them.
11. As an iOS developer, I want region variants like `pt-BR.lproj` recognized (and legacy
    `pt_BR` accepted on read), so that regional locales sync correctly.
12. As an iOS developer, I want unusual locale qualifiers warned about rather than silently
    dropped, so that I know when something wasn't synced.
13. As an iOS developer, I want my Apple `.strings` files to keep their comments after a
    pull, so that the context explaining each permission string isn't destroyed.
14. As an iOS developer, I want pull to update only the values of existing keys in place and
    append new keys, so that my file diffs stay small and reviewable in the Xcode repo.
15. As an iOS developer, I want pulled values written as UTF-8 without a BOM, so that the
    files match the modern Xcode default.
16. As an iOS developer, I want both UTF-8 and UTF-16 `.strings` files read correctly, so
    that older files still parse.
17. As an iOS developer, I want standard `.strings` escaping (`\"`, `\\`, `\n`, `\t`,
    `\Uxxxx`) handled on read and write, so that values with quotes and newlines round-trip.
18. As an iOS developer, I want unparseable lines warned-and-skipped rather than failing the
    build, so that one malformed line doesn't break my sync.
19. As an iOS developer, I want a missing remote locale's `.lproj` file created on pull plus
    a warning to register the region in Xcode `knownRegions`, so that I get the content
    immediately and know the one manual step needed to bundle it.
20. As an iOS developer, I want a generated header comment in freshly created `.strings`
    files noting they're managed by TranslationTools and need a `knownRegions` entry, so
    that the file's provenance and the required follow-up are self-documenting.
21. As an iOS developer, I want iOS and Android entries kept distinct by origin
    (`:/InfoPlist.strings` vs `:/strings.xml`), so that identical keys across platforms
    don't collide in TranslationTools.
22. As a localization manager, I want `prune` to apply across both Android and Apple
    sources uniformly, so that pruning means "remote equals exactly my local union."
23. As an existing Android-only consumer, I want everything to keep working when no
    `appleResources` is configured, so that adding this feature doesn't change my build.
24. As an iOS developer, I want one shared `defaultLocale` and `locales` config governing
    both platforms, so that there's a single locale source of truth for the project.
25. As a maintainer, I want it documented that iOS strings are sync-only with no runtime
    refresh and no codegen, so that nobody expects `Translations.*` accessors for them or
    tries to "fix" their absence.
26. As a maintainer, I want it documented that runtime-updatable iOS UI text is a separate
    future initiative (routing native reads through the KMP client), so that the boundary
    of this feature is clear.
27. As an iOS developer, I want the `generateTranslationResources` task to remain
    Android-only, so that adding Apple support doesn't change Kotlin code generation.
28. As an iOS developer with no `.lproj` files found under the configured directories, I
    want a warning and a no-op rather than a failure, so that misconfiguration is visible
    but not fatal.
29. As an iOS developer, I want `appleResources.resourceDirectories` to accept paths outside
    the Gradle module (e.g. `../iosApp/iosApp`), so that I can reach the Xcode app target
    that lives beside the shared module.
30. As a developer reviewing config, I want `appleResources` validated like
    `androidResources` (clear errors on wrong YAML shapes), so that misconfiguration
    produces an actionable message.

## Implementation Decisions

- **Scope: capabilities 1 + 2 only (central sync + bundled fallback); no runtime refresh,
  no codegen for iOS strings.** Apple `.strings` are OS-/native-consumed from the signed,
  read-only bundle, so a runtime-fetched value has no delivery path. Recorded in ADR 0001.
- **Independent second source of truth.** Apple `.strings` are parsed into the same
  `(origin, key, locale)` model as Android and are neither derived from nor feed the
  generated `Translations.*` API. They are not added to `TranslationsBundledSnapshot`; the
  `.lproj/*.strings` files in the app bundle are themselves the iOS bundled fallback.
- **One origin per `.strings` filename.** Each distinct filename becomes its own origin
  (e.g. `:/InfoPlist.strings`, `:/Localizable.strings`), parallel to `:/strings.xml`. iOS
  and Android entries coexist by origin; identical keys across platforms do not collide.
  **No backend / API contract changes** — push and pull are already keyed by
  `(origin, key, locale)`.
- **New config block `appleResources`.** Sibling to `androidResources`, carrying only
  `resourceDirectories` (a list of directories that *contain* `.lproj/` folders). Paths may
  resolve outside the module via `../`. `defaultLocale` and `locales` remain top-level and
  shared across both platforms. Validation mirrors `androidResources` (clear errors on
  malformed YAML shapes). Absence of `appleResources` is a no-op.
- **Auto-discovery, no allow/deny list (v1).** Every `.strings` file inside the discovered
  `.lproj/` directories is managed. Every discovered Apple key is `managedRemotely = true`;
  there is no per-key opt-out in v1 (the format has no `translatable` attribute).
- **Locale mapping.** `.lproj` directory names map to the canonical lowercase-hyphen locale
  axis shared with Android: `en.lproj → en`, `pt-BR.lproj`/legacy `pt_BR` → `pt-br`,
  `Base.lproj → defaultLocale`. When both `Base.lproj` and an explicit default-locale dir
  exist, the explicit one wins and a collision warning is emitted. Unrecognized qualifiers
  are lowercased pass-through with a warning. Write-back renders Apple's conventional
  casing (`pt-BR.lproj`).
- **`.strings` codec.** Read UTF-8 and UTF-16 (BOM detection); write UTF-8 without BOM.
  Support the modern quoted form `"key" = "value";`. Handle standard escapes (`\"`, `\\`,
  `\n`, `\t`, `\Uxxxx`) on read and write. Warn-and-skip unparseable lines rather than
  failing the build.
- **Write-back is preserve-on-merge** (not regenerate). On pull, read the existing
  `.lproj/*.strings`, update values of existing keys in place, append genuinely-new keys at
  the end, and leave comments, blank lines, and ordering intact. Comments are local-only
  (not part of the sync model), so regeneration is the only thing that could destroy them.
- **Pull creates missing-locale files and warns.** For a remote locale with no existing
  local `.lproj`, create the `.lproj/<file>.strings` anyway and warn the developer to add
  the region to Xcode `knownRegions`. Newly created files get a generated header comment
  noting TranslationTools management and the `knownRegions` follow-up. Recorded in ADR 0002.
  The plugin does not edit `project.pbxproj`.
- **Unified tasks.** `pushTranslations` parses both Android XML and (when configured) Apple
  `.strings` and pushes the union; `pullTranslations` writes back both Android XML and
  `.lproj/*.strings`. `prune` applies uniformly across both sources. The
  `generateTranslationResources` task stays Android-only.
- **Module shape.** Introduce an Apple `.strings` parser and writer as their own units
  (mirroring the structure of the Android parser/writer and its resource models) so the two
  codecs stay cleanly separated, and have the shared push/pull tasks invoke both. The
  existing push/pull merge logic (origin/locale/key keyed) is reused unchanged.
- **Out-of-scope formats.** `.xcstrings` (String Catalogs, Apple's JSON format) are not
  handled in v1.

## Testing Decisions

Good tests here assert **external behavior** — what the Gradle tasks produce and what the
files on disk look like after a sync — not internal helper structure. Drive HTTP with Ktor
`MockEngine` and assert on captured request bodies and on written file contents; never hit
the real network. Reuse existing fixtures rather than rolling new project scaffolding.

- **Primary seam — push/pull task tests** (existing `PushTranslationsTaskTests` /
  `PullTranslationsTaskTests`): exercise the feature end-to-end minus network. With a temp
  project containing `appleResources` and real `.lproj/*.strings` files plus a `MockEngine`:
  assert that push produces the expected `(origin, locale, key, value)` items (Apple origins
  alongside Android, merged correctly), and that pull writes back the `.lproj/*.strings`
  files with preserve-on-merge behavior, creates missing-locale files, and emits the
  `knownRegions` warning. Prior art: the existing `pullTranslations`/`mergeRemoteAndLocalPushItems`
  tests with `MockEngine` and temp-dir file assertions.
- **Supporting seam — Apple `.strings` codec tests** (new file mirroring
  `AndroidStringResourceParserTests`): cover the combinatorial format cases directly —
  escaping round-trips, UTF-8 and UTF-16 reads, comment preserve-on-merge, quoted-form
  parsing, warn-and-skip on malformed lines, and `.lproj`↔locale mapping including
  `Base.lproj → defaultLocale` and the collision warning. Prior art: `AndroidStringResourceParserTests`
  and the `writeStringsFile` / `toAndroidValuesDirectory` tests.
- **Config seam — `appleResources` parsing** (existing `TranslationToolsConfigTests`):
  assert `appleResources.resourceDirectories` parses, that absence is a clean no-op, and
  that malformed shapes produce clear errors. Prior art: the existing `androidResources`
  config tests.

Test stack and locations per the repo testing reference: plugin tests under
`gradle/translationtools-plugin/src/test/` using `kotlin.test`, Ktor `MockEngine`, and
Gradle TestKit where a full task run is needed. Run the plugin's included-build wrapper for
its tests; never run two Gradle invocations in parallel.

## Out of Scope

- **Runtime refresh of iOS strings** and any `Translations.*` codegen for them (ADR 0001).
- **Routing native iOS `NSLocalizedString` reads through the KMP client** to make
  app-owned iOS UI text runtime-updatable — a separate, larger initiative.
- **`.xcstrings` (String Catalogs)** — Apple's newer JSON format; classic `.strings` only.
- **Editing `project.pbxproj` / `knownRegions`** — the plugin writes files and warns; it
  does not register locales in the Xcode project.
- **Per-key exclusion / allow-deny lists** for Apple strings (no v1 opt-out).
- **Per-platform `defaultLocale` / `locales`** — config stays shared and top-level.

## Further Notes

- The colleague's concrete case is `Jewel-Software/jewel-app`'s
  `iosApp/iosApp/{de,en,nl}.lproj/InfoPlist.strings`: UTF-8, `"key" = "value";` with
  semicolon terminators, a top-of-file `/* comment */`, six `NS*UsageDescription` keys per
  locale. That project's `knownRegions` is `(en, nl, de, Base)` with no `Base.lproj` file
  present — the motivating example for the create-and-warn behavior.
- Domain vocabulary: see `CONTEXT.md` (source of truth, origin, key, locale, Apple
  `.strings`, `.lproj`, bundled fallback, runtime refresh).
- Release hygiene: this is a backward-compatible feature → **MINOR** version bump in
  `build.gradle.kts`, and update `README.md` (it currently states the tool supports Android
  XML only). Add tests per the repo's TDD convention.
