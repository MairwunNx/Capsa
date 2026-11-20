package com.mairwunnx.capsa.samples.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.compose.CollectEffects
import com.mairwunnx.capsa.effectHandlers
import com.mairwunnx.capsa.mapState
import com.mairwunnx.capsa.samples.SampleScaffold
import com.mairwunnx.capsa.samples.backend.Backend
import com.mairwunnx.capsa.viewmodel.CapsaViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProfileUi(val name: String, val email: String, val sending: Boolean, val error: String?)

fun ProfileSlice.toUi() = ProfileUi(
    name = name,
    email = email,
    sending = sending,
    error = when (error) {
        ProfileError.EmptyName -> "Type your name"
        ProfileError.BadEmail -> "An email has an @"
        ProfileError.SendFailed -> "Couldn’t save. Try again."
        null -> null
    },
)

class ProfileViewModel(ports: ProfilePorts, savedStateHandle: SavedStateHandle) : CapsaViewModel(savedStateHandle = savedStateHandle) {
    // The name and the email survive the death of the process: ProfileSaver keeps them in the SavedStateHandle.
    private val form = installSaved(profileForm(ports), ProfileSaver, key = "profile")
    val ui: StateFlow<ProfileUi> = form.mapState(capsaScope) { it.toUi() }
}

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val viewModel = viewModel { ProfileViewModel(ProfilePorts(Backend.profile::send), createSavedStateHandle()) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    CollectEffects(
        viewModel.effects,
        effectHandlers {
            on<ShowProfileSaved> { scope.launch { snackbar.showSnackbar("Profile saved") } }
        },
    )
    SampleScaffold("Profile form", onBack, snackbar) {
        ProfileContent(ui, onIntent = viewModel::send)
    }
}

@Composable
private fun ProfileContent(ui: ProfileUi, onIntent: (Intent) -> Unit) {
    OutlinedTextField(
        value = ui.name,
        onValueChange = { onIntent(NameChanged(it)) },
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = ui.email,
        onValueChange = { onIntent(EmailChanged(it)) },
        label = { Text("Email") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (ui.error != null) Text(ui.error, color = MaterialTheme.colorScheme.error)
    Button(onClick = { onIntent(SaveClicked) }, enabled = !ui.sending, modifier = Modifier.fillMaxWidth()) {
        Text(if (ui.sending) "Saving…" else "Save")
    }
    Text(
        saveHint,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}
