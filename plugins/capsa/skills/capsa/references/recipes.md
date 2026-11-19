# Recipes

Each recipe shows a capability for a common scenario. The examples are in Kotlin, the only platform Capsa has today. The ideas carry over to any implementation of CSA. Imports, hosts, and tests are in `kotlin.md`.

Every capability here is a function with the context parameter `CapabilityScope`, so only a host or a test can create it.

## Search: debounce, stale answers, loading state

The user types, the request leaves after a pause, and a new character cancels the old request. The runtime drops a late answer of a cancelled request, so no token is needed.

```kotlin
data class SearchSlice(val query: String = "", val results: Load<List<Item>> = Load.Loaded(emptyList()))

data class QueryChanged(val text: String) : Intent
data class ResultsLoaded(val items: List<Item>) : Signal
data object SearchFailed : Signal

class SearchPorts(val search: suspend (query: String) -> List<Item>)

private val SEARCH = WorkKey("search")

context(_: CapabilityScope)
fun search(ports: SearchPorts): Capability<SearchSlice> = capability("search", SearchSlice()) {
    on<QueryChanged> { state, fact ->
        if (fact.text.isBlank()) {
            state.copy(query = fact.text, results = Load.Loaded(emptyList())).commit(cancel = listOf(SEARCH))
        } else {
            state.copy(query = fact.text, results = Load.Loading).commit(
                work(SEARCH, onError = { SearchFailed }, debounce = 300.milliseconds) { ResultsLoaded(ports.search(fact.text)) },
            )
        }
    }
    on<ResultsLoaded> { state, fact -> state.copy(results = Load.Loaded(fact.items)).commit() }
    on<SearchFailed> { state, _ -> state.copy(results = Load.Failed).commit() }
}
```

- `Latest` is the default policy, so each new query cancels the debounce wait or the request of the previous one.
- An empty query cancels the work by its key, and its late answer never arrives.

## Search: loading starts after a pause

The recipe above shows `Loading` with the first character. Some screens keep the old results while the user types and show the loader only after a pause. Debounce stays a parameter of a work: the `PAUSE` work only waits and sends a signal, and the reaction to that signal starts the request.

```kotlin
data class SearchSlice(val query: String = "", val results: Load<List<Item>> = Load.Loaded(emptyList()))

data class QueryChanged(val text: String) : Intent
data class QueryPaused(val query: String) : Signal
data class ResultsLoaded(val items: List<Item>) : Signal
data object SearchFailed : Signal

class SearchPorts(val search: suspend (query: String) -> List<Item>)

private val PAUSE = WorkKey("pause")
private val SEARCH = WorkKey("search")

context(_: CapabilityScope)
fun search(ports: SearchPorts): Capability<SearchSlice> = capability("search", SearchSlice()) {
    on<QueryChanged> { state, fact ->
        if (fact.text.isBlank()) {
            state.copy(query = fact.text, results = Load.Loaded(emptyList())).commit(cancel = listOf(PAUSE, SEARCH))
        } else {
            state.copy(query = fact.text).commit(
                work(PAUSE, onError = { SearchFailed }, debounce = 300.milliseconds) { QueryPaused(fact.text) },
            )
        }
    }
    on<QueryPaused> { state, fact ->
        state.copy(results = Load.Loading).commit(
            work(SEARCH, onError = { SearchFailed }) { ResultsLoaded(ports.search(fact.query)) },
        )
    }
    on<ResultsLoaded> { state, fact -> state.copy(results = Load.Loaded(fact.items)).commit() }
    on<SearchFailed> { state, _ -> state.copy(results = Load.Failed).commit() }
}
```

- Each new character restarts `PAUSE` with `Latest`, so `QueryPaused` arrives only for the text the user stopped at.
- A request that already runs keeps going while the user types, and its answer still shows. The next `QueryPaused` restarts `SEARCH` with `Latest`, and the runtime drops the old answer.
- A blank query cancels both keys.

## Like: optimistic update and rollback

The heart turns red at once, the request goes in the background, and a failure rolls the like back and shows a message.

```kotlin
data class LikeSlice(val liked: Boolean = false, val likes: Int = 0)

data object LikeClicked : Intent
data class LikeSaved(val likes: Int) : Signal
data object LikeFailed : Signal
data object ShowLikeError : Effect

class LikePorts(val setLiked: suspend (liked: Boolean) -> Int)

private val SAVE = WorkKey("save")

context(_: CapabilityScope)
fun likes(ports: LikePorts): Capability<LikeSlice> = capability("likes", LikeSlice()) {
    on<LikeClicked> { state, _ ->
        val liked = !state.liked
        state.copy(liked = liked, likes = state.likes + if (liked) 1 else -1).commit(
            work(SAVE, onError = { LikeFailed }) { LikeSaved(ports.setLiked(liked)) },
        )
    }
    on<LikeSaved> { state, fact -> state.copy(likes = fact.likes).commit() }
    on<LikeFailed> { state, _ ->
        state.copy(liked = !state.liked, likes = state.likes + if (state.liked) -1 else 1).commit(ShowLikeError)
    }
}
```

- A second tap restarts the work with `Latest`: only the last state goes to the server.
- `ShowLikeError` is an effect: the capability doesn’t know about snackbars.

## Timer: a stream that pauses in the background

An offer ends at a deadline. The countdown ticks only while the user sees the screen, and it starts from the real time left when the screen comes back.

