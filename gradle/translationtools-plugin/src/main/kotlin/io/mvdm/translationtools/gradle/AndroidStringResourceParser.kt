package io.mvdm.translationtools.gradle

import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

internal data class AndroidProjectTranslationState(
   val defaultLocale: String,
   val locales: List<String>,
   val translations: Map<String, Map<String, String?>>,
   val warnings: List<String>,
)

internal data class AndroidResourceLocaleFile(
   val locale: String,
   val stringsFile: File,
)

internal data class SkippedAndroidResource(
   val resourceName: String,
   val reason: String,
)

internal class AndroidStringResourceParser
{
   fun parse(
      resourceDirectories: List<File>,
      defaultLocale: String,
      keyOverrides: Map<String, String>,
   ): AndroidProjectTranslationState
   {
      val localeFiles = discoverLocaleFiles(resourceDirectories, defaultLocale)
      if (localeFiles.isEmpty())
         throw IllegalStateException("No Android locale directories with strings.xml files were found.")

      val warnings = mutableListOf<String>()
      val collected = linkedMapOf<String, MutableMap<String, String?>>()

      localeFiles.forEach { localeFile ->
         val parsed = parseStringsFile(localeFile.stringsFile.readText())
         if (parsed.imported.isEmpty())
            warnings += "No importable strings found in '${localeFile.stringsFile.path}'. Locale '${localeFile.locale}' kept in locale metadata."

         warnings += parsed.skipped.map { "Skipped '${it.resourceName}' in '${localeFile.stringsFile.name}': ${it.reason}." }

         parsed.imported.forEach { (resourceName, value) ->
            val translationKey = keyOverrides[resourceName] ?: resourceName
            collected.getOrPut(translationKey) { linkedMapOf() }[localeFile.locale] = value
         }
      }

      val locales = localeFiles.map { it.locale }.distinct().sorted()
      val items = collected.toSortedMap().mapValues { (_, perLocale) ->
         locales.associateWith { locale -> perLocale[locale] }.toSortedMap()
      }

      if (items.isEmpty())
         warnings += "All discovered Android locale directories were empty. Importing locale metadata with an empty translation payload."

      return AndroidProjectTranslationState(
         defaultLocale = normalizeAndroidTranslationLocale(defaultLocale),
         locales = locales,
         translations = items,
         warnings = warnings,
      )
   }
}

internal data class ParsedAndroidStringsFile(
   val imported: Map<String, String?>,
   val skipped: List<SkippedAndroidResource>,
)

internal fun discoverLocaleFiles(resourceDirectories: List<File>, defaultLocale: String): List<AndroidResourceLocaleFile>
{
   val normalizedDefaultLocale = normalizeAndroidTranslationLocale(defaultLocale)
   val discovered = resourceDirectories
      .distinct()
      .filter { it.exists() }
      .flatMap { directory ->
         directory.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            ?.sortedBy { it.name }
            .orEmpty()
            .mapNotNull { valuesDirectory ->
               val locale = resolveAndroidLocale(valuesDirectory.name, normalizedDefaultLocale) ?: return@mapNotNull null
               val stringsFile = File(valuesDirectory, "strings.xml")
               if (!stringsFile.exists())
                  return@mapNotNull null

               AndroidResourceLocaleFile(locale = locale, stringsFile = stringsFile)
            }
      }

   val collisions = discovered.groupBy { it.locale }.filterValues { it.size > 1 }
   if (collisions.isNotEmpty()) {
      val detail = collisions.entries.joinToString(", ") { (locale, files) -> "$locale -> ${files.joinToString(", ") { it.stringsFile.path }}" }
      throw IllegalStateException("Multiple Android directories normalize to the same locale: $detail")
   }

   return discovered
}

internal fun resolveAndroidLocale(directoryName: String, defaultLocale: String): String?
{
   if (directoryName == "values")
      return defaultLocale

   val qualifier = directoryName.removePrefix("values-")
   if (qualifier.isBlank())
      return defaultLocale

   return when {
      qualifier.matches(Regex("[a-z]{2}")) -> qualifier
      qualifier.matches(Regex("[a-z]{2}-r[A-Z]{2}")) -> qualifier.replace("-r", "-").lowercase()
      else -> null
   }
}

internal fun normalizeAndroidTranslationLocale(locale: String): String
{
   return locale.trim().lowercase()
}

internal fun parseStringsFile(xml: String): ParsedAndroidStringsFile
{
   val documentBuilder = DocumentBuilderFactory.newInstance().apply {
      isNamespaceAware = false
      isIgnoringComments = true
      setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      setFeature("http://xml.org/sax/features/external-general-entities", false)
      setFeature("http://xml.org/sax/features/external-parameter-entities", false)
   }.newDocumentBuilder()
   val document = documentBuilder.parse(InputSource(StringReader(xml)))
   val resources = document.documentElement ?: return ParsedAndroidStringsFile(emptyMap(), emptyList())
   val imported = linkedMapOf<String, String?>()
   val skipped = mutableListOf<SkippedAndroidResource>()
   val seenNames = mutableSetOf<String>()
   val children = resources.childNodes

   for (index in 0 until children.length) {
      val node = children.item(index)
      if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE)
         continue

      val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
      if (!seenNames.add(name))
         throw IllegalStateException("Duplicate Android resource name '$name' in one locale file.")

      when (node.nodeName) {
         "string" -> {
            if (node.attributes?.getNamedItem("translatable")?.nodeValue == "false") {
               skipped += SkippedAndroidResource(name, "translatable=false")
               continue
            }

            imported[name] = node.textContent.takeUnless { it == "" }
         }

         "plurals" -> skipped += SkippedAndroidResource(name, "plurals are not supported")
         "string-array" -> skipped += SkippedAndroidResource(name, "string-array is not supported")
      }
   }

   return ParsedAndroidStringsFile(imported = imported.toMap(), skipped = skipped)
}
