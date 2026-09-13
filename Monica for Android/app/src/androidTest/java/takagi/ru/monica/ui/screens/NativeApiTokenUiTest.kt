package takagi.ru.monica.ui.screens

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.R
import takagi.ru.monica.data.*
import takagi.ru.monica.repository.Mdbx2Repository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.viewmodel.CategoryFilter
import takagi.ru.monica.viewmodel.MdbxViewModel
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NativeApiTokenUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun quickFilterAndNativeRowOpenTheCorrectObject() {
        val summary = NativeApiTokenSummary(91, "native-id", "folder-id", "CLI category", "gitlab-work")
        var opened: Pair<Long?, String?>? = null
        compose.setContent {
            var only by remember { mutableStateOf(false) }
            val state = NativeTokenListUi(listOf(summary), true, only, false, false,
                { only = !only }, { db, id -> opened = db to id })
            MonicaTheme { LazyColumn {
                item { NativeTokenFilterChip(state) }
                nativeTokenRows(state)
            } }
        }
        compose.onNodeWithText(context.getString(R.string.entry_type_api_token)).performClick().assertIsSelected()
        compose.onNodeWithText("gitlab-work").performClick()
        compose.runOnIdle { assertEquals(91L to "native-id", opened) }
    }

    @Test fun editAndReturnKeepNativeIdentityExtensionsAndTheVisibleList() = runBlocking {
        val fixture = Fixture()
        val visible = mutableStateOf(true)
        try {
            val databaseId = fixture.createDatabase("CLI integration demo")
            val folder = fixture.repository.createFolder(databaseId, "Development", null)
            val payload = """{"schema":"monica.gateway.credential.v1","provider":"gitlab","api_base":"https://gitlab.example.test/api/v4/","note":"Original CLI context","token":"synthetic-ui-token-123456","extension":{"purpose":"demo"}}"""
            val summary = fixture.repository.saveNativeApiToken(databaseId, null, "gitlab-work", payload, folder.folderId, isFavorite = true)
            val returnCounts = mutableListOf<Int>()
            var recordingReturn = false
            compose.setContent { if (visible.value) MonicaTheme(darkTheme = true) {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "list") {
                    composable("list") {
                        val list = rememberNativeTokenList(fixture.model, CategoryFilter.MdbxDatabase(databaseId), "",
                            onlyTokens = true, onToggle = {}, onOpen = { _, _ -> nav.navigate("detail") })
                        SideEffect { if (recordingReturn) returnCounts += list.entries.size }
                        LazyColumn { nativeTokenRows(list) }
                    }
                    composable("detail") {
                        ApiTokenDetailScreen(fixture.model, databaseId, summary.entryId,
                            onNavigateBack = { recordingReturn = true; nav.popBackStack() },
                            onEdit = { nav.navigate("edit") })
                    }
                    composable("edit") {
                        AddEditApiTokenScreen(fixture.model, databaseId, summary.entryId,
                            onNavigateBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() },
                            onSwitchType = { _, _, _ -> }, onManageDatabases = {})
                    }
                }
            } }
            awaitText("gitlab-work")
            compose.onNodeWithText("gitlab-work").performClick()
            awaitTag("api_token_edit")
            screenshot("native-api-token-detail.png")
            compose.onNodeWithTag("api_token_edit").performClick()
            awaitTag("api_token_note")
            compose.onNodeWithTag("api_token_favorite").assertIsDisplayed().assertIsOn().performClick().assertIsOff()
            compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
            compose.onNodeWithText(context.getString(R.string.api_token_discard_message)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
            compose.onNodeWithTag("api_token_note").performScrollTo().performClick()
                .performTextReplacement("Edited through Android")
            compose.onNodeWithTag("api_token_save").assertIsEnabled().assertIsDisplayed().performClick()
            awaitTag("api_token_edit")
            val updated = fixture.repository.readNativeApiToken(databaseId, summary.entryId)
            assertEquals(summary.entryId, updated.summary.entryId)
            assertEquals(folder.folderId, updated.summary.collectionId)
            assertFalse(updated.summary.isFavorite)
            val fields = ApiTokenPayload.decode(updated.payload)
            assertEquals("Edited through Android", ApiTokenPayload.text(fields, "note"))
            assertNotNull(fields?.get("extension"))
            assertTrue(fixture.room.passwordEntryDao().getByMdbxDatabaseIdSync(databaseId).isEmpty())
            compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
            awaitTag("native-token:" + databaseId + ":" + summary.entryId)
            compose.runOnIdle {
                assertTrue(returnCounts.isNotEmpty())
                assertTrue("Returning must not replace the token row with an empty list", returnCounts.all { it == 1 })
            }
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            fixture.close()
        }
    }

    @Test fun createChoosesTheDatabaseAndCategoryWithoutSavingSecretDrafts() = runBlocking {
        val fixture = Fixture()
        val visible = mutableStateOf(true)
        try {
            val firstId = fixture.createDatabase("CLI Personal")
            val secondId = fixture.createDatabase("CLI Work")
            val folder = fixture.repository.createFolder(secondId, "Automation", null)
            val saved = AtomicReference<NativeApiTokenSummary?>()
            val registry = SaveableStateRegistry(restoredValues = null, canBeSaved = { true })
            val secret = "synthetic-new-ui-token-123456"
            compose.setContent { if (visible.value) MonicaTheme(darkTheme = true) {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    AddEditApiTokenScreen(fixture.model, initialDatabaseId = firstId,
                        onNavigateBack = {}, onSaved = saved::set,
                        onSwitchType = { _, _, _ -> }, onManageDatabases = {})
                }
            } }
            awaitText("CLI Personal", substring = true)
            compose.onNodeWithTag("api_token_save").assertIsNotEnabled()
            compose.onNodeWithTag("api_token_favorite").assertIsDisplayed().assertIsOff().performClick().assertIsOn()
            compose.onNodeWithText("CLI Personal", substring = true).performClick()
            awaitText("CLI Work")
            compose.onNodeWithText("CLI Work").performClick()
            awaitText("Automation")
            compose.onNodeWithText("Automation").performClick()
            compose.onNodeWithText(context.getString(R.string.confirm)).performClick()
            compose.onNodeWithText("CLI Work", substring = true).assertIsDisplayed()
            compose.onNodeWithTag("api_token_name").performTextReplacement("github-ci")
            compose.onNodeWithTag("api_token_provider").performClick()
            compose.onNodeWithText("GitHub").performClick()
            compose.onNodeWithTag("api_token_api_base").assertTextContains("https://api.github.com/")
            compose.onNodeWithTag("api_token_secret").performScrollTo().performClick().performTextReplacement(secret)
            compose.onNodeWithTag("api_token_save").assertIsEnabled().assertIsDisplayed()
            compose.onNodeWithTag("api_token_favorite").assertIsOn()
            compose.runOnIdle {
                assertFalse("Secret draft must not enter an Android saved-state Bundle",
                    registry.performSave().toString().contains(secret))
            }
            screenshot("native-api-token-editor.png")
            compose.onNodeWithTag("api_token_save").performClick()
            compose.waitUntil(30_000) { saved.get() != null }
            val created = saved.get()!!
            assertEquals(secondId, created.databaseId)
            assertEquals(folder.folderId, created.collectionId)
            assertTrue(created.isFavorite)
            assertTrue(fixture.repository.listNativeApiTokens(firstId).isEmpty())
            val stored = fixture.repository.readNativeApiToken(secondId, created.entryId)
            assertTrue(stored.summary.isFavorite)
            assertTrue(fixture.repository.listNativeApiTokens(secondId).single().isFavorite)
            assertEquals("github", ApiTokenPayload.text(ApiTokenPayload.decode(stored.payload), "provider"))
            assertEquals(secret, ApiTokenPayload.text(ApiTokenPayload.decode(stored.payload), "token"))
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            fixture.close()
        }
    }

    private fun awaitText(text: String, substring: Boolean = false) = compose.waitUntil(30_000) {
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitTag(tag: String) = compose.waitUntil(30_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun screenshot(name: String) {
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    private inner class Fixture {
        val room = PasswordDatabase.getDatabase(context)
        private val dao = room.localMdbxDatabaseDao()
        private val security = SecurityManager(context)
        val repository = Mdbx2Repository(context, dao, security)
        val model = MdbxViewModel(context.applicationContext as Application, dao, room.mdbxRemoteSourceDao(),
            room.passwordEntryDao(), room.secureItemDao(), room.passkeyDao(), room.attachmentDao(), room.customFieldDao(), security)
        private val owned = mutableListOf<Pair<Long, File>>()

        suspend fun createDatabase(name: String): Long {
            val password = "Synthetic UI test vault password 123"
            val file = repository.createInitializedVaultFile(MdbxTigaMode.SKY, password)
            val id = dao.insertDatabase(LocalMdbxDatabase(name = name, filePath = file.absolutePath,
                engineType = MdbxEngineType.RUST_MDBX2.name, encryptedPassword = security.encryptData(password),
                unlockMethod = MdbxUnlockMethod.MASTER_PASSWORD.storedValue))
            owned += id to file
            return id
        }

        suspend fun close() {
            model.viewModelScope.cancel()
            owned.forEach { (id, file) -> dao.deleteDatabaseById(id); repository.deleteOwnedVaultFile(file) }
        }
    }
}