```kotlin
data class OfferSlice(val left: Load<Duration> = Load.Loading)

data class DeadlineLoaded(val deadlineMillis: Long) : Signal
data class Ticked(val left: Duration) : Signal
data object OfferFailed : Signal
data object OfferExpired : Effect

class OfferPorts(
    val deadlineMillis: suspend () -> Long,
    val nowMillis: () -> Long,
)

private val LOAD = WorkKey("load")
private val TICK = WorkKey("tick")

context(_: CapabilityScope)
fun offer(ports: OfferPorts): Capability<OfferSlice> = capability("offer", OfferSlice()) {
    on<Started> { state, _ ->
        state.commit(work(LOAD, onError = { OfferFailed }, policy = WorkPolicy.KeepExisting) { DeadlineLoaded(ports.deadlineMillis()) })
    }
    on<DeadlineLoaded> { state, fact ->
        state.commit(stream(TICK, onError = { OfferFailed }, whileVisible = true) { ticks(fact.deadlineMillis, ports.nowMillis) })
    }
    on<Ticked> { state, fact ->
        val next = state.copy(left = Load.Loaded(fact.left))
        if (fact.left == Duration.ZERO) next.commit(OfferExpired) else next.commit()
    }
    on<OfferFailed> { state, _ -> state.copy(left = Load.Failed).commit() }
}

// Counts from the deadline, not from the moment of subscription: after a pause the first tick shows the real time left.
private fun ticks(deadlineMillis: Long, nowMillis: () -> Long): Flow<Signal> = flow {
    while (true) {
        val left = (deadlineMillis - nowMillis()).coerceAtLeast(0).milliseconds
        emit(Ticked(left))
        if (left == Duration.ZERO) break
        delay(1.seconds)
    }
}
```

- `decide` reads no clock: the stream reads it through a port, and the time arrives as a fact.
- `whileVisible` stops the flow in the back stack and calls the block again on return.

## Form: validation, submit once, input that survives process death

```kotlin
data class FormSlice(
    val name: String = "",
    val email: String = "",
    val sending: Boolean = false,
    val error: FormError? = null,
)

enum class FormError { EmptyName, BadEmail, SendFailed }

data class NameChanged(val text: String) : Intent
data class EmailChanged(val text: String) : Intent
data object SubmitClicked : Intent
data object FormSent : Signal
data object FormFailed : Signal
data object CloseForm : Effect

class FormPorts(val send: suspend (name: String, email: String) -> Unit)

private val SEND = WorkKey("send")

context(_: CapabilityScope)
fun form(ports: FormPorts): Capability<FormSlice> = capability("form", FormSlice()) {
    on<NameChanged> { state, fact -> state.copy(name = fact.text, error = null).commit() }
    on<EmailChanged> { state, fact -> state.copy(email = fact.text, error = null).commit() }
    on<SubmitClicked> { state, _ ->
        when {
            state.sending -> state.commit()
            state.name.isBlank() -> state.copy(error = FormError.EmptyName).commit()
            '@' !in state.email -> state.copy(error = FormError.BadEmail).commit()
            else -> state.copy(sending = true).commit(
                work(SEND, onError = { FormFailed }, policy = WorkPolicy.KeepExisting) {
                    ports.send(state.name, state.email)
                    FormSent
                },
            )
        }
    }
    on<FormSent> { state, _ -> state.copy(sending = false).commit(CloseForm) }
    on<FormFailed> { state, _ -> state.copy(sending = false, error = FormError.SendFailed).commit() }
}
```

- The slice holds `FormError`, not a text: the projection picks the string.
- `KeepExisting` ignores a second tap while the request runs.
- Save `name` and `email` through a slice saver in the host, not `sending` or `error`. See `kotlin.md`.

## Two capabilities on one screen

The header of a post shows the number of likes, and the like button owns that number. The like capability doesn’t know the header: it publishes a signal, and any capability that cares listens.

```kotlin
// In LikeFacts.kt: a signal other capabilities may listen to.
data class LikesChanged(val likes: Int) : Signal

// In the like capability.
on<LikeSaved> { state, fact -> state.copy(likes = fact.likes).commit(LikesChanged(fact.likes)) }

// In the header capability. It imports the fact, never the like capability or its slice.
on<LikesChanged> { state, fact -> state.copy(likes = fact.likes).commit() }
```

The runtime delivers `LikesChanged` to every capability that handles it, right after `LikeSaved` and before the next fact in the queue. Somebody must handle it, or the runtime reports `UnhandledFact`.

## Two screens

Screens don’t share runtimes or facts. They share a source in the app, and each screen sees it through a port.

```kotlin
// In an API module, in domain terms: no Fact here.
interface CartEvents {
    val changes: SharedFlow<CartChange>
    suspend fun publish(change: CartChange)
}

// The product screen publishes inside the work it already has.
class AddToCartPorts(val add: suspend (ProductId) -> Unit) // the wiring calls the repository, then publish()

// The cart screen subscribes once, on start, and turns the data into its own signal.
class CartPorts(val changes: () -> Flow<CartChange>)

on<Started> { state, _ ->
    state.commit(stream(CHANGES, onError = { CartFailed }, policy = WorkPolicy.KeepExisting) { ports.changes().map(::CartChanged) })
}
```

- State that a later screen must see at once is a `StateFlow`. An event is a `SharedFlow` with a buffer, and a screen that isn’t subscribed misses it.
- Keep the flow one-way: the runtime can’t catch a cycle between screens.
