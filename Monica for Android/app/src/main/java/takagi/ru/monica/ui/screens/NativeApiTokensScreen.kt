package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import takagi.ru.monica.R
import takagi.ru.monica.data.MdbxEngineType
import takagi.ru.monica.ui.components.GroupedItemDefaults
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.viewmodel.CategoryFilter
import takagi.ru.monica.viewmodel.MdbxViewModel

/** Browser only. Details and edits use independent navigation destinations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeApiTokensScreen(
    viewModel: MdbxViewModel,
    initialDatabaseId: Long? = null,
    onNavigateBack: () -> Unit,
    onOpen: (Long, String) -> Unit,
    onCreate: (Long?) -> Unit,
    onManageDatabases: () -> Unit,
) {
    val allDatabases by viewModel.allDatabases.collectAsStateWithLifecycle()
    val databasesLoaded by viewModel.allDatabasesLoaded.collectAsStateWithLifecycle()
    val databases = remember(allDatabases) { allDatabases.filter { it.engineTypeEnum == MdbxEngineType.RUST_MDBX2 } }
    var databaseId by rememberSaveable { mutableStateOf(initialDatabaseId) }
    var folderId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(databasesLoaded, databases.map { it.id }, databaseId) {
        if (databasesLoaded && databases.none { it.id == databaseId }) {
            databaseId = (databases.firstOrNull { it.isDefault } ?: databases.firstOrNull())?.id
            folderId = null
        }
    }
    val filter = databaseId?.let { id -> folderId?.let { CategoryFilter.MdbxFolderFilter(id, it) } ?: CategoryFilter.MdbxDatabase(id) }
        ?: CategoryFilter.All
    val tokens = rememberNativeTokenList(viewModel, filter, query, onlyTokens = true, onToggle = {},
        onOpen = { db, entry -> if (db != null && entry != null) onOpen(db, entry) })
    Scaffold(modifier = Modifier.imePadding(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.entry_type_api_token)) },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(MonicaIcons.Navigation.back, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)) },
        floatingActionButton = { if (databases.isNotEmpty()) FloatingActionButton(onClick = { onCreate(databaseId) }) {
            Icon(Icons.Default.Add, stringResource(R.string.add))
        } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), state = rememberLazyListState(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(GroupedItemDefaults.Spacing)) {
            item("source") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (databasesLoaded) ApiTokenStorageSelector(viewModel, databases, databaseId,
                        folderId, editing = false, enabled = true,
                        onSelect = { id, folder -> databaseId = id; folderId = folder }, onManageDatabases = onManageDatabases)
                    takagi.ru.monica.ui.components.OutlinedTextField(query, { query = it },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), singleLine = true,
                        label = { Text(stringResource(R.string.search)) })
                }
            }
            nativeTokenRows(tokens)
        }
    }
}
