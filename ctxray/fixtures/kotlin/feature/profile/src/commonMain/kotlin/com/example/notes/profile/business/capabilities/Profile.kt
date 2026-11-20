package com.example.notes.profile.business.capabilities

import com.mairwunnx.capsa.*
import kotlin.time.Clock

data class ProfileSlice(val name: String = "", val notes: Load<Int> = Load.Loading, val seenAt: Long = 0)

data class NameChanged(val name: String) : Intent
data class NotesCounted(val count: Int) : Signal
data object CountFailed : Signal
data object ShowSaved : Effect

class ProfilePorts(val countNotes: suspend () -> Int, val saveName: suspend (String) -> Unit)

private val COUNT = WorkKey("count")

context(_: CapabilityScope)
fun profile(ports: ProfilePorts): Capability<ProfileSlice> = capability("profile", ProfileSlice()) {
    on<Started> { state, _ ->
        state.copy(seenAt = Clock.System.now().toEpochMilliseconds()).commit(
            work(COUNT, onError = { CountFailed }) { NotesCounted(ports.countNotes()) },
        )
    }
    on<NameChanged> { state, fact -> state.copy(name = fact.name).commit(ShowSaved) }
    on<NotesCounted> { state, fact -> state.copy(notes = Load.Loaded(fact.count)).commit() }
}
