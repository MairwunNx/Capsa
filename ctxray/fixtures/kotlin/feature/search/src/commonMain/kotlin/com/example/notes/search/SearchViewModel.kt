package com.example.notes.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val notes: List<Note> = emptyList(),
    val error: Boolean = false,
)

class SearchViewModel(private val notes: NoteRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChanged(query: String) {
        _state.update { it.copy(query = query) }
        job?.cancel()
        job = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(loading = true, error = false) }
            try {
                val found = notes.search(query)
                _state.update { it.copy(loading = false, notes = found) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = true) }
            }
        }
    }
}
