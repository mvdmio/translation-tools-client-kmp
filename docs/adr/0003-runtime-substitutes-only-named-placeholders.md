# The runtime client substitutes only named Placeholders, never Android format specifiers

The runtime client fills only Placeholders (`{userName}`) and treats Android format specifiers (`%1$s`, `%d`) as plain text. A developer who reads a string through the client writes Placeholders in `strings.xml`. `pushTranslations` warns about any value that still holds an Android format specifier. We chose this over teaching the client Android's format because the Placeholder grammar is one contract that TranslationTools, the .NET client, and this client all follow. A second grammar in one client would make the same value render differently in each. It would also need a new positional-argument API that nothing else in the product has.

## Considered options

- **Substitute Android format specifiers in the runtime client.** Rejected. It needs a positional-argument API, a grammar change that the .NET client and TranslationTools must also adopt, and rules for how a Placeholder and an Android format specifier mix in one value.

## Consequences

- A string in `strings.xml` that holds a Placeholder shows the raw `{name}` when Android code reads it with `getString(R.string.x, arg)`. The README treats `R.string` reads as the state before migration. Code that keeps reading a key through `R.string` keeps its Android format specifiers, and `pushTranslations` keeps warning about that key.
