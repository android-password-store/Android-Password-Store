/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.sshkeygen

import android.app.ProgressDialog
import android.content.DialogInterface
import android.os.AsyncTask
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.zeapo.pwdstore.R
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference

class KeyGenerateTask(activity: AppCompatActivity) : AsyncTask<String?, Void?, Exception?>() {
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
            println("Exception caught :(")
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
                val editor = prefs.edit()
                editor.putBoolean("use_generated_key", true)
                editor.apply()
            } else {
                MaterialAlertDialogBuilder(weakReference.get())
                        .setTitle("Error while trying to generate the ssh-key")
                        .setMessage(activity.resources.getString(R.string.ssh_key_error_dialog_text) +
                                e.message)
                        .setPositiveButton(activity.resources.getString(R.string.dialog_ok)) {
                            _: DialogInterface?, _: Int ->
                        }
                        .show()
            }
        } else {
            // TODO: When activity is destroyed
        }
    }
}
