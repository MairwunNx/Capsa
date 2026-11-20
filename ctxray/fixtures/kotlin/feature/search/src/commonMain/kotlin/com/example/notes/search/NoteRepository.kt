package com.example.notes.search

data class Note(val id: Long, val title: String)

interface NoteRepository {
    suspend fun search(query: String): List<Note>
}
