/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.config

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import org.eclipse.jgit.transport.JschConfigSessionFactory
import org.eclipse.jgit.transport.OpenSshConfig
import org.eclipse.jgit.util.FS

open class GitConfigSessionFactory : JschConfigSessionFactory() {

    override fun configure(hc: OpenSshConfig.Host, session: Session) {
        session.setConfig("StrictHostKeyChecking", "no")
    }

    @Throws(JSchException::class)
    override fun getJSch(hc: OpenSshConfig.Host, fs: FS): JSch {
        val jsch = super.getJSch(hc, fs)
        jsch.removeAllIdentity()
        return jsch
    }
}
