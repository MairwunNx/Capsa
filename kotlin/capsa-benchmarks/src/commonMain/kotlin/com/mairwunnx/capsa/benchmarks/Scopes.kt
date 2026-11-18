package com.mairwunnx.capsa.benchmarks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * A scope that runs every coroutine in place, so `append` returns after the runtime has processed the fact.
 *
 * One benchmark operation is then one full pass, without a thread hop that would measure the dispatcher instead of Capsa.
 */
@Suppress("InjectDispatcher")
internal fun unconfinedScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
