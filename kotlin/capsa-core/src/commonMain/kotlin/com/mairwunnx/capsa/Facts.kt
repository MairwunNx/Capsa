package com.mairwunnx.capsa

/**
 * Marks an event that enters the runtime through [CapsaRuntime.append].
 *
 * A fact lives for one pass of the pipeline only. The state of a capability holds the memory of the screen.
 */
public interface Fact

/**
 * Describes an action or an observation on the UI side: a click, an item shown, or the size of a view.
 *
 * Only the UI creates an [Intent]. Impressions and measurements are intents too, because they come
 * from the UI.
 */
public interface Intent : Fact

/** Describes an outside event: the result of a work, an answer from another screen, or a lifecycle change. */
public interface Signal : Fact

/** Describes a command for the host: navigation, a snackbar, sharing, or analytics. It doesn’t perform the action itself. */
public interface Effect

/**
 * Triggers the initial reactions of the capabilities once the runtime is built.
 *
 * The runtime sends [Started] exactly once. The host doesn’t repeat the start on a screen rotation
 * or when the screen enters the composition again.
 */
public data object Started : Signal
