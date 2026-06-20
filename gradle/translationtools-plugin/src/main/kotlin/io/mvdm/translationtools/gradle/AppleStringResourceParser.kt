package io.mvdm.translationtools.gradle

import java.io.File

internal data class ParsedAppleEntry(
   val key: String,
   val value: String,
)

internal data class ParsedAppleStringsFile(
   val entries: List<ParsedAppleEntry>,
   val warnings: List<String>,
)

/**
 * Parses Apple `.strings` files into the shared `(origin, key, locale)` translation model.
 *
 * Apple `.strings` are an independent second source of truth alongside Android XML: every
 * discovered key is managed remotely, no typed accessors are generated, and the files in the
 * app bundle are themselves the iOS bundled fallback (see docs/adr/0001).
 */
internal class AppleStringResourceParser
{
   fun parse(
      resourceDirectories: List<File>,
      defaultLocale: String,
      projectPath: String,
   ): AppleTranslationProject
   {
      val discovery = discoverAppleLocaleFiles(resourceDirectories, defaultLocale)
      val warnings = discovery.warnings.toMutableList()
      if (discovery.files.isEmpty()) {
         warnings += "No .lproj directories with .strings files were found under the configured appleResources.resourceDirectories."
         return AppleTranslationProject(locales = emptyList(), entries = emptyList(), warnings = warnings)
      }

      val collected = linkedMapOf<Pair<String, String>, MutableMap<String, String?>>()
      discovery.files.forEach { variant ->
         val parsed = parseAppleStringsContent(decodeAppleStrings(variant.file.readBytes()))
         warnings += parsed.warnings.map { "${variant.file.path}: $it" }

         val origin = buildOrigin(projectPath, variant.relativeBaseFile)
         parsed.entries.forEach { entry ->
            collected.getOrPut(origin to entry.key) { linkedMapOf() }[variant.locale] = entry.value
         }
      }

      val locales = discovery.files.map { it.locale }.distinct().sorted()
      val entries = collected
         .toSortedMap(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
         .map { (ref, perLocale) ->
            AppleTranslationEntry(
               origin = ref.first,
               key = ref.second,
               valuesByLocale = locales.associateWith { locale -> perLocale[locale] }.toSortedMap(),
            )
         }

      return AppleTranslationProject(locales = locales, entries = entries, warnings = warnings)
   }
}

internal fun discoverAppleLocaleFiles(resourceDirectories: List<File>, defaultLocale: String): AppleResourceDiscovery
{
   val normalizedDefaultLocale = normalizeAppleLocale(defaultLocale)
   val warnings = mutableListOf<String>()
   val discovered = resourceDirectories
      .distinct()
      .filter { it.exists() }
      .flatMap { directory ->
         directory.listFiles()
            ?.filter { it.isDirectory && it.name.endsWith(".lproj") }
            ?.sortedBy { it.name }
            .orEmpty()
            .flatMap lprojLoop@{ lproj ->
               val resolution = resolveAppleLocale(lproj.name, normalizedDefaultLocale)
               resolution.warning?.let { warnings += it }

               lproj.listFiles()
                  ?.filter { it.isFile && it.extension.equals("strings", ignoreCase = true) }
                  ?.sortedBy { it.name }
                  .orEmpty()
                  .map { file ->
                     AppleResourceVariantFile(
                        locale = resolution.locale,
                        isBase = resolution.isBase,
                        file = file,
                        resourceDirectory = directory,
                        relativeBaseFile = file.name,
                     )
                  }
            }
      }

   val deduplicated = mutableListOf<AppleResourceVariantFile>()
   discovered.groupBy { Triple(it.resourceDirectory, it.locale, it.relativeBaseFile) }.forEach { (_, group) ->
      if (group.size == 1) {
         deduplicated += group.single()
         return@forEach
      }

      val explicit = group.filter { !it.isBase }
      val base = group.filter { it.isBase }
      if (explicit.isNotEmpty() && base.isNotEmpty()) {
         warnings += "Both Base.lproj and an explicit '${group.first().locale}' .lproj define '${group.first().relativeBaseFile}'. Using the explicit locale directory."
         deduplicated += explicit.first()
      }
      else {
         warnings += "Multiple .lproj directories map to locale '${group.first().locale}' for '${group.first().relativeBaseFile}'. Using '${group.first().file.path}'."
         deduplicated += group.first()
      }
   }

   return AppleResourceDiscovery(deduplicated.sortedBy { it.file.path }, warnings)
}

internal fun resolveAppleLocale(lprojDirectoryName: String, defaultLocale: String): AppleLocaleResolution
{
   val qualifier = lprojDirectoryName.removeSuffix(".lproj")
   if (qualifier == "Base")
      return AppleLocaleResolution(locale = defaultLocale, isBase = true, warning = null)

   val normalized = normalizeAppleLocale(qualifier)
   val recognized = normalized.matches(Regex("[a-z]{2,3}(-[a-z0-9]+)?"))
   val warning = if (recognized) null
      else "Unrecognized .lproj locale qualifier '$qualifier'; treating it as locale '$normalized'."

   return AppleLocaleResolution(locale = normalized, isBase = false, warning = warning)
}

internal fun normalizeAppleLocale(locale: String): String
{
   return locale.trim().lowercase().replace('_', '-')
}

/**
 * Renders a canonical locale back to Apple's conventional `.lproj` casing: two-letter subtags
 * are uppercased as regions (`pt-br` -> `pt-BR`), longer subtags are title-cased as scripts
 * (`zh-hans` -> `zh-Hans`, `zh-hans-cn` -> `zh-Hans-CN`).
 */
internal fun toLprojDirectory(locale: String): String
{
   val parts = locale.split('-')
   if (parts.size == 1)
      return "${parts[0]}.lproj"

   val rendered = parts.drop(1).joinToString("-") { subtag ->
      if (subtag.length == 2) subtag.uppercase() else subtag.replaceFirstChar { it.uppercase() }
   }
   return "${parts[0]}-$rendered.lproj"
}

internal fun decodeAppleStrings(bytes: ByteArray): String
{
   if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
      return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
   if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
      return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
   if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
      return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)

