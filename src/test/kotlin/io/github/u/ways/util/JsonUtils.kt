package io.github.u.ways.util

import java.time.Duration
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

internal fun runBlockingWithTimeoutUnit(
    duration: Duration = Duration.ofSeconds(5),
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> Unit
): Unit = runBlockingWithTimeout(duration, context, block)

internal fun <T> runBlockingWithTimeout(
    duration: Duration = Duration.ofSeconds(5),
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): T = runBlocking(context) { withTimeout(duration.toMillis(), block) }