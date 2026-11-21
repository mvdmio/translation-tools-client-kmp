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
exist, and TranslationTools mirrors them. These are Android `strings.xml`, Apple `.strings`
and `.stringsdict` files, and Apple String Catalogs.

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

**Default locale**:
The Locale a developer writes the Source of truth in, set by `defaultLocale`. Every other
Locale is a translation of it. Its Keys come only from the local files, and its values are
edited in both the local files and TranslationTools.
_Avoid_: source language, base locale, base language.

**Synced value**:
For one Key in the Default locale, the value the local file and TranslationTools both held
after the last pull or push that touched that Key. A value that differs from it changed since then, on
the side where it differs. A Key that has never been pushed or pulled has no Synced value.
_Avoid_: base value, last-known value, snapshot.

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
Together with the Plural, it is the substitution grammar TranslationTools, its clients, and
this client share.
_Avoid_: token, parameter, format argument, variable.

**Global placeholder**:
A Placeholder whose value the app supplies once for every lookup, from app state, rather than at
each call site. A value passed for the same name at the call site wins over it for that lookup.
_Avoid_: ambient placeholder, context variable.

**Plural**:
A translation value that is one ICU plural argument wrapping the whole value, with a whole
sentence per Plural form: `{count, plural, one {# item} other {# items}}`. The app passes a
whole number, and that number picks the form. A Key is a Plural when its default-locale value
is one. Each Android `<plurals>` resource syncs as a Plural whose argument is `count`. Each
Apple `.stringsdict` entry whose format string is exactly one plural variable syncs as a
Plural whose argument is that variable's name. In an Apple String Catalog, a localization that varies by
plural as a whole syncs as a Plural whose argument is `count`, and a localization whose value is
exactly one substitution that varies by plural syncs as a Plural whose argument is that
substitution's name.
_Avoid_: plural string, ICU message, quantity string.

**Plural form**:
One case of a Plural: a CLDR plural category (`zero`, `one`, `two`, `few`, `many`, `other`) or
an exact number such as `=0`, together with the sentence shown for it. A `#` in the sentence
prints the number. In a `<plurals>` resource, each `<item quantity="...">` is the form of that
category. In an Apple `.stringsdict` entry, each category key under the plural variable is the
form of that category. In an Apple String Catalog, each category under the plural variation is
the form of that category.
_Avoid_: branch, variant, quantity.

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

**Apple `.stringsdict` file**:
An Apple localization file in property-list format, living in a `<locale>.lproj/` directory
next to the `.strings` files, that holds an app's plural text. Each entry names a variable and
gives one sentence per CLDR plural category. It syncs as a source of truth for Plurals.
_Avoid_: plist, plural dictionary.

**Apple String Catalog**:
An Apple localization file in JSON format, named `<table>.xcstrings` and living next to the
`.lproj` directories rather than inside one, that holds one table's Keys in every Locale. A Key's
value in a Locale is either plain text or text that varies by plural or by device. A Key may be
marked "don't translate", and a value carries a review state that stays in the file. The whole
file is one Origin.
_Avoid_: xcstrings file, catalog, string table.

**`.lproj` directory**:
Apple's per-locale resource directory (`en.lproj`, `de.lproj`, `Base.lproj`). The iOS
equivalent of Android's `values-<qualifier>` directories.
