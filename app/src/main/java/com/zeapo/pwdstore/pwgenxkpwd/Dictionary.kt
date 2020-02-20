package com.zeapo.pwdstore.pwgenxkpwd

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import androidx.preference.PreferenceManager
import com.zeapo.pwdstore.R
import java.util.*

class Dictionary(context: Context) {
    val words: HashMap<Int, ArrayList<String>>

    init {
        words = HashMap()

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        var lines: List<String>? = null

        if (prefs.getBoolean("pref_key_is_custom_dict", false)) {

            val uri = prefs.getString("pref_key_custom_dict", "")

            if (!TextUtils.isEmpty(uri)) {
                val customDictStream = context.contentResolver.openInputStream(Uri.parse(uri))

                if (customDictStream != null) {
                    lines = customDictStream.bufferedReader().readLines()
                }
            }
        }

        if (lines == null) {
            lines = context.getResources().openRawResource(R.raw.xkpwdict).bufferedReader().readLines();

        }

        for (word in lines) {
            if (!word.trim { it <= ' ' }.contains(" ")) {
                val length = word.trim { it <= ' ' }.length

                if (length > 0) {
                    if (!words.containsKey(length)) {
                        words[length] = ArrayList()
                    }
                    val strings = words[length]!!
                    strings.add(word.trim { it <= ' ' })
                }
            }
        }
    }
}
