package com.mairwunnx.capsa.samples.dashboard

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Load
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability

data class TasksSlice(val open: Load<Int> = Load.Loading)

sealed interface TasksSignal : Signal
data class TasksLoaded(val open: Int) : TasksSignal
data object TasksFailed : TasksSignal

class TasksPorts(val openTasks: suspend () -> Int)

private val LOAD = WorkKey("load")

context(_: CapabilityScope)
fun tasks(ports: TasksPorts): Capability<TasksSlice> = capability<TasksSlice, Nothing, TasksSignal>(name = "tasks", initial = TasksSlice()) {
    val works = object {
        fun load() = work(key = LOAD, onError = { TasksFailed }) { TasksLoaded(ports.openTasks()) }
    }

    bootstrap { state -> Decision(state = state, work = works.load()) }
    onSignal<RefreshRequested> { _, _ -> Decision(state = TasksSlice(), work = works.load()) }
    onSignal<TasksLoaded> { state, loaded -> Decision(state = state.copy(open = Load.Loaded(loaded.open))) }
    onSignal<TasksFailed> { state, _ -> Decision(state = state.copy(open = Load.Failed)) }
}
