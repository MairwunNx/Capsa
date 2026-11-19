package com.mairwunnx.capsa.samples.dashboard

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Load
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.Started
import com.mairwunnx.capsa.Work
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.commit
import com.mairwunnx.capsa.work

data class TasksSlice(val open: Load<Int> = Load.Loading)

data class TasksLoaded(val open: Int) : Signal
data object TasksFailed : Signal

class TasksPorts(val openTasks: suspend () -> Int)

private val LOAD = WorkKey("load")

context(_: CapabilityScope)
fun tasks(ports: TasksPorts): Capability<TasksSlice> = capability("tasks", TasksSlice()) {
    on<Started> { state, _ -> state.commit(load(ports)) }
    on<RefreshClicked> { _, _ -> TasksSlice().commit(load(ports)) }
    on<TasksLoaded> { state, fact -> state.copy(open = Load.Loaded(fact.open)).commit() }
    on<TasksFailed> { state, _ -> state.copy(open = Load.Failed).commit() }
}

private fun load(ports: TasksPorts): Work = work(LOAD, onError = { TasksFailed }) { TasksLoaded(ports.openTasks()) }
