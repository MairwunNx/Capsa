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
import com.mairwunnx.capsa.valueOrNull
import com.mairwunnx.capsa.work
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class NoteSlice(val note: Load<Note> = Load.Loading, val saving: Boolean = false)

data object StarClicked : Intent
data class NoteLoaded(val note: Note) : Signal
data object NoteFailed : Signal
data object StarSaved : Signal
data object StarFailed : Signal
data object ShowStarError : Effect

/** The details see one note through this port and change it there. The list watches the same source. */
class NotePorts(val note: () -> Flow<Note>, val setStarred: suspend (starred: Boolean) -> Unit)

private val WATCH = WorkKey("watch")
private val STAR = WorkKey("star")

context(_: CapabilityScope)
fun noteDetails(ports: NotePorts): Capability<NoteSlice> = capability("note", NoteSlice()) {
    on<Started> { state, _ ->
        state.commit(stream(WATCH, onError = { NoteFailed }, policy = WorkPolicy.KeepExisting) { ports.note().map(::NoteLoaded) })
    }
    on<NoteLoaded> { state, fact -> state.copy(note = Load.Loaded(fact.note)).commit() }
    on<NoteFailed> { state, _ -> state.copy(note = Load.Failed).commit() }
    on<StarClicked> { state, _ ->
        val note = state.note.valueOrNull()
        if (note == null || state.saving) {
            state.commit()
        } else {
            state.copy(saving = true).commit(
                work(STAR, onError = { StarFailed }, policy = WorkPolicy.KeepExisting) {
                    ports.setStarred(!note.starred)
                    StarSaved
                },
            )
        }
    }
    // The new star comes back through the note stream, so the reaction only ends the saving.
    on<StarSaved> { state, _ -> state.copy(saving = false).commit() }
    on<StarFailed> { state, _ -> state.copy(saving = false).commit(ShowStarError) }
}
