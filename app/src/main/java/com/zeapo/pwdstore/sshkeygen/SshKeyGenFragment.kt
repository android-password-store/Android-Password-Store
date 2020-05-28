/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.sshkeygen

import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.FragmentSshKeygenBinding
import com.zeapo.pwdstore.git.ANDROID_KEYSTORE_ALIAS_SSH_KEY
import com.zeapo.pwdstore.git.config.PROVIDER_ANDROID_KEY_STORE
import com.zeapo.pwdstore.utils.BiometricAuthenticator
import com.zeapo.pwdstore.utils.keyguardManager
import com.zeapo.pwdstore.utils.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import java.io.File
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class KeyGenType(val algorithm: String, val keyLength: Int,
                      val applyToSpec: KeyGenParameterSpec.Builder.() -> Unit) {

    Rsa2048(KeyProperties.KEY_ALGORITHM_RSA, 2048, {
        setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
        setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
    }),
    Rsa3072(KeyProperties.KEY_ALGORITHM_RSA, 3072, {
        setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
        setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
    }),
    Rsa4096(KeyProperties.KEY_ALGORITHM_RSA, 4096, {
        setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
        setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
    }),
    Ecdsa256(KeyProperties.KEY_ALGORITHM_EC, 256, {
        setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
        setDigests(KeyProperties.DIGEST_SHA256)
    }),
    Ecdsa384(KeyProperties.KEY_ALGORITHM_EC, 384, {
        setAlgorithmParameterSpec(ECGenParameterSpec("secp384r1"))
        setDigests(KeyProperties.DIGEST_SHA384)
    }),
    Ecdsa521(KeyProperties.KEY_ALGORITHM_EC, 521, {
        setAlgorithmParameterSpec(ECGenParameterSpec("secp521r1"))
        setDigests(KeyProperties.DIGEST_SHA512)
    }),
}

class SshKeyGenFragment : Fragment(R.layout.fragment_ssh_keygen) {

    private var keyType = KeyGenType.Ecdsa384
    private val binding by viewBinding(FragmentSshKeygenBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            generate.setOnClickListener {
                lifecycleScope.launch { generate(comment.text.toString()) }
            }
            keyLengthGroup.check(R.id.key_type_ecdsa_384)
            keyLengthGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    keyType = when (checkedId) {
                        R.id.key_type_rsa_2048 -> KeyGenType.Rsa2048
                        R.id.key_type_rsa_3072 -> KeyGenType.Rsa3072
                        R.id.key_type_ecdsa_384 -> KeyGenType.Ecdsa384
                        else -> throw IllegalStateException("Invalid key type selection")
                    }
                }
            }
            keyRequireAuthentication.isEnabled =
                requireContext().keyguardManager?.isDeviceSecure == true
            keyRequireAuthentication.isChecked = keyRequireAuthentication.isEnabled
        }
    }

    private fun generateAndStoreKey(parameterSpec: KeyGenParameterSpec, comment: String) {
        val keyPair = KeyPairGenerator.getInstance(keyType.algorithm,
            PROVIDER_ANDROID_KEY_STORE).run {
            initialize(parameterSpec)
            generateKeyPair()
        }
        val keyType = KeyType.fromKey(keyPair.public)
        val rawPublicKey = Buffer.PlainBuffer().run {
            keyType.putPubKeyIntoBuffer(keyPair.public, this)
            compactData
        }
        val encodedPublicKey = Base64.encodeToString(rawPublicKey, Base64.NO_WRAP)
        val sshPublicKey = "$keyType $encodedPublicKey $comment"
        File(requireActivity().filesDir, ".ssh_key").writeText("keystore")
        File(requireActivity().filesDir, ".ssh_key.pub").writeText(sshPublicKey)
    }

    // Invoked when 'Generate' button of SshKeyGenFragment clicked. Generates a
    // private and public key, then replaces the SshKeyGenFragment with a
    // ShowSshKeyFragment which displays the public key.
    private suspend fun generate(comment: String) {
        binding.generate.apply {
            text = getString(R.string.ssh_key_gen_generating_progress)
            isEnabled = false
        }
        val e = try {
            withContext(Dispatchers.IO) {
                val createWithUserAuthentication = binding.keyRequireAuthentication.isChecked
                val parameterSpec = KeyGenParameterSpec.Builder(
                    ANDROID_KEYSTORE_ALIAS_SSH_KEY,
                    KeyProperties.PURPOSE_SIGN
                ).run {
                    setKeySize(keyType.keyLength)
                    apply(keyType.applyToSpec)
                    if (createWithUserAuthentication) {
                        setUserAuthenticationRequired(true)
                        // 60 seconds should provide ample time to connect to the SSH server and
                        // perform authentication (and possibly a Git operation and another connect,
                        // in case of the clone operation).
                        setUserAuthenticationValidityDurationSeconds(60)
                    }
                    build()
                }
                if (createWithUserAuthentication) {
                    val result = withContext(Dispatchers.Main) {
                        suspendCoroutine<BiometricAuthenticator.Result> { cont ->
                            BiometricAuthenticator.authenticate(requireActivity(), R.string.biometric_prompt_title_ssh_keygen) {
                                cont.resume(it)
                            }
                        }
                    }
                    if (result is BiometricAuthenticator.Result.Success) {
                        generateAndStoreKey(parameterSpec, comment)
                    } else {
                        throw UserNotAuthenticatedException(getString(R.string.biometric_auth_generic_failure))
                    }
                } else {
                    generateAndStoreKey(parameterSpec, comment)
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            e
        }
        val activity = requireActivity()
        binding.generate.apply {
            text = getString(R.string.ssh_keygen_generate)
            isEnabled = true
        }
        if (e == null) {
            val df = ShowSshKeyFragment()
            df.show(requireActivity().supportFragmentManager, "public_key")
            val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
            prefs.edit { putBoolean("use_generated_key", true) }
        } else {
            MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.error_generate_ssh_key))
                .setMessage(activity.getString(R.string.ssh_key_error_dialog_text) + e.message)
                .setPositiveButton(activity.getString(R.string.dialog_ok)) { _, _ ->
                    requireActivity().finish()
                }
                .show()
        }
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val activity = activity ?: return
        val imm = activity.getSystemService<InputMethodManager>() ?: return
        var view = activity.currentFocus
        if (view == null) {
            view = View(activity)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}