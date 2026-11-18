package io.mvdm.translationtools.gradle

/**
 * One `.stringsdict` entry that syncs as a Plural: its [key], the plural variable's [variable] name and [valueType]
 * (`d`, `lld`, ...; null when the file names none), and the [forms] in file order with their text as written.
 */
internal data class ParsedStringsdictEntry(
   val key: String,
   val variable: String,
   val valueType: String?,
   val forms: List<AndroidPluralItem>,
)

internal sealed interface ParsedAppleStringsdict
{
   /** A property list with a dictionary root: the [entries] that sync and a warning per entry that does not. */
   data class Readable(val entries: List<ParsedStringsdictEntry>, val warnings: List<String>) : ParsedAppleStringsdict

   /** A file that is not an XML property list with a dictionary root. */
   data class Unreadable(val warning: String) : ParsedAppleStringsdict
}

internal const val FORMAT_KEY = "NSStringLocalizedFormatKey"
internal const val RULE_TYPE_KEY = "NSStringFormatSpecTypeKey"
internal const val PLURAL_RULE_TYPE = "NSStringPluralRuleType"
internal const val VALUE_TYPE_KEY = "NSStringFormatValueTypeKey"

internal val wholeVariableFormat = Regex("%#@([^@]+)@")

/** [path] only names the file in warnings; [bytes] is its content. */
internal fun parseAppleStringsdict(bytes: ByteArray, path: String): ParsedAppleStringsdict
{
   val root = readXmlPropertyListDictionary(bytes)
      ?: return ParsedAppleStringsdict.Unreadable("Skipped '$path': it is not a readable XML property list.")

   val entries = mutableListOf<ParsedStringsdictEntry>()
   val warnings = mutableListOf<String>()
   root.asMap().forEach { (key, value) ->
      when (val read = readStringsdictEntry(key, value, path)) {
         is StringsdictEntryRead.Syncs -> entries += read.entry
         is StringsdictEntryRead.Skipped -> warnings += read.warning
      }
   }

   return ParsedAppleStringsdict.Readable(entries, warnings)
}

internal sealed interface StringsdictEntryRead
{
   data class Syncs(val entry: ParsedStringsdictEntry) : StringsdictEntryRead

   data class Skipped(val warning: String) : StringsdictEntryRead
}

/** Decides whether the entry [value] under [key] syncs as a Plural; [path] only names the file in the skip warning. */
internal fun readStringsdictEntry(key: String, value: PlistNode, path: String): StringsdictEntryRead
{
   val notWholeVariable = StringsdictEntryRead.Skipped(
      "Skipped '$key' in '$path': only an entry whose format string is exactly one plural variable, such as '%#@items@', syncs as a Plural.",
   )
   if (value !is PlistDict)
      return notWholeVariable

   val entry = value.asMap()
   val variable = entry[FORMAT_KEY].stringValue()
      ?.let { wholeVariableFormat.matchEntire(it)?.groupValues?.get(1) }
      ?: return notWholeVariable
   val variableDictionary = (entry[variable] as? PlistDict)?.asMap()
      ?: return notWholeVariable

   val notPlural = StringsdictEntryRead.Skipped("Skipped '$key' in '$path': variable '$variable' is not a plural rule.")
   if (variableDictionary[RULE_TYPE_KEY].stringValue() != PLURAL_RULE_TYPE)
      return notPlural

   val forms = variableDictionary.mapNotNull { (category, form) ->
      val quantity = PluralQuantity.entries.firstOrNull { it.keyword == category } ?: return@mapNotNull null
      form.stringValue()?.let { AndroidPluralItem(quantity, it) }
   }
   if (forms.isEmpty())
      return notPlural

   return StringsdictEntryRead.Syncs(
      ParsedStringsdictEntry(key, variable, variableDictionary[VALUE_TYPE_KEY].stringValue(), forms),
   )
}

private fun PlistNode?.stringValue(): String? = (this as? PlistString)?.text

/**
 * The Plural value [entry] syncs as: the variable name is the argument, forms in CLDR order, and the number's own
 * specifier in each form becomes `#`.
 */
internal fun ParsedStringsdictEntry.toPluralValue(): String =
   composePluralValue(
      variable,
      forms.map { form ->
         // The number is `%` plus exactly the value type; an entry that names none has no number specifier.
         AndroidPluralItem(form.quantity, form.text.toPluralFormText(appleSpecifier) { valueType != null && it.groups["spec"]?.value == valueType })
      },
   )

/**
 * `%%`, or a whole specifier: `%`, an optional `position` such as `1$`, then the `spec` (flags, width, precision, length,
 * conversion).
 */
internal val appleSpecifier = Regex("%%|%(?<position>\\d+\\$)?(?<spec>[-+ 0#']*\\d*(?:\\.\\d+)?(?:hh|h|ll|l|q|L|z|t|j)?[a-zA-Z@])")

/**
 * This Apple form text as Plural form text, the one rule both Apple sources of Plurals push by: each token of [tokens] for
 * which [isNumber] holds becomes `#`, and every other `#` (a literal one, or one inside another specifier such as `%#x`) is
 * quoted as `'#'`. A single apostrophe directly before a literal `#` is doubled so the Plural reads it as an apostrophe.
 */
internal fun String.toPluralFormText(tokens: Regex, isNumber: (MatchResult) -> Boolean): String
{
   val text = StringBuilder()
   var from = 0
   tokens.findAll(this).forEach { token ->
      text.append(substring(from, token.range.first).quoteLiteralHashes())
      text.append(if (isNumber(token)) "#" else token.value.quoteLiteralHashes())
      from = token.range.last + 1
   }

   return text.append(substring(from).quoteLiteralHashes()).toString()
}

private val hashWithOptionalApostrophe = Regex("'?#")

private fun String.quoteLiteralHashes(): String =
   replace(hashWithOptionalApostrophe) { match -> if (match.value.length == 2) "'''#'" else "'#'" }
