package app.passwordstore.util.ssh

import android.net.Uri
import app.passwordstore.ssh.SSHKeyAlgorithm
import app.passwordstore.ssh.SSHKeyManager
import app.passwordstore.util.features.Feature
import app.passwordstore.util.features.Features
import app.passwordstore.util.git.operation.CredentialFinder
import app.passwordstore.util.git.sshj.SshKey
import javax.inject.Inject
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.keyprovider.KeyProvider

/** A wrapper around [SshKey] and [SSHKeyManager] to allow switching between them at runtime. */
class SSHFacade
@Inject
constructor(
  private val features: Features,
  private val sshKeyManager: SSHKeyManager,
) {

  private val useNewSSH
    get() = features.isEnabled(Feature.EnableNewSSHLayer)

  fun canShowPublicKey(): Boolean {
    return if (useNewSSH) {
      sshKeyManager.canShowPublicKey()
    } else {
      SshKey.canShowSshPublicKey
    }
  }

  fun publicKey(): String? {
    return if (useNewSSH) {
      sshKeyManager.publicKey()
    } else {
      SshKey.sshPublicKey
    }
  }

  fun keyExists(): Boolean {
    return if (useNewSSH) {
      sshKeyManager.keyExists()
    } else {
      SshKey.exists
    }
  }

  suspend fun generateKey(keyAlgorithm: SSHKeyAlgorithm, requireAuthentication: Boolean) {
    if (useNewSSH) {
      sshKeyManager.generateKey(keyAlgorithm, requireAuthentication)
    } else {
      when (keyAlgorithm) {
        SSHKeyAlgorithm.RSA ->
          SshKey.generateKeystoreNativeKey(SshKey.Algorithm.Rsa, requireAuthentication)
        SSHKeyAlgorithm.ECDSA ->
          SshKey.generateKeystoreNativeKey(SshKey.Algorithm.Ecdsa, requireAuthentication)
        SSHKeyAlgorithm.ED25519 -> SshKey.generateKeystoreWrappedEd25519Key(requireAuthentication)
      }
    }
  }

  suspend fun importKey(uri: Uri) {
    if (useNewSSH) {
      sshKeyManager.importKey(uri)
    } else {
      SshKey.import(uri)
    }
  }

  fun needsAuthentication(): Boolean {
    return if (useNewSSH) {
      sshKeyManager.needsAuthentication()
    } else {
      SshKey.mustAuthenticate
    }
  }

  fun keyProvider(client: SSHClient, credentialFinder: CredentialFinder): KeyProvider? {
    return if (useNewSSH) {
      sshKeyManager.keyProvider(client, credentialFinder)
    } else {
      SshKey.provide(client, credentialFinder)
    }
  }
}
