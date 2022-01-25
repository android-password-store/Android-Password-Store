package dev.msfjarvis.aps.ssh

import java.io.File

data class SSHKey(val privateKey: File, val publicKey: File, private val type: SSHKeyType)
