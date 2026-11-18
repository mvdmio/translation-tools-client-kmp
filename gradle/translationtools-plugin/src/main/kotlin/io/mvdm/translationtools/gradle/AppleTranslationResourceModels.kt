package io.mvdm.translationtools.gradle

import java.io.File

internal data class AppleTranslationProject(
   val locales: List<String>,
   val entries: List<AppleTranslationEntry>,
   val warnings: List<String>,
)

internal data class AppleTranslationEntry(
   val origin: String,
   val key: String,
   val valuesByLocale: Map<String, String?>,
)

/** The two Apple resource file types the plugin syncs; each is its own Origin per file name. */
internal enum class AppleResourceKind(val extension: String)
{
   STRINGS("strings"),
   STRINGSDICT("stringsdict"),
}

internal data class AppleResourceVariantFile(
   val locale: String,
   val isBase: Boolean,
   val file: File,
   val resourceDirectory: File,
   val relativeBaseFile: String,
   val kind: AppleResourceKind,
)

internal data class AppleResourceDiscovery(
   val files: List<AppleResourceVariantFile>,
   val warnings: List<String>,
)

internal data class AppleLocaleResolution(
   val locale: String,
   val isBase: Boolean,
   val warning: String?,
)
