package io.mvdm.translationtools.gradle

/**
 * A CLDR plural category, in CLDR's order. [keyword] is the Android `quantity` and the Plural selector.
 */
internal enum class PluralQuantity(val keyword: String)
{
   ZERO("zero"),
   ONE("one"),
   TWO("two"),
   FEW("few"),
   MANY("many"),
   OTHER("other"),
}

/** One `<item>` of a `<plurals>` resource; [text] is the item text exactly as the file holds it. */
internal data class AndroidPluralItem(val quantity: PluralQuantity, val text: String)

internal sealed interface PluralSelector
{
   data class Exact(val digits: String) : PluralSelector

   data class Category(val quantity: PluralQuantity) : PluralSelector
}

/**
 * One case of a Plural value; [text] is the raw text between the form's braces and [numberHashes] the indexes in [text] of
 * each unquoted `#`, the places where the number stands (a quoted `'#'` is a literal and not listed). [placeholders] are the
 * distinct Placeholder names of the form in first-seen order; `{argument}` is the number and is not one.
 */
internal data class PluralCase(val selector: PluralSelector, val text: String, val numberHashes: List<Int>, val placeholders: List<String>)

/** A whole-value Plural: the [argument] name and its [cases] in written order. */
internal data class ParsedPlural(val argument: String, val cases: List<PluralCase>)

/** The Plural value `{count, plural, one {# item} other {# items}}` for [items] and [argument]: single spaces, CLDR order. */
internal fun composePluralValue(argument: String, items: List<AndroidPluralItem>): String
{
   val cases = items.sortedBy { it.quantity }.joinToString(" ") { "${it.quantity.keyword} {${it.text}}" }
   return "{$argument, plural, $cases}"
}

/**
 * The items of a `<plurals>` resource that [value] stands for, in CLDR order, or null when [value] is not a Plural
 * or holds an exact case such as `=0`, which a `<plurals>` resource cannot hold.
 */
internal fun pluralValueToAndroidItems(value: String): List<AndroidPluralItem>?
{
   val plural = parsePluralValue(value) ?: return null
   val items = plural.cases.map { case ->
      val selector = case.selector as? PluralSelector.Category ?: return null
      AndroidPluralItem(selector.quantity, case.text)
   }

   return items.sortedBy { it.quantity }
}

/**
 * Parses [value] as a whole-value Plural with the grammar of the TranslationTools server (the same one the runtime client
 * reads) and returns its argument and cases in written order, or null when it is not one.
 */
internal fun parsePluralValue(value: String): ParsedPlural?
{
   return PluralValueReader(value).readPlural()
}

private class PluralValueReader(private val input: String)
{
   private var i = 0
   private val numberHashes = mutableListOf<Int>()
   private val placeholders = mutableListOf<String>()
   private var argument = ""

   fun readPlural(): ParsedPlural?
   {
      if (!consume('{'))
         return null

      skipWhitespace()
      val argumentStart = i
      i = identifierEnd(i) ?: return null
      argument = input.substring(argumentStart, i)
      skipWhitespace()
      if (!consume(',') || !skipWhitespaceThen("plural") || !consume(','))
         return null

      val cases = mutableListOf<PluralCase>()
      while (true) {
         skipWhitespace()
         if (consume('}'))
            break

         val selector = readSelector() ?: return null
         if (cases.any { it.selector == selector })
            return null

         skipWhitespace()
         if (!consume('{'))
            return null

         val start = i
         numberHashes.clear()
         placeholders.clear()
         if (!skipForm(start))
            return null

         cases += PluralCase(selector, input.substring(start, i - 1), numberHashes.toList(), placeholders.toList())
      }

      return cases.takeIf { it.isNotEmpty() && i == input.length }?.let { ParsedPlural(argument, it) }
   }

   private fun consume(c: Char): Boolean
   {
      if (i < input.length && input[i] == c) {
         i += 1
         return true
      }

      return false
   }

   private fun skipWhitespace()
   {
      while (i < input.length && input[i] in " \t\r\n")
         i += 1
   }

   private fun skipWhitespaceThen(word: String): Boolean
   {
      skipWhitespace()
      if (!input.startsWith(word, i))
         return false

      i += word.length
      skipWhitespace()
      return true
   }

   private fun readSelector(): PluralSelector?
   {
      if (consume('=')) {
         val start = i
         while (i < input.length && input[i] in '0'..'9')
            i += 1

         return if (i > start) PluralSelector.Exact(input.substring(start, i)) else null
      }

      val start = i
      while (i < input.length && input[i] in 'a'..'z')
         i += 1

      val keyword = input.substring(start, i)
      return PluralQuantity.entries.firstOrNull { it.keyword == keyword }?.let(PluralSelector::Category)
   }

   /** Moves past the form body that starts at [start] and its closing `}`, noting each unquoted `#`, or returns false when the body is not valid. */
   private fun skipForm(start: Int): Boolean
   {
      while (i < input.length) {
         val c = input[i]
         val next = if (i + 1 < input.length) input[i + 1] else ' '

         when {
            c == '}' -> {
               i += 1
               return true
            }

            c == '\'' && next == '\'' -> i += 2

            c == '\'' && (next == '{' || next == '}' || next == '#') -> skipQuotedRun()

            c == '{' -> if (!skipPlaceholderOrBraceLiteral()) return false

            c == '#' -> {
               numberHashes += i - start
               i += 1
            }

            else -> i += 1
         }
      }

      return false
   }

   /** Moves past an apostrophe and the quoted run it opens, through the lone apostrophe that closes it. */
   private fun skipQuotedRun()
   {
      i += 1
      while (i < input.length) {
         if (input[i] == '\'') {
            if (i + 1 < input.length && input[i + 1] == '\'') {
               i += 2
               continue
            }

            i += 1
            return
         }

         i += 1
      }
   }

   /** A `{camelCase}` Placeholder or a literal `{0}`-style brace span; a nested argument or an unclosed `{` fails. */
   private fun skipPlaceholderOrBraceLiteral(): Boolean
   {
      val afterBrace = i + 1
      val nameEnd = identifierEnd(afterBrace)
      if (nameEnd != null) {
         var j = nameEnd
         while (j < input.length && input[j] in " \t\r\n")
            j += 1

         if (j < input.length && input[j] == ',')
            return false
      }

      val close = input.indexOf('}', afterBrace)
      if (close < 0 || input.indexOf('{', afterBrace).let { it in 0 until close })
         return false

      val name = input.substring(afterBrace, close)
      if (PLACEHOLDER_NAME_REGEX.matches(name) && name != argument && name !in placeholders)
         placeholders += name

      i = close + 1
      return true
   }

   /** The end of the identifier `[a-z][a-zA-Z0-9]*` starting at [start], or null when none starts there. */
   private fun identifierEnd(start: Int): Int?
   {
      if (start >= input.length || input[start] !in 'a'..'z')
         return null

      var end = start + 1
      while (end < input.length && (input[end] in 'a'..'z' || input[end] in 'A'..'Z' || input[end] in '0'..'9'))
         end += 1

      return end
   }
}
