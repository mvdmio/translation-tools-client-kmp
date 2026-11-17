package io.mvdm.translationtools.gradle

// `%%` is consumed first so `%%s` reads as a literal percent then `s`.
private val androidFormatSpecifierPattern = Regex(
   """%%|%\d+\$[-#+0,(]*\d*(?:\.\d+)?[sSdfxXoeEgGcbh]|%(?:[sd]|(?:\.\d+)?f)""",
)

/** Distinct Android format specifiers in [value], exact text, in order of first appearance. */
internal fun findAndroidFormatSpecifiers(value: String): List<String> =
   androidFormatSpecifierPattern.findAll(value)
      .map { it.value }
      .filter { it != "%%" }
      .distinct()
      .toList()

/** One warning per pushed entry whose value in any locale holds an Android format specifier, sorted by origin then key. */
internal fun androidFormatSpecifierWarnings(entries: List<AndroidTranslationEntry>): List<String> =
   entries
      .filter { it.managedRemotely }
      .sortedWith(compareBy<AndroidTranslationEntry> { it.origin }.thenBy { it.key })
      .mapNotNull { entry ->
         val specifiersByLocale = entry.valuesByLocale.entries
            .sortedBy { it.key }
            .mapNotNull { (locale, value) ->
               value?.let(::findAndroidFormatSpecifiers)?.takeIf { it.isNotEmpty() }?.let { locale to it }
            }
         if (specifiersByLocale.isEmpty())
            return@mapNotNull null

         val specifiers = specifiersByLocale.flatMap { it.second }.distinct().joinToString(", ") { "'$it'" }
         val locales = specifiersByLocale.joinToString(", ") { it.first }
         "'${entry.key}' in '${entry.origin}' contains Android format specifier $specifiers (locales: $locales). " +
            "The runtime client fills only {named} placeholders, so these show as-is when read through " +
            "TranslationToolsClient. Replace them with a placeholder such as {name}."
      }
