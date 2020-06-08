/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.config

import com.github.ajalt.timberkt.d
import com.hierynomus.sshj.key.KeyAlgorithms
import com.hierynomus.sshj.transport.cipher.BlockCiphers
import com.hierynomus.sshj.transport.mac.Macs
import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.ConfigImpl
import net.schmizz.sshj.common.LoggerFactory
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.compression.NoneCompression
import net.schmizz.sshj.transport.kex.Curve25519SHA256
import net.schmizz.sshj.transport.kex.Curve25519SHA256.FactoryLibSsh
import net.schmizz.sshj.transport.kex.DHGexSHA256
import net.schmizz.sshj.transport.kex.ECDHNistP
import net.schmizz.sshj.transport.random.BouncyCastleRandom
import net.schmizz.sshj.transport.random.SingletonRandomFactory
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.keyprovider.PKCS5KeyFile
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import net.schmizz.sshj.userauth.keyprovider.PuTTYKeyFile
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security


fun setUpBouncyCastleForSshj() {
    // Replace the Android BC provider with the Java BouncyCastle provider since the former does
    // not include all the required algorithms.
    // TODO: Verify that we are indeed using the fast Android-native implementation whenever
    //  possible.
    // Note: This may affect crypto operations in other parts of the application.
    val bcIndex = Security.getProviders().indexOfFirst {
        it.name == BouncyCastleProvider.PROVIDER_NAME
    }
    if (bcIndex == -1) {
        // No Android BC found, install Java BC at lowest priority.
        Security.addProvider(BouncyCastleProvider())
    } else {
        // Replace Android BC with Java BC, inserted at the same position.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        // May be needed on Android Pie+ as per https://stackoverflow.com/a/57897224/297261
        try {
            Class.forName("sun.security.jca.Providers")
        } catch (e: ClassNotFoundException) {
        }
        Security.insertProviderAt(BouncyCastleProvider(), bcIndex + 1)
    }
    d { "JCE providers: ${Security.getProviders().joinToString { "${it.name} (${it.version})" }}" }
    // By setting this to false, we prevent SSHJ from specifying the BC provider explicitly, which
    // is preferred on Android and ensures compatibility with Android Keystore keys.
    SecurityUtils.setRegisterBouncyCastle(false)
    SecurityUtils.setSecurityProvider(null)
}

/**
 * Based on SSHJ's DefaultConfig.java, which is licensed under the Apache License, Version 2.0, with
 * the following copyright notice:
 * Copyright (C)2009 - SSHJ Contributors
 */
class SshjConfig : ConfigImpl() {

    init {
        // TODO: Replace with a Timber-backed LoggerFactory
        loggerFactory = LoggerFactory.DEFAULT
        keepAliveProvider = KeepAliveProvider.HEARTBEAT

        initKeyExchangeFactories()
        initKeyAlgorithms()
        initRandomFactory()
        initFileKeyProviderFactories()
        initCipherFactories()
        initCompressionFactories()
        initMACFactories()
    }

    private fun initKeyExchangeFactories() {
        keyExchangeFactories = listOf(
            Curve25519SHA256.Factory(),
            FactoryLibSsh(),
            ECDHNistP.Factory521(),
            ECDHNistP.Factory384(),
            ECDHNistP.Factory256(),
            DHGexSHA256.Factory()
        )
    }

    private fun initKeyAlgorithms() {
        keyAlgorithms = listOf(
            KeyAlgorithms.EdDSA25519(),
            KeyAlgorithms.ECDSASHANistp521(),
            KeyAlgorithms.ECDSASHANistp384(),
            KeyAlgorithms.ECDSASHANistp256(),
            KeyAlgorithms.RSASHA512(),
            KeyAlgorithms.RSASHA256(),
            KeyAlgorithms.SSHRSACertV01(),
            KeyAlgorithms.SSHRSA()
        )
    }

    private fun initRandomFactory() {
        randomFactory = SingletonRandomFactory(BouncyCastleRandom.Factory())
    }

    private fun initFileKeyProviderFactories() {
        fileKeyProviderFactories = listOf(
            OpenSSHKeyV1KeyFile.Factory(),
            PKCS8KeyFile.Factory(),
            PKCS5KeyFile.Factory(),
            OpenSSHKeyFile.Factory(),
            PuTTYKeyFile.Factory()
        )
    }


    private fun initCipherFactories() {
        cipherFactories = listOf(
            BlockCiphers.AES128CTR(),
            BlockCiphers.AES192CTR(),
            BlockCiphers.AES256CTR()
        )
    }

    private fun initMACFactories() {
        macFactories = listOf(
            Macs.HMACSHA2256(),
            Macs.HMACSHA2256Etm(),
            Macs.HMACSHA2512(),
            Macs.HMACSHA2512Etm()
        )
    }

    private fun initCompressionFactories() {
        compressionFactories = listOf(
            NoneCompression.Factory()
        )
    }
}
