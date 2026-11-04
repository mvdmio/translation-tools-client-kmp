package io.mvdm.translationtools.client

import kotlinx.serialization.Serializable
import kotlin.time.Instant

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

/**
 * One entry of a Declaration: the [value] the app holds for [ref] in [locale]. An empty [value]
 * declares the Key without a value.
 */
public data class TranslationDeclarationItem(
   val ref: TranslationRef,
   val locale: String,
   val value: String,
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
   val clientId: String? = null,
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
