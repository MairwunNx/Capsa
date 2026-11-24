# Recipes

Each recipe shows a capability for a common scenario. The examples are in Kotlin, the only platform Capsa has today. The ideas carry over to any implementation of CSA. Imports, hosts, and tests are in `kotlin.md`.

Every capability here is a function with the context parameter `CapabilityScope`, so only a host or a test can create it. It states its slice, its intents, and its signals, and declares its works inside its block.

## Search: debounce, stale answers, loading state

The user types, the request leaves after a pause, and a new character cancels the old request. The runtime drops a late answer of a cancelled request, so no token is needed.

```kotlin
data class SearchSlice(val query: String = "", val results: Load<List<Item>> = Load.Loaded(emptyList()))

sealed interface SearchIntent : Intent
data class QueryChanged(val text: String) : SearchIntent

sealed interface SearchSignal : Signal
data class ResultsLoaded(val items: List<Item>) : SearchSignal
data object SearchFailed : SearchSignal

class SearchPorts(val search: suspend (query: String) -> List<Item>)

private val SEARCH = WorkKey("search")

context(_: CapabilityScope)
fun search(ports: SearchPorts) = capability<SearchSlice, SearchIntent, SearchSignal>(name = "search", initial = SearchSlice()) {
    val works = object {
        fun request(query: String) = work(key = SEARCH, onError = { SearchFailed }, debounce = 300.milliseconds) { ResultsLoaded(ports.search(query)) }
    }

    onIntent<QueryChanged> { state, changed ->
        if (changed.text.isBlank()) {
            Decision(state = state.copy(query = changed.text, results = Load.Loaded(emptyList())), cancel = SEARCH)
        } else {
            Decision(state = state.copy(query = changed.text, results = Load.Loading), work = works.request(changed.text))
        }
    }
    onSignal<ResultsLoaded> { state, loaded -> Decision(state = state.copy(results = Load.Loaded(loaded.items))) }
    onSignal<SearchFailed> { state, _ -> Decision(state = state.copy(results = Load.Failed)) }
}
```

- `Latest` is the default policy, so each new query cancels the debounce wait or the request of the previous one.
- An empty query cancels the work by its key, and its late answer never arrives.

## Search: loading starts after a pause

The recipe above shows `Loading` with the first character. Some screens keep the old results while the user types and show the loader only after a pause. Debounce stays a parameter of a work: the `PAUSE` work only waits and sends a signal, and the reaction to that signal starts the request.

```kotlin
data class SearchSlice(val query: String = "", val results: Load<List<Item>> = Load.Loaded(emptyList()))

sealed interface SearchIntent : Intent
data class QueryChanged(val text: String) : SearchIntent

sealed interface SearchSignal : Signal
data class QueryPaused(val query: String) : SearchSignal
data class ResultsLoaded(val items: List<Item>) : SearchSignal
data object SearchFailed : SearchSignal

class SearchPorts(val search: suspend (query: String) -> List<Item>)

private val PAUSE = WorkKey("pause")
private val SEARCH = WorkKey("search")

context(_: CapabilityScope)
fun search(ports: SearchPorts) = capability<SearchSlice, SearchIntent, SearchSignal>(name = "search", initial = SearchSlice()) {
    val works = object {
        fun pause(query: String) = work(key = PAUSE, onError = { SearchFailed }, debounce = 300.milliseconds) { QueryPaused(query) }
        fun request(query: String) = work(key = SEARCH, onError = { SearchFailed }) { ResultsLoaded(ports.search(query)) }
    }

    onIntent<QueryChanged> { state, changed ->
        if (changed.text.isBlank()) {
            Decision(state = state.copy(query = changed.text, results = Load.Loaded(emptyList())), cancels = listOf(PAUSE, SEARCH))
        } else {
            Decision(state = state.copy(query = changed.text), work = works.pause(changed.text))
        }
    }
    onSignal<QueryPaused> { state, paused -> Decision(state = state.copy(results = Load.Loading), work = works.request(paused.query)) }
    onSignal<ResultsLoaded> { state, loaded -> Decision(state = state.copy(results = Load.Loaded(loaded.items))) }
    onSignal<SearchFailed> { state, _ -> Decision(state = state.copy(results = Load.Failed)) }
}
```

- Each new character restarts `PAUSE` with `Latest`, so `QueryPaused` arrives only for the text the user stopped at.
- A request that already runs keeps going while the user types, and its answer still shows. The next `QueryPaused` restarts `SEARCH` with `Latest`, and the runtime drops the old answer.
- A blank query cancels both keys.

## Like: optimistic update and rollback

The heart turns red at once, the request goes in the background, and a failure rolls the like back and shows a message.

