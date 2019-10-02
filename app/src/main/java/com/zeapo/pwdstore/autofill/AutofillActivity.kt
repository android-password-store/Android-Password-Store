/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0
 */
package com.zeapo.pwdstore.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.zeapo.pwdstore.PasswordStore
import com.zeapo.pwdstore.utils.splitLines
import org.eclipse.jgit.util.StringUtils
import java.util.ArrayList
import java.util.Arrays

// blank activity started by service for calling startIntentSenderForResult
class AutofillActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent.extras

        if (extras != null && extras.containsKey("pending_intent")) {
            try {
                val pi = extras.getParcelable<PendingIntent>("pending_intent") ?: return
                startIntentSenderForResult(pi.intentSender, REQUEST_CODE_DECRYPT_AND_VERIFY, null, 0, 0, 0)
            } catch (e: IntentSender.SendIntentException) {
                Log.e(AutofillService.Constants.TAG, "SendIntentException", e)
            }
        } else if (extras != null && extras.containsKey("pick")) {
            val intent = Intent(applicationContext, PasswordStore::class.java)
            intent.putExtra("matchWith", true)
            startActivityForResult(intent, REQUEST_CODE_PICK)
        } else if (extras != null && extras.containsKey("pickMatchWith")) {
            val intent = Intent(applicationContext, PasswordStore::class.java)
            intent.putExtra("matchWith", true)
            startActivityForResult(intent, REQUEST_CODE_PICK_MATCH_WITH)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        finish() // go back to the password field app
        when (requestCode) {
            REQUEST_CODE_DECRYPT_AND_VERIFY -> if (resultCode == RESULT_OK) {
                AutofillService.instance?.setResultData(data!!) // report the result to service
            }
            REQUEST_CODE_PICK -> if (resultCode == RESULT_OK) {
                AutofillService.instance?.setPickedPassword(data!!.getStringExtra("path"))
            }
            REQUEST_CODE_PICK_MATCH_WITH -> if (resultCode == RESULT_OK) {
                // need to not only decrypt the picked password, but also
                // update the "match with" preference
                val extras = intent.extras ?: return
                val packageName = extras.getString("packageName")
                val isWeb = extras.getBoolean("isWeb")

                val path = data!!.getStringExtra("path")
                AutofillService.instance?.setPickedPassword(data.getStringExtra("path"))

                val prefs: SharedPreferences
                prefs = if (!isWeb) {
                    applicationContext.getSharedPreferences("autofill", Context.MODE_PRIVATE)
                } else {
                    applicationContext.getSharedPreferences("autofill_web", Context.MODE_PRIVATE)
                }
                val editor = prefs.edit()
                when (val preference = prefs.getString(packageName, "")) {
                    "", "/first", "/never" -> editor.putString(packageName, path)
                    else -> {
                        val matches = ArrayList(Arrays.asList(*preference!!.trim { it <= ' ' }.splitLines()))
                        matches.add(path)
                        val paths = StringUtils.join(matches, "\n")
                        editor.putString(packageName, paths)
                    }
                }
                editor.apply()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        const val REQUEST_CODE_DECRYPT_AND_VERIFY = 9913
        const val REQUEST_CODE_PICK = 777
        const val REQUEST_CODE_PICK_MATCH_WITH = 778
    }
}
