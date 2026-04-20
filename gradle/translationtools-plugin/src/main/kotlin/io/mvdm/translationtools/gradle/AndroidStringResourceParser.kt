package io.mvdm.translationtools.gradle

import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

internal data class SkippedAndroidResource(
   val resourceName: String,
   val reason: String,
)

internal data class ParsedAndroidResourceValue(
   val resourceName: String,
   val value: String?,
   val managedRemotely: Boolean,
)

internal class AndroidStringResourceParser
{
   fun parse(
      resourceDirectories: List<File>,
      defaultLocale: String,
      keyOverrides: Map<String, String>,
      projectPath: String,
   ): AndroidTranslationProject
   {
      val discovery = discoverLocaleFiles(resourceDirectories, defaultLocale)
      val localeFiles = discovery.files
      if (localeFiles.isEmpty())
         throw IllegalStateException("No Android locale directories with XML string resources were found.")

      val warnings = discovery.warnings.toMutableList()
      val collected = linkedMapOf<Pair<String, String>, MutableMap<String, String?>>()
      val metadata = linkedMapOf<Pair<String, String>, Pair<String, Boolean>>()

      localeFiles.forEach { localeFile ->
         val parsed = parseStringsFile(localeFile.file.readText())
         if (parsed.imported.isEmpty())
            warnings += "No importable strings found in '${localeFile.file.path}'. Locale '${localeFile.locale}' kept in locale metadata."

         warnings += parsed.skipped.map { "Skipped '${it.resourceName}' in '${localeFile.file.name}': ${it.reason}." }

         parsed.imported.forEach { entry ->
            val translationKey = keyOverrides[entry.resourceName] ?: entry.resourceName
            val origin = buildOrigin(projectPath, localeFile.relativeBaseFile)
            val ref = origin to translationKey
            collected.getOrPut(ref) { linkedMapOf() }[localeFile.locale] = entry.value
            metadata.putIfAbsent(ref, localeFile.relativeBaseFile to entry.managedRemotely)
         }
      }

      val locales = localeFiles.map { it.locale }.distinct().sorted()
      val items = collected.toSortedMap(compareBy<Pair<String, String>> { it.first }.thenBy { it.second }).map { (ref, perLocale) ->
         val (origin, key) = ref
         val (baseFile, managedRemotely) = metadata.getValue(ref)
         AndroidTranslationEntry(
            origin = origin,
            resourceName = key,
            key = key,
            valuesByLocale = locales.associateWith { locale -> perLocale[locale] }.toSortedMap(),
            owningBaseFile = baseFile,
            managedRemotely = managedRemotely,
         )
      }

      if (items.isEmpty())
         warnings += "All discovered Android locale directories were empty. Importing locale metadata with an empty translation payload."

      return AndroidTranslationProject(
         defaultLocale = normalizeAndroidTranslationLocale(defaultLocale),
         locales = locales,
         entries = items,
         warnings = warnings,
      )
   }
}

internal data class ParsedAndroidStringsFile(
   val imported: List<ParsedAndroidResourceValue>,
   val skipped: List<SkippedAndroidResource>,
)

internal data class AndroidResourceDiscovery(
   val files: List<AndroidResourceVariantFile>,
   val warnings: List<String>,
)

internal fun discoverLocaleFiles(resourceDirectories: List<File>, defaultLocale: String): AndroidResourceDiscovery
{
   val normalizedDefaultLocale = normalizeAndroidTranslationLocale(defaultLocale)
   val warnings = mutableListOf<String>()
   val discovered = resourceDirectories
       .distinct()
       .filter { it.exists() }
       .flatMap { directory ->
          directory.listFiles()
             ?.filter { it.isDirectory && it.name.startsWith("values") }
             ?.sortedBy { it.name }
             .orEmpty()
             .flatMap valuesDirectoryLoop@{ valuesDirectory ->
                 val locale = resolveAndroidLocale(valuesDirectory.name, normalizedDefaultLocale)
                 if (locale == null) {
                    warnings += "Ignored unsupported resource qualifier directory '${valuesDirectory.path}'."
                    return@valuesDirectoryLoop emptyList()
                 }

                 valuesDirectory.listFiles()
                   ?.filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
                   ?.sortedBy { it.name }
                   .orEmpty()
                   .map { file ->
                       AndroidResourceVariantFile(
                          locale = locale,
                          file = file,
                          resourceDirectory = directory,
                          relativeBaseFile = file.name,
                       )
                   }
             }
       }

   val collisions = discovered.groupBy { it.locale to it.relativeBaseFile }.filterValues { it.size > 1 }
   if (collisions.isNotEmpty()) {
      val detail = collisions.entries.joinToString(", ") { (entry, files) -> "${entry.first}:${entry.second} -> ${files.joinToString(", ") { it.file.path }}" }
      throw IllegalStateException("Multiple Android resource files normalize to the same locale and base file: $detail")
   }

   return AndroidResourceDiscovery(discovered, warnings)
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

private val documentBuilderFactory: DocumentBuilderFactory by lazy {
   DocumentBuilderFactory.newInstance().apply {
      isNamespaceAware = false
      isIgnoringComments = true
      setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      setFeature("http://xml.org/sax/features/external-general-entities", false)
      setFeature("http://xml.org/sax/features/external-parameter-entities", false)
   }
}

internal fun parseStringsFile(xml: String): ParsedAndroidStringsFile
{
   val documentBuilder = documentBuilderFactory.newDocumentBuilder()
   val document = documentBuilder.parse(InputSource(StringReader(xml)))
   val resources = document.documentElement ?: return ParsedAndroidStringsFile(emptyList(), emptyList())
   val imported = mutableListOf<ParsedAndroidResourceValue>()
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
            val managedRemotely = node.attributes?.getNamedItem("translatable")?.nodeValue != "false"
            imported += ParsedAndroidResourceValue(
               resourceName = name,
               value = node.textContent.takeUnless { it == "" },
               managedRemotely = managedRemotely,
            )
         }

         "plurals" -> skipped += SkippedAndroidResource(name, "plurals are not supported")
         "string-array" -> skipped += SkippedAndroidResource(name, "string-array is not supported")
      }
   }

   return ParsedAndroidStringsFile(imported = imported.toList(), skipped = skipped)
}

internal fun buildOrigin(projectPath: String, relativeBaseFile: String): String
{
   val normalizedProjectPath = projectPath.ifBlank { ":" }
   return if (normalizedProjectPath == ":") {
      ":/$relativeBaseFile"
   }
   else {
      "$normalizedProjectPath:/$relativeBaseFile"
   }
}
