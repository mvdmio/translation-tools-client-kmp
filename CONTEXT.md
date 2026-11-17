# TranslationTools Client (KMP)

A Kotlin Multiplatform client and Gradle plugin that keep app translations in one
place: local resource files are the editable source, TranslationTools is the remote
store, and the runtime client serves translations to shared KMP code.

## Language

**TranslationTools**:
The remote translation service that stores and serves translations. The Gradle plugin
pushes local strings to it and pulls remote changes back.
_Avoid_: backend, server, API.

**Source of truth**:
The local resource files a developer edits by hand. These define which keys and locales
exist; TranslationTools mirrors them. Today this is Android `strings.xml`; Apple
`.strings` files are being added as a second source.

**Translation ref**:
The identity of a single translatable entry, as an `(origin, key)` pair. Combined with a
locale it addresses one translated value.

**Origin**:
The provenance of a translation ref — which source file it came from (e.g.
`:/strings.xml`, `:/InfoPlist.strings`). Keeps entries from different files distinct even
when they share a key.

**Key**:
The stable identifier of a translatable entry within its origin (e.g. `home_title`,
`NSCameraUsageDescription`).

**Locale**:
A language (optionally region) a value is translated into, e.g. `en`, `nl`, `pt-br`.

**Bundled fallback**:
Translations shipped inside the app build so it works offline / before the first remote
refresh. For Android/KMP this is the generated `TranslationsBundledSnapshot`; for iOS it
is the `.lproj/*.strings` files in the app bundle themselves.

**Runtime refresh**:
Fetching updated translations from TranslationTools after install and serving them
without a new build. Available to shared KMP/Android code via the client; **not**
available to Apple `.strings`, which the OS/app reads from the read-only signed bundle.

**Placeholder**:
A named slot in a translation value, written as `{` + a camelCase name + `}` (for example
`{userName}`), that the runtime client fills with a value when the app reads the string.
It is the only substitution grammar TranslationTools, its clients, and this client share.
_Avoid_: token, parameter, format argument, variable.

**Android format specifier**:
Android's own `String.format` slot syntax inside a `strings.xml` value, such as `%1$s`,
`%2$d`, or `%s`. Android's `getString(R.string.x, arg)` fills it. The runtime client does
not, so to the client it is plain text.
_Avoid_: positional placeholder, format string.

## Apple resources

**Apple `.strings` file**:
A localization file in Apple's `"key" = "value";` format, living in a `<locale>.lproj/`
directory. Newly supported as a source of truth for sync (push/pull + bundled files).

**`InfoPlist.strings`**:
An Apple `.strings` file holding localized values for `Info.plist` keys (app display
name, permission usage descriptions). Read by the **OS**, not app code.

**`Localizable.strings`**:
An Apple `.strings` file holding an app's own UI strings, read by iOS code via
`NSLocalizedString`. Same format as `InfoPlist.strings`; differs only in who consumes it.

**`.lproj` directory**:
Apple's per-locale resource directory (`en.lproj`, `de.lproj`, `Base.lproj`). The iOS
equivalent of Android's `values-<qualifier>` directories.
