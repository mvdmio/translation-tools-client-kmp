package io.mvdm.translationtools.client

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

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
