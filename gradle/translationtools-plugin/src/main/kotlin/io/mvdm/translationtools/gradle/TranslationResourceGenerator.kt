package io.mvdm.translationtools.gradle

import java.security.MessageDigest

private val invalidIdentifierChars = Regex("[^a-z0-9_]+")
private val repeatedUnderscores = Regex("_+")
private val kotlinKeywords = setOf(
   "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
   "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true",
   "try", "typealias", "val", "var", "when", "while",
)

internal fun sanitizeResourcePropertyName(rawKey: String): String
{
   var sanitized = rawKey.lowercase()
      .replace('.', '_')
      .replace('-', '_')
      .replace('/', '_')
      .replace(Regex("\\s+"), "_")
      .replace(invalidIdentifierChars, "_")
      .replace(repeatedUnderscores, "_")
      .trim('_')

   if (sanitized.isEmpty())
      sanitized = "key"

   if (sanitized.first().isDigit())
      sanitized = "key_$sanitized"

   if (sanitized in kotlinKeywords)
      sanitized = "${sanitized}_"

   return sanitized
}

internal const val GENERATED_OBJECT_NAME: String = "Translations"

internal fun renderTranslationResources(
   project: AndroidTranslationProject,
   packageName: String,
): String
{
   val rawKeys = project.entries.map { it.resourceName }.distinct().sorted()
   val entriesByKey = project.entries.associateBy { it.resourceName }

   val collisions = rawKeys.groupBy(::sanitizeResourcePropertyName)
   val resolvedNames = rawKeys.associateWith { rawKey ->
      val sanitized = sanitizeResourcePropertyName(rawKey)
      val group = collisions[sanitized].orEmpty()
      if (group.size == 1) sanitized else "${sanitized}__${stableHashSuffix(rawKey)}"
   }

   val builder = StringBuilder()
   builder.appendLine("package $packageName")
   builder.appendLine()
   builder.appendLine("import io.mvdm.translationtools.client.TranslationRef")
   builder.appendLine("import io.mvdm.translationtools.client.TranslationStringResource")
   builder.appendLine()
   builder.appendLine("public object $GENERATED_OBJECT_NAME {")

   rawKeys.forEachIndexed { index, rawKey ->
      val entry = entriesByKey.getValue(rawKey)
      val fallback = entry.valuesByLocale[project.defaultLocale]
      builder.appendLine("   public val ${resolvedNames.getValue(rawKey)}: TranslationStringResource =")
      builder.appendLine("      TranslationStringResource(")
      builder.appendLine("         ref = TranslationRef(")
      builder.appendLine("            origin = ${entry.origin.asKotlinStringLiteral()},")
      builder.appendLine("            key = ${entry.key.asKotlinStringLiteral()},")
      builder.appendLine("         ),")
      builder.appendLine("         fallback = ${fallback.asKotlinStringLiteral()},")
      builder.appendLine("         managedRemotely = ${entry.managedRemotely},")
      builder.appendLine("      )")
      if (index != rawKeys.lastIndex)
         builder.appendLine()
   }

   builder.appendLine("}")
   return builder.toString()
}

private fun stableHashSuffix(rawKey: String): String
{
   val digest = MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray(Charsets.UTF_8))
   return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }.take(8)
}

internal fun String?.asKotlinStringLiteral(): String
{
   if (this == null)
      return "null"

   return buildString {
      append('"')
      this@asKotlinStringLiteral.forEach { character ->
         when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '$' -> append("\\$")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
         }
      }
      append('"')
   }
}
