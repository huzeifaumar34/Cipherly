package app.cipherly.ui.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.cipherly.R
import app.cipherly.domain.model.ItemType
import app.cipherly.domain.model.VaultItem

@Composable
private fun typeLabel(type: ItemType): String = stringResource(
    when (type) {
        ItemType.PASSWORD -> R.string.type_password
        ItemType.API_KEY -> R.string.type_api_key
        ItemType.NOTE -> R.string.type_note
    },
)

@Composable
fun ItemListScreen(
    items: List<VaultItem>,
    query: String,
    onQuery: (String) -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onSecurity: () -> Unit,
    onLock: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.action_add_item))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.items_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSecurity) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.security_title))
                }
                IconButton(onClick = onLock) {
                    Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.action_lock))
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                label = { Text(stringResource(R.string.search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(12.dp))
            if (items.isEmpty()) {
                Text(
                    stringResource(if (query.isBlank()) R.string.items_empty else R.string.items_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(items, key = { it.id }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(item.id) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOf(typeLabel(item.type), item.username).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ItemEditScreen(
    initial: VaultItem?,
    error: ItemError?,
    onSave: (ItemType, String, String, String, String, String) -> Unit,
    onDelete: () -> Unit,
    onCopy: (String) -> Unit,
    onBack: () -> Unit,
) {
    var showGenerator by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(initial?.type ?: ItemType.PASSWORD) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var secret by remember { mutableStateOf(initial?.secret ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var reveal by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            stringResource(if (initial == null) R.string.item_new else R.string.item_edit),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ItemType.entries.forEach { option ->
                if (option == type) {
                    Button(onClick = { type = option }) { Text(typeLabel(option)) }
                } else {
                    OutlinedButton(onClick = { type = option }) { Text(typeLabel(option)) }
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.field_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (type == ItemType.PASSWORD) {
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.field_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.size(8.dp))
        val hidden = type != ItemType.NOTE && !reveal
        OutlinedTextField(
            value = secret,
            onValueChange = { secret = it },
            label = {
                Text(
                    stringResource(
                        when (type) {
                            ItemType.PASSWORD -> R.string.field_password
                            ItemType.API_KEY -> R.string.field_api_key
                            ItemType.NOTE -> R.string.field_note
                        },
                    ),
                )
            },
            singleLine = type != ItemType.NOTE,
            minLines = if (type == ItemType.NOTE) 5 else 1,
            visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (hidden) KeyboardType.Password else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            if (type != ItemType.NOTE) {
                TextButton(onClick = { reveal = !reveal }) {
                    Text(stringResource(if (reveal) R.string.action_hide else R.string.action_show))
                }
            }
            TextButton(onClick = { onCopy(secret) }, enabled = secret.isNotEmpty()) {
                Text(stringResource(R.string.action_copy))
            }
            if (type == ItemType.PASSWORD && username.isNotEmpty()) {
                TextButton(onClick = { onCopy(username) }) { Text(stringResource(R.string.action_copy_username)) }
            }
            if (type == ItemType.PASSWORD) {
                TextButton(onClick = { showGenerator = !showGenerator }) {
                    Text(stringResource(R.string.action_generator))
                }
            }
        }
        if (showGenerator && type == ItemType.PASSWORD) {
            GeneratorPanel(onUse = {
                secret = it
                reveal = true
                showGenerator = false
            })
        }
        if (type != ItemType.NOTE) {
            if (type == ItemType.PASSWORD) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.field_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.field_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let {
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(
                    when (it) {
                        ItemError.TITLE_REQUIRED -> R.string.error_title_required
                        ItemError.TOO_LONG -> R.string.error_too_long
                        ItemError.SAVE_FAILED -> R.string.error_save_failed
                    },
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.size(20.dp))
        Button(
            onClick = { onSave(type, title, username, secret, url, notes) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_save)) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_cancel)) }
        if (initial != null) {
            if (confirmDelete) {
                Button(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_confirm_delete))
                }
            } else {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
