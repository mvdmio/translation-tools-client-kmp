package io.mvdm.translationtools.gradle

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/** A String Catalog's JSON tree after a merge, and the [warnings] the merge raised. */
internal data class MergedAppleCatalog(val root: JsonObject, val warnings: List<String>)

/**
 * Merges the pulled [items] of the catalog Origin into the String Catalog [file] and writes the whole file in Xcode's layout,
 * only when the content changed. A file that is not a readable catalog is left as it is, and [onDetail] gets why its JSON failed to parse.
 * Returns the warnings to print.
 */
internal fun writeAppleCatalogFile(file: File, items: List<PulledTranslationItem>, onDetail: (String) -> Unit = {}): List<String>
{
   val bytes = file.readBytes()
   val catalog = when (val parsed = parseAppleStringCatalog(decodeAppleStrings(bytes), file.path)) {
      is ParsedAppleStringCatalog.Readable -> parsed.catalog
      is ParsedAppleStringCatalog.Unreadable -> {
         parsed.detail?.let(onDetail)
         return listOf("Left '${file.path}' unchanged: it is not a readable String Catalog.")
      }
   }

   val merged = mergeAppleCatalog(catalog, items)
   if (merged.root != catalog.root) {
      val rendered = renderAppleCatalog(merged.root).toByteArray(Charsets.UTF_8)
      if (!rendered.contentEquals(bytes))
         file.writeBytes(rendered)
   }

   return merged.warnings
}

/** A remote value as a String Catalog can hold it. */
private sealed interface RemoteValue
{
   val text: String

   data class Plain(override val text: String) : RemoteValue

   data class Plural(override val text: String, val plural: HoldablePlural) : RemoteValue

   /** A Plural with an exact case other than `=0`. */
   data class NotHoldable(override val text: String) : RemoteValue
}

private fun remoteValue(text: String): RemoteValue
{
   if (parsePluralValue(text) == null)
      return RemoteValue.Plain(text)

   return holdablePlural(text)?.let { RemoteValue.Plural(text, it) } ?: RemoteValue.NotHoldable(text)
}

/**
 * Applies the remote values of [items] to [catalog]. A value changes only when it differs from what push would send for that
 * Key and Locale; a Key marked "Don't translate" and a value push skips stay as they are, a Plural a catalog cannot hold keeps
 * the local value with a warning, and nothing is deleted. A Key the catalog lacks is added with `extractionState: "manual"`.
 */
internal fun mergeAppleCatalog(catalog: AppleStringCatalog, items: List<PulledTranslationItem>): MergedAppleCatalog
{
   val memberNames = localeMemberNames(catalog)
   val addedLocales = linkedSetOf<String>()
   val keptLocal = mutableListOf<String>()
   var strings = catalog.strings
   val keysByName = catalog.keys.associateBy { it.key }

   items.forEach { item ->
      val existing = keysByName[item.key]
      if (existing != null && !existing.shouldTranslate)
         return@forEach

      val updates = item.valuesByLocale.entries
         .mapNotNull { (locale, value) -> value?.let { normalizeAppleLocale(locale) to remoteValue(it) } }
         .filter { (locale, _) -> existing == null || catalog.pushValue(existing, locale) !is CatalogPushValue.Skipped }
      updates.filter { (_, value) -> value is RemoteValue.NotHoldable }.forEach { (locale, _) ->
         keptLocal += "Kept local '${item.key}' in '${catalog.path}': the value for '$locale' in TranslationTools is not a Plural that a String Catalog can hold."
      }

      val changes = updates
         .filter { (locale, value) -> value !is RemoteValue.NotHoldable && (existing == null || catalog.pushValue(existing, locale) != CatalogPushValue.Value(value.text)) }
         .toMap()
      if (changes.isEmpty())
         return@forEach

      val written = if (existing == null)
         newKey(catalog, item.key, changes, memberNames)
      else
         changedKey(strings[item.key] as? JsonObject ?: JsonObject(emptyMap()), existing, catalog.sourceLanguage, changes, memberNames)
      strings = strings.withMember(item.key, written.key)
      addedLocales += written.addedLocales
   }

   val newLocaleWarnings = (addedLocales - catalog.locales).map { locale ->
      "Added locale '$locale' to '${catalog.path}'. Add '$locale' (or its region) to your Xcode project's knownRegions to bundle it."
   }

   return MergedAppleCatalog(catalog.root.withMember("strings", strings), keptLocal + newLocaleWarnings)
}