```kotlin
data class LikeSlice(val liked: Boolean = false, val likes: Int = 0)

sealed interface LikeIntent : Intent
data object LikeClicked : LikeIntent

sealed interface LikeSignal : Signal
data class LikeSaved(val likes: Int) : LikeSignal
data object LikeFailed : LikeSignal

data object ShowLikeError : Effect

class LikePorts(val setLiked: suspend (liked: Boolean) -> Int)

private val SAVE = WorkKey("save")

context(_: CapabilityScope)
fun likes(ports: LikePorts) = capability<LikeSlice, LikeIntent, LikeSignal>(name = "likes", initial = LikeSlice()) {
    val works = object {
        fun save(liked: Boolean) = work(key = SAVE, onError = { LikeFailed }) { LikeSaved(ports.setLiked(liked)) }
    }

    onIntent<LikeClicked> { state, _ ->
        val liked = !state.liked
        Decision(state = state.copy(liked = liked, likes = state.likes + if (liked) 1 else -1), work = works.save(liked))
    }
    onSignal<LikeSaved> { state, saved -> Decision(state = state.copy(likes = saved.likes)) }
    onSignal<LikeFailed> { state, _ ->
        Decision(state = state.copy(liked = !state.liked, likes = state.likes + if (state.liked) -1 else 1), effect = ShowLikeError)
    }
}
```

- A second tap restarts the work with `Latest`: only the last state goes to the server.
- `ShowLikeError` is an effect: the capability doesn’t know about snackbars.

## Timer: a stream that pauses in the background

An offer ends at a deadline. The countdown ticks only while the user sees the screen, and it starts from the real time left when the screen comes back.

“Pauses in the background” means one of two timers, and the request rarely says which. Ask the user before you write it:

- **Go on from the deadline:** the time runs out in the background too, the screen only stops ticking. An offer, a parking ticket, a code that expires. This recipe.
- **Freeze the countdown:** the time left stays as it was while the screen is hidden. A game round, a workout, a quiz question. The variant after this recipe.

```kotlin
data class OfferSlice(val left: Load<Duration> = Load.Loading)

sealed interface OfferSignal : Signal
data class DeadlineLoaded(val deadlineMillis: Long) : OfferSignal
data class Ticked(val left: Duration) : OfferSignal
data object OfferFailed : OfferSignal

data object OfferExpired : Effect

class OfferPorts(
    val deadlineMillis: suspend () -> Long,
    val nowMillis: () -> Long,
)

private val LOAD = WorkKey("load")
private val TICK = WorkKey("tick")

context(_: CapabilityScope)
fun offer(ports: OfferPorts) = capability<OfferSlice, Nothing, OfferSignal>(name = "offer", initial = OfferSlice()) {
    val works = object {
        fun load() = work(key = LOAD, onError = { OfferFailed }, policy = WorkPolicy.KeepExisting) { DeadlineLoaded(ports.deadlineMillis()) }
        fun tick(deadlineMillis: Long) = stream(key = TICK, onError = { OfferFailed }, whileVisible = true) { ticks(deadlineMillis, ports.nowMillis) }
    }

    bootstrap { state -> Decision(state = state, work = works.load()) }

    onSignal<DeadlineLoaded> { state, loaded -> Decision(state = state, work = works.tick(loaded.deadlineMillis)) }
    onSignal<Ticked> { state, ticked ->
        val next = state.copy(left = Load.Loaded(ticked.left))
        // Zero may arrive more than once: the slice tells whether the offer expires right now.
        Decision(state = next, effect = OfferExpired.takeIf { ticked.left == Duration.ZERO && state.left != next.left })
    }
    onSignal<OfferFailed> { state, _ -> Decision(state = state.copy(left = Load.Failed)) }
}

// Counts from the deadline, not from the moment of subscription: after a pause the first tick shows the real time left.
private fun ticks(deadlineMillis: Long, nowMillis: () -> Long): Flow<OfferSignal> = flow {
    while (true) {
        val left = (deadlineMillis - nowMillis()).coerceAtLeast(0).milliseconds
        emit(Ticked(left))
        if (left == Duration.ZERO) break
        delay(1.seconds)
    }
}
```

- `decide` reads no clock: the stream reads it through a port, and the time arrives as a fact.
- `whileVisible` stops the flow in the back stack and calls the block again on return. If the deadline passed in the background, the first tick after the return is already zero.
- A stream that has ended doesn’t start again. The runtime restarts only the runs that were alive when the screen hid. After the zero tick `ticks` ends, so hiding and showing the screen leaves `Loaded(Duration.ZERO)` and sends no new ticks. To count again, start `TICK` from a reaction, as `DeadlineLoaded` does.
- `OfferExpired` leaves once because the reaction compares the tick with the slice, not because the flow sends zero once. A run that is still alive when the screen hides, such as a flow that does more after the last tick, starts over on return and sends zero again. The check `state.left != next.left` lets only the change to zero through.

**Variant: freeze the countdown.** The stream only beats once a second, and the slice keeps the time left. A hidden screen gets no beats, so it loses no time, and no clock port is needed:

