package com.mairwunnx.capsa.samples

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.mairwunnx.capsa.samples.clock.ClockScreen
import com.mairwunnx.capsa.samples.counter.CounterScreen
import com.mairwunnx.capsa.samples.crash.CrashFreeScreen
import com.mairwunnx.capsa.samples.dashboard.DashboardScreen
import com.mairwunnx.capsa.samples.like.LikeScreen
import com.mairwunnx.capsa.samples.notes.NoteScreen
import com.mairwunnx.capsa.samples.notes.NotesScreen
import com.mairwunnx.capsa.samples.profile.ProfileScreen
import com.mairwunnx.capsa.samples.search.SearchScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** The screens of the demo. The back stack keeps them through the death of the process. */
@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route

    @Serializable
    data object Counter : Route

    @Serializable
    data object Search : Route

    @Serializable
    data object Like : Route

    @Serializable
    data object Clock : Route

    @Serializable
    data object Profile : Route

    @Serializable
    data object Dashboard : Route

    @Serializable
    data object CrashFree : Route

    @Serializable
    data object Notes : Route

    @Serializable
    data class Note(val id: Int) : Route
}

// Outside Android, Navigation 3 saves the back stack only with every key registered.
private val routes = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Route.Home::class)
            subclass(Route.Counter::class)
            subclass(Route.Search::class)
            subclass(Route.Like::class)
            subclass(Route.Clock::class)
            subclass(Route.Profile::class)
            subclass(Route.Dashboard::class)
            subclass(Route.CrashFree::class)
            subclass(Route.Notes::class)
            subclass(Route.Note::class)
        }
    }
}

/** The demo app: a list of samples, each on a screen of its own. */
@Composable
fun App() {
    SamplesTheme {
        val backStack = rememberNavBackStack(routes, Route.Home)
        val back: () -> Unit = { backStack.removeLastOrNull() }
        NavDisplay(
            backStack = backStack,
            onBack = back,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                // Each entry gets its own ViewModelStore, so a CapsaViewModel lives while its screen is on the back stack.
                rememberViewModelStoreNavEntryDecorator(),
                // And its own RetainedValuesStore, so does a runtime from rememberCapsa.
                rememberRetainedValuesStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<Route.Home> { HomeScreen(onOpen = { route -> backStack.add(route) }) }
                entry<Route.Counter> { CounterScreen(onBack = back) }
                entry<Route.Search> { SearchScreen(onBack = back) }
                entry<Route.Like> { LikeScreen(onBack = back) }
                entry<Route.Clock> { ClockScreen(onBack = back) }
                entry<Route.Profile> { ProfileScreen(onBack = back) }
                entry<Route.Dashboard> { DashboardScreen(onBack = back) }
                entry<Route.CrashFree> { CrashFreeScreen(onBack = back) }
                entry<Route.Notes> { NotesScreen(onBack = back, onOpenNote = { id -> backStack.add(Route.Note(id)) }) }
                entry<Route.Note> { route -> NoteScreen(id = route.id, onBack = back) }
            },
        )
    }
}
