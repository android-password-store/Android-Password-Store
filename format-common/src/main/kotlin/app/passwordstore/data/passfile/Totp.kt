package app.passwordstore.data.passfile

import kotlin.time.Duration

/** Holder for a TOTP secret and the duration for which it is valid. */
public data class Totp(public val value: String, public val remainingTime: Duration)
