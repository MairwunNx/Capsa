package com.mairwunnx.capsa

/**
 * Marks the API that the Capsa modules share among themselves, such as [CapsaHostCore].
 *
 * It isn’t part of the public API: it can change in any release without notice. An app doesn’t need it.
 * A host of your own builds on `CapsaRuntime` from `capsa-core` instead.
 */
@RequiresOptIn(message = "An internal API of the Capsa hosts. It can change in any release.", level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CONSTRUCTOR)
public annotation class InternalCapsaApi
