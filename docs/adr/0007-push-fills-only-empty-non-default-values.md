# Push fills only empty non-default values

`pushTranslations` sends a value outside the default Locale only where TranslationTools holds none. Before it uploads, push reads from TranslationTools each non-default Locale it is about to send. For a non-default value that TranslationTools already holds, push sends no value, and TranslationTools keeps its own. When the local value differs from it, push warns and names the Key, Origin, and Locale. Default-locale values follow [ADR 0008](0008-a-committed-file-holds-the-synced-values.md) instead, so push sends one only when it changed locally since its Synced value (#15). We chose this because translators edit non-default values on TranslationTools, and TranslationTools replaces any value a push sends that differs from the stored one. Without this rule, a push from a file pulled days ago would put old text back over a translator's fix. Pull already lets TranslationTools win for non-default Locales (#15), so a local edit to a non-default value would not last past the next pull anyway.

## Considered options

- **A pull baseline, as the .NET client keeps.** Pull records what it wrote, and push sends a non-default value only if it changed since that pull. Rejected. The team has to commit a new file. A teammate whose baseline is missing or old still overwrites translator edits, and a value edited both locally and on TranslationTools is still overwritten with no warning. The .NET client keeps this baseline, so the two clients treat a local non-default edit differently.
- **Send non-default values through the declaration route, which fills only empty values.** Rejected. Push could not say which local values it left out, so a developer's local fix would vanish with no warning. The order is also fragile: if the default-locale values go first, auto-translation can fill the empty value before the real one arrives.
- **Keep the single request with no read, as #4 left it.** Rejected. Nothing in one request can tell a stale value from a deliberate fix. #4 removed the old fetch because push merged the remote Keys into its upload and so declared every Key again. This read is only compared with the local values and never merged, so push still sends only local Keys.

## Consequences

- A push now makes one read per non-default Locale before its upload, and none when the local files hold only the default Locale. If a read fails, the push fails and uploads nothing.
- To change a non-default value that TranslationTools already holds, a developer edits it on TranslationTools. Editing the local file only produces the warning.
