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
   composePluralValue(variable, forms.map { AndroidPluralItem(it.quantity, it.text.numberSpecifierToHash(valueType)) })

/**
 * `%%`, or a whole specifier: `%`, an optional `position` such as `1$`, then the `spec` (flags, width, precision, length,
 * conversion).
 */
internal val appleSpecifier = Regex("%%|%(?<position>\\d+\\$)?(?<spec>[-+ 0#']*\\d*(?:\\.\\d+)?(?:hh|h|ll|l|q|L|z|t|j)?[a-zA-Z@])")

/** Replaces the specifiers that are `%` plus exactly [valueType] with `#`; every other specifier stays as written. */
private fun String.numberSpecifierToHash(valueType: String?): String
{
   if (valueType == null)
      return this

   return appleSpecifier.replace(this) { match -> if (match.groups["spec"]?.value == valueType) "#" else match.value }
}
