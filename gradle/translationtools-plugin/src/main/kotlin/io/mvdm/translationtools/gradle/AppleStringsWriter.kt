package io.mvdm.translationtools.gradle

import java.io.File

internal val APPLE_MANAGED_HEADER = listOf(
   "/*",
   "  Managed by TranslationTools.",
   "  Add this locale's region to your Xcode project's knownRegions to bundle it.",
   "*/",
).joinToString("\n")

private val appleEntryLineRegex = Regex("^(\\s*)\"($APPLE_QUOTED_LITERAL)\"(\\s*=\\s*)\"($APPLE_QUOTED_LITERAL)\"(\\s*;.*)$")

/**
 * Writes Apple `.strings` content with preserve-on-merge semantics: values of existing keys are
 * updated in place, genuinely new keys are appended, and comments, blank lines, and ordering are
 * left intact. A non-existent target is rendered fresh with a managed header.
 *
 * Returns true when the file did not exist before and was newly created.
 */
internal fun writeAppleStringsFile(target: File, updates: Map<String, String?>): Boolean
{
   val existingContent = if (target.exists()) decodeAppleStrings(target.readBytes()) else null
   val merged = mergeAppleStrings(existingContent, updates)

   target.parentFile?.mkdirs()
   if (existingContent != merged)
      target.writeText(merged, Charsets.UTF_8)

   return existingContent == null
}

internal fun mergeAppleStrings(existingContent: String?, updates: Map<String, String?>): String
{
   val effective = updates.filterValues { it != null }.mapValues { it.value!! }

   if (existingContent == null) {
      val builder = StringBuilder()
      builder.append(APPLE_MANAGED_HEADER)
      builder.append("\n\n")
      effective.entries.sortedBy { it.key }.forEach { (key, value) ->
         builder.append(renderAppleEntry(key, value))
         builder.append("\n")
      }
      return builder.toString()
   }

   // Preserve the file's existing line ending so appended keys don't introduce mixed endings.
   val lineSeparator = if (existingContent.contains("\r\n")) "\r\n" else "\n"
   val lines = existingContent.split("\n").map { it.removeSuffix("\r") }.toMutableList()
   val seen = mutableSetOf<String>()
   for (lineIndex in lines.indices) {
      val match = appleEntryLineRegex.matchEntire(lines[lineIndex]) ?: continue
      val key = unescapeAppleString(match.groupValues[2])
      val newValue = effective[key] ?: continue

      seen += key
      lines[lineIndex] = "${match.groupValues[1]}\"${match.groupValues[2]}\"${match.groupValues[3]}\"${escapeAppleString(newValue)}\"${match.groupValues[5]}"
   }

   val newKeys = effective.keys.filter { it !in seen }.sorted()
   if (newKeys.isNotEmpty()) {
      // Insert before a trailing empty line (final newline) so it stays at the end of the file.
      val insertAt = if (lines.isNotEmpty() && lines.last().isEmpty()) lines.size - 1 else lines.size
      newKeys.forEachIndexed { offset, key ->
         lines.add(insertAt + offset, renderAppleEntry(key, effective.getValue(key)))
      }
   }

   return lines.joinToString(lineSeparator)
}

private fun renderAppleEntry(key: String, value: String): String =
   "\"${escapeAppleString(key)}\" = \"${escapeAppleString(value)}\";"
