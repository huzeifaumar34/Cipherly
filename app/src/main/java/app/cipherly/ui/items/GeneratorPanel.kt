package app.cipherly.ui.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.cipherly.R
import app.cipherly.domain.model.GeneratorOptions
import app.cipherly.domain.model.PasswordGenerator

@Composable
private fun OptionRow(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun GeneratorPanel(onUse: (String) -> Unit) {
    val generator = remember { PasswordGenerator() }
    var options by remember { mutableStateOf(GeneratorOptions()) }
    var preview by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.generator_length, options.length),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = options.length.toFloat(),
            onValueChange = { options = options.copy(length = it.toInt()) },
            valueRange = GeneratorOptions.MIN_LENGTH.toFloat()..GeneratorOptions.MAX_LENGTH.toFloat(),
        )
        OptionRow(options.lower, stringResource(R.string.generator_lower)) { options = options.copy(lower = it) }
        OptionRow(options.upper, stringResource(R.string.generator_upper)) { options = options.copy(upper = it) }
        OptionRow(options.digits, stringResource(R.string.generator_digits)) { options = options.copy(digits = it) }
        OptionRow(options.symbols, stringResource(R.string.generator_symbols)) { options = options.copy(symbols = it) }
        OptionRow(options.avoidAmbiguous, stringResource(R.string.generator_avoid_ambiguous)) {
            options = options.copy(avoidAmbiguous = it)
        }
        Spacer(Modifier.size(8.dp))
        OutlinedButton(
            onClick = { preview = generator.generate(options) },
            enabled = options.isValid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_generate)) }
        if (preview.isNotEmpty()) {
            Spacer(Modifier.size(8.dp))
            Text(preview, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = {
                    onUse(preview)
                    preview = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_use_generated)) }
        }
    }
}
