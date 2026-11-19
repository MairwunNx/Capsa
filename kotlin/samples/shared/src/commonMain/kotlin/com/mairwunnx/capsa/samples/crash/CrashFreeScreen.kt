package com.mairwunnx.capsa.samples.crash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mairwunnx.capsa.Capsa
import com.mairwunnx.capsa.CapsaLogger
import com.mairwunnx.capsa.samples.SampleScaffold
import com.mairwunnx.capsa.viewmodel.CapsaViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Writes where [CapsaLogger.Default] writes and keeps the last error, so the screen shows what Capsa survived. */
class ScreenLogger : CapsaLogger {
    private val last = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = last

    override fun log(level: CapsaLogger.Level, error: Throwable?, message: () -> String) {
        CapsaLogger.Default.log(level, error, message)
        if (level == CapsaLogger.Level.Error) last.value = error?.message ?: message()
    }
}

class CrashFreeViewModel(val log: ScreenLogger = ScreenLogger()) : CapsaViewModel(logger = log) {
    val state: StateFlow<TapsSlice> = install(taps())
}

@Composable
fun CrashFreeScreen(onBack: () -> Unit) {
    val viewModel = viewModel { CrashFreeViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastError by viewModel.log.lastError.collectAsStateWithLifecycle()
    SampleScaffold("Crash-free", onBack) {
        Text(
            if (Capsa.failFast) "A debug build: a bug crashes the app." else "A release build: the screen survives a bug.",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text("${state.taps} taps", style = MaterialTheme.typography.displayMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { viewModel.send(TapClicked) }) { Text("Tap") }
            Button(
                onClick = { viewModel.send(BugClicked) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Throw in decide") }
        }
        lastError?.let { error ->
            // The violation carries the last facts of the runtime, the same text a crash report gets.
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            )
        }
    }
}
