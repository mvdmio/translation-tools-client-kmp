# A committed file holds the Synced values of the Default locale

Default-locale values are edited in the local files and on TranslationTools, and both edits must survive. Pull and push tell them apart by comparing each default-locale value with its Synced value. The plugin keeps the Synced values in `.mvdmio-translations.pull.json`, next to `translationtools.yaml`, and rewrites it after every pull and push. The developer commits it. Non-default Locales keep the rule in [ADR 0007](0007-push-fills-only-empty-non-default-values.md) and have no Synced values.

The compare runs per Key in the Default locale:

- If only the local value differs from the Synced value, it is a local edit. Push sends it and pull keeps it.
- If only the remote value differs, it is an edit made on TranslationTools. Pull writes it into the file and push leaves it alone.
- If both differ, and from each other, the value changed on both sides. Pull keeps the local value, prints both values, and records the remote one as the Synced value, so the next push sends the local one. Push skips the Key, prints both values, and tells the developer to pull.

We keep the Synced values in a committed file because it is the only place where they match the strings files on every branch. A branch that is behind still holds the Synced values of its own files, so it reads a newer remote value as an edit made on TranslationTools and pulls it in, rather than pushing old text back. The file needs nothing from TranslationTools, whose API has no timestamps or versions.

The file takes the name and format of the pull baseline the .NET client already writes, so that the .NET and js clients can share one format. It is JSON with one item per Origin, Locale, and Key:

```json
{
  "items": [
    {
      "origin": ":/strings.xml",
      "locale": "en",
      "key": "home_title",
      "value": "Home"
    }
  ]
}
```

Each value is the text pull receives from TranslationTools, so a Plural is stored in its ICU form. This plugin sorts the items by Origin, Key, and Locale and writes two-space indentation with LF line endings, so a sync changes only the lines of the values that changed.

The two clients use the file differently. The .NET client writes it only on pull and records every Locale. This plugin writes it on pull and push and records only the Default locale. A reader in either client accepts both.

## Considered options

- **TranslationTools stores the last value it received from a push.** Rejected. It needs API and server work outside this repo. Every branch shares one stored value, so a push from an older branch looks like a fresh local edit and puts old text back.
- **A per-machine file under `.gradle/`, not committed.** Rejected. A fresh clone, a CI run, or a branch switch starts with Synced values that are missing or belong to another branch.
- **The file as committed in git (`HEAD`).** Rejected. Developers commit before they push, so an unpushed edit would look synced and pull would overwrite it.
- **Pull never writes the Default locale.** Rejected. People edit default-locale values on TranslationTools and expect them in the local files.
- **Synced values for every Locale.** Rejected. ADR 0007 already keeps translator edits in non-default Locales without a file, and only the Default locale needs edits from both sides to survive.

## Consequences

- When two branches change the same default-locale value, their Synced values conflict in git along with the strings file. The developer resolves that conflict like the one in the strings file.
- On the first pull or push after the upgrade, a Key with no Synced value takes the shared value as its Synced value when local and remote match. When they differ, it counts as changed on both sides, so nothing is lost silently.
