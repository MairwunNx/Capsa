package com.mairwunnx.capsa.samples.notes

import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Load
import com.mairwunnx.capsa.compose.CollectEffects
import com.mairwunnx.capsa.effectHandlers
import com.mairwunnx.capsa.samples.SampleScaffold
import com.mairwunnx.capsa.samples.backend.Backend
import com.mairwunnx.capsa.viewmodel.CapsaViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NoteViewModel(id: Int) : CapsaViewModel() {
    val state: StateFlow<NoteSlice> = install(
        noteDetails(NotePorts(note = { Backend.notes.note(id) }, setStarred = { Backend.notes.setStarred(id, it) })),
    )
}

@Composable
fun NoteScreen(id: Int, onBack: () -> Unit) {
    val viewModel = viewModel { NoteViewModel(id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    CollectEffects(viewModel.effects, effectHandlers { on<ShowStarError> { scope.launch { snackbar.showSnackbar("Couldn’t save the star") } } })
    SampleScaffold("Note", onBack, snackbar) {
        NoteContent(state, onIntent = viewModel::send)
    }
}

@Composable
private fun NoteContent(state: NoteSlice, onIntent: (Intent) -> Unit) {
    when (val note = state.note) {
        Load.Loading -> CircularProgressIndicator()
        Load.Failed -> Text("Couldn’t load the note", color = MaterialTheme.colorScheme.error)
        is Load.Loaded -> {
            Text(note.value.title, style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { onIntent(StarClicked) }, enabled = !state.saving) {
                Text(if (note.value.starred) "Remove the star" else "Star it")
            }
            Text("Go back: the list shows the star too.", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
