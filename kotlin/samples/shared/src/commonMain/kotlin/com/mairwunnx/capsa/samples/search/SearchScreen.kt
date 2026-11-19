package com.mairwunnx.capsa.samples.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mairwunnx.capsa.Load
import com.mairwunnx.capsa.samples.SampleScaffold
import com.mairwunnx.capsa.samples.backend.Backend
import com.mairwunnx.capsa.viewmodel.CapsaViewModel
import kotlinx.coroutines.flow.StateFlow

class SearchViewModel(ports: SearchPorts) : CapsaViewModel() {
    val state: StateFlow<SearchSlice> = install(search(ports))
}

@Composable
fun SearchScreen(onBack: () -> Unit) {
    val viewModel = viewModel { SearchViewModel(SearchPorts(Backend.fruits::search)) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SampleScaffold("Search", onBack) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.send(QueryChanged(it)) },
            label = { Text("A fruit") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        when (val results = state.results) {
            Load.Loading -> CircularProgressIndicator()
            Load.Failed -> Text("The search failed. Type again to retry.", color = MaterialTheme.colorScheme.error)
            is Load.Loaded -> LazyColumn(Modifier.fillMaxWidth()) {
                items(results.value) { Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}
