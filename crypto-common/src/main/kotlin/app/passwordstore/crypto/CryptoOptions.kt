package app.passwordstore.crypto

/** Defines the contract for a grab-bag of options for individual cryptographic operations. */
public interface CryptoOptions {

  /** Returns a [Boolean] indicating if the [option] is enabled for this operation. */
  public fun isOptionEnabled(option: String): Boolean
}
