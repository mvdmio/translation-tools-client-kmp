package io.mvdm.translationtools.client

import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmTranslationSnapshotStoresTests
{
   @Test
   fun default_should_write_inside_mvdmio_translationtools_folder() = runTest {
      val fileSystem = FakeFileSystem()
      val store = JvmTranslationSnapshotStores.default(
         userHome = "/users/tester",
         fileSystem = fileSystem,
      )

      store.save(
         StoredTranslations(
            projectMetadata = null,
            snapshots = emptyList(),
            lastSuccessfulRefreshAt = null,
         )
      )

      val loaded = store.load()

      assertEquals(emptyList(), loaded?.snapshots)
      assertEquals(null, loaded?.projectMetadata)
   }
}