/** The member name each Locale has in the catalog: the first one any Key uses, else Xcode's casing. */
private class LocaleMemberNames(private val known: Map<String, String>)
{
   fun of(locale: String): String = known[locale] ?: toLprojDirectory(locale).removeSuffix(".lproj")
}

private fun localeMemberNames(catalog: AppleStringCatalog): LocaleMemberNames
{
   val known = linkedMapOf<String, String>()
   catalog.strings.values.forEach { key ->
      ((key as? JsonObject)?.get("localizations") as? JsonObject)?.keys?.forEach { name -> known.putIfAbsent(normalizeAppleLocale(name), name) }
   }
   known.putIfAbsent(catalog.sourceLanguage, catalog.sourceLanguageName)
   return LocaleMemberNames(known)
}

/** A Key's JSON after a merge, and the normalized Locales it gained. */
private class WrittenKey(val key: JsonObject, val addedLocales: List<String>)

/** [key] (the local [existing] Key, or null for a new one) with each of [changes] (by normalized Locale) written as `translated`. */
private fun changedKey(
   key: JsonObject,
   existing: AppleCatalogKey?,
   sourceLanguage: String,
   changes: Map<String, RemoteValue>,
   memberNames: LocaleMemberNames,
): WrittenKey
{
   val addedLocales = mutableListOf<String>()
   var localizations = key["localizations"] as? JsonObject ?: JsonObject(emptyMap())
   changes.forEach { (locale, value) ->
      val existingName = localizations.keys.lastOrNull { normalizeAppleLocale(it) == locale }
      val current = existingName?.let { localizations[it] } as? JsonObject ?: JsonObject(emptyMap())
      val localization = when (value) {
         is RemoteValue.Plain -> plainLocalization(current, value.text)
         is RemoteValue.Plural -> pluralLocalization(current, existing?.localizations?.get(locale), existing?.localizations?.get(sourceLanguage), value.plural)
         is RemoteValue.NotHoldable -> current
      }
      if (existingName == null)
         addedLocales += locale
      localizations = localizations.withMember(existingName ?: memberNames.of(locale), localization)
   }

   return WrittenKey(key.withMember("localizations", localizations), addedLocales)
}

private fun plainLocalization(current: JsonObject, text: String): JsonObject
{
   val unit = current["stringUnit"] as? JsonObject ?: JsonObject(emptyMap())
   return current
      .withoutMember("variations")
      .withoutMember("substitutions")
      .withMember("stringUnit", unit.withMember("state", JsonPrimitive("translated")).withMember("value", JsonPrimitive(text)))
}

/**
 * [current] with the forms of [plural] in the shape of the [local] value: a substitution keeps its name, format specifier and
 * argument number, a value that varies by plural as a whole keeps its shape, anything else becomes the whole-value shape.
 * `#` is written as the number token [local] uses, else the one the [source]-language value uses, else the shape's default.
 */
private fun pluralLocalization(current: JsonObject, local: AppleCatalogLocalization?, source: AppleCatalogLocalization?, plural: HoldablePlural): JsonObject
{
   val number = if (local is AppleCatalogLocalization.SubstitutionPlural) NumberSpecifiers.substitution(local.formatSpecifier) else NumberSpecifiers.WHOLE_VALUE
   val body = number.bodyIn(local?.pluralForms().orEmpty()) ?: number.bodyIn(source?.pluralForms().orEmpty()) ?: number.defaultBody

   return when (local) {
      is AppleCatalogLocalization.SubstitutionPlural -> {
         val substitutions = current["substitutions"] as? JsonObject ?: JsonObject(emptyMap())
         current.withMember("substitutions", substitutions.withMember(local.name, local.substitution.withPluralVariations(plural, body)))
      }
      is AppleCatalogLocalization.WholePlural -> current.withPluralVariations(plural, body)
      else -> current.withoutMember("stringUnit").withoutMember("substitutions").withoutMember("variations").withPluralVariations(plural, body)
   }
}

