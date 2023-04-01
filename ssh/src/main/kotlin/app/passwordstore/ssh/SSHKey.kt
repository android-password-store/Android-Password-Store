package app.passwordstore.ssh

import java.io.File

public data class SSHKey(val privateKey: File, val publicKey: File, val type: SSHKeyType)
