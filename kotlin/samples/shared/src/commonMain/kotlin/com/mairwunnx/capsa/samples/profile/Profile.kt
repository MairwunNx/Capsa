package com.mairwunnx.capsa.samples.profile

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.WorkPolicy
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.commit
import com.mairwunnx.capsa.work

data class ProfileSlice(
    val name: String = "",
    val email: String = "",
    val sending: Boolean = false,
    val error: ProfileError? = null,
)

enum class ProfileError { EmptyName, BadEmail, SendFailed }

data class NameChanged(val text: String) : Intent
data class EmailChanged(val text: String) : Intent
data object SaveClicked : Intent
data object ProfileSent : Signal
data object ProfileFailed : Signal
data object ShowProfileSaved : Effect

class ProfilePorts(val send: suspend (name: String, email: String) -> Unit)

private val SEND = WorkKey("send")

context(_: CapabilityScope)
fun profileForm(ports: ProfilePorts): Capability<ProfileSlice> = capability("profile", ProfileSlice()) {
    on<NameChanged> { state, fact -> state.copy(name = fact.text, error = null).commit() }
    on<EmailChanged> { state, fact -> state.copy(email = fact.text, error = null).commit() }
    on<SaveClicked> { state, _ ->
        when {
            state.sending -> state.commit()
            state.name.isBlank() -> state.copy(error = ProfileError.EmptyName).commit()
            '@' !in state.email -> state.copy(error = ProfileError.BadEmail).commit()
            // KeepExisting ignores a second tap while the request runs.
            else -> state.copy(sending = true).commit(
                work(SEND, onError = { ProfileFailed }, policy = WorkPolicy.KeepExisting) {
                    ports.send(state.name, state.email)
                    ProfileSent
                },
            )
        }
    }
    on<ProfileSent> { state, _ -> state.copy(sending = false).commit(ShowProfileSaved) }
    on<ProfileFailed> { state, _ -> state.copy(sending = false, error = ProfileError.SendFailed).commit() }
}
