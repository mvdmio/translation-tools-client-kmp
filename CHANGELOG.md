# Changelog

## 2026-11-18: Typed Placeholder functions (breaking)

Version 4.0.0 turns every `Translations.*` Key that needs Placeholders into a generated function, so the build fails when a call leaves out, misspells, or nulls a Placeholder. **This is a breaking change.** After you regenerate, `Translations.greeting` is no longer a property for each Key whose default-locale value holds a Placeholder that is not a Global placeholder, and for each Plural. Code that reads the property stops compiling until it passes the values:

```kotlin
client.get(Translations.greeting(userName = "Sam"), "en")
client.get(Translations.cart_items(count = 22, cartName = "Koszyk"), "pl")
```

- Each function takes one `String` parameter per Placeholder, in the order they first appear in the default-locale value. A Plural function takes the number first (named after the Plural's argument, `count` for a synced `<plurals>`, as a `Long` or an `Int`), then a parameter per Placeholder found in any form. A Placeholder named like a Kotlin hard keyword keeps its name and is written in backticks.
- A Key with no Placeholder, with only Global placeholders, or with no default-locale value keeps its property.
- New `placeholders.globals` in `translationtools.yaml` lists the Global placeholder names the app registers once in `globalPlaceholders`. Those names are never parameters. `generateTranslationResources` fails and names an entry that is not a valid Placeholder name.
- `getCached`, `observe`, and the Compose `stringResource` take the value of a generated function and render its Placeholders and Plural; `get` does too. With a plain property they still return the raw value.
- `withPlaceholders` and lookups by `TranslationRef` or by Origin and Key are unchanged, and still fill a Global placeholder you override for one lookup. A function's value exposes its `TranslationStringResource` as `.resource` for that.

## 2026-11-18: Apple .stringsdict plurals

Version 3.4.0 syncs Apple `.stringsdict` plurals as Plurals. `pushTranslations` reads every `.stringsdict` file in the `.lproj` directories under `appleResources.resourceDirectories` and sends each entry whose format string is exactly one plural variable, such as `%#@items@`, as a Plural named after that variable, with the number's own specifier written as `#`. Each file is its own Origin (`:/Localizable.stringsdict`). Entries that cannot sync, and files that are not readable XML property lists, are skipped with a warning.

`pullTranslations` writes the Plurals back into the same files. It replaces the forms of an entry that already exists and keeps its variable name, value type, and position, appends new Keys after the existing ones, and writes `#` back as the entry's value type. A value TranslationTools holds that no `.stringsdict` entry can hold keeps the local entry and warns, and an unreadable file is left unchanged. The file is written the way Xcode writes it, tab-indented, so an unchanged pull leaves it byte for byte the same. A new Locale gets a created file and the `knownRegions` warning. `.stringsdict` files stay sync-only: no `Translations.*` reference, no bundled snapshot, no runtime refresh.

## 2026-11-18: Plurals

Version 3.3.0 renders Plurals. A Plural is a translation value with one whole sentence per plural form, such as `{count, plural, one {# item} other {# items}}`. Pass the number with `client.withPlaceholders(Translations.cart_items, "pl").setPluralNumber(22)` and the client shows the one sentence that number picks, by the CLDR plural rules of the Locale. `withPlaceholders` now also takes a `Translations.*` reference.

The Gradle plugin syncs Android `<plurals>` resources as Plurals. `generateTranslationResources` gives each one a `Translations.*` reference and a bundled fallback, `pushTranslations` sends it, and `pullTranslations` writes it back as `<plurals>` instead of deleting it. `<string-array>` is still skipped.

Builds of an app on an older client show the raw ICU text of a Plural. Put a Plural on a new Key, and keep the old Key for those builds.

## 2026-11-04: The app declares its Keys at start

Version 3.2.0 makes the app declare the Keys of its bundled snapshot to TranslationTools when it starts, under your `environment`. The values fill only empty Translation Values, so an edit made in TranslationTools is never overwritten. A start with no network or no bundled snapshot is unaffected, and a failed Declaration is tried again on the next start.

`pushTranslations` now sends only the Keys and values in your local XML (and Apple `.strings`), and fetches nothing from TranslationTools first. A Key you delete locally is no longer removed by a push; TranslationTools drops it once no running build of the app declares it. The `androidResources.prune` setting is gone; a leftover one in `translationtools.yaml` is ignored.

## 2026-08-19: Plugin apply without workarounds

Version 3.1.0 lets you apply the Gradle plugin on the module that owns your Android XML and run only push and pull. Set `generated.enabled: false` so Kotlin compile does not wait on `Translations.*` files. Push and pull work with Gradle configuration cache on, and `includeBuild` no longer puts the Kotlin Gradle plugin on your build classpath.

## 2026-08-18: Runtime client 3.0.0

Version 3.0.0 of the runtime client and Compose helpers needs Kotlin 2.3.21 and Ktor 3. You can put both artifacts on commonMain and the iOS variants resolve. Runtime refresh timestamps use kotlin.time Instant; apps still on Ktor 2 stay on published 2.3.0.
