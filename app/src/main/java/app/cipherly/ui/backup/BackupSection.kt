package app.cipherly.ui.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.cipherly.R

@Composable
private fun BackupPasswordField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun messageText(message: BackupMessage): String = when (message) {
    is BackupMessage.ExportOk -> stringResource(R.string.backup_export_ok, message.count)
    is BackupMessage.ImportOk ->
        stringResource(R.string.backup_import_ok, message.added, message.updated, message.skipped)
    BackupMessage.TooShort -> stringResource(R.string.error_too_short)
    BackupMessage.Mismatch -> stringResource(R.string.error_mismatch)
    BackupMessage.WrongPassword -> stringResource(R.string.backup_error_wrong_password)
    BackupMessage.BadFile -> stringResource(R.string.backup_error_bad_file)
    BackupMessage.TooLarge -> stringResource(R.string.backup_error_too_large)
    BackupMessage.Failed -> stringResource(R.string.backup_error_failed)
}

@Composable
fun BackupSection(
    state: BackupUiState,
    onExport: (CharArray, CharArray) -> Unit,
    onImport: () -> Unit,
    onImportPassword: (CharArray) -> Unit,
    onCancelImport: () -> Unit,
) {
    var showExport by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }

    Column {
        Text(
            stringResource(R.string.backup_title),
            style = MaterialTheme.typography.titleMedium,
        )
        state.message?.let {
            Spacer(Modifier.size(8.dp))
            val isError = it !is BackupMessage.ExportOk && it !is BackupMessage.ImportOk
            Text(
                messageText(it),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        TextButton(onClick = { showExport = true }, enabled = !state.busy) {
            Text(stringResource(if (state.busy) R.string.working else R.string.action_export_backup))
        }
        TextButton(onClick = onImport, enabled = !state.busy) {
            Text(stringResource(R.string.action_import_backup))
        }
    }

    if (showExport) {
        AlertDialog(
            onDismissRequest = {
                showExport = false
                password = ""
                confirm = ""
            },
            title = { Text(stringResource(R.string.backup_export_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.backup_export_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.size(8.dp))
                    BackupPasswordField(password, stringResource(R.string.field_backup_password)) { password = it }
                    Spacer(Modifier.size(8.dp))
                    BackupPasswordField(confirm, stringResource(R.string.field_confirm_backup_password)) { confirm = it }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onExport(password.toCharArray(), confirm.toCharArray())
                        showExport = false
                        password = ""
                        confirm = ""
                    },
                    enabled = password.isNotEmpty(),
                ) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExport = false
                    password = ""
                    confirm = ""
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (state.askImportPassword) {
        AlertDialog(
            onDismissRequest = {
                importPassword = ""
                onCancelImport()
            },
            title = { Text(stringResource(R.string.backup_import_title)) },
            text = {
                BackupPasswordField(importPassword, stringResource(R.string.field_backup_password)) {
                    importPassword = it
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onImportPassword(importPassword.toCharArray())
                        importPassword = ""
                    },
                    enabled = importPassword.isNotEmpty(),
                ) { Text(stringResource(R.string.action_import_backup)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    importPassword = ""
                    onCancelImport()
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
