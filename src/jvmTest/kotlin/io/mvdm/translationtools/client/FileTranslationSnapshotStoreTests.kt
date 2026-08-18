package io.mvdm.translationtools.client

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant

class FileTranslationSnapshotStoreTests
{
   @Test
   fun save_then_load_should_roundtrip_translations() = runTest {
      val fileSystem = FakeFileSystem()
      val store = FileTranslationSnapshotStore(fileSystem, "/cache/translations.json")
      val expected = StoredTranslations(
         projectMetadata = ProjectMetadata(locales = listOf("en", "nl"), defaultLocale = "en"),
         snapshots = listOf(
            TranslationSnapshot("en", listOf(TranslationItem(TranslationRef(":app:/strings.xml", "home_title"), "Hello"))),
            TranslationSnapshot("nl", listOf(TranslationItem(TranslationRef(":app:/strings.xml", "home_title"), "Hallo"))),
         ),
         lastSuccessfulRefreshAt = Instant.parse("2026-03-25T10:00:00Z"),
      )

      store.save(expected)
      val actual = store.load()

      assertEquals(expected, actual)
   }

   @Test
   fun save_should_write_lastSuccessfulRefreshAt_as_iso8601_string() = runTest {
      val fileSystem = FakeFileSystem()
      val filePath = "/cache/translations.json".toPath()
      val store = FileTranslationSnapshotStore(fileSystem, filePath.toString())

      store.save(
         StoredTranslations(
            projectMetadata = null,
            snapshots = emptyList(),
            lastSuccessfulRefreshAt = Instant.parse("2026-03-25T10:00:00Z"),
         )
      )

      val raw = fileSystem.read(filePath) { readUtf8() }
      val timestamp = Json.parseToJsonElement(raw).jsonObject["lastSuccessfulRefreshAt"]!!.jsonPrimitive.content
      assertEquals("2026-03-25T10:00:00Z", timestamp)
   }

   @Test
   fun load_should_restore_timestamp_from_2x_iso8601_fixture() = runTest {
      val fileSystem = FakeFileSystem()
      val filePath = "/cache/translations.json".toPath()
      fileSystem.createDirectories(filePath.parent!!)
      fileSystem.write(filePath) {
         writeUtf8(
            """
            {
              "projectMetadata": {"locales":["en"],"defaultLocale":"en"},
              "snapshots": [
                {
                  "locale": "en",
                  "items": [
                    {"ref":{"origin":":app:/strings.xml","key":"home_title"},"value":"Hello"}
                  ]
                }
              ],
              "lastSuccessfulRefreshAt": "2026-03-25T10:00:00Z",
              "clientId": null
            }
            """.trimIndent()
         )
      }
      val store = FileTranslationSnapshotStore(fileSystem, filePath.toString())

      val actual = store.load()

      assertEquals(Instant.parse("2026-03-25T10:00:00Z"), actual?.lastSuccessfulRefreshAt)
      assertEquals("Hello", actual?.snapshots?.single()?.items?.single()?.value)
   }

   @Test
   fun load_should_return_null_when_file_missing() = runTest {
      val fileSystem = FakeFileSystem()
      val store = FileTranslationSnapshotStore(fileSystem, "/cache/translations.json")

      val actual = store.load()

      assertNull(actual)
   }

   @Test
   fun load_should_clear_invalid_json_and_return_null() = runTest {
      val fileSystem = FakeFileSystem()
      val filePath = "/cache/translations.json".toPath()
      fileSystem.createDirectories(filePath.parent!!)
      fileSystem.write(filePath) {
         writeUtf8("not-json")
      }
      val store = FileTranslationSnapshotStore(fileSystem, filePath.toString())

      val actual = store.load()

      assertNull(actual)
      assertFalse(fileSystem.exists(filePath))
   }

   @Test
   fun clear_should_delete_snapshot_file() = runTest {
      val fileSystem = FakeFileSystem()
      val filePath = "/cache/translations.json".toPath()
      val store = FileTranslationSnapshotStore(fileSystem, filePath.toString())
      store.save(
         StoredTranslations(
            projectMetadata = null,
            snapshots = emptyList(),
            lastSuccessfulRefreshAt = null,
         )
      )

      store.clear()

      assertFalse(fileSystem.exists(filePath))
   }
}
