package takagi.ru.monica.repository

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.data.ApiTokenPayload
import takagi.ru.monica.data.ApiTokenMetadata
import takagi.ru.monica.data.CustomFieldDraft
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.MdbxEngineType
import takagi.ru.monica.data.MdbxSourceType
import takagi.ru.monica.data.MdbxStorageLocation
import takagi.ru.monica.data.MdbxSyncStateStore
import takagi.ru.monica.data.MdbxSyncStatus
import takagi.ru.monica.data.MdbxTigaMode
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.MdbxRemoteObject
import takagi.ru.monica.utils.MdbxRemoteSyncPaths
import takagi.ru.monica.utils.MdbxRemoteTransport
import takagi.ru.monica.utils.MdbxRemoteWriteMode

@RunWith(AndroidJUnit4::class)
class NativeApiTokenSyncInstrumentedTest {
    @Test
    fun nativeTokenSurvivesFailedUploadRetryAndDownloadWithoutRemainingPending() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = PasswordDatabase.getDatabase(context)
        val dao = room.localMdbxDatabaseDao()
        val state = MdbxSyncStateStore(room.mdbxSyncStateDao())
        val security = SecurityManager(context)
        val sourceContext = DeviceContext(context)
        val targetContext = DeviceContext(context)
        val source = Mdbx2Repository(sourceContext, dao, security)
        val target = Mdbx2Repository(targetContext, dao, security)
        val password = "Synthetic sync test password 123"
        val sourceFile = source.createInitializedVaultFile(MdbxTigaMode.SKY, password)
        val targetFile = File(sourceFile.parentFile, "${UUID.randomUUID()}.mdbx")
        val root = File(context.cacheDir, "native-token-sync-${UUID.randomUUID()}").apply { mkdirs() }
        val transport = DirectoryTransport(File(root, "remote").apply { mkdirs() })
        val sourceSync = Mdbx2RemoteSyncCoordinator(File(root, "source"),
            Mdbx2RepositorySyncSessionProvider(source), state)
        val targetSync = Mdbx2RemoteSyncCoordinator(File(root, "target"),
            Mdbx2RepositorySyncSessionProvider(target), state)
        val ids = mutableListOf<Long>()
        suspend fun register(file: File): Long = dao.insertDatabase(LocalMdbxDatabase(
            name = "Synthetic token sync", filePath = "vaults/synthetic.mdbx",
            workingCopyPath = file.absolutePath, cacheCopyPath = file.absolutePath,
            engineType = MdbxEngineType.RUST_MDBX2.name,
            storageLocation = MdbxStorageLocation.REMOTE_WEBDAV.name,
            sourceType = MdbxSourceType.REMOTE_WEBDAV.name,
            encryptedPassword = security.encryptData(password),
            lastSyncStatus = MdbxSyncStatus.IN_SYNC.name, lastSyncedAt = System.currentTimeMillis(),
        )).also(ids::add)
        try {
            val sourceId = register(sourceFile)
            val path = "vaults/synthetic.mdbx"
            sourceSync.publishBootstrap(sourceId, path, transport)
            targetSync.downloadBootstrapTo(path, transport, targetFile)
            val targetId = register(targetFile)
            targetSync.registerDownloadedBootstrap(targetId, path)
            val payload = """{"schema":"monica.gateway.credential.v1","provider":"gitlab","api_base":"https://synthetic.example.test/api/v4/","token":"synthetic-only-token","note":"sync test","future":{"scope":"api"}}"""
            val metadata = ApiTokenMetadata.withCustomFields(ApiTokenMetadata.withNotes(ApiTokenMetadata.empty(), "Synced app note"),
                listOf(CustomFieldDraft(-1, "Protected scope", "synthetic scope", true)))
            val created = source.saveNativeApiToken(sourceId, null, "synthetic-token", payload, isFavorite = true, metadata = metadata)
            assertEquals(MdbxSyncStatus.PENDING_UPLOAD.name, dao.getDatabaseById(sourceId)?.lastSyncStatus)
            assertTrue(source.getPendingSyncCount(sourceId) > 0)

            transport.failNextSegment = true
            assertTrue(runCatching { sourceSync.synchronize(sourceId, path, transport) }.isFailure)
            assertNotNull(state.read(sourceId).pendingSegment)
            assertTrue(source.getPendingSyncCount(sourceId) > 0)
            assertTrue(sourceSync.synchronize(sourceId, path, transport).uploadedSegments > 0)
            dao.updateSyncSuccess(sourceId, MdbxSyncStatus.IN_SYNC.name, System.currentTimeMillis())
            assertEquals(0, source.getPendingSyncCount(sourceId))
            assertEquals(0, source.getVaultDiagnostics(sourceId).pendingSyncCount)

            assertTrue(targetSync.synchronize(targetId, path, transport).downloadedSegments > 0)
            val received = target.readNativeApiToken(target.listNativeApiTokens(targetId).single())
            assertEquals(created.entryId, received.summary.entryId)
            assertTrue(received.summary.isFavorite)
            assertTrue(target.listNativeApiTokens(targetId).single().isFavorite)
            assertEquals(ApiTokenPayload.decode(payload), ApiTokenPayload.decode(received.payload))
            assertEquals(ApiTokenMetadata.decode(metadata), ApiTokenMetadata.decode(checkNotNull(received.extras).payload))
            assertEquals(0, sourceSync.synchronize(sourceId, path, transport).uploadedSegments)
            assertEquals(0, source.getPendingSyncCount(sourceId))
        } finally {
            for (id in ids) { state.delete(id); dao.deleteDatabaseById(id) }
            source.deleteOwnedVaultFile(sourceFile)
            target.deleteOwnedVaultFile(targetFile)
            sourceContext.clearTestPreferences()
            targetContext.clearTestPreferences()
            check(root.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            root.deleteRecursively()
        }
    }

