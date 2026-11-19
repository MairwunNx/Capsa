package com.mairwunnx.capsa.samples.clock

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mairwunnx.capsa.mapState
import com.mairwunnx.capsa.samples.SampleScaffold
import com.mairwunnx.capsa.viewmodel.CapsaViewModel
import kotlinx.coroutines.flow.StateFlow

class ClockViewModel : CapsaViewModel() {
    private val clock = install(clock())
    val time: StateFlow<String> = clock.mapState(capsaScope) { it.seconds.asMinutes() }
}

private fun Int.asMinutes() = "${(this / 60).toString().padStart(2, '0')}:${(this % 60).toString().padStart(2, '0')}"

@Composable
fun ClockScreen(onBack: () -> Unit) {
    val viewModel = viewModel { ClockViewModel() }
    // collectAsStateWithLifecycle stops in the background, so the runtime sees a hidden screen and stops the ticks.
    val time by viewModel.time.collectAsStateWithLifecycle()
    SampleScaffold("Clock", onBack) {
        Text("You’ve looked at this screen for", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(time, style = MaterialTheme.typography.displayLarge)
        Text(
            "Put the app in the background for a while. The clock stops there and goes on when you come back.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
