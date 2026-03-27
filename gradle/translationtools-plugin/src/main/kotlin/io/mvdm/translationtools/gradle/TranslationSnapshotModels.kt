package io.mvdm.translationtools.gradle

import kotlinx.serialization.Serializable

@Serializable
data class TranslationSnapshotFile(
   val schemaVersion: Int,
   val project: SnapshotProject,
   val translations: Map<String, Map<String, String?>>,
)

@Serializable
data class SnapshotProject(
   val defaultLocale: String,
   val locales: List<String>,
)
