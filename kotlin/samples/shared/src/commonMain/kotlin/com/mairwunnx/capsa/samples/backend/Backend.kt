package com.mairwunnx.capsa.samples.backend

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlin.random.Random

/** The fake servers of the demo, one for the whole app. A real app gets them from its data layer. */
object Backend {
    val likes = LikeServer()
    val fruits = FruitCatalog()
    val profile = ProfileServer()
    val dashboard = DashboardServer()
    val notes = NotesStore()
}

class LikeServer {
    /** The switch on the like screen: while it is off, every request fails, so you can watch the retries and the rollback. */
    val online = MutableStateFlow(true)

    /** The likes of the post before you liked it. */
    val likes = 128

    suspend fun setLiked(liked: Boolean): Int = network(failRate = if (online.value) 0.0 else 1.0) { if (liked) likes + 1 else likes }
}

class FruitCatalog {
    private val fruits = listOf(
        "Apple", "Apricot", "Avocado", "Banana", "Blackberry", "Blueberry", "Cherry", "Coconut", "Cranberry", "Date",
        "Dragon fruit", "Fig", "Grape", "Grapefruit", "Guava", "Kiwi", "Lemon", "Lime", "Lychee", "Mango",
        "Melon", "Nectarine", "Orange", "Papaya", "Passion fruit", "Peach", "Pear", "Pineapple", "Plum", "Pomegranate",
        "Raspberry", "Strawberry", "Tangerine", "Watermelon",
    )

    /** Answers in 0.2 to 1.5 seconds, so a slow answer to an old query could come after a fast answer to a new one. */
    suspend fun search(query: String): List<String> =
        network(failRate = 0.1, latency = 200L..1500L) { fruits.filter { it.contains(query, ignoreCase = true) } }
}

class ProfileServer {
    suspend fun send(name: String, email: String): Unit = network(failRate = 0.2) { check(name.isNotBlank() && email.isNotBlank()) }
}

class DashboardServer {
    suspend fun celsius(): Int = network(failRate = 0.2) { Random.nextInt(-5, 30) }

    suspend fun openTasks(): Int = network(failRate = 0.2) { Random.nextInt(0, 12) }
}

data class Note(val id: Int, val title: String, val starred: Boolean)

/** The source both note screens see: the details change a note here, and the list watches the same flow. */
class NotesStore {
    private val notes = MutableStateFlow(
        listOf(
            Note(1, "Buy oat milk", starred = false),
            Note(2, "Call grandma", starred = true),
            Note(3, "Book the dentist", starred = false),
            Note(4, "Water the plants", starred = false),
        ),
    )

    fun all(): StateFlow<List<Note>> = notes

    fun note(id: Int): Flow<Note> = notes.map { all -> all.firstOrNull { it.id == id } }.mapNotNull { it }

    suspend fun setStarred(id: Int, starred: Boolean): Unit = network(failRate = 0.1) {
        notes.update { all -> all.map { if (it.id == id) it.copy(starred = starred) else it } }
    }
}
