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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class NotesSlice(val notes: Load<List<Note>> = Load.Loading)

sealed interface NotesIntent : Intent
data class NoteClicked(val id: Int) : NotesIntent

sealed interface NotesSignal : Signal
data class NotesChanged(val notes: List<Note>) : NotesSignal
data object NotesFailed : NotesSignal

data class OpenNote(val id: Int) : Effect

/** The list sees the notes through this port. It never talks to the details screen. */
class NotesPorts(val notes: () -> Flow<List<Note>>)

private val WATCH = WorkKey("watch")

context(_: CapabilityScope)
fun notesList(ports: NotesPorts): Capability<NotesSlice> = capability<NotesSlice, NotesIntent, NotesSignal>(name = "notes", initial = NotesSlice()) {
    val works = object {
        // The subscription lives while the list is on the back stack, so a change on the details screen is here on return.
        fun watch() = stream(key = WATCH, onError = { NotesFailed }, policy = WorkPolicy.KeepExisting) { ports.notes().map(::NotesChanged) }
    }

    bootstrap { state -> Decision(state = state, work = works.watch()) }
    onSignal<NotesChanged> { state, changed -> Decision(state = state.copy(notes = Load.Loaded(changed.notes))) }
    onSignal<NotesFailed> { state, _ -> Decision(state = state.copy(notes = Load.Failed)) }
    onIntent<NoteClicked> { state, clicked -> Decision(state = state, effect = OpenNote(clicked.id)) }
}
