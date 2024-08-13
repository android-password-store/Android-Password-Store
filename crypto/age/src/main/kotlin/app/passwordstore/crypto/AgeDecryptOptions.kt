package app.passwordstore.crypto

/** [CryptoOptions] implementation for [kage.Age] decryption operations. */
public class AgeDecryptOptions private constructor(private val values: Map<String, Boolean>) :
  CryptoOptions {

  override fun isOptionEnabled(option: String): Boolean {
    return values.getOrDefault(option, false)
  }

  /** Builder for [AgeDecryptOptions]. */
  public class Builder {
    /** Build the final [AgeDecryptOptions] object. */
    public fun build(): AgeDecryptOptions {
      return AgeDecryptOptions(emptyMap())
    }
  }
}
