package com.mairwunnx.capsa.compose

/**
 * The base of the tests that run a real composition.
 *
 * On Android host tests it runs them under Robolectric: a composition calls the Android framework, which the stub android.jar lacks.
 * On the JVM and Kotlin/Native it adds nothing.
 */
// An abstract class, not an interface: JUnit inherits @RunWith from a superclass only.
@Suppress("AbstractClassCanBeInterface")
expect abstract class CompositionTest()
