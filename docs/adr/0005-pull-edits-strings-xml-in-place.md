# Pull edits `strings.xml` in place

`pullTranslations` edits each existing Android `strings.xml` in place. It changes only the elements whose value differs from TranslationTools, appends new Keys before `</resources>`, and deletes the element of a Key whose remote value is null. Every other byte stays as the developer wrote it: comments, `<string-array>` and other resource types, attributes such as `tools:ignore`, inline markup, order, line endings, and the XML header. We chose this because the README promises the plugin skips what it does not sync, and a developer reads "skips" as "leaves alone". A re-render cannot keep that promise, because the parser never sees comments, unknown elements, or markup. The Apple `.strings` writer already edits in place the same way, and an in-place edit also gives the smallest pull diff.

## Considered options

- **Re-render the whole file from the parsed entries, sorted by name.** This was the original design. Its plan said to normalize output after the first managed write and not to keep comments or whitespace, so that pull diffs stay reviewable. Rejected. It deletes every resource the parser skips, and the first pull reorders the whole file, which is the opposite of a reviewable diff.
- **Keep the re-render and copy what it did not write to the end of `<resources>`.** Rejected. A comment would end up far from the element it describes, and the attributes and markup on a `<string>` would still be lost.

## Consequences

- A file that does not exist yet still gets the sorted, canonical writer. There is nothing to keep.
- A value that changes remotely replaces the element's content. If the old content held markup, the markup is gone and pull warns, because push flattens markup and TranslationTools never holds it.
