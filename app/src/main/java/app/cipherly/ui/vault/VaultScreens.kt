package app.cipherly.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import app.cipherly.R

@Composable
private fun VaultScaffold(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { content() }
    }
}

@Composable
private fun PasswordField(value: String, label: String, onChange: (String) -> Unit) {
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
private fun errorText(state: VaultUiState): String? = when (state.error) {
    null -> null
    VaultError.TOO_SHORT -> stringResource(R.string.error_too_short)
    VaultError.MISMATCH -> stringResource(R.string.error_mismatch)
    VaultError.WRONG_PASSWORD -> stringResource(R.string.error_wrong_password)
    VaultError.THROTTLED -> stringResource(R.string.error_throttled, state.retrySeconds)
    VaultError.CORRUPTED -> stringResource(R.string.error_corrupted)
    VaultError.SAVE_FAILED -> stringResource(R.string.error_save_failed)
}

@Composable
fun OnboardingScreen(state: VaultUiState, onCreate: (CharArray, CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    VaultScaffold {
        Text(
            stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.size(10.dp))
        Text(
            stringResource(R.string.onboarding_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
        )
        Spacer(Modifier.size(24.dp))
        PasswordField(password, stringResource(R.string.field_master_password)) { password = it }
        Spacer(Modifier.size(12.dp))
        PasswordField(confirm, stringResource(R.string.field_confirm_password)) { confirm = it }
        errorText(state)?.let {
            Spacer(Modifier.size(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.size(20.dp))
        Button(
            onClick = {
                onCreate(password.toCharArray(), confirm.toCharArray())
                password = ""
                confirm = ""
            },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (state.busy) R.string.working else R.string.action_create_vault)) }
    }
}

@Composable
fun LockScreen(state: VaultUiState, onUnlock: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    VaultScaffold {
        Text(
            stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.size(24.dp))
        PasswordField(password, stringResource(R.string.field_master_password)) { password = it }
        errorText(state)?.let {
            Spacer(Modifier.size(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.size(20.dp))
        Button(
            onClick = {
                onUnlock(password.toCharArray())
                password = ""
            },
            enabled = !state.busy && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (state.busy) R.string.working else R.string.action_unlock)) }
    }
}

@Composable
fun UnlockedScreen(onLock: () -> Unit) {
    VaultScaffold {
        Text(
            stringResource(R.string.unlocked_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.size(10.dp))
        Text(
            stringResource(R.string.unlocked_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
        )
        Spacer(Modifier.size(24.dp))
        TextButton(onClick = onLock) { Text(stringResource(R.string.action_lock)) }
    }
}
