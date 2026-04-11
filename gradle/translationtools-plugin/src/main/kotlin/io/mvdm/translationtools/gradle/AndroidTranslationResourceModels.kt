package io.mvdm.translationtools.gradle

import java.io.File

internal data class AndroidTranslationProject(
   val defaultLocale: String,
   val locales: List<String>,
   val entries: List<AndroidTranslationEntry>,
   val warnings: List<String>,
)

internal data class AndroidTranslationEntry(
   val origin: String,
   val resourceName: String,
   val key: String,
   val valuesByLocale: Map<String, String?>,
   val owningBaseFile: String,
   val managedRemotely: Boolean,
)

internal data class AndroidResourceVariantFile(
   val locale: String,
   val file: File,
   val resourceDirectory: File,
   val relativeBaseFile: String,
)
