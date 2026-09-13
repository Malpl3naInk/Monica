package takagi.ru.monica.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.data.*
import takagi.ru.monica.security.SecurityManager
import uniffi.mdbx_ffi.MdbxWriteCommand
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NativeApiTokenInstrumentedTest {
    @Test fun cliNativeIdentityCategoryExtensionsAndSnapshotSurviveRoundTrip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = PasswordDatabase.getDatabase(context)
        val dao = room.localMdbxDatabaseDao()
        val security = SecurityManager(context)
        val repository = Mdbx2Repository(context, dao, security)
        val password = "Synthetic native token vault password 123"
        val file = repository.createInitializedVaultFile(MdbxTigaMode.SKY, password)
        var databaseId = 0L
        try {
            databaseId = dao.insertDatabase(LocalMdbxDatabase(
                name = "Native token test", filePath = file.absolutePath,
                engineType = MdbxEngineType.RUST_MDBX2.name,
                encryptedPassword = security.encryptData(password),
                unlockMethod = MdbxUnlockMethod.MASTER_PASSWORD.storedValue
            ))
            val parent = repository.createFolder(databaseId, "CLI parent", null)
            val child = repository.createFolder(databaseId, "CLI child", parent.folderId)
            val entryId = UUID.randomUUID().toString()
            val payload = """{"schema":"monica.gateway.credential.v1","provider":"gitlab","api_base":"https://gitlab.example.test/api/v4/","note":"CLI note","token":"synthetic-cli-token-123456","future":{"scope":"api"}}"""
            // Exactly the native CreateEntry contract used by Monica CLI: no Android password metadata.
            repository.withVaultForSync(databaseId) { _, vault ->
                vault.executeWriteOperation(UUID.randomUUID().toString(), "test-cli-create", listOf(
                    MdbxWriteCommand.CreateEntry(entryId, child.folderId, "api-token", "cli-credential", payload),
                    MdbxWriteCommand.CreateEntry(UUID.randomUUID().toString(), child.folderId, "login", "Not a token", "{}")
                ))
            }
            val summary = repository.listNativeApiTokens(databaseId).single()
            assertEquals(entryId, summary.entryId)
            assertEquals(child.folderId, summary.collectionId)
            assertFalse(summary.isFavorite)
            val original = repository.readNativeApiToken(summary)
            assertEquals(ApiTokenPayload.decode(payload), ApiTokenPayload.decode(original.payload))
            val snapshot = repository.createSnapshot(databaseId, "Native token before edit")
            val edited = ApiTokenPayload.update(original.payload, "note", "Edited on Android")
            repository.saveNativeApiToken(databaseId, original, "android-renamed", edited, isFavorite = true)
            val reopened = Mdbx2Repository(context, dao, security)
            val updatedSummary = reopened.listNativeApiTokens(databaseId).single()
            assertEquals(entryId, updatedSummary.entryId)
            assertEquals(child.folderId, updatedSummary.collectionId)
            assertEquals("android-renamed", updatedSummary.title)
            assertTrue(updatedSummary.isFavorite)
            assertTrue(reopened.readNativeApiToken(updatedSummary).summary.isFavorite)
            assertEquals(ApiTokenPayload.decode(edited), ApiTokenPayload.decode(reopened.readNativeApiToken(updatedSummary).payload))
            assertTrue(runCatching {
                repository.saveNativeApiToken(databaseId, original, "stale-edit", original.payload)
            }.isFailure)
            assertTrue(repository.readStoredEntries(databaseId).any { it.entryId == entryId && it.entryType == "api-token" })
            assertTrue(room.passwordEntryDao().getByMdbxDatabaseIdSync(databaseId).isEmpty())
            val moved = repository.saveNativeApiToken(databaseId, reopened.readNativeApiToken(updatedSummary),
                "android-renamed", edited, collectionId = "")
            assertEquals(entryId, moved.entryId)
            assertNotEquals(child.folderId, moved.collectionId)
            val beforeUnfavorite = repository.readNativeApiToken(moved)
            assertTrue(beforeUnfavorite.summary.isFavorite)
            repository.saveNativeApiToken(databaseId, beforeUnfavorite, moved.title, edited, isFavorite = false)
            assertFalse(repository.listNativeApiTokens(databaseId).single().isFavorite)
            val unfavorited = repository.readNativeApiToken(databaseId, entryId)
            assertFalse(unfavorited.summary.isFavorite)
            assertEquals(edited, unfavorited.payload)
            assertTrue("A stale editor must not undo a newer favorite change", runCatching {
                repository.saveNativeApiToken(databaseId, beforeUnfavorite, moved.title, edited)
            }.isFailure)
            val refavorited = repository.saveNativeApiToken(databaseId, unfavorited, moved.title, edited, isFavorite = true)
            assertTrue(repository.readNativeApiToken(refavorited).summary.isFavorite)
            repository.deleteNativeApiToken(repository.readNativeApiToken(refavorited))
            assertTrue(repository.listNativeApiTokens(databaseId).isEmpty())
            repository.revertToSnapshot(databaseId, snapshot.snapshotId)
            val restored = repository.readNativeApiToken(repository.listNativeApiTokens(databaseId).single())
            assertEquals(child.folderId, restored.summary.collectionId)
            assertFalse(restored.summary.isFavorite)
            assertEquals(ApiTokenPayload.decode(payload), ApiTokenPayload.decode(restored.payload))
        } finally {
            if (databaseId > 0) dao.deleteDatabaseById(databaseId)
            repository.deleteOwnedVaultFile(file)
        }
    }
}
