package com.mairwunnx.capsa.samples.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Load
import com.mairwunnx.capsa.compose.CollectEffects
import com.mairwunnx.capsa.effectHandlers
import com.mairwunnx.capsa.samples.SampleScaffold
import com.mairwunnx.capsa.samples.backend.Backend
import com.mairwunnx.capsa.samples.backend.Note
import com.mairwunnx.capsa.viewmodel.CapsaViewModel
import kotlinx.coroutines.flow.StateFlow

class NotesViewModel(ports: NotesPorts) : CapsaViewModel() {
    val state: StateFlow<NotesSlice> = install(notesList(ports))
}

@Composable
fun NotesScreen(onBack: () -> Unit, onOpenNote: (id: Int) -> Unit) {
    val viewModel = viewModel { NotesViewModel(NotesPorts(Backend.notes::all)) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Navigation is an effect: the capability asks, the screen opens.
    CollectEffects(viewModel.effects, effectHandlers { on<OpenNote> { onOpenNote(it.id) } })
    SampleScaffold("Notes", onBack) {
        NotesContent(state.notes, onIntent = viewModel::send)
    }
}

@Composable
private fun NotesContent(notes: Load<List<Note>>, onIntent: (Intent) -> Unit) {
    when (notes) {
        Load.Loading -> CircularProgressIndicator()
        Load.Failed -> Text("Couldn’t load the notes", color = MaterialTheme.colorScheme.error)
        is Load.Loaded -> LazyColumn(Modifier.fillMaxWidth()) {
            items(notes.value, key = { it.id }) { note ->
                ListItem(
                    headlineContent = { Text(note.title, style = MaterialTheme.typography.titleLarge) },
                    trailingContent = { if (note.starred) Icon(Icons.Filled.Star, contentDescription = "Starred") },
                    modifier = Modifier.clickable { onIntent(NoteClicked(note.id)) },
                )
            }
        }
    }
}
