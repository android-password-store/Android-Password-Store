package app.passwordstore.crypto

/** [CryptoOptions] implementation for [kage.Age] encryption operations. */
public class AgeEncryptOptions private constructor(private val values: Map<String, Boolean>) :
  CryptoOptions {

  override fun isOptionEnabled(option: String): Boolean {
    return values.getOrDefault(option, false)
  }

  /** Builder for [AgeEncryptOptions]. */
  public class Builder {
    /** Build the final [AgeEncryptOptions] object. */
    public fun build(): AgeEncryptOptions {
      return AgeEncryptOptions(emptyMap())
    }
  }
}
