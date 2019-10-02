/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0
 */
package com.zeapo.pwdstore.git.config

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import org.eclipse.jgit.errors.UnsupportedCredentialItem
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.CredentialsProviderUserInfo
import org.eclipse.jgit.transport.OpenSshConfig
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.util.FS

class SshConfigSessionFactory(private val sshKey: String, private val username: String, private val passphrase: String) : GitConfigSessionFactory() {

    @Throws(JSchException::class)
    override fun getJSch(hc: OpenSshConfig.Host, fs: FS): JSch {
        val jsch = super.getJSch(hc, fs)
        jsch.removeAllIdentity()
        jsch.addIdentity(sshKey)
        return jsch
    }

    override fun configure(hc: OpenSshConfig.Host, session: Session) {
        session.setConfig("StrictHostKeyChecking", "no")
        session.setConfig("PreferredAuthentications", "publickey,password")

        val provider = object : CredentialsProvider() {
            override fun isInteractive(): Boolean {
                return false
            }

            override fun supports(vararg items: CredentialItem): Boolean {
                return true
            }

            @Throws(UnsupportedCredentialItem::class)
            override fun get(uri: URIish, vararg items: CredentialItem): Boolean {
                for (item in items) {
                    if (item is CredentialItem.Username) {
                        item.value = username
                        continue
                    }
                    if (item is CredentialItem.StringType) {
                        item.value = passphrase
                    }
                }
                return true
            }
        }
        val userInfo = CredentialsProviderUserInfo(session, provider)
        session.userInfo = userInfo
    }
}
