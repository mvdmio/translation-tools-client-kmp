# Placeholder warnings go to the platform log unless the app sets its own handler

The runtime client writes each Placeholder warning to the platform log by default: Logcat on Android, `System.err` on JVM, and `NSLog` on iOS. An app can pass its own handler in the `onPlaceholderWarning` option to send warnings elsewhere, or pass `{}` to mute them. We chose a default that writes because the developer who most needs a warning is the one who misspelled `{userName}` and never thought to set up a handler. A library that stays silent until it is configured would leave that developer looking at a raw `{userName}` with no clue in the log.

## Considered options

- **An option only, silent when unset.** Rejected. It keeps today's behavior for every app that does not know the option exists, which is the failure this decision fixes.
- **A platform default only, with no option.** Rejected. An app could not send warnings to its own logger or crash reporter, and could not mute them.

## Consequences

- An app that upgrades starts seeing Placeholder warnings in its log without changing any code. Each distinct warning (same Key, Origin, Locale, and text) appears once per client instance, so a screen that renders on every recomposition does not repeat it.
- The handler takes a plain message string, the same shape the Gradle plugin passes to `logger.warn`. Changing that parameter to a structured warning type later would break every app that passes a handler.
