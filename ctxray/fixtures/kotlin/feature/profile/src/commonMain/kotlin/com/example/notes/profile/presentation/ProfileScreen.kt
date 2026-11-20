package com.example.notes.profile.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val ui by viewModel.ui.collectAsState()
    Column {
        Text(ui.title)
        Text(ui.notes)
    }
}
