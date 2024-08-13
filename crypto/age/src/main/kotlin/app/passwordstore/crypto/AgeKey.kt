package app.passwordstore.crypto

import kage.Identity
import kage.Recipient

/** Sealed hierarchy for the types of keys that [kage.Age] expects. */
public sealed interface AgeKey {

  /** The public key in [kage.Age], wrapping a [kage.Recipient]. */
  @JvmInline public value class Public(public val recipient: Recipient) : AgeKey

  /** The private key in [kage.Age], wrapping a [kage.Identity]. */
  @JvmInline public value class Private(public val identity: Identity) : AgeKey
}
