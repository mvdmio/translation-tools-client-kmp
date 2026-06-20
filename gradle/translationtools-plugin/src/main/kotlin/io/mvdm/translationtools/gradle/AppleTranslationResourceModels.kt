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

internal data class AppleResourceVariantFile(
   val locale: String,
   val isBase: Boolean,
   val file: File,
   val resourceDirectory: File,
   val relativeBaseFile: String,
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
