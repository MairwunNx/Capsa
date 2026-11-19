package com.mairwunnx.capsa.samples.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.compose.rememberCapsa
import com.mairwunnx.capsa.samples.SampleScaffold

@Composable
fun CounterScreen(onBack: () -> Unit) {
    // No class and no view model: the lambda installs the capability and exposes its slice.
    val screen = rememberCapsa { expose(install(counter())) }
    val state by screen.state.collectAsStateWithLifecycle()
    SampleScaffold("Counter", onBack) {
        CounterContent(state, onIntent = screen::send)
    }
}

@Composable
private fun CounterContent(state: CounterSlice, onIntent: (Intent) -> Unit) {
    Text("${state.count}", style = MaterialTheme.typography.displayLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = { onIntent(MinusClicked) }) { Text("−1") }
        Button(onClick = { onIntent(PlusClicked) }) { Text("+1") }
    }
    OutlinedButton(onClick = { onIntent(ResetClicked) }) { Text("Reset") }
}
