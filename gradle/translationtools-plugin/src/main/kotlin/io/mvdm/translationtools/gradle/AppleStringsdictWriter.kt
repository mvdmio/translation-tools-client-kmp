package io.mvdm.translationtools.gradle

import java.io.File

/** What [writeAppleStringsdictFile] did: whether it created the file, and the [warnings] to print. */
internal data class AppleStringsdictWrite(val created: Boolean, val warnings: List<String>)

/** A `.stringsdict` file's root dictionary after a merge, and the [warnings] the merge raised. */
internal data class MergedStringsdict(val root: PlistDict, val warnings: List<String>)

/** The value type of each syncing entry of [file] that names one; empty when there is no [file] or it is unreadable. */
internal fun stringsdictValueTypes(file: File?): Map<String, String>
{
   if (file == null)
      return emptyMap()

   val parsed = parseAppleStringsdict(file.readBytes(), file.path) as? ParsedAppleStringsdict.Readable ?: return emptyMap()
   return parsed.entries.mapNotNull { entry -> entry.valueType?.let { entry.key to it } }.toMap()
}

/**
 * Merges the pulled [updates] (Key to remote value, for [locale]) into the `.stringsdict` file [target] and writes the whole
 * property list in Xcode's format, only when its bytes differ from the file's. A file that does not exist is created.
 * [defaultValueTypes] holds the value type of each Key in the default Locale's file, for a Key the file lacks.
 */
internal fun writeAppleStringsdictFile(
   target: File,
   locale: String,
   updates: Map<String, String?>,
   defaultValueTypes: Map<String, String>,
): AppleStringsdictWrite
{
   val existingBytes = if (target.exists()) target.readBytes() else null
   val existingRoot = if (existingBytes == null) PlistDict(emptyList()) else readXmlPropertyListDictionary(existingBytes)
      ?: return AppleStringsdictWrite(created = false, warnings = listOf("Left '${target.path}' unchanged: it is not a readable XML property list."))

   val merged = mergeStringsdict(existingRoot, target.path, locale, updates, defaultValueTypes)
   val bytes = serializePropertyList(merged.root)
   if (existingBytes == null || !bytes.contentEquals(existingBytes)) {
      target.parentFile?.mkdirs()
      target.writeBytes(bytes)
   }

   return AppleStringsdictWrite(created = existingBytes == null, warnings = merged.warnings)
}

/**
 * Applies [updates] to [root]: the forms of an entry that syncs are replaced by the remote Plural's, a Key the file lacks is
 * appended after all entries (sorted among themselves), and everything else stays as it is. A value that is null leaves the
 * Key alone; a value no `.stringsdict` entry can hold keeps the local entry and warns, naming [path] and [locale].
 */
internal fun mergeStringsdict(
   root: PlistDict,
   path: String,
   locale: String,
   updates: Map<String, String?>,
   defaultValueTypes: Map<String, String>,
): MergedStringsdict
{
   val entries = root.entries.toMutableList()
   val appended = sortedMapOf<String, PlistDict>()
   val warnings = mutableListOf<String>()

   updates.toSortedMap().forEach { (key, value) ->
      if (value == null)
         return@forEach

      val plural = holdablePlural(value)
      if (plural == null) {
         warnings += "Kept local '$key' in '$path': the value for '$locale' in TranslationTools is not a Plural that a .stringsdict entry can hold."
         return@forEach
      }

      val index = entries.indexOfFirst { it.first == key }
      if (index < 0) {
         appended[key] = newEntry(plural, defaultValueTypes[key] ?: NEW_ENTRY_VALUE_TYPE)
         return@forEach
      }

      // An entry push skips is not the sync's to change.
      val local = entries[index].second
      val read = readStringsdictEntry(key, local, path)
      if (read is StringsdictEntryRead.Syncs && local is PlistDict)
         entries[index] = key to local.withForms(read.entry, plural)
   }

   return MergedStringsdict(PlistDict(entries + appended.map { (key, entry) -> key to entry }), warnings)
}

