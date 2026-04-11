package io.mvdm.translationtools.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.path
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

public class TranslationToolsHttpApi(
   private val httpClient: HttpClient,
   private val apiKey: String,
) : TranslationToolsApi
{
   private val json = Json {
      ignoreUnknownKeys = true
   }

   override suspend fun getProjectMetadata(): ProjectMetadata
   {
      return execute {
         val response = httpClient.get("$BASE_URL/api/v1/translations/project") {
            addCommonHeaders()
         }

         val bodyText = response.requireSuccessBody()
         val body = json.decodeFromString<ProjectMetadataResponse>(bodyText)
         ProjectMetadata(body.locales, body.defaultLocale)
      }
   }

   override suspend fun getLocale(locale: String): List<TranslationItem>
   {
      return execute {
         val response = httpClient.get(BASE_URL) {
            url {
               path("api", "v1", "translations", locale)
            }
            addCommonHeaders()
         }

          val bodyText = response.requireSuccessBody()
          json.decodeFromString<List<TranslationItemResponse>>(bodyText)
             .map { TranslationItem(TranslationRef(it.origin, it.key), it.value) }
       }
   }

   override suspend fun getTranslation(locale: String, ref: TranslationRef, defaultValue: String?): TranslationItem
   {
      return execute {
          val response = httpClient.get(BASE_URL) {
             url {
                path("api", "v1", "translations", locale, ref.key)
                parameter("origin", ref.origin)
                if (defaultValue != null)
                   parameter("defaultValue", defaultValue)
             }
            addCommonHeaders()
         }

          val bodyText = response.requireSuccessBody()
          val body = json.decodeFromString<TranslationItemResponse>(bodyText)
          TranslationItem(TranslationRef(body.origin, body.key), body.value)
       }
   }

   private fun io.ktor.client.request.HttpRequestBuilder.addCommonHeaders()
   {
      header(HttpHeaders.Authorization, apiKey)
      header(HttpHeaders.AcceptEncoding, "gzip")
      accept(ContentType.Application.Json)
   }

   private suspend fun HttpResponse.requireSuccessBody(): String
   {
      val body = bodyAsText()
      if (status.value !in 200..299)
         throw TranslationToolsHttpException(status.value, body)

      return body
   }

   private suspend fun <T> execute(action: suspend () -> T): T
   {
      try {
         return action()
      }
      catch (exception: TranslationToolsException) {
         throw exception
      }
      catch (exception: SerializationException) {
         throw TranslationToolsSerializationException("Failed to deserialize TranslationTools response.", exception)
      }
      catch (exception: ClientRequestException) {
         throw TranslationToolsHttpException(exception.response.status.value, exception.response.bodyAsText())
      }
      catch (exception: ServerResponseException) {
         throw TranslationToolsHttpException(exception.response.status.value, exception.response.bodyAsText())
      }
      catch (exception: Throwable) {
         throw TranslationToolsNetworkException("TranslationTools request failed.", exception)
      }
   }

   private companion object
   {
      const val BASE_URL = "https://translations.mvdm.io"
   }
}

@Serializable
private data class ProjectMetadataResponse(
   val locales: List<String>,
   @SerialName("defaultLocale") val defaultLocale: String,
)

@Serializable
private data class TranslationItemResponse(
   val origin: String,
   val key: String,
   val value: String?,
)
