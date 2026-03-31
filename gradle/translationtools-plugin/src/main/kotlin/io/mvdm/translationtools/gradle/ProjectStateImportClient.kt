package io.mvdm.translationtools.gradle

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class ProjectTranslationStateImportRequest(
   val defaultLocale: String,
   val locales: List<String>,
   val items: List<ProjectTranslationStateImportItemRequest>,
)

@Serializable
internal data class ProjectTranslationStateImportItemRequest(
   val key: String,
   val translations: Map<String, String?>,
)

@Serializable
internal data class ProjectTranslationStateImportResponse(
   val receivedKeyCount: Int,
   val receivedLocaleCount: Int,
   val createdTranslationCount: Int,
   val updatedTranslationCount: Int,
)

internal suspend fun importProjectState(
   client: HttpClient,
   apiKey: String,
   request: ProjectTranslationStateImportRequest,
): ProjectTranslationStateImportResponse
{
   return executeImportRequest {
      val body = client.post("$BASE_URL/api/v1/translations/project/import") {
         header(HttpHeaders.Authorization, apiKey)
         contentType(ContentType.Application.Json)
         setBody(importJson.encodeToString(request))
      }.requireImportSuccessBodyText()

      importJson.decodeFromString(ProjectTranslationStateImportResponse.serializer(), body)
   }
}

internal fun createDefaultImportHttpClient(): HttpClient
{
   return HttpClient(io.ktor.client.engine.cio.CIO) {
      install(ContentNegotiation) {
         json(Json { ignoreUnknownKeys = true })
      }
   }
}

private suspend fun <T> executeImportRequest(action: suspend () -> T): T
{
   try {
      return action()
   }
   catch (exception: TranslationToolsImportException) {
      throw exception
   }
   catch (exception: SerializationException) {
      throw TranslationToolsImportException("Failed to parse TranslationTools import response.", exception)
   }
   catch (exception: ClientRequestException) {
      throw TranslationToolsImportException(
         "TranslationTools import request failed with status ${exception.response.status.value}: ${exception.response.bodyAsText().trim()}",
         exception,
      )
   }
   catch (exception: ServerResponseException) {
      throw TranslationToolsImportException(
         "TranslationTools import request failed with status ${exception.response.status.value}: ${exception.response.bodyAsText().trim()}",
         exception,
      )
   }
   catch (exception: Throwable) {
      throw TranslationToolsImportException("TranslationTools import request failed: ${exception.message ?: "unknown error"}", exception)
   }
}

private suspend fun HttpResponse.requireImportSuccessBodyText(): String
{
   if (!status.isSuccess()) {
      val body = bodyAsText().trim()
      throw TranslationToolsImportException("TranslationTools request failed with status ${status.value}: $body")
   }

   return bodyAsText()
}

internal class TranslationToolsImportException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private val importJson = Json { ignoreUnknownKeys = true }