   return String(bytes, Charsets.UTF_8)
}

/** Matches the body of a double-quoted `.strings` literal, honoring backslash escapes. */
internal const val APPLE_QUOTED_LITERAL = "(?:[^\"\\\\]|\\\\.)*"

private val appleEntryRegex = Regex("\"($APPLE_QUOTED_LITERAL)\"\\s*=\\s*\"($APPLE_QUOTED_LITERAL)\"\\s*;")

internal fun parseAppleStringsContent(content: String): ParsedAppleStringsFile
{
   val cleaned = stripAppleComments(content)
   val warnings = mutableListOf<String>()
   val entries = linkedMapOf<String, String>()
   var cursor = 0

   appleEntryRegex.findAll(cleaned).forEach { match ->
      val gap = cleaned.substring(cursor, match.range.first)
      if (gap.isNotBlank())
         warnings += "Skipped unparseable content: '${gap.trim().take(60)}'."

      entries[unescapeAppleString(match.groupValues[1])] = unescapeAppleString(match.groupValues[2])
      cursor = match.range.last + 1
   }

   val tail = cleaned.substring(cursor)
   if (tail.isNotBlank())
      warnings += "Skipped unparseable content: '${tail.trim().take(60)}'."

   return ParsedAppleStringsFile(
      entries = entries.map { (key, value) -> ParsedAppleEntry(key, value) },
      warnings = warnings,
   )
}

/** Removes `/* */` block comments and `//` line comments, leaving quoted string literals intact. */
private fun stripAppleComments(content: String): String
{
   val builder = StringBuilder()
   var index = 0
   val length = content.length
   while (index < length) {
      val char = content[index]
      when {
         char == '"' -> {
            builder.append(char)
            index++
            while (index < length) {
               val inner = content[index]
               builder.append(inner)
               index++
               if (inner == '\\' && index < length) {
                  builder.append(content[index])
                  index++
               }
               else if (inner == '"') {
                  break
               }
            }
         }

         char == '/' && index + 1 < length && content[index + 1] == '/' -> {
            index += 2
            while (index < length && content[index] != '\n') index++
         }

         char == '/' && index + 1 < length && content[index + 1] == '*' -> {
            index += 2
            while (index + 1 < length && !(content[index] == '*' && content[index + 1] == '/')) index++
            index += 2
         }

         else -> {
            builder.append(char)
            index++
         }
      }
   }

   return builder.toString()
}

internal fun unescapeAppleString(raw: String): String
{
   val builder = StringBuilder()
   var index = 0
   while (index < raw.length) {
      val char = raw[index]
      if (char != '\\' || index + 1 >= raw.length) {
         builder.append(char)
         index++
         continue
      }

      when (val escaped = raw[index + 1]) {
         'n' -> { builder.append('\n'); index += 2 }
         't' -> { builder.append('\t'); index += 2 }
         'r' -> { builder.append('\r'); index += 2 }
         '"' -> { builder.append('"'); index += 2 }
         '\\' -> { builder.append('\\'); index += 2 }
         'U', 'u' -> {
            val hex = if (index + 6 <= raw.length) raw.substring(index + 2, index + 6) else ""
            if (hex.length == 4 && hex.all { it.isAppleHexDigit() }) {
               builder.append(hex.toInt(16).toChar())
               index += 6
            }
            else {
               builder.append(escaped)
               index += 2
            }
         }
         else -> { builder.append(escaped); index += 2 }
      }
   }

   return builder.toString()
}

internal fun escapeAppleString(value: String): String
{
   val builder = StringBuilder()
   for (char in value) {
      when (char) {
         '\\' -> builder.append("\\\\")
         '"' -> builder.append("\\\"")
         '\n' -> builder.append("\\n")
         '\r' -> builder.append("\\r")
         '\t' -> builder.append("\\t")
         else -> builder.append(char)
      }
   }

   return builder.toString()
}

private fun Char.isAppleHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
