package com.zeapo.pwdstore.utils

/**
 * Emulates the Rust Result enum but without returning a value in the [Ok] case.
 * https://doc.rust-lang.org/std/result/enum.Result.html
 */
sealed class Result {

    object Ok : Result()
    data class Err(val err: Exception) : Result()
}
