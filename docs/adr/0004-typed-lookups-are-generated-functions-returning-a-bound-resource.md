# A Key with Placeholders gets a generated function that returns a bound resource

The TranslationTools suite asks every client generator for one named, required argument per Placeholder, taken from the default-locale value. In this client, a Key whose default-locale value holds a Placeholder that is not a Global placeholder, or is a Plural, gets a generated function on `Translations` instead of a property. The function takes one parameter per such Placeholder, and the number first for a Plural. It returns a `BoundTranslationStringResource`: the Key's `TranslationStringResource` plus the values passed. The app hands that to the client, as in `client.get(Translations.greeting(userName = "Sam"), locale)`. A Key with no such Placeholder keeps its property.

We chose this because the compiler can only stop a call that leaves out a Placeholder if the old call no longer compiles. Kotlin has no way to type a map by the names it must hold, so a property next to a function would leave `client.get(Translations.greeting)` compiling. The client stays instance-based, so the generated function binds values and does not look anything up itself.

## Considered options

- **Placeholder names as type information on the resource, as the JavaScript client does (its ADR 0005).** Rejected. Kotlin cannot express a set of property names as a type, so it would need a generated argument class per Key, which is more code for the app to write at every call.
- **A generated function per Key that returns the string, as the .NET client does.** Rejected. This client looks up through an instance, so each function would need the client passed in, and the bundled fallback and Compose would still need the resource.
- **Keep the property and add the function next to it.** Rejected. A lookup that leaves out a Placeholder would still compile.

## Consequences

- Regenerating turns `Translations.greeting` from a property into a function for every Key with such a Placeholder. Code that used the property stops compiling until it passes the values. That is the point of the change, and it makes the release a major version.
- The Gradle plugin carries its own copy of the Placeholder and Plural parse, because it runs in Gradle's classloader on Ktor 2 and cannot load the runtime client. Both copies are tested against the same shared examples so they find the same names.