/** The value type of a new entry whose Key the default Locale's file does not hold. */
private const val NEW_ENTRY_VALUE_TYPE = "lld"

/** Apple's default value type for a plural variable of an existing entry that names none. */
private const val UNNAMED_VALUE_TYPE = "d"

/** A remote Plural a `.stringsdict` entry can hold: its [argument] and the [forms] by category, in CLDR order. */
private data class HoldablePlural(val argument: String, val forms: List<HoldableForm>)

private data class HoldableForm(val quantity: PluralQuantity, val text: String, val numberHashes: List<Int>)

/** [value] as a Plural whose cases are categories or `=0` (written as `zero`), or null for anything else. */
private fun holdablePlural(value: String): HoldablePlural?
{
   val plural = parsePluralValue(value) ?: return null
   val forms = plural.cases.map { case ->
      val quantity = when (val selector = case.selector) {
         is PluralSelector.Category -> selector.quantity
         is PluralSelector.Exact -> if (selector.digits == "0") PluralQuantity.ZERO else return null
      }
      HoldableForm(quantity, case.text, case.numberHashes)
   }

   return HoldablePlural(plural.argument, forms.distinctBy { it.quantity }.sortedBy { it.quantity })
}

private fun newEntry(plural: HoldablePlural, valueType: String): PlistDict =
   PlistDict(
      listOf(
         FORMAT_KEY to PlistString("%#@${plural.argument}@"),
         plural.argument to PlistDict(
            listOf(RULE_TYPE_KEY to PlistString(PLURAL_RULE_TYPE), VALUE_TYPE_KEY to PlistString(valueType)) +
               plural.forms.map { it.quantity.keyword to PlistString(it.render(valueType)) },
         ),
      ),
   )

/**
 * This entry dictionary with the forms of its variable replaced by exactly [plural]'s. The variable keeps its name, value
 * type and other keys; a form already in the file keeps its place, a new form goes before the first later category.
 */
private fun PlistDict.withForms(entry: ParsedStringsdictEntry, plural: HoldablePlural): PlistDict
{
   val valueType = entry.valueType ?: UNNAMED_VALUE_TYPE
   return PlistDict(
      entries.map { (key, value) ->
         if (key == entry.variable && value is PlistDict) key to value.withVariableForms(plural, valueType) else key to value
      },
   )
}

private fun PlistDict.withVariableForms(plural: HoldablePlural, valueType: String): PlistDict
{
   val remote = plural.forms.associateBy { it.quantity }
   val kept = mutableListOf<Pair<String, PlistNode>>()
   val placed = mutableSetOf<PluralQuantity>()
   entries.forEach { (key, value) ->
      val quantity = pluralQuantityOf(key)
      if (quantity == null)
         kept += key to value
      else if (placed.add(quantity))
         remote[quantity]?.let { kept += key to PlistString(it.render(valueType)) }
   }

   plural.forms.filter { it.quantity !in placed }.forEach { form ->
      val added = form.quantity.keyword to PlistString(form.render(valueType))
      val before = kept.indexOfFirst { (key, _) -> pluralQuantityOf(key)?.let { it > form.quantity } == true }
      if (before < 0) kept += added else kept.add(before, added)
   }

   return PlistDict(kept)
}

private fun pluralQuantityOf(key: String): PluralQuantity? = PluralQuantity.entries.firstOrNull { it.keyword == key }

/**
 * The form text with each unquoted `#` written as `%` plus [valueType]; `%1$` plus [valueType] when the form also holds a
 * positional specifier such as `%2$@`.
 */
private fun HoldableForm.render(valueType: String): String
{
   if (numberHashes.isEmpty())
      return text

   val positional = appleSpecifier.findAll(text).any { it.groups["position"] != null }
   val specifier = if (positional) "%1\$$valueType" else "%$valueType"
   val rendered = StringBuilder()
   var from = 0
   numberHashes.forEach { hash ->
      rendered.append(text, from, hash).append(specifier)
      from = hash + 1
   }

   return rendered.append(text, from, text.length).toString()
}