```kotlin
data class RoundSlice(val left: Duration = 60.seconds)

sealed interface RoundSignal : Signal
data object Beat : RoundSignal
data object RoundFailed : RoundSignal

data object RoundOver : Effect

private val TICK = WorkKey("tick")

context(_: CapabilityScope)
fun round() = capability<RoundSlice, Nothing, RoundSignal>(name = "round", initial = RoundSlice()) {
    // A restored round that already ended starts no beats.
    bootstrap { state ->
        Decision(state = state, work = stream(key = TICK, onError = { RoundFailed }, whileVisible = true) { beats() }.takeIf { state.left > Duration.ZERO })
    }

    onSignal<Beat> { state, _ ->
        val left = (state.left - 1.seconds).coerceAtLeast(Duration.ZERO)
        val over = left == Duration.ZERO
        Decision(state = state.copy(left = left), effect = RoundOver.takeIf { over }, cancel = TICK.takeIf { over })
    }
    onSignal<RoundFailed> { state, _ -> Decision(state = state) }
}

private fun beats(): Flow<RoundSignal> = flow {
    while (true) {
        delay(1.seconds)
        emit(Beat)
    }
}
```

- On return the flow starts over, and the first beat comes a second later: the round loses up to a second per hide, never more.
- `RoundOver` leaves once: the beat that reaches zero cancels `TICK`, so no beat follows.

## Form: validation, submit once, input that survives process death

```kotlin
data class FormSlice(
    val name: String = "",
    val email: String = "",
    val sending: Boolean = false,
    val error: FormError? = null,
)

enum class FormError { EmptyName, BadEmail, SendFailed }

sealed interface FormIntent : Intent
data class NameChanged(val text: String) : FormIntent
data class EmailChanged(val text: String) : FormIntent
data object SubmitClicked : FormIntent

sealed interface FormSignal : Signal
data object FormSent : FormSignal
data object FormFailed : FormSignal

data object CloseForm : Effect

class FormPorts(val send: suspend (name: String, email: String) -> Unit)

private val SEND = WorkKey("send")

context(_: CapabilityScope)
fun form(ports: FormPorts) = capability<FormSlice, FormIntent, FormSignal>(name = "form", initial = FormSlice()) {
    val works = object {
        fun send(name: String, email: String) = work(key = SEND, onError = { FormFailed }, policy = WorkPolicy.KeepExisting) {
            ports.send(name, email)
            FormSent
        }
    }

    onIntent<NameChanged> { state, changed -> Decision(state = state.copy(name = changed.text, error = null)) }
    onIntent<EmailChanged> { state, changed -> Decision(state = state.copy(email = changed.text, error = null)) }
    onIntent<SubmitClicked> { state, _ ->
        when {
            state.sending -> Decision(state = state)
            state.name.isBlank() -> Decision(state = state.copy(error = FormError.EmptyName))
            '@' !in state.email -> Decision(state = state.copy(error = FormError.BadEmail))
            else -> Decision(state = state.copy(sending = true), work = works.send(state.name, state.email))
        }
    }
    onSignal<FormSent> { state, _ -> Decision(state = state.copy(sending = false), effect = CloseForm) }
    onSignal<FormFailed> { state, _ -> Decision(state = state.copy(sending = false, error = FormError.SendFailed)) }
}
```

- The slice holds `FormError`, not a text: the projection picks the string.
- `KeepExisting` ignores a second tap while the request runs.
- Save `name` and `email` through a slice saver in the host, not `sending` or `error`. See `kotlin.md`.

## Two capabilities on one screen

The header of a post shows the number of likes, and the like button owns that number. The like capability doesn’t know the header: it publishes a signal, and any capability that cares listens.

```kotlin
// In LikeFacts.kt: a signal of the like capability that neighbours may listen to.
data class LikesChanged(val likes: Int) : LikeSignal

// In the like capability. It owns the click, so it tells the neighbours in the same decision.
onIntent<LikeClicked> { state, _ ->
    val liked = !state.liked
    val likes = state.likes + if (liked) 1 else -1
    Decision(state = state.copy(liked = liked, likes = likes), work = works.save(liked), signal = LikesChanged(likes))
}

// In the header capability. It imports the like’s facts, never the like capability or its slice.
onSignal<LikesChanged> { state, changed -> Decision(state = state.copy(likes = changed.likes)) }
onSignal<LikeSaved> { state, saved -> Decision(state = state.copy(likes = saved.likes)) }
```

The header can’t react to `LikeClicked`: the like capability owns that intent. So the like sends `LikesChanged`, and the runtime delivers it to every capability that handles it, right after the click and before the next fact in the queue. Somebody must handle it, or the runtime reports `UnhandledFact`.

The server’s answer needs no new signal. `LikeSaved` is a signal already, and it reaches the header the same way it reaches the like. A rollback after `LikeFailed` sends `LikesChanged` again.

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

// The cart screen subscribes once, in its bootstrap, and turns the data into its own signal.
class CartPorts(val changes: () -> Flow<CartChange>)

data class CartChanged(val change: CartChange) : CartSignal

bootstrap { state ->
    Decision(state = state, work = stream(key = CHANGES, onError = { CartFailed }, policy = WorkPolicy.KeepExisting) { ports.changes().map(::CartChanged) })
}
```

- State that a later screen must see at once is a `StateFlow`. An event is a `SharedFlow` with a buffer, and a screen that isn’t subscribed misses it.
- Keep the flow one-way: the runtime can’t catch a cycle between screens.
