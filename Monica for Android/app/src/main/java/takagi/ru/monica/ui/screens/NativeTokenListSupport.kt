package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import takagi.ru.monica.R
import takagi.ru.monica.data.*
import takagi.ru.monica.ui.components.MonicaExpressiveFilterChip
import takagi.ru.monica.ui.components.GroupedItemDefaults
import takagi.ru.monica.ui.password.PasswordEntryCard
import takagi.ru.monica.viewmodel.CategoryFilter
import takagi.ru.monica.viewmodel.MdbxViewModel
import takagi.ru.monica.viewmodel.NativeApiTokenListState
import takagi.ru.monica.viewmodel.nativeApiTokenSource

internal class NativeTokenListUi(
    val entries: List<NativeApiTokenSummary>,
    val visible: Boolean,
    val onlyTokens: Boolean,
    val loading: Boolean,
    val failed: Boolean,
    val onToggle: () -> Unit,
    val onOpen: (Long?, String?) -> Unit,
    val onRetry: () -> Unit = {},
)

internal fun filterNativeApiTokens(
    entries: List<NativeApiTokenSummary>, filter: CategoryFilter, query: String,
    favoritesOnly: Boolean = false
): List<NativeApiTokenSummary> = entries.filter { token ->
    val inSource = when (filter) {
        CategoryFilter.All -> true
        CategoryFilter.Starred -> token.isFavorite
        is CategoryFilter.MdbxDatabase -> token.databaseId == filter.databaseId
        is CategoryFilter.MdbxFolderFilter -> token.databaseId == filter.databaseId &&
            (token.collectionId == filter.folderId || filter.folderId in token.ancestorCollectionIds)
        else -> false
    }
    inSource && (!favoritesOnly || token.isFavorite) &&
        (query.isBlank() || listOf(token.title, token.collectionTitle).any { it.contains(query.trim(), true) })
}

@Composable
internal fun rememberNativeTokenList(
    viewModel: MdbxViewModel?, filter: CategoryFilter, query: String,
    onlyTokens: Boolean, onToggle: () -> Unit, onOpen: (Long?, String?) -> Unit,
    includeTokens: Boolean = true, favoritesOnly: Boolean = false
): NativeTokenListUi {
    val databases = viewModel?.allDatabases?.collectAsStateWithLifecycle()?.value.orEmpty()
    val databasesLoaded = viewModel?.allDatabasesLoaded?.collectAsStateWithLifecycle()?.value ?: true
    val sources = remember(databases, filter) { databases.filter {
        it.engineTypeEnum == MdbxEngineType.RUST_MDBX2 && when (filter) {
            is CategoryFilter.MdbxDatabase -> it.id == filter.databaseId
            is CategoryFilter.MdbxFolderFilter -> it.id == filter.databaseId
            CategoryFilter.All, CategoryFilter.Starred -> true
            else -> false
        }
    }.map { it.nativeApiTokenSource() } }
    val store = viewModel?.nativeApiTokenList
    val snapshot = store?.state?.collectAsStateWithLifecycle()?.value ?: NativeApiTokenListState()
    val active = includeTokens || onlyTokens
    val latestSources by rememberUpdatedState(if (active) sources else emptyList())
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, store) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) store?.request(latestSources, refresh = true)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(store, sources, active) {
        if (active) store?.request(sources)
    }
    val entries = remember(snapshot.entries, sources, filter, query, active, favoritesOnly) {
        if (active) filterNativeApiTokens(sources.flatMap(snapshot::rowsFor), filter, query, favoritesOnly)
        else emptyList()
    }
    val loading = active && (!databasesLoaded || sources.any { it in snapshot.loading ||
        (it !in snapshot.entries && it !in snapshot.failed) })
    val failed = active && sources.any { it in snapshot.failed }
    val sourceVisible = filter == CategoryFilter.All || filter == CategoryFilter.Starred ||
        filter is CategoryFilter.MdbxDatabase || filter is CategoryFilter.MdbxFolderFilter
    return NativeTokenListUi(
        entries = entries,
        visible = sources.isNotEmpty() && sourceVisible,
        onlyTokens = onlyTokens && sourceVisible && sources.isNotEmpty(),
        loading = loading, failed = failed,
        onToggle = onToggle, onOpen = onOpen,
        onRetry = { store?.request(sources, refresh = true) },
    )
}

@Composable
internal fun NativeTokenFilterChip(state: NativeTokenListUi?) {
    if (state?.visible == true) MonicaExpressiveFilterChip(
        selected = state.onlyTokens, onClick = state.onToggle,
        label = stringResource(R.string.entry_type_api_token), leadingIcon = Icons.Default.Key
    )
}

internal fun LazyListScope.nativeTokenRows(state: NativeTokenListUi?) {
    if (state == null || !state.visible) return
    if (state.loading && state.entries.isEmpty()) item("native_token_loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    if (state.failed) item("native_token_error") {
        TextButton(onClick = state.onRetry) { Text(stringResource(R.string.api_token_reload)) }
    }
    itemsIndexed(state.entries, key = { _, token -> "native-token:${token.databaseId}:${token.entryId}" },
        contentType = { _, _ -> "password_entry_card" }) { index, token ->
        // Reuse the normal password card so native records share the same density,
        // typography, icon slot and interaction cost as every other password row.
        val typeLabel = stringResource(R.string.entry_type_api_token)
        val displayEntry = remember(token, typeLabel) {
            PasswordEntry(
                id = token.entryId.hashCode().toLong(), title = token.title,
                website = "", username = listOf(typeLabel, token.collectionTitle).filter(String::isNotBlank).joinToString(" · "),
                password = "", notes = "", appName = "", isFavorite = token.isFavorite
            )
        }
        Box(Modifier.testTag("native-token:${token.databaseId}:${token.entryId}")) {
        PasswordEntryCard(
            entry = displayEntry,
            onClick = { state.onOpen(token.databaseId, token.entryId) },
            onLongClick = { state.onOpen(token.databaseId, token.entryId) },
            shape = GroupedItemDefaults.shape(index, state.entries.size),
            enableSharedBounds = false,
            leadingIconOverride = { Icon(Icons.Default.Key, contentDescription = null) },
            passwordCardDisplayMode = takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_USERNAME,
            passwordCardDisplayFields = listOf(takagi.ru.monica.data.PasswordCardDisplayField.USERNAME)
        )
        }
    }
    if (state.onlyTokens && state.entries.isEmpty() && !state.loading && !state.failed) item("native_token_empty") {
        Text(stringResource(R.string.api_token_empty))
    }
}
