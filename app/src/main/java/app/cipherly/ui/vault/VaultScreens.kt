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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.cipherly.domain.model.SecurityDefaults

@Composable
private fun VaultScaffold(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
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
    VaultError.PIN_INVALID -> stringResource(R.string.error_pin_invalid)
    VaultError.PIN_WRONG -> stringResource(R.string.error_pin_wrong, state.pinRemaining)
    VaultError.PIN_DISABLED -> stringResource(R.string.error_pin_disabled)
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
private fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(SecurityDefaults.PIN_LENGTH)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun LockScreen(
    state: VaultUiState,
    pinEnabled: Boolean,
    biometricEnabled: Boolean,
    onUnlock: (CharArray) -> Unit,
    onUnlockPin: (CharArray) -> Unit,
    onBiometric: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var usePin by remember { mutableStateOf(pinEnabled) }
    LaunchedEffect(biometricEnabled) { if (biometricEnabled) onBiometric() }
    VaultScaffold {
        Text(
            stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.size(24.dp))
        if (usePin && pinEnabled) {
            PinField(pin, stringResource(R.string.field_pin)) { pin = it }
        } else {
            PasswordField(password, stringResource(R.string.field_master_password)) { password = it }
        }
        errorText(state)?.let {
            Spacer(Modifier.size(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.size(20.dp))
        if (usePin && pinEnabled) {
            Button(
                onClick = {
                    onUnlockPin(pin.toCharArray())
                    pin = ""
                },
                enabled = !state.busy && pin.length == SecurityDefaults.PIN_LENGTH,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (state.busy) R.string.working else R.string.action_unlock)) }
            TextButton(onClick = { usePin = false }) { Text(stringResource(R.string.action_use_password)) }
        } else {
            Button(
                onClick = {
                    onUnlock(password.toCharArray())
                    password = ""
                },
                enabled = !state.busy && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (state.busy) R.string.working else R.string.action_unlock)) }
            if (pinEnabled) {
                TextButton(onClick = { usePin = true }) { Text(stringResource(R.string.action_use_pin)) }
            }
        }
        if (biometricEnabled) {
            TextButton(onClick = onBiometric) { Text(stringResource(R.string.action_use_biometric)) }
        }
    }
}

@Composable
private fun durationLabel(seconds: Int): String =
    if (seconds < 60) stringResource(R.string.duration_seconds, seconds)
    else stringResource(R.string.duration_minutes, seconds / 60)

@Composable
fun UnlockedScreen(
    state: VaultUiState,
    pinEnabled: Boolean,
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    autoLockSeconds: Int,
    clipboardSeconds: Int,
    onCycleAutoLock: () -> Unit,
    onCycleClipboard: () -> Unit,
    onLock: () -> Unit,
    onBack: () -> Unit,
    extra: @Composable () -> Unit,
    onEnablePin: (CharArray, CharArray) -> Unit,
    onDisablePin: () -> Unit,
    onEnableBiometric: () -> Unit,
    onDisableBiometric: () -> Unit,
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
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
        Text(
            stringResource(R.string.security_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        errorText(state)?.let {
            Spacer(Modifier.size(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (pinEnabled) {
            TextButton(onClick = onDisablePin) { Text(stringResource(R.string.action_disable_pin)) }
        } else {
            TextButton(onClick = { showPinDialog = true }) { Text(stringResource(R.string.action_enable_pin)) }
        }
        if (biometricEnabled) {
            TextButton(onClick = onDisableBiometric) { Text(stringResource(R.string.action_disable_biometric)) }
        } else if (biometricAvailable) {
            TextButton(onClick = onEnableBiometric) { Text(stringResource(R.string.action_enable_biometric)) }
        }
        TextButton(onClick = onCycleAutoLock) {
            Text(stringResource(R.string.setting_auto_lock, durationLabel(autoLockSeconds)))
        }
        TextButton(onClick = onCycleClipboard) {
            Text(stringResource(R.string.setting_clipboard, durationLabel(clipboardSeconds)))
        }
        Spacer(Modifier.size(12.dp))
        extra()
        Spacer(Modifier.size(16.dp))
        Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        TextButton(onClick = onLock) { Text(stringResource(R.string.action_lock)) }
    }
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pin = ""
                confirm = ""
            },
            title = { Text(stringResource(R.string.pin_dialog_title)) },
            text = {
                Column {
                    PinField(pin, stringResource(R.string.field_pin)) { pin = it }
                    Spacer(Modifier.size(8.dp))
                    PinField(confirm, stringResource(R.string.field_confirm_pin)) { confirm = it }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEnablePin(pin.toCharArray(), confirm.toCharArray())
                        showPinDialog = false
                        pin = ""
                        confirm = ""
                    },
                    enabled = !state.busy && pin.length == SecurityDefaults.PIN_LENGTH,
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog = false
                    pin = ""
                    confirm = ""
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
