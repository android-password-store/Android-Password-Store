package app.passwordstore.ssh.utils

public sealed class SSHException(message: String? = null, cause: Throwable? = null) :
  Exception(message, cause)

public class NullKeyException(message: String? = "keyType was null", cause: Throwable? = null) :
  SSHException(message, cause)

public class SSHKeyNotFoundException(
  message: String? = "SSH key does not exist in Keystore",
  cause: Throwable? = null
) : SSHException(message, cause)
