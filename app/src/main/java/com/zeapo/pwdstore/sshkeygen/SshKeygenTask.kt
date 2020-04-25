/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.sshkeygen

import android.app.ProgressDialog
import android.os.AsyncTask
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.zeapo.pwdstore.R
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference

class KeyGenerateTask(activity: FragmentActivity) : AsyncTask<String?, Void?, Exception?>() {
    private var pd: ProgressDialog? = null
    private val weakReference = WeakReference(activity)
    override fun onPreExecute() {
        super.onPreExecute()
        pd = ProgressDialog.show(weakReference.get(), "", "Generating keys")
    }

    override fun doInBackground(vararg strings: String?): Exception? {
        val length = strings[0]?.toInt()
        val passphrase = strings[1]
        val comment = strings[2]
        val jsch = JSch()
        try {
            val kp = length?.let { KeyPair.genKeyPair(jsch, KeyPair.RSA, it) }
            var file = File(weakReference.get()!!.filesDir.toString() + "/.ssh_key")
            var out = FileOutputStream(file, false)
            if (passphrase?.isNotEmpty()!!) {
                kp?.writePrivateKey(out, passphrase.toByteArray())
            } else {
                kp?.writePrivateKey(out)
            }
            file = File(weakReference.get()!!.filesDir.toString() + "/.ssh_key.pub")
            out = FileOutputStream(file, false)
            kp?.writePublicKey(out, comment)
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return e
        }
    }

    override fun onPostExecute(e: Exception?) {
        super.onPostExecute(e)
        val activity = weakReference.get()
        if (activity is AppCompatActivity) {
            pd!!.dismiss()
            if (e == null) {
                Toast.makeText(activity, "SSH-key generated", Toast.LENGTH_LONG).show()
                val df: DialogFragment = ShowSshKeyFragment()
                df.show(activity.supportFragmentManager, "public_key")
                val prefs = PreferenceManager.getDefaultSharedPreferences(weakReference.get())
                prefs.edit { putBoolean("use_generated_key", true) }
            } else {
                MaterialAlertDialogBuilder(activity)
                        .setTitle(activity.getString(R.string.error_generate_ssh_key))
                        .setMessage(activity.getString(R.string.ssh_key_error_dialog_text) + e.message)
                        .setPositiveButton(activity.getString(R.string.dialog_ok), null)
                        .show()
            }
        } else {
            // TODO: When activity is destroyed
        }
    }
}
