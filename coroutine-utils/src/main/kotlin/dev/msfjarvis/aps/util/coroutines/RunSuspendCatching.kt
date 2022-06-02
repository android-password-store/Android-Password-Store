@file:OptIn(ExperimentalContracts::class)
@file:Suppress("RedundantSuspendModifier")

package dev.msfjarvis.aps.util.coroutines

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlinx.coroutines.CancellationException

/**
 * Calls the specified function [block] and returns its encapsulated result if invocation was
 * successful, catching any [Throwable] except [CancellationException] that was thrown from the
 * [block] function execution and encapsulating it as a failure.
 */
@OptIn(ExperimentalContracts::class)
public suspend inline fun <V> runSuspendCatching(block: () -> V): Result<V, Throwable> {
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

  return try {
    Ok(block())
  } catch (e: Throwable) {
    if (e is CancellationException) throw e
    Err(e)
  }
}

/**
 * Calls the specified function [block] with [this] value as its receiver and returns its
 * encapsulated result if invocation was successful, catching any [Throwable] except
 * [CancellationException] that was thrown from the [block] function execution and encapsulating it
 * as a failure.
 */
@OptIn(ExperimentalContracts::class)
public suspend inline infix fun <T, V> T.runSuspendCatching(
  block: T.() -> V
): Result<V, Throwable> {
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

  return try {
    Ok(block())
  } catch (e: Throwable) {
    if (e is CancellationException) throw e
    Err(e)
  }
}
