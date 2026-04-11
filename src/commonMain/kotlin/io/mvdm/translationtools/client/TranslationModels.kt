package io.mvdm.translationtools.client

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
public data class ProjectMetadata(
   val locales: List<String>,
   val defaultLocale: String,
)

@Serializable
public data class TranslationItem(
   val ref: TranslationRef,
   val value: String?,
)

@Serializable
public data class TranslationSnapshot(
   val locale: String,
   val items: List<TranslationItem>,
)

@Serializable
public data class StoredTranslations(
   val projectMetadata: ProjectMetadata?,
   val snapshots: List<TranslationSnapshot>,
   val lastSuccessfulRefreshAt: Instant?,
)

public enum class TranslationRefreshStatus
{
   Idle,
   RestoringCache,
   Refreshing,
   Ready,
   Failed,
}

public data class TranslationRefreshState(
   val status: TranslationRefreshStatus = TranslationRefreshStatus.Idle,
   val lastSuccessfulRefreshAt: Instant? = null,
   val lastFailureMessage: String? = null,
)
