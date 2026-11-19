package com.mairwunnx.capsa.samples

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private class Sample(val route: Route, val title: String, val shows: String, val host: String)

private val samples = listOf(
    Sample(Route.Counter, "Counter", "The first capability: intents, a slice, a host, a test", "rememberCapsa with expose"),
    Sample(Route.Search, "Search", "Debounce, and a new query cancels the old request", "CapsaViewModel"),
    Sample(Route.Like, "Like", "Shows at once, retries with backoff, rolls back on failure", "CapsaComposable"),
    Sample(Route.Clock, "Clock", "A stream that stops while the app is in the background", "CapsaViewModel"),
    Sample(Route.Profile, "Profile form", "Input that survives the death of the process", "CapsaViewModel"),
    Sample(Route.Dashboard, "Dashboard", "Two capabilities in one UI model", "CapsaViewModel"),
    Sample(Route.CrashFree, "Crash-free", "A bug in decide: debug crashes, release goes on", "CapsaViewModel"),
    Sample(Route.Notes, "Two screens", "Details change a note, and the list sees it", "CapsaViewModel"),
)

/** The list of samples. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (Route) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Capsa samples") }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(samples, key = { it.title }) { sample ->
                ElevatedCard(onClick = { onOpen(sample.route) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(sample.title, style = MaterialTheme.typography.titleLarge)
                        Text(sample.shows, style = MaterialTheme.typography.bodyLarge)
                        Text(sample.host, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
