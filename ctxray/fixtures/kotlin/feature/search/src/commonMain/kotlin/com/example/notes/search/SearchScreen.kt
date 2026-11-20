package com.example.notes.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column {
        TextField(value = state.query, onValueChange = viewModel::onQueryChanged)
        when {
            state.loading -> CircularProgressIndicator()
            state.error -> Text("Something went wrong")
            else -> LazyColumn { items(state.notes) { Text(it.title) } }
        }
    }
}
