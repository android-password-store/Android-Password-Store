package com.zeapo.pwdstore.utils

import android.content.SharedPreferences
import java.util.LinkedList

object MRU {

    private const val maxSize: Int = 5
    var mRU: LinkedList<String> = LinkedList<String>()
    var settings: SharedPreferences? = null

    fun mRUInit(settings: SharedPreferences) {
        if (this.settings == null){
            this.settings = settings
            for (i in 4 downTo 0) {
                MRU.settings!!.getString("R$i", null)?.let { mRU.add(it) }
            }
        }
    }

    /**
     * Function to add a new file's absolute path inside the Most Recently Used Passwords.
     * The head of the list is the most recently used (MRU), going down to the last recently used (LRU).
     * If list contains already the path, this will be send to the top of the list (i.e becomes the MRU)
     * When the list contains [maxSize] elements, if new path is inserted (and it's not already in the list),
     * the LRU will be erased.
     */
    fun add(element: String): Boolean {
        val test = mRU
        when {
            mRU.contains(element) -> {
                //remove this element from current position
                mRU.remove(element)
                //put in head
                mRU.push(element)
            }
            mRU.size < maxSize -> {
                mRU.push(element)
            }
            else -> {
                mRU.removeLast()
                mRU.push(element)
            }
        }
        save()
        return true
    }

    //only for testing
    fun print() {
        for (i in 0 until mRU.size) {
            println("R$i ${mRU[i]}")
        }
    }

    /**
     * Save all the recently used passwords inside sharedPreferences with key R#
     * Where 0 is the most recently used, until [mRU.size] is last recently used
     */
    fun save() {
        val editor = settings?.edit()

        for (i in 0 until mRU.size) {
            editor?.putString("R$i", mRU[i])
        }

        editor?.apply()
    }

}