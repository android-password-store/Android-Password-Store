/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.config

import com.github.ajalt.timberkt.Timber
import com.github.ajalt.timberkt.d
import com.hierynomus.sshj.signature.SignatureEdDSA
import com.hierynomus.sshj.transport.cipher.BlockCiphers
import com.hierynomus.sshj.transport.mac.Macs
import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.ConfigImpl
import net.schmizz.sshj.common.LoggerFactory
import net.schmizz.sshj.signature.SignatureECDSA
import net.schmizz.sshj.signature.SignatureRSA
import net.schmizz.sshj.signature.SignatureRSA.FactoryCERT
import net.schmizz.sshj.transport.compression.NoneCompression
import net.schmizz.sshj.transport.kex.Curve25519SHA256
import net.schmizz.sshj.transport.kex.Curve25519SHA256.FactoryLibSsh
import net.schmizz.sshj.transport.kex.DHGexSHA256
import net.schmizz.sshj.transport.kex.ECDHNistP
import net.schmizz.sshj.transport.random.JCERandom
import net.schmizz.sshj.transport.random.SingletonRandomFactory
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.keyprovider.PKCS5KeyFile
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import net.schmizz.sshj.userauth.keyprovider.PuTTYKeyFile
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.Logger
import org.slf4j.Marker
import java.security.Security


fun setUpBouncyCastleForSshj() {
    // Replace the Android BC provider with the Java BouncyCastle provider since the former does
    // not include all the required algorithms.
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
}

private abstract class AbstractLogger(private val name: String) : Logger {

    abstract fun t(message: String, t: Throwable? = null, vararg args: Any?)
    abstract fun d(message: String, t: Throwable? = null, vararg args: Any?)
    abstract fun i(message: String, t: Throwable? = null, vararg args: Any?)
    abstract fun w(message: String, t: Throwable? = null, vararg args: Any?)
    abstract fun e(message: String, t: Throwable? = null, vararg args: Any?)

    override fun getName() = name

    override fun isTraceEnabled(marker: Marker?): Boolean = isTraceEnabled
    override fun isDebugEnabled(marker: Marker?): Boolean = isDebugEnabled
    override fun isInfoEnabled(marker: Marker?): Boolean = isInfoEnabled
    override fun isWarnEnabled(marker: Marker?): Boolean = isWarnEnabled
    override fun isErrorEnabled(marker: Marker?): Boolean = isErrorEnabled

    override fun trace(msg: String) = t(msg)
    override fun trace(format: String, arg: Any?) = t(format, null, arg)
    override fun trace(format: String, arg1: Any?, arg2: Any?) = t(format, null, arg1, arg2)
    override fun trace(format: String, vararg arguments: Any?) = t(format, null, *arguments)
    override fun trace(msg: String, t: Throwable?) = t(msg, t)
    override fun trace(marker: Marker, msg: String) = trace(msg)
    override fun trace(marker: Marker?, format: String, arg: Any?) = trace(format, arg)
    override fun trace(marker: Marker?, format: String, arg1: Any?, arg2: Any?) =
        trace(format, arg1, arg2)

    override fun trace(marker: Marker?, format: String, vararg arguments: Any?) =
        trace(format, *arguments)

    override fun trace(marker: Marker?, msg: String, t: Throwable?) = trace(msg, t)

    override fun debug(msg: String) = d(msg)
    override fun debug(format: String, arg: Any?) = d(format, null, arg)
    override fun debug(format: String, arg1: Any?, arg2: Any?) = d(format, null, arg1, arg2)
    override fun debug(format: String, vararg arguments: Any?) = d(format, null, *arguments)
    override fun debug(msg: String, t: Throwable?) = d(msg, t)
    override fun debug(marker: Marker, msg: String) = debug(msg)
    override fun debug(marker: Marker?, format: String, arg: Any?) = debug(format, arg)
    override fun debug(marker: Marker?, format: String, arg1: Any?, arg2: Any?) =
        debug(format, arg1, arg2)

    override fun debug(marker: Marker?, format: String, vararg arguments: Any?) =
        debug(format, *arguments)

    override fun debug(marker: Marker?, msg: String, t: Throwable?) = debug(msg, t)