    /** Give each synthetic replica its own device identity without touching the user's settings. */
    private class DeviceContext(base: Context) : ContextWrapper(base) {
        private val suffix = "-token-sync-${UUID.randomUUID()}"
        private val preferences = mutableSetOf<String>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolated = name + suffix
            preferences.add(isolated)
            return super.getSharedPreferences(isolated, mode)
        }
        fun clearTestPreferences() { preferences.forEach { baseContext.deleteSharedPreferences(it) } }
    }

    /** Exercise the real Rust protocol with an isolated file transport and a failed publication. */
    private class DirectoryTransport(private val root: File) : MdbxRemoteTransport {
        var failNextSegment = false
        private fun file(path: String): File = File(root, MdbxRemoteSyncPaths.normalizePath(path))
        private fun info(file: File) = MdbxRemoteObject(file.relativeTo(root).invariantSeparatorsPath,
            file.isDirectory, file.length(), file.lastModified().toString())
        override suspend fun testConnection() = Unit
        override suspend fun stat(path: String) = file(path).takeIf { it.exists() }?.let(::info)
        override suspend fun list(path: String?) = (path?.let(::file) ?: root)
            .listFiles().orEmpty().map(::info).sortedBy { it.path }
        override suspend fun ensureDirectory(path: String) { check(file(path).let { it.isDirectory || it.mkdirs() }) }
        override suspend fun readTo(path: String, destination: File) {
            destination.parentFile?.mkdirs()
            file(path).copyTo(destination, overwrite = true)
        }
        override suspend fun writeFrom(path: String, source: File, mode: MdbxRemoteWriteMode,
            expectedVersion: String?): MdbxRemoteObject {
            if (failNextSegment && path.endsWith(".mdbxsync")) {
                failNextSegment = false
                throw IOException("Synthetic upload interruption")
            }
            val destination = file(path)
            if (mode == MdbxRemoteWriteMode.CREATE_ONLY && destination.exists()) {
                check(source.readBytes().contentEquals(destination.readBytes()))
                return info(destination)
            }
            destination.parentFile?.mkdirs()
            source.copyTo(destination, overwrite = true)
            return info(destination)
        }
    }
}
