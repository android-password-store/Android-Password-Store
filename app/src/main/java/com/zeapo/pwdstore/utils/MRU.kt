package com.zeapo.pwdstore.utils

import android.content.SharedPreferences
import java.util.LinkedList

class MRU private constructor() : LinkedList<String>() {
    companion object {

        @JvmStatic
        private val maxSize: Int = 5

        @JvmStatic
        private var mRU: MRU? = null

        @JvmStatic
        var settings: SharedPreferences? = null

        @JvmStatic
        fun MRUInit(settings: SharedPreferences) {
            this.settings = settings
        }

        @JvmStatic
        fun getInstance(): MRU? {
            if (mRU == null) {
                mRU = MRU()
                for (i in 4 downTo 0) {
                    settings!!.getString("R$i", null)?.let { mRU!!.add(it) }
                }
            }
            return mRU
        }

    }

    /**
     * Function to add a new file's absolute path inside the Most Recently Used Passwords.
     * The head of the list is the most recently used (MRU), going down to the last recently used (LRU).
     * If list contains already the path, this will be send to the top of the list (i.e becomes the MRU)
     * When the list contains [maxSize] elements, if new path is inserted (and it's not already in the list),
     * the LRU will be erased.
     */
    override fun add(element: String): Boolean {
        when {
            mRU!!.contains(element) -> {
                //remove this element from current position
                mRU!!.remove(element)
                //put in head
                mRU!!.push(element)
            }
            mRU!!.size < maxSize -> {
                mRU!!.push(element)
            }
            else -> {
                mRU!!.removeLast()
                mRU!!.push(element)
            }
        }
        save()
        return true
    }

    //only for testing
    fun print() {
        for (i in 0 until mRU!!.size) {
            println("R$i ${mRU!![i]}")
        }
    }

    /**
     * Save all the recently used passwords inside sharedPreferences with key R#
     * Where 0 is the most recently used, to [size]-1 is last recently used
     */
    private fun save() {
        val editor = settings?.edit()

        for (i in 0 until mRU!!.size) {
            editor?.putString("R$i", mRU!![i])
        }

        editor?.apply()
    }

}