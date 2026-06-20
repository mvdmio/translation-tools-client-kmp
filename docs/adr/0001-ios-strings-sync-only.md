# iOS Apple `.strings` are sync-only, with no codegen and no runtime refresh

## Context

We are adding Apple `.strings` files (`InfoPlist.strings`, `Localizable.strings`) as a
second source of truth so iOS translations can be managed centrally in TranslationTools.
The existing Android/KMP route offers three things: central sync, a bundled fallback, and
runtime refresh (fetch updated translations after install, no new build). The question
was whether iOS strings could get the same runtime-refresh parity, optionally via
generated `Translations.*` accessors.

## Decision

iOS Apple `.strings` are **sync-only**: the Gradle plugin parses them, pushes/pulls them
to TranslationTools, and writes the `.lproj/*.strings` files back. They get **no generated
`Translations.*` accessors** and are **not** added to `TranslationsBundledSnapshot`. The
`.lproj` files in the app bundle are themselves the bundled fallback. There is no runtime
refresh for these keys.

## Why

- `InfoPlist.strings` are consumed by the **OS** (permission dialogs, app display name)
  from the signed, read-only bundle. No API exists to hand a runtime-fetched value to the
  OS, so codegen cannot deliver runtime parity — ever.
- `Localizable.strings` are consumed by native iOS code via `NSLocalizedString`, also from
  the bundle. Runtime updates are only possible by routing those reads through the KMP
  client instead — which converts the keys into KMP-managed strings (the `.strings` file
  degrades to a fallback) and requires rewriting native call sites. That is a separate,
  larger initiative ("expose `Translations.*` to native Swift"), explicitly out of scope
  here, and it still does nothing for `InfoPlist.strings`.

## Consequences

- A maintainer wanting runtime-updatable iOS UI text should use the KMP/Android route, not
  expect it from this feature. Reversing this decision means building the separate
  Swift-bridging initiative, not a small change here.
