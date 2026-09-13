package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import takagi.ru.monica.R
import takagi.ru.monica.data.ApiTokenPayload
import takagi.ru.monica.data.ApiTokenMetadata
import takagi.ru.monica.ui.components.CustomFieldDisplayCard
import takagi.ru.monica.ui.components.InfoFieldWithCopy
import takagi.ru.monica.ui.components.PasswordField
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.viewmodel.MdbxViewModel
import takagi.ru.monica.viewmodel.NativeApiTokenDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiTokenDetailScreen(
    mdbxViewModel: MdbxViewModel,
    databaseId: Long,
    entryId: String,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val model: NativeApiTokenDetailViewModel = viewModel(key = "api-token-detail:$databaseId:$entryId",
        factory = viewModelFactory { initializer { NativeApiTokenDetailViewModel(mdbxViewModel, databaseId, entryId) } })
    val state by model.state.collectAsStateWithLifecycle()
    val databases by mdbxViewModel.allDatabases.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var fieldVisibilityEpoch by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.refresh()
            if (event == Lifecycle.Event.ON_STOP) fieldVisibilityEpoch++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onNavigateBack() }
    val current = state.token
    val fields = remember(current) { current?.payload?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() } }
    val supported = remember(current) { current?.payload?.let(ApiTokenPayload::decode) != null &&
        (current?.extras?.payload?.let(ApiTokenMetadata::isValid) ?: true) }
    val provider = when (ApiTokenPayload.text(fields, "provider")) {
        "github" -> "GitHub"
        "gitlab" -> "GitLab"
        else -> ApiTokenPayload.text(fields, "provider")
    }
    Scaffold(
        topBar = { TopAppBar(
            title = { Text(current?.summary?.title ?: stringResource(R.string.entry_type_api_token),
                style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(MonicaIcons.Navigation.back, stringResource(R.string.back)) } },
            actions = { if (current != null) IconButton(onClick = { confirmDelete = true }, enabled = !state.deleting && !state.loading) {
                Icon(Icons.Default.Delete, stringResource(R.string.delete))
            } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
        ) },
        floatingActionButton = { if (current != null && supported && !state.deleting) {
            ExtendedFloatingActionButton(onClick = onEdit, modifier = Modifier.testTag("api_token_edit"),
                icon = { Icon(Icons.Default.Edit, stringResource(R.string.edit)) },
                text = { Text(stringResource(R.string.edit)) })
        } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (current == null && state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.failed) ApiTokenError(model::refresh)
            if (current != null) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.Key, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(current.summary.title, style = MaterialTheme.typography.titleLarge)
                            Text(listOf(provider, stringResource(R.string.entry_type_api_token)).filter(String::isNotBlank).joinToString(" · "),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                if (fields != null) ApiTokenSection(stringResource(R.string.api_token_credentials)) {
                    if (provider.isNotBlank()) InfoFieldWithCopy(stringResource(R.string.api_token_provider), provider, context = context)
                    ApiTokenPayload.text(fields, "api_base").takeIf(String::isNotBlank)?.let {
                        InfoFieldWithCopy(stringResource(R.string.api_token_api_base), it, context = context)
                    }
                    ApiTokenPayload.text(fields, "token").takeIf(String::isNotBlank)?.let {
                        ApiTokenSecretField(stringResource(R.string.entry_type_api_token), it)
                    }
                }
                ApiTokenMetadata.notes(current.extras?.payload ?: ApiTokenMetadata.empty(),
                    ApiTokenPayload.text(fields, "note")).takeIf(String::isNotBlank)?.let { note ->
                    ApiTokenSection(stringResource(R.string.api_token_note), Icons.Default.Notes) {
                        InfoFieldWithCopy(stringResource(R.string.api_token_note), note, context = context)
                    }
                }
                val customFields = remember(current) {
                    ApiTokenMetadata.customFields(current.extras?.payload ?: ApiTokenMetadata.empty())
                        .mapIndexed { index, field -> field.toCustomField(0, index).copy(id = field.id) }
                }
                key(fieldVisibilityEpoch, current.summary.entryId) {
                    CustomFieldDisplayCard(customFields)
                }
                ApiTokenSection(stringResource(R.string.api_token_storage), Icons.Default.Storage) {
                    InfoFieldWithCopy(stringResource(R.string.api_token_database),
                        databases.firstOrNull { it.id == databaseId }?.name.orEmpty(), context = context)
                    InfoFieldWithCopy(stringResource(R.string.api_token_collection),
                        current.summary.collectionTitle.ifBlank { stringResource(R.string.api_token_root_collection) }, context = context)
                }
                if (!supported) Text(stringResource(R.string.api_token_unknown_schema),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { advanced = !advanced }, modifier = Modifier.testTag("api_token_advanced")) {
                    Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.api_token_advanced))
                }
                if (advanced) ApiTokenSection(stringResource(R.string.api_token_advanced), Icons.Default.DataObject) {
                    InfoFieldWithCopy(stringResource(R.string.api_token_entry_id), current.summary.entryId, context = context)
                    InfoFieldWithCopy(stringResource(R.string.api_token_collection_id), current.summary.collectionId, context = context)
                    fields?.get("schema")?.let { InfoFieldWithCopy(stringResource(R.string.api_token_schema),
                        (it as? JsonPrimitive)?.content ?: it.toString(), context = context) }
                    fields?.filterKeys { it !in setOf("schema", "provider", "api_base", "note", "token") }?.forEach { (field, value) ->
                        key(field) { ApiTokenSecretField(field, (value as? JsonPrimitive)?.content ?: value.toString()) }
                    }
                    ApiTokenSecretField(stringResource(R.string.api_token_complete_payload), current.payload)
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete)) },
        text = { Text(stringResource(R.string.api_token_delete_confirmation)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; model.delete() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun ApiTokenSecretField(label: String, value: String) {
    var visible by remember(value) { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) visible = false }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    PasswordField(label, value, visible, { visible = !visible }, LocalContext.current, maskedValue = "••••••••")
}
