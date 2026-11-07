package com.neteinstein.goodcompany.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Runs a ViewModel test with `Dispatchers.Main` (used by viewModelScope) swapped for an unconfined
 * test dispatcher, so state updates are visible synchronously.
 */
fun runViewModelTest(block: suspend TestScope.() -> Unit) {
    val dispatcher = UnconfinedTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) { block() }
    } finally {
        Dispatchers.resetMain()
    }
}

/** Keeps a `WhileSubscribed` StateFlow hot for the duration of the test and returns it. */
fun <T> TestScope.collecting(flow: StateFlow<T>): StateFlow<T> {
    backgroundScope.launch { flow.collect {} }
    return flow
}
