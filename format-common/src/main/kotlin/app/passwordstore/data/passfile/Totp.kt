package app.passwordstore.data.passfile

import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/** Holder for a TOTP secret and the duration for which it is valid. */
@OptIn(ExperimentalTime::class)
public data class Totp(public val value: String, public val remainingTime: Duration)
