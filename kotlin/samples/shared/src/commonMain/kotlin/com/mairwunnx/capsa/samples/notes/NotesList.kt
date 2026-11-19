package com.mairwunnx.capsa.samples.notes

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Load
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.Started
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.WorkPolicy
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.commit
import com.mairwunnx.capsa.samples.backend.Note
import com.mairwunnx.capsa.stream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class NotesSlice(val notes: Load<List<Note>> = Load.Loading)

data class NoteClicked(val id: Int) : Intent
data class NotesChanged(val notes: List<Note>) : Signal
data object NotesFailed : Signal
data class OpenNote(val id: Int) : Effect

/** The list sees the notes through this port. It never talks to the details screen. */
class NotesPorts(val notes: () -> Flow<List<Note>>)

private val WATCH = WorkKey("watch")

context(_: CapabilityScope)
fun notesList(ports: NotesPorts): Capability<NotesSlice> = capability("notes", NotesSlice()) {
    // The subscription lives while the list is on the back stack, so a change on the details screen is here on return.
    on<Started> { state, _ ->
        state.commit(stream(WATCH, onError = { NotesFailed }, policy = WorkPolicy.KeepExisting) { ports.notes().map(::NotesChanged) })
    }
    on<NotesChanged> { state, fact -> state.copy(notes = Load.Loaded(fact.notes)).commit() }
    on<NotesFailed> { state, _ -> state.copy(notes = Load.Failed).commit() }
    on<NoteClicked> { state, fact -> state.commit(OpenNote(fact.id)) }
}
