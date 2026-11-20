package com.example.notes.profile.presentation

import androidx.lifecycle.viewModelScope
import com.example.notes.profile.business.capabilities.ProfilePorts
import com.example.notes.profile.business.capabilities.ProfileSlice
import com.example.notes.profile.business.capabilities.profile
import com.mairwunnx.capsa.viewmodel.CapsaViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ProfileUi(val title: String, val notes: String)

class ProfileViewModel(ports: ProfilePorts) : CapsaViewModel() {
    private val profile = install(profile(ports))

    val ui: StateFlow<ProfileUi> = profile
        .map { ProfileUi(title = if (it.name.isBlank()) "Anonymous" else it.name, notes = it.notes.valueOrNull()?.toString() ?: "…") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProfileUi("", ""))
}