    override fun info(msg: String) = i(msg)
    override fun info(format: String, arg: Any?) = i(format, null, arg)
    override fun info(format: String, arg1: Any?, arg2: Any?) = i(format, null, arg1, arg2)
    override fun info(format: String, vararg arguments: Any?) = i(format, null, *arguments)
    override fun info(msg: String, t: Throwable?) = i(msg, t)
    override fun info(marker: Marker, msg: String) = info(msg)
    override fun info(marker: Marker?, format: String, arg: Any?) = info(format, arg)
    override fun info(marker: Marker?, format: String, arg1: Any?, arg2: Any?) =
        info(format, arg1, arg2)

    override fun info(marker: Marker?, format: String, vararg arguments: Any?) =
        info(format, *arguments)

    override fun info(marker: Marker?, msg: String, t: Throwable?) = info(msg, t)

    override fun warn(msg: String) = w(msg)
    override fun warn(format: String, arg: Any?) = w(format, null, arg)
    override fun warn(format: String, arg1: Any?, arg2: Any?) = w(format, null, arg1, arg2)
    override fun warn(format: String, vararg arguments: Any?) = w(format, null, *arguments)
    override fun warn(msg: String, t: Throwable?) = w(msg, t)
    override fun warn(marker: Marker, msg: String) = warn(msg)
    override fun warn(marker: Marker?, format: String, arg: Any?) = warn(format, arg)
    override fun warn(marker: Marker?, format: String, arg1: Any?, arg2: Any?) =
        warn(format, arg1, arg2)

    override fun warn(marker: Marker?, format: String, vararg arguments: Any?) =
        warn(format, *arguments)

    override fun warn(marker: Marker?, msg: String, t: Throwable?) = warn(msg, t)

    override fun error(msg: String) = e(msg)
    override fun error(format: String, arg: Any?) = e(format, null, arg)
    override fun error(format: String, arg1: Any?, arg2: Any?) = e(format, null, arg1, arg2)
    override fun error(format: String, vararg arguments: Any?) = e(format, null, *arguments)
    override fun error(msg: String, t: Throwable?) = e(msg, t)
    override fun error(marker: Marker, msg: String) = error(msg)
    override fun error(marker: Marker?, format: String, arg: Any?) = error(format, arg)
    override fun error(marker: Marker?, format: String, arg1: Any?, arg2: Any?) =
        error(format, arg1, arg2)

    override fun error(marker: Marker?, format: String, vararg arguments: Any?) =
        error(format, *arguments)

    override fun error(marker: Marker?, msg: String, t: Throwable?) = error(msg, t)
}

object TimberLoggerFactory : LoggerFactory {
    private class TimberLogger(name: String) : AbstractLogger(name) {

        // We defer the log level checks to Timber.
        override fun isTraceEnabled() = true
        override fun isDebugEnabled() = true
        override fun isInfoEnabled() = true
        override fun isWarnEnabled() = true
        override fun isErrorEnabled() = true

        // Replace slf4j's "{}" format string style with standard Java's "%s".
        // The supposedly redundant escape on the } is not redundant.
        @Suppress("RegExpRedundantEscape")
        private fun String.fix() = replace("""(?!<\\)\{\}""".toRegex(), "%s")

        override fun t(message: String, t: Throwable?, vararg args: Any?) {
            Timber.tag(name).v(t, message.fix(), *args)
        }

        override fun d(message: String, t: Throwable?, vararg args: Any?) {
            Timber.tag(name).d(t, message.fix(), *args)
        }

        override fun i(message: String, t: Throwable?, vararg args: Any?) {
            Timber.tag(name).i(t, message.fix(), *args)
        }

        override fun w(message: String, t: Throwable?, vararg args: Any?) {
            Timber.tag(name).w(t, message.fix(), *args)
        }

        override fun e(message: String, t: Throwable?, vararg args: Any?) {
            Timber.tag(name).e(t, message.fix(), *args)
        }
    }

    override fun getLogger(name: String): Logger {
        return TimberLogger(name)
    }

    override fun getLogger(clazz: Class<*>): Logger {
        return TimberLogger(clazz.name)
    }

}

class SshjConfig : ConfigImpl() {

    init {
        loggerFactory = TimberLoggerFactory
        keepAliveProvider = KeepAliveProvider.HEARTBEAT

        initKeyExchangeFactories()
        initSignatureFactories()
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

    private fun initSignatureFactories() {
        signatureFactories = listOf(
            SignatureEdDSA.Factory(),
            SignatureECDSA.Factory256(),
            SignatureECDSA.Factory384(),
            SignatureECDSA.Factory521(),
            SignatureRSA.Factory(),
            FactoryCERT()
        )
    }

    private fun initRandomFactory() {
        randomFactory = SingletonRandomFactory(JCERandom.Factory())
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
