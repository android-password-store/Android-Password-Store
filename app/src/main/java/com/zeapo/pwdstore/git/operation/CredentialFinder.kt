package com.zeapo.pwdstore.git.operation

import android.annotation.SuppressLint
import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.git.config.ConnectionMode
import com.zeapo.pwdstore.git.config.InteractivePasswordFinder
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.getEncryptedPrefs
import com.zeapo.pwdstore.utils.requestInputFocusOnView
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class CredentialFinder(
    val callingActivity: FragmentActivity,
    val connectionMode: ConnectionMode
) : InteractivePasswordFinder() {

    override fun askForPassword(cont: Continuation<String?>, isRetry: Boolean) {
        val gitOperationPrefs = callingActivity.getEncryptedPrefs("git_operation")
        val credentialPref: String
        @StringRes val messageRes: Int
        @StringRes val hintRes: Int
        @StringRes val rememberRes: Int
        @StringRes val errorRes: Int
        when (connectionMode) {
            ConnectionMode.SshKey -> {
                credentialPref = PreferenceKeys.SSH_KEY_LOCAL_PASSPHRASE
                messageRes = R.string.passphrase_dialog_text
                hintRes = R.string.ssh_keygen_passphrase
                rememberRes = R.string.git_operation_remember_passphrase
                errorRes = R.string.git_operation_wrong_passphrase
            }
            ConnectionMode.Password -> {
                // Could be either an SSH or an HTTPS password
                credentialPref = PreferenceKeys.HTTPS_PASSWORD
                messageRes = R.string.password_dialog_text
                hintRes = R.string.git_operation_hint_password
                rememberRes = R.string.git_operation_remember_password
                errorRes = R.string.git_operation_wrong_password
            }
            else -> throw IllegalStateException("Only SshKey and Password connection mode ask for passwords")
        }
        val storedCredential = gitOperationPrefs.getString(credentialPref, null)
        if (isRetry)
            gitOperationPrefs.edit { remove(credentialPref) }
        if (storedCredential == null) {
            val layoutInflater = LayoutInflater.from(callingActivity)

            @SuppressLint("InflateParams")
            val dialogView = layoutInflater.inflate(R.layout.git_credential_layout, null)
            val credentialLayout = dialogView.findViewById<TextInputLayout>(R.id.git_auth_passphrase_layout)
            val editCredential = dialogView.findViewById<TextInputEditText>(R.id.git_auth_credential)
            editCredential.setHint(hintRes)
            val rememberCredential = dialogView.findViewById<MaterialCheckBox>(R.id.git_auth_remember_credential)
            rememberCredential.setText(rememberRes)
            if (isRetry)
                credentialLayout.error = callingActivity.resources.getString(errorRes)
            MaterialAlertDialogBuilder(callingActivity).run {
                setTitle(R.string.passphrase_dialog_title)
                setMessage(messageRes)
                setView(dialogView)
                setPositiveButton(R.string.dialog_ok) { _, _ ->
                    val credential = editCredential.text.toString()
                    if (rememberCredential.isChecked) {
                        gitOperationPrefs.edit {
                            putString(credentialPref, credential)
                        }
                    }
                    cont.resume(credential)
                }
                setNegativeButton(R.string.dialog_cancel) { _, _ ->
                    cont.resume(null)
                }
                setOnCancelListener {
                    cont.resume(null)
                }
                create()
            }.run {
                requestInputFocusOnView<TextInputEditText>(R.id.git_auth_credential)
                show()
            }
        } else {
            cont.resume(storedCredential)
        }
    }
}
