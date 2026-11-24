package com.mairwunnx.capsa.samples.notes

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Load
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.WorkPolicy
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.samples.backend.Note
import com.mairwunnx.capsa.valueOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class NoteSlice(val note: Load<Note> = Load.Loading, val saving: Boolean = false)

sealed interface NoteIntent : Intent
data object StarClicked : NoteIntent

sealed interface NoteSignal : Signal
data class NoteLoaded(val note: Note) : NoteSignal
data object NoteFailed : NoteSignal
data object StarSaved : NoteSignal
data object StarFailed : NoteSignal

data object ShowStarError : Effect

/** The details see one note through this port and change it there. The list watches the same source. */
class NotePorts(val note: () -> Flow<Note>, val setStarred: suspend (starred: Boolean) -> Unit)

private val WATCH = WorkKey("watch")
private val STAR = WorkKey("star")

context(_: CapabilityScope)
fun noteDetails(ports: NotePorts): Capability<NoteSlice> = capability<NoteSlice, NoteIntent, NoteSignal>(name = "note", initial = NoteSlice()) {
    val works = object {
        fun watch() = stream(key = WATCH, onError = { NoteFailed }, policy = WorkPolicy.KeepExisting) { ports.note().map(::NoteLoaded) }

        fun star(starred: Boolean) = work(key = STAR, onError = { StarFailed }, policy = WorkPolicy.KeepExisting) {
            ports.setStarred(starred)
            StarSaved
        }
    }

    bootstrap { state -> Decision(state = state, work = works.watch()) }
    onSignal<NoteLoaded> { state, loaded -> Decision(state = state.copy(note = Load.Loaded(loaded.note))) }
    onSignal<NoteFailed> { state, _ -> Decision(state = state.copy(note = Load.Failed)) }
    onIntent<StarClicked> { state, _ ->
        val note = state.note.valueOrNull()
        if (note == null || state.saving) {
            Decision(state = state)
        } else {
            Decision(state = state.copy(saving = true), work = works.star(!note.starred))
        }
    }
    // The new star comes back through the note stream, so the reaction only ends the saving.
    onSignal<StarSaved> { state, _ -> Decision(state = state.copy(saving = false)) }
    onSignal<StarFailed> { state, _ -> Decision(state = state.copy(saving = false), effect = ShowStarError) }
}
