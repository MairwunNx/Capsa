package com.mairwunnx.capsa.samples.profile

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.WorkPolicy
import com.mairwunnx.capsa.capability

data class ProfileSlice(
    val name: String = "",
    val email: String = "",
    val sending: Boolean = false,
    val error: ProfileError? = null,
)

enum class ProfileError { EmptyName, BadEmail, SendFailed }

sealed interface ProfileIntent : Intent
data class NameChanged(val text: String) : ProfileIntent
data class EmailChanged(val text: String) : ProfileIntent
data object SaveClicked : ProfileIntent

sealed interface ProfileSignal : Signal
data object ProfileSent : ProfileSignal
data object ProfileFailed : ProfileSignal

data object ShowProfileSaved : Effect

class ProfilePorts(val send: suspend (name: String, email: String) -> Unit)

private val SEND = WorkKey("send")

context(_: CapabilityScope)
fun profileForm(ports: ProfilePorts): Capability<ProfileSlice> =
    capability<ProfileSlice, ProfileIntent, ProfileSignal>(name = "profile", initial = ProfileSlice()) {
        val works = object {
            // KeepExisting ignores a second tap while the request runs.
            fun send(name: String, email: String) = work(key = SEND, onError = { ProfileFailed }, policy = WorkPolicy.KeepExisting) {
                ports.send(name, email)
                ProfileSent
            }
        }

        onIntent<NameChanged> { state, changed -> Decision(state = state.copy(name = changed.text, error = null)) }
        onIntent<EmailChanged> { state, changed -> Decision(state = state.copy(email = changed.text, error = null)) }
        onIntent<SaveClicked> { state, _ ->
            when {
                state.sending -> Decision(state = state)
                state.name.isBlank() -> Decision(state = state.copy(error = ProfileError.EmptyName))
                '@' !in state.email -> Decision(state = state.copy(error = ProfileError.BadEmail))
                else -> Decision(state = state.copy(sending = true), work = works.send(state.name, state.email))
            }
        }
        onSignal<ProfileSent> { state, _ -> Decision(state = state.copy(sending = false), effect = ShowProfileSaved) }
        onSignal<ProfileFailed> { state, _ -> Decision(state = state.copy(sending = false, error = ProfileError.SendFailed)) }
    }