/** This object with `variations.plural` holding exactly the forms of [plural], its `#` written as `%` plus [body]. */
private fun JsonObject.withPluralVariations(plural: HoldablePlural, body: String): JsonObject
{
   val variations = this["variations"] as? JsonObject ?: JsonObject(emptyMap())
   val remote = plural.forms.map { it.quantity.keyword }.toSet()
   val categories = PluralQuantity.entries.map { it.keyword }
   var forms = JsonObject((variations["plural"] as? JsonObject).orEmpty().filterKeys { it !in categories || it in remote })

   plural.forms.forEach { form ->
      val name = form.quantity.keyword
      val text = form.render(body)
      val localForm = forms[name] as? JsonObject ?: JsonObject(emptyMap())
      val unit = localForm["stringUnit"] as? JsonObject ?: JsonObject(emptyMap())
      if (unit.stringMember("value") != text)
         forms = forms.withMember(name, localForm.withMember("stringUnit", unit.withMember("state", JsonPrimitive("translated")).withMember("value", JsonPrimitive(text))))
   }

   return withMember("variations", variations.withMember("plural", forms))
}

/**
 * A Key the catalog lacks: manual, so Xcode does not mark it Stale, with a value per Locale in [values]. The source language
 * gets a plain one only when it differs from the Key text, which push sends anyway.
 */
private fun newKey(catalog: AppleStringCatalog, key: String, values: Map<String, RemoteValue>, memberNames: LocaleMemberNames): WrittenKey
{
   val written = values.filter { (locale, value) -> locale != catalog.sourceLanguage || value !is RemoteValue.Plain || value.text != key }
   val manual = JsonObject(emptyMap()).withMember("extractionState", JsonPrimitive("manual"))
   return if (written.isEmpty()) WrittenKey(manual, emptyList()) else changedKey(manual, null, catalog.sourceLanguage, written, memberNames)
}

/** This object with [name] set to [value]: in place when it exists, else before the first member that sorts after it, else last. */
private fun JsonObject.withMember(name: String, value: JsonElement): JsonObject
{
   if (name in this)
      return JsonObject(LinkedHashMap(this).also { it[name] = value })

   val members = toList()
   val index = members.indexOfFirst { (existing, _) -> existing > name }.let { if (it < 0) members.size else it }
   return JsonObject((members.take(index) + (name to value) + members.drop(index)).toMap(LinkedHashMap()))
}

private fun JsonObject.withoutMember(name: String): JsonObject = JsonObject(LinkedHashMap(this).also { it.remove(name) })

/**
 * [root] as Xcode writes a String Catalog: two-space indentation, ` : ` between a name and its value, members in their order,
 * an empty object or array as an empty line between its brackets, and a newline at the end.
 */
internal fun renderAppleCatalog(root: JsonObject): String =
   StringBuilder().also { it.appendElement(root, 0) }.append('\n').toString()

private fun StringBuilder.appendElement(element: JsonElement, depth: Int)
{
   when (element) {
      is JsonObject -> appendContainer('{', '}', element.entries.map { (name, value) -> { appendString(name); append(" : "); appendElement(value, depth + 1) } }, depth)
      is JsonArray -> appendContainer('[', ']', element.map { value -> { appendElement(value, depth + 1) } }, depth)
      JsonNull -> append("null")
      is JsonPrimitive -> if (element.isString) appendString(element.content) else append(element.content)
   }
}

private fun StringBuilder.appendContainer(open: Char, close: Char, members: List<() -> Unit>, depth: Int)
{
   append(open).append('\n')
   members.forEachIndexed { index, member ->
      append("  ".repeat(depth + 1))
      member()
      if (index < members.lastIndex)
         append(',')
      append('\n')
   }
   if (members.isEmpty())
      append('\n')
   append("  ".repeat(depth)).append(close)
}

/** Escapes only `"`, `\` and control characters, so `/` and non-ASCII text are written as they are. */
private fun StringBuilder.appendString(value: String)
{
   append('"')
   value.forEach { char ->
      when {
         char == '"' -> append("\\\"")
         char == '\\' -> append("\\\\")
         char == '\n' -> append("\\n")
         char == '\r' -> append("\\r")
         char == '\t' -> append("\\t")
         char == '\b' -> append("\\b")
         char == '\u000C' -> append("\\f")
         char < ' ' -> append("\\u%04x".format(char.code))
         else -> append(char)
      }
   }
   append('"')
}
