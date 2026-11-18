package io.mvdm.translationtools.gradle

/**
 * What a generated `Translations` function takes for one Key: the [pluralNumber] parameter name when the Key's value is a
 * Plural (null otherwise), then one `String` parameter per name in [placeholders], in order.
 */
internal data class PlaceholderSignature(val pluralNumber: String?, val placeholders: List<String>)

/**
 * The signature of the function a Key with default-locale [value] gets, or null when the Key stays a property: it has no
 * value, or a plain value with no Placeholder besides the [globals]. A Plural always has one; its number takes the
 * argument's name, and the argument and the [globals] are not among its [PlaceholderSignature.placeholders].
 */
internal fun placeholderSignatureOf(value: String?, globals: Set<String>): PlaceholderSignature?
{
   if (value == null)
      return null

   val plural = parsePluralValue(value)
   if (plural != null) {
      val names = plural.cases.flatMap { it.placeholders }.distinct().filter { it !in globals }
      return PlaceholderSignature(plural.argument, names)
   }

   val names = plainPlaceholderNames(value).filter { it !in globals }
   return if (names.isEmpty()) null else PlaceholderSignature(null, names)
}

/**
 * The distinct Placeholder names of the plain [value] in first-seen order, read with the runtime client's grammar:
 * `{name}` is a Placeholder, `''` an apostrophe, and an apostrophe before `{` or `}` opens a quoted run with no Placeholder.
 */
internal fun plainPlaceholderNames(value: String): List<String>
{
   val names = linkedSetOf<String>()
   var i = 0
   while (i < value.length) {
      val c = value[i]
      val next = if (i + 1 < value.length) value[i + 1] else ' '

      when {
         c == '\'' && next == '\'' -> i += 2

         c == '\'' && (next == '{' || next == '}') -> i = endOfQuotedRun(value, i + 1)

         c == '{' -> {
            val close = value.indexOf('}', i + 1)
            val name = if (close < 0) null else value.substring(i + 1, close)
            if (name != null && PLACEHOLDER_NAME_REGEX.matches(name)) {
               names += name
               i = close + 1
            }
            else {
               i += 1
            }
         }

         else -> i += 1
      }
   }

   return names.toList()
}

/** The index after the quoted run whose first character is at [start], through the lone apostrophe that closes it. */
private fun endOfQuotedRun(value: String, start: Int): Int
{
   var i = start
   while (i < value.length) {
      if (value[i] == '\'') {
         if (i + 1 < value.length && value[i + 1] == '\'') {
            i += 2
            continue
         }

         return i + 1
      }

      i += 1
   }

   return i
}

/** The Key-name list plus `typeof`, which is reserved too but must not rename any Key. */
private val kotlinHardKeywords = kotlinKeywords + "typeof"

/** [name] as a Kotlin identifier, in backticks when it is a hard keyword. */
internal fun asKotlinIdentifier(name: String): String
{
   return if (name in kotlinHardKeywords) "`$name`" else name
}
