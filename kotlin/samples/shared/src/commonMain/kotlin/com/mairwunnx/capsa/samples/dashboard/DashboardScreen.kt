package com.mairwunnx.capsa.samples.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Load
import com.mairwunnx.capsa.combineState
import com.mairwunnx.capsa.samples.SampleScaffold
import com.mairwunnx.capsa.samples.backend.Backend
import com.mairwunnx.capsa.viewmodel.CapsaViewModel
import kotlinx.coroutines.flow.StateFlow

data class DashboardUi(val weather: String, val tasks: String, val loading: Boolean)

/** The projection: it joins the two slices outside the capabilities, so neither reads the other. */
fun dashboardUi(weather: WeatherSlice, tasks: TasksSlice) = DashboardUi(
    weather = weather.celsius.text { "$it °C" },
    tasks = tasks.open.text { if (it == 1) "1 open task" else "$it open tasks" },
    loading = weather.celsius == Load.Loading || tasks.open == Load.Loading,
)

private fun <T> Load<T>.text(loaded: (T) -> String) = when (this) {
    Load.Loading -> "…"
    Load.Failed -> "Couldn’t load"
    is Load.Loaded -> loaded(value)
}

class DashboardViewModel(weatherPorts: WeatherPorts, tasksPorts: TasksPorts) : CapsaViewModel() {
    private val weather = install(weather(weatherPorts))
    private val tasks = install(tasks(tasksPorts))
    val ui: StateFlow<DashboardUi> = combineState(capsaScope, weather, tasks) { weather, tasks -> dashboardUi(weather, tasks) }
}

@Composable
fun DashboardScreen(onBack: () -> Unit) {
    val viewModel = viewModel {
        DashboardViewModel(WeatherPorts(Backend.dashboard::celsius), TasksPorts(Backend.dashboard::openTasks))
    }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    SampleScaffold("Dashboard", onBack) {
        DashboardContent(ui, onIntent = viewModel::send)
    }
}

@Composable
private fun DashboardContent(ui: DashboardUi, onIntent: (Intent) -> Unit) {
    Tile("Weather", ui.weather)
    Tile("Tasks", ui.tasks)
    Button(onClick = { onIntent(RefreshClicked) }, enabled = !ui.loading) { Text("Refresh both") }
}

@Composable
private fun Tile(title: String, value: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}
